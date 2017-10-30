/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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

package probcog.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Pair;
import probcog.exception.ProbCogException;
import probcog.srl.Database;
import probcog.srl.Signature;
import probcog.srl.Variable;
import probcog.srl.directed.RelationalNode;
import probcog.srl.directed.bln.BayesianLogicNetwork;
import probcog.srl.directed.bln.GroundBLN;
import probcog.srl.directed.inference.BLNinfer;

/**
 * Represents a Bayesian logic network model for use in the ProbCog service.
 * @author Dominik Jain
 */
public class BLNModel extends Model {

	protected BayesianLogicNetwork bln;
	protected GroundBLN gbln;
	protected Database db;
	protected String filenames;
	
	public BLNModel(String modelName, String blogFile, String networkFile, String logicFile) throws ProbCogException {
		super(modelName);
		this.filenames = String.format("%s;%s;%s", blogFile, networkFile, logicFile);
		this.bln = new BayesianLogicNetwork(blogFile, networkFile, logicFile);		
	}
	
	@Override
	public void instantiate() throws ProbCogException {
		gbln = bln.ground(db);
		paramHandler.addSubhandler(gbln);
		gbln.instantiateGroundNetwork();
	}
	
	@Override
	public void beginSession(Map<String, Object> params) throws ProbCogException {
		super.beginSession(params);
		db = new Database(bln.rbn);
		paramHandler.addSubhandler(db);
	}

	@Override
	protected Vector<InferenceResult> _infer(Iterable<String> queries) throws ProbCogException {		
		BLNinfer inference = new BLNinfer(actualParams);
		paramHandler.addSubhandler(inference);		
		inference.setGroundBLN(gbln);
		inference.setQueries(queries);
		Collection<probcog.srl.directed.inference.InferenceResult> results = inference.run();
		
		// store results in common InferenceResult format
		Vector<InferenceResult> ret = new Vector<InferenceResult>();
		for(probcog.srl.directed.inference.InferenceResult res : results) {
			 Pair<String, String[]> var = RelationalNode.parse(res.varName);
			 Signature sig = bln.rbn.getSignature(var.first);
			 String[] params = var.second;
			 boolean isBool = sig.isBoolean();
			 if(!isBool) {
				 String[] fullParams = new String[params.length+1];
				 for(int i = 0; i < params.length; i++)
					 fullParams[i] = params[i];
				 params = fullParams;
			 }
			 for(int i = 0; i < res.domainElements.length; i++) {
				 if(!isBool) 
					 params[params.length-1] = res.domainElements[i];					 
				 else
					 if(!res.domainElements[i].equalsIgnoreCase("True"))
						 continue;
				 ret.add(new InferenceResult(var.first, params.clone(), res.probabilities[i]));
			 }
		}
		return ret;
	}

	@Override
	protected void _setEvidence(Iterable<String[]> evidence) throws ProbCogException {
		for(String[] tuple : evidence) {
			String functionName = tuple[0];
			Signature sig = bln.rbn.getSignature(functionName);
			if(sig == null)
				throw new ProbCogException("Function '" + functionName + "' appearing in evidence not found in model " + name);
			String value;
			String[] params;
			if(sig.argTypes.length == tuple.length-1) {
				params = new String[tuple.length-1];
				for(int i = 0; i < params.length; i++)
					params[i] = tuple[i+1];
				value = "True";
			}
			else {
				if(tuple.length < sig.argTypes.length+2)
					throw new ProbCogException("Evidence entry has too few parameters: " + StringTool.join(", ", tuple));
				params = new String[sig.argTypes.length];
				for(int i = 0; i < params.length; i++)
					params[i] = tuple[i+1];
				value = tuple[params.length+1];
			}
			db.addVariable(new Variable(functionName, params, value, this.bln.rbn));
		}
	}

	@Override
	public Vector<String[]> getPredicates() {
		return getPredicatesFromSignatures(this.bln.rbn.getSignatures());
	}
	
	public Vector<String[]> getDomains() {
		Vector<String[]> ret = new Vector<String[]>();
		for(Entry<String,? extends Collection<String>> e : this.bln.rbn.getGuaranteedDomainElements().entrySet()) {
			Collection<String> elems = e.getValue();
			ArrayList<String> tuple = new ArrayList<String>(elems.size()+1);
			tuple.add(e.getKey());
			for(String elem : elems) {
				String c = mapConstantFromProbCog(elem);
				if(c == null)
					continue;
				tuple.add(c);
			}				
			ret.add(tuple.toArray(new String[tuple.size()]));
		}
		return ret;
	}

	@Override
	protected String _getConstantType(String constant) {
		return db.getConstantType(constant);
	}
    
    @Override
    public String toString() {
    	return String.format("%s=BLN[%s]", this.name, this.filenames);
    }
}
