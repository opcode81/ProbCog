/*******************************************************************************
 * Copyright (C) 2009-2012 Ralf Wernicke, Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.srl.mln;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.logic.Formula;
import probcog.logic.parser.ParseException;
import probcog.logic.sat.weighted.WeightedFormula;
import probcog.srl.Database;
import probcog.srl.RelationKey;
import probcog.srl.RelationalModel;
import probcog.srl.Signature;
import probcog.srl.taxonomy.Taxonomy;
import probcog.tools.JythonInterpreter;

import edu.tum.cs.util.FileUtil;

/**
 * represents a Markov logic network
 * @author Ralf Wernicke
 * @author Dominik Jain
 */
public class MarkovLogicNetwork implements RelationalModel {

    protected File mlnFile;
    //protected HashMap<Formula, Double> formula2weight;
    protected Vector<WeightedFormula> formulas;
    /**
     * maps a predicate name to its signature
     */
    protected HashMap<String, Signature> signatures;
    /**
     * maps domain/type names to a list of guaranteed domain elements
     */
    protected HashMap<String, HashSet<String>> guaranteedDomainElements;
    /**
     * mapping from predicate name to index of argument that is functionally determined
     */
    protected HashMap<String, Integer> functionalPreds;
    double sumAbsWeights = 0;

    /**
     * constructs a Markov logic network from an MLN file
     * @param mlnFileLoc location of the MLN-file
     * @throws Exception 
     */
    public MarkovLogicNetwork(String mlnFileLoc) throws Exception {
    	this();
        // read the complete MLN-File and save it in a String
    	mlnFile = new File(mlnFileLoc);
        String content = FileUtil.readTextFile(mlnFile);
        read(content);
    }
    
    public MarkovLogicNetwork(String[] mlnFiles) throws Exception {
    	this();
    	mlnFile = new File(mlnFiles[0]);
    	StringBuffer content = new StringBuffer();
    	for(String filename : mlnFiles) {
    		content.append(FileUtil.readTextFile(filename));
    		content.append("\n");
    	}
    	read(content.toString());
    }
    
    /**
     * constructs an empty MLN
     */
    public MarkovLogicNetwork() {
    	mlnFile = null;
        signatures = new HashMap<String, Signature>();
        functionalPreds = new HashMap<String, Integer>();        
        guaranteedDomainElements = new HashMap<String, HashSet<String>>();
        formulas = new Vector<WeightedFormula>();
    }
    
    /**
     * adds a predicate signature to this model 
     * @param sig signature of this predicate
     */
    public void addSignature(Signature sig) {
        signatures.put(sig.functionName, sig);
    }
    
    public void addFormula(Formula f, double weight) throws Exception {
    	f.addConstantsToModel(this);
    	this.formulas.add(new WeightedFormula(f, weight, false));
    }
    
    public void addHardFormula(Formula f) throws Exception {
    	f.addConstantsToModel(this);
    	this.formulas.add(new WeightedFormula(f, getHardWeight(), true));
    }
    
    public void addFunctionalDependency(String predicateName, Integer index) {
    	this.functionalPreds.put(predicateName, index);
    }
    
    public void addGuaranteedDomainElement(String domain, String element) {
    	HashSet<String> s = guaranteedDomainElements.get(domain);
    	if(s == null)
    		guaranteedDomainElements.put(domain, s=new HashSet<String>());
    	s.add(element);
    }
    
    public void addGuaranteedDomainElements(String domain, String[] elements) {
    	for(String e : elements)
    		addGuaranteedDomainElement(domain, e);
    }
    
    public Vector<WeightedFormula> getFormulas() {
    	return formulas;
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
     * @return the index of the argument that is functionally determined or null if there is no such argument
     */
    public Integer getFunctionallyDeterminedArgument(String predicateName) {
        return this.functionalPreds.get(predicateName);
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
     * reads the contents of an MLN file
     * @throws Exception 
     */
    public void read(String content) throws Exception {
        String actLine;
        ArrayList<Formula> hardFormulas = new ArrayList<Formula>();
    	
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
                    }
                }
                sig = new Signature(predName, "boolean", argTypes);
                addSignature(sig);
                continue;
            }
            
            // domain declaration
            m = domDecl.matcher(line);
            if(m.matches()) { 
                addGuaranteedDomainElements(m.group(1), m.group(2).trim().split("\\s*,\\s*"));
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
            addFormula(f, weight);
            sumAbsWeights += Math.abs(weight);
        }
        
        for (Formula f : hardFormulas)
            addHardFormula(f);
    }

    /**
     * @return the weight used for hard constraints
     */
    public double getHardWeight() {
        return sumAbsWeights + 100000; // TODO this number should be selected with extreme care (especially for MPE inference it is very relevant); we should set it to the sum of abs. weights of soft formulas in the *ground* model + X
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
	@Override
    public HashMap<String, HashSet<String>> getGuaranteedDomainElements() {
        return guaranteedDomainElements;
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

    /**
     * @return null because MLNs do not use a taxonomy
     */
	public Taxonomy getTaxonomy() {
		return null;
	}
	
	public void write(PrintStream out) {
		MLNWriter writer = new MLNWriter(out);
		
		// domain declarations
		if(this.guaranteedDomainElements.size() > 0) {
			out.println("// domain declarations");
			for(java.util.Map.Entry<String,? extends Iterable<String>> e : this.getGuaranteedDomainElements().entrySet()) {
				writer.writeDomainDecl(e.getKey(), e.getValue());			
			}
			out.println();
		}
		
		// predicate declarations
		out.println("// predicate declarations");
		for(Signature sig : this.getSignatures()) {
			writer.writePredicateDecl(sig, this.getFunctionallyDeterminedArgument(sig.functionName));
		}
		out.println();
		
		out.println("// formulas");
		for(WeightedFormula wf : getFormulas()) {
			if(wf.isHard)
				out.printf("%s.\n", wf.formula.toString());
			else
				out.printf("%f  %s\n", wf.weight, wf.formula.toString());
		}
	}
	
	public void write(File f) throws FileNotFoundException {
		write(new PrintStream(f));
	}

	@Override
	public Collection<String> getPrologRules() {
		return null;
	}
}
