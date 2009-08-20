package edu.tum.cs.srl.mln;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.srl.RelationKey;
import edu.tum.cs.srl.RelationalModel;
import edu.tum.cs.srl.Signature;

/**
 * represents a Markov logic network
 * @author wernickr
 */
public class MarkovLogicNetwork implements RelationalModel {

    File mlnFile;
    ArrayList<Formula> formulas;
    HashMap<Formula, Double> formula2weight;
    /**
     * maps a predicate name to its signature
     */
    HashMap<String, Signature> signatures;
    /**
     * maps domain/type names to a list of guaranteed domain elements
     */
    protected HashMap<String, String[]> decDomains;
    protected HashMap<String, String> block;
    boolean makelist;
    GroundingCallback gc;
    double sumAbsWeights = 0;

    /**
     * constructs an MLN
     * @param mlnFileLoc location of the MLN-file
     * @param makelist if true, a list of grounded formulas would be generated through the grounding
     * @param gc an optional Callback-function (if not null), which is applied to every grounded formula
     * @throws Exception 
     */
    public MarkovLogicNetwork(String mlnFileLoc, boolean makelist, GroundingCallback gc) throws Exception {
        mlnFile = new File(mlnFileLoc);
        signatures = new HashMap<String, Signature>();
        block = new HashMap<String, String>();        
        decDomains = new HashMap<String, String[]>();
        formulas = new ArrayList<Formula>();
        formula2weight = new HashMap<Formula, Double>();
        read(mlnFile);
        this.makelist = makelist;
        this.gc = gc;
    }
    
    /**
     * constructs an MLN from the given text file
     * @param mlnFile
     * @throws Exception 
     */
    public MarkovLogicNetwork(String mlnFile) throws Exception {
    	this(mlnFile, true, null);
    }

    /**
     * adds a predicate signature to this model 
     * @param predicateName name of the predicate
     * @param sig signature of this predicate
     */
    public void addSignature(String predicateName, Signature sig) {
        signatures.put(predicateName, sig);
    }

    /**
     * returns the signature for the given predicate
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
     * Method that return the position of a column in a given Array
     * @param column name of column (position of this column will be searched)
     * @param stringArray array to search for the given column
     * @return returns an integer that represents position of the column in the given array
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
     * @throws Exception 
     */
    public MarkovRandomField ground(String dbFileLoc) throws Exception {
        MarkovRandomField mrf = new MarkovRandomField(this, dbFileLoc, makelist, gc);
        return mrf;
    }

    /**
     * Method parses a MLN-file
     * @throws Exception 
     */
    public void read(File mlnFile) throws Exception {
        String actLine;
        ArrayList<Formula> hardCon = new ArrayList<Formula>();
    	
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
        
        // parse line by line         
        for(actLine = breader.readLine(); breader != null && actLine != null; actLine = breader.readLine()) {
            if(actLine.equals(""))
            	continue;
            
            String line = actLine;
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
                    sumAbsWeights += Math.abs(weight);
                } else {
                    throw new IllegalArgumentException();
                }

            } else if (m3.matches()) { // parse predicate with signature and predicate name
                Pattern pat = Pattern.compile("\\s*(\\w+)\\s*(\\w*)\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);
                matcher = pat.matcher(line);
                if (matcher.find()) {
                    String predicate2 = matcher.group(1);
                    Signature sig = getSignature(predicate2);
                    if(sig != null) {
                    	throw new Exception(String.format("Signature declared in line '%s' was previously declared as '%s'", line, sig.toString()));
                    }
                    String[] argTypes = matcher.group(3).trim().split("\\s*,\\s*");
                    for (int c = 0; c < argTypes.length; c++) {
                        // check whether it's a blockvariable
                        if (argTypes[c].contains("!")) {
                            argTypes[c] = argTypes[c].replace("!", "");
                            block.put(predicate2, argTypes[c]);
                            break;
                        }
                    }
                    sig = new Signature(predicate2, "boolean", argTypes);
                    addSignature(matcher.group(1), sig);
                }
                
            } else if (m4.matches()) {
                continue;
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
        }
        
        // assign weights of the hard constraints
        double hardWeight = getHardWeight();
        for (Formula f : hardCon)
            formula2weight.put(f, hardWeight);
    }

    /**
     * returns the weight used for hard constraints
     * @return
     */
    public double getHardWeight() {
        return sumAbsWeights + 100;
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
	 * replace a type by a new type in all function signatures
	 * @param oldType
	 * @param newType
	 */
	public void replaceType(String oldType, String newType) {
		for(Signature sig : signatures.values()) 
			sig.replaceType(oldType, newType);		
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
