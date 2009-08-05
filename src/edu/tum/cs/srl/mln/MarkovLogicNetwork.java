/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.tum.cs.srl.mln;

import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork.RelationKey;
import edu.tum.cs.bayesnets.relational.core.Signature;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.srl.RelationalModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to instantiate a Markov Logic Network from an existing MLN-File
 * @author wernickr
 */
public class MarkovLogicNetwork implements RelationalModel {

    File mlnFile;
    ArrayList<Formula> formulas;
    HashMap<Formula, Double> formula2weight;
    MLNFileParser parser;
    HashMap<String, Signature> signatures;
    HashMap<String, String[]> decDomains;
    HashMap<String, String> block;
    boolean makelist;
    GroundingCallback gc;
    double sumWeights = 0;

    /**
     * Constructor for the MLN
     * @param mlnFileLoc location of the MLN-file
     * @param makelist if true, a list of grounded formulas would be generated through the grounding
     * @param gc an optional Callback-function (if not null), which is applied to every grounded formula
     */
    public MarkovLogicNetwork(String mlnFileLoc, boolean makelist, GroundingCallback gc) {
        mlnFile = new File(mlnFileLoc);
        signatures = new HashMap<String, Signature>();
        block = new HashMap<String, String>();        
        decDomains = new HashMap<String, String[]>();
        formulas = new ArrayList<Formula>();
        formula2weight = new HashMap<Formula, Double>();
        parser = new MLNFileParser(mlnFile);
        this.makelist = makelist;
        this.gc = gc;

    }
    
    public MarkovLogicNetwork(String mlnFile) {
    	this(mlnFile, false, null);
    }

    /**
     * Method adds signature for an atom 
     * @param predicateName name of the predicate
     * @param sig signature of this predicate
     */
    public void addSignature(String predicateName, Signature sig) {
        signatures.put(predicateName, sig);
    }

    /**
     * Method returns signature for a given predicate
     * @param predName name of predicate (signature for this predicate will be returned)
     * @return
     */
    public Signature getSignature(String predName) {
        return signatures.get(predName);
    }

    /**
     * Method returns blockvariables of MLN-file
     * @return 
     */
    public HashMap<String, String> getBlock() {
        return block;
    }

    /**
     * Method that retrurn the position of a colum in a given Array
     * @param column name of column (position of this column will be searched)
     * @param stringArray array to search for the given column
     * @return returns an int that represents position of the column in the given array
     */
    public int getPosinArray(String column, String[] stringArray) {
        int c = -1;
        for (int i = 0; i <= stringArray.length - 1; i++) {
            if (column.equals(stringArray[i]))
                return i;
        }
        return c;
    }

    /**
     * Method that grounds MLN to a MarkovRandomField
     * @param dbFileLoc file location of evidence for this scenario
     * @return returns a grounded MLN as a MarkovRandomField MRF
     */
    public MarkovRandomField groundMLN(String dbFileLoc) {
        MarkovRandomField mrf = new MarkovRandomField(this, dbFileLoc, makelist, gc);
        return mrf;
    }

    /**
     * Parser to read a MLN-File
     * @author wernickr
     */
    public class MLNFileParser {

        private File mlnFile;
        String actLine;
        ArrayList<Formula> hardCon;

        /**
         * Constructor of the MLNFileParser
         * @param mlnFile filelocation of the MLN-file to parse
         */
        public MLNFileParser(File mlnFile) {
            hardCon = new ArrayList<Formula>();
            this.mlnFile = mlnFile;
            try {
                parsemlnFile();
            } catch (FileNotFoundException ex) {
                Logger.getLogger(MarkovLogicNetwork.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(MarkovLogicNetwork.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        /**
         * Method parses a MLN-file
         */
        public void parsemlnFile() throws FileNotFoundException, IOException {
            
            // read the complete MLN-File and save it in a String
            FileReader fr = new FileReader(mlnFile);
            char[] cbuf = new char[(int) mlnFile.length()];
            fr.read(cbuf);
            String content = new String(cbuf);
            fr.close();
            
            // remove all comments
            Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
            Matcher matcher = comments.matcher(content);
            content = matcher.replaceAll("");
            BufferedReader breader = new BufferedReader(new StringReader(content));

            // parse line by line 
            try {
                actLine = breader.readLine();
                while (breader != null && actLine != null) {
                    if (!actLine.equals(""))
                        insertintoList(actLine);
                    actLine = breader.readLine();
                }
            } catch (FileNotFoundException fnfe) {
                System.out.println("Sorry! The file not found, try again!");
                fnfe.printStackTrace();
            } catch (IOException ioe) {
                System.out.println("There was an error during process! Could not parse file!");
                ioe.printStackTrace();
            } catch (Exception e) {
                System.out.println("There was an error during parsing! No valid mlnfile!");
                System.out.println(actLine);
                e.printStackTrace();
            }
        }

        /**
         * Method that parses the given line and saves the result (formula, domain declaration, etc.) in the according sets
         * @param line the stringrepresentation of the line to parse
         * @throws iris.kitool.logic.parser.ParseException
         */
        public void insertintoList(String line) throws ParseException {
            int brleft = 0;
            int brright = 0;

            // check whether number of left brackets and number of right brackets is the same
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '(')
                    brleft++;
                else if (line.charAt(i) == ')')
                    brright++;
            }
            // if the numbers are different throw an exception
            if (brleft != brright) {
                System.out.println("Error parsing file! There is an unequal number of left and right brackets in your predicate/formula!");
                System.out.println("Line: " + line);
                throw new IllegalArgumentException();
            }


            //Pattern for declaration of predicates
            Pattern predicate = Pattern.compile("[a-z]+[\\w]*[(]{1}([a-z|A-Z]+[\\w]*[!]?){1}(,[\\s]*([a-z|A-Z]+[\\w]*[!]?))*[)]{1}");
            //Pattern for valid operators
            Pattern operators = Pattern.compile("[\\s]*([v]{1}|[=>]{2}|[\\^]{1})[\\s]*");
            //Pattern for valid literals
            Pattern literals = Pattern.compile("[(]*[!]?[(]*" + predicate + "[)]*");
            //Pattern for comparison
            Pattern literals2 = Pattern.compile("[!]?[(]*[a-z|A-Z]+[\\w]*[=|<|>][a-z|A-Z]+[\\w]*[)]*");
            // Pattern for exisistence-quantor
            Pattern existence = Pattern.compile("[\\s]*[(]*[!]?[(]*[E][X][I][S][T][\\s]+[a-z|A-Z]+[\\w]*([\\s]+|[(]+)*");
            //Pattern for valid formulas (general)
            Pattern formula = Pattern.compile("(" + existence + ")*" + "(" + literals + "|" + literals2 + ")" + "(" + operators + "(" + existence + ")*" + "(" + literals + "|" + literals2 + ")" + ")*[\\s]*");
            //Pattern for valid numbers
            Pattern validNumber = Pattern.compile("[-]?([0-9]+[.]?[0-9]*){1,18}");
            //Pattern for valid weight
            Pattern validWeight = Pattern.compile("[\\s]*[(]*[\\s]*" + validNumber + "[\\s]*[)]*[\\s]+");
            //Pattern for a weighted formula
            Pattern formula2 = Pattern.compile("(" + validWeight + ")" + formula);
            //Pattern for a hard constarint
            Pattern formula3 = Pattern.compile("(" + existence + ")*" + "(" + literals + "|" + literals2 + ")" + "(" + operators + "(" + existence + ")*" + "(" + literals + "|" + literals2 + ")" + ")*.");
            //Pattern for blanks
            Pattern blank = Pattern.compile("[\\s]+");
            // Pattern for domain declaration
            Pattern domain = Pattern.compile("[\\s]*[a-z]+[\\w]*[\\s]*[=][\\s]*[{][\\s]*[\\w]*[\\s]*([,][\\s]*[\\w]*[\\s]*)*[}][\\s]*");

            Matcher m = formula3.matcher(line);
            Matcher m2 = formula2.matcher(line);
            Matcher m3 = predicate.matcher(line);
            Matcher m4 = blank.matcher(line);
            Matcher m5 = domain.matcher(line);
            Matcher m6 = validNumber.matcher(line);
            Matcher m8 = formula.matcher(line);

            
            if (m.matches()) { // it's a hard constraint
                Formula f = Formula.fromString(line.substring(0, line.length() - 1));
                formulas.add(f);
                hardCon.add(f);
            } else if (m2.matches()) { // it's a weighted formula
                if (m6.find()) {
                    // parse weight
                    Double weight = Double.parseDouble(m6.group());
                    m8.find();
                    // parse formula from string
                    Formula f = Formula.fromString(m8.group());
                    // add formula and weight in according sets
                    formulas.add(f);
                    formula2weight.put(f, weight);
                    sumWeights += weight;
                } else {
                    throw new IllegalArgumentException();
                }

            } else if (m3.matches()) { // parse predicate with signature and predicate name
                Pattern pat = Pattern.compile("\\s*(\\w+)\\s*(\\w*)\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pat.matcher(line);
                if (matcher.find()) {
                    String predicate2 = matcher.group(1);
                    String[] argTypes = matcher.group(3).trim().split("\\s*,\\s*");
                    for (int c = 0; c < argTypes.length; c++) {
                        // check whether it's a blockvariable
                        if (argTypes[c].contains("!")) {
                            argTypes[c] = argTypes[c].replace("!", "");
                            block.put(predicate2, argTypes[c]);
                            break;
                        }
                    }
                    Signature sig = new Signature(predicate2, "boolean", argTypes);
                    addSignature(matcher.group(1), sig);
                }
                
            } else if (m4.matches()) {
                return;
            } else if (m5.matches()) { // it's a domain declaration
                Pattern domName = Pattern.compile("[a-z]+\\w+");
                Pattern domCont = Pattern.compile("\\{(\\s*[A-Z]+\\w*\\s*,?)+\\}");
                Matcher mat = domName.matcher(line);
                Matcher mat2 = domCont.matcher(line);
                // parse entries of domain
                if (mat.find() && mat2.find()) {
                    String domarg = mat2.group(0).substring(1, mat2.group(0).length() - 1);
                    String[] cont = domarg.trim().split("\\s*,\\s*");
                    decDomains.put(mat.group(0), cont);
                }
            } else { // if none of the pattern matches, throw an exception
                System.out.println("Error parsing predicate/formula! Line: " + actLine);
                throw new IllegalArgumentException();
            }

            // calculate weights of the hard constraints
            for (Formula f : hardCon)
                formula2weight.put(f, sumWeights + 1);
        }
    }

    /**
     * Method returns the maximum weight of the MLN
     * @return
     */
    public double getMaxWeight() {
        return sumWeights;
    }

    /**
     * Method calculates the minimum difference among all weights
     * @return returns the minimum (delta) difference of all weights
     */
    public double getdeltaMin() {
        double deltaMin = Double.MAX_VALUE;
        TreeSet<Double> weight = new TreeSet<Double>();
        // add weights in a sorted treeset
        for (double d : formula2weight.values())
            weight.add(d);
        // calculate the smallest difference between sorted weightst and set  deltaMin-Value
        while (weight.iterator().hasNext()) {
            Double d = weight.first();
            weight.remove(d);
            if (weight.iterator().hasNext()) {
                if (Math.abs(d - weight.first()) < deltaMin)
                    deltaMin = Math.abs(d - weight.first());
            }
        }
        return deltaMin;
    }

    /**
     * 
     * @param oldType
     * @param newType
     */
    public void replaceType(String oldType, String newType) {
        throw new RuntimeException("Not supported yet.");
    }

    /**
     * 
     * @return
     */
    public HashMap<String, String[]> getGuaranteedDomainElements() {
        return decDomains;
    }

    /**
     * 
     * @param relation
     * @return
     */
    public Collection<RelationKey> getRelationKeys(String relation) {
    	// TODO     
    	return null;
    }
}
