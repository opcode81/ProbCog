package edu.tum.cs.srl.mln;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.RelationKey;
import edu.tum.cs.srl.RelationalModel;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.tools.FileUtil;
import edu.tum.cs.tools.JythonInterpreter;

/**
 * represents a Markov logic network
 * @author wernickr, jain
 */
public class MarkovLogicNetwork implements RelationalModel {

    File mlnFile;
    ArrayList<Formula> formulas;
    protected HashMap<Formula, Double> formula2weight;
    /**
     * maps a predicate name to its signature
     */
    protected HashMap<String, Signature> signatures;
    /**
     * maps domain/type names to a list of guaranteed domain elements
     */
    protected HashMap<String, String[]> decDomains;
    /**
     * mapping from predicate name to index of argument that is functionally determined
     */
    protected HashMap<String, Integer> functionalPreds;
    double sumAbsWeights = 0;

    /**
     * constructs an MLN
     * @param mlnFileLoc location of the MLN-file
     * @param makelist if true, a list of grounded formulas would be generated through the grounding
     * @param gc an optional Callback-function (if not null), which is applied to every grounded formula
     * @throws Exception 
     */
    public MarkovLogicNetwork(String mlnFileLoc) throws Exception {
        mlnFile = new File(mlnFileLoc);
        signatures = new HashMap<String, Signature>();
        functionalPreds = new HashMap<String, Integer>();        
        decDomains = new HashMap<String, String[]>();
        formulas = new ArrayList<Formula>();
        formula2weight = new HashMap<Formula, Double>();
        read(mlnFile);
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
     * gets the functionally determined argument of a functional predicate 
     * @return the index of the argument that is functionally determined
     */
    public Integer getFunctionallyDeterminedArgument(String predicateName) {
        return this.functionalPreds.get(predicateName);
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
    public MarkovRandomField ground(Database db) throws Exception {
    	return ground(db, true, null);
    }
    
    public MarkovRandomField ground(Database db, boolean storeFormulasInMRF, GroundingCallback gc) throws Exception {
        return new MarkovRandomField(this, db, storeFormulasInMRF, gc);
    }

    /**
     * Method parses a MLN-file
     * @throws Exception 
     */
    public void read(File mlnFile) throws Exception {
        String actLine;
        ArrayList<Formula> hardFormulas = new ArrayList<Formula>();
    	
        // read the complete MLN-File and save it in a String
        String content = FileUtil.readTextFile(mlnFile);
        
        // remove all comments
        Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = comments.matcher(content);
        content = matcher.replaceAll("");
        BufferedReader breader = new BufferedReader(new StringReader(content));

        String identifier = "\\w+";
        String constant = "(?:[A-Z]\\w*|[0-9]+)";
        // predicate declaration
        Pattern predDecl = Pattern.compile(String.format("(%s)\\(\\s*(%s!?(?:\\s*,\\s*%s!?)*)\\s*\\)", identifier, identifier, identifier));
        // domain declaration
        Pattern domDecl = Pattern.compile(String.format("(%s)\\s*=\\s*\\{\\s*(%s(?:\\s*,\\s*%s)*)\\s*\\}", identifier, constant, constant));
        
        JythonInterpreter jython = null;
        
        // parse line by line         
        for(actLine = breader.readLine(); breader != null && actLine != null; actLine = breader.readLine()) {
        	String line = actLine.trim();
        	if(line.length() == 0)
            	continue;            

            // hard constraint
            if(line.endsWith(".")) {
            	Formula f;
                String strF = line.substring(0, line.length() - 1);
                try {
                	f = Formula.fromString(strF);
                }
                catch(ParseException e) {
                	throw new Exception("The hard formula '" + strF + "' could not be parsed: " + e.toString());
                }
                formulas.add(f);
                hardFormulas.add(f);
                continue;
            } 
            
            // predicate declaration
            Matcher m = predDecl.matcher(line);
            if(m.matches()) {                 
                String predName = m.group(1);
                Signature sig = getSignature(predName);
                if(sig != null) {
                	throw new Exception(String.format("Signature declared in line '%s' was previously declared as '%s'", line, sig.toString()));
                }
                String[] argTypes = m.group(2).trim().split("\\s*,\\s*");
                for (int c = 0; c < argTypes.length; c++) {
                    // check whether it's a blockvariable
                    if(argTypes[c].endsWith("!")) {
                        argTypes[c] = argTypes[c].replace("!", "");
                        Integer oldValue = functionalPreds.put(predName, c);
                        if(oldValue != null)
                        	throw new Exception(String.format("Predicate '%s' was declared to have more than one functionally determined parameter", predName));
                        break;
                    }
                }
                sig = new Signature(predName, "boolean", argTypes);
                addSignature(predName, sig);
                continue;
            }
            
            // domain declaration
            m = domDecl.matcher(line);
            if(m.matches()) { 
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
                continue;
            }
            
            // must be a weighted formula
            int iSpace = line.indexOf(' ');
            if(iSpace == -1)
            	throw new Exception("This line is not a correct declaration of a weighted formula: " + line);
            String strWeight = line.substring(0, iSpace);
            Double weight = null;
            try {
            	weight = Double.parseDouble(strWeight);
            }            
            catch(NumberFormatException e) {
            	if(jython == null) {
            		jython = new JythonInterpreter();
            		jython.exec("from math import *");
            		jython.exec("def logx(x):\n  if x == 0: return -100\n  return log(x)");
            	}
            	try {
            		weight = jython.evalDouble(strWeight);
            	}
            	catch(Exception e2) {
            		throw new Exception("Could not interpret weight '" + strWeight + "': " + e2.toString());
            	}            
            }
            String strF = line.substring(iSpace+1).trim();
            Formula f;
            try {
            	f = Formula.fromString(strF);
            }
            catch(ParseException e) {
            	throw new Exception("The formula '" + strF + "' could not be parsed: " + e.toString());
            }
            formulas.add(f);
            formula2weight.put(f, weight);
            sumAbsWeights += Math.abs(weight);
        }
        
        // assign weights to hard constraints
        double hardWeight = getHardWeight();
        for (Formula f : hardFormulas)
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
     * @return a mapping from domain names to arrays of elements
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
    
    /**
     * @return the set of functional predicates (i.e. their names)
     */
    public Set<String> getFunctionalPreds() {
    	return functionalPreds.keySet();
    }
    
    public Collection<Signature> getSignatures() {
    	return this.signatures.values();
    }
}
