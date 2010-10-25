package edu.tum.cs.probcog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;

import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.ABL;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.srl.bayesnets.inference.BLNinfer;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Pair;

public class BLNModel extends Model {

	protected BayesianLogicNetwork bln;
	protected GroundBLN gbln;
	protected Database db;
	protected String filenames;
	
	public BLNModel(String modelName, String blogFile, String networkFile, String logicFile) throws IOException, ParseException, Exception {
		super(modelName);
		this.filenames = String.format("%s;%s;%s", blogFile, networkFile, logicFile);
		this.bln = new BayesianLogicNetwork(new ABL(blogFile, networkFile), logicFile);		
	}
	
	@Override
	public void instantiate() throws Exception {
		gbln = bln.ground(db);
		paramHandler.addSubhandler(gbln);
		gbln.instantiateGroundNetwork();
	}
	
	@Override
	public void beginSession(Map<String, Object> params) throws Exception {
		super.beginSession(params);
		db = new Database(bln.rbn);
		paramHandler.addSubhandler(db);
	}

	@Override
	protected Vector<InferenceResult> _infer(Iterable<String> queries) throws Exception {		
		BLNinfer inference = new BLNinfer(actualParams);
		paramHandler.addSubhandler(inference);		
		inference.setGroundBLN(gbln);
		inference.setQueries(queries);
		Collection<edu.tum.cs.srl.bayesnets.inference.InferenceResult> results = inference.run();
		
		// store results in common InferenceResult format
		Vector<InferenceResult> ret = new Vector<InferenceResult>();
		for(edu.tum.cs.srl.bayesnets.inference.InferenceResult res : results) {
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
	protected void _setEvidence(Iterable<String[]> evidence) throws Exception {
		for(String[] tuple : evidence) {
			String functionName = tuple[0];
			Signature sig = bln.rbn.getSignature(functionName);
			if(sig == null)
				throw new Exception("Function '" + functionName + "' appearing in evidence not found in model " + name);
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
					throw new Exception("Evidence entry has too few parameters: " + StringTool.join(", ", tuple));
				params = new String[sig.argTypes.length];
				for(int i = 0; i < params.length; i++)
					params[i] = tuple[i+1];
				value = tuple[params.length+1];
			}
			db.addVariable(new Database.Variable(functionName, params, value, this.bln.rbn));
		}
	}

	@Override
	public Vector<String[]> getPredicates() {
		return getPredicatesFromSignatures(this.bln.rbn.getSignatures());
	}
	
	public Vector<String[]> getDomains() {
		Vector<String[]> ret = new Vector<String[]>();
		for(Entry<String,String[]> e : this.bln.rbn.getGuaranteedDomainElements().entrySet()) {
			String[] elems = e.getValue();
			ArrayList<String> tuple = new ArrayList<String>(elems.length+1);
			tuple.add(e.getKey());
			for(int i = 0; i < elems.length; i++) {
				String c = mapConstantFromProbCog(elems[i]);
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
