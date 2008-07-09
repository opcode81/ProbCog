package edu.tum.cs.probcog;

import java.io.IOException;
import java.util.Vector;

import edu.tum.cs.bayesnets.relational.core.ABL;
import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.bayesnets.relational.core.RelationalNode;
import edu.tum.cs.bayesnets.relational.core.Signature;
import edu.tum.cs.bayesnets.relational.core.bln.BayesianLogicNetwork;
import edu.tum.cs.bayesnets.relational.core.bln.GroundBLN;
import edu.tum.cs.bayesnets.relational.inference.LikelihoodWeighting;
import edu.tum.cs.bayesnets.relational.inference.Sampler;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.tools.Pair;

public class BLNModel extends Model {

	protected BayesianLogicNetwork bln;
	protected GroundBLN gbln;
	protected Database db;
	
	public BLNModel(String blogFile, String networkFile, String logicFile) throws IOException, ParseException, Exception {
		super(networkFile);
		this.bln = new BayesianLogicNetwork(new ABL(blogFile, networkFile), logicFile);
	}
	
	@Override
	public void instantiate() throws Exception {
		gbln = bln.instantiate(db);
	}

	@Override
	public Vector<InferenceResult> infer(Iterable<String> queries) throws Exception {
		Sampler sampler;
		// determine inference method and instantiate sampler
		String inferenceMethod = getParameter("inferenceMethod", "LW");
		if(inferenceMethod.equals("LW")) {
			sampler = new LikelihoodWeighting(gbln);
		}
		else {
			throw new Exception("Specified inference method unhandled");
		}
		// run inference
		Vector<edu.tum.cs.bayesnets.relational.inference.InferenceResult> results = sampler.infer(queries, getIntParameter("numSamples", 1000), 100);
		// store results
		Vector<InferenceResult> ret = new Vector<InferenceResult>();
		for(edu.tum.cs.bayesnets.relational.inference.InferenceResult res : results) {
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
				 ret.add(new InferenceResult(var.first, params, res.probabilities[i]));
			 }
		}
		return ret;
	}

	@Override
	public void setEvidence(Iterable<String[]> evidence) throws Exception {
		db = new Database(bln.rbn);
		for(String[] tuple : evidence) {
			String functionName = tuple[0];
			Signature sig = bln.rbn.getSignature(functionName);
			if(sig == null)
				throw new Exception("Function " + functionName + " appearing in evidence not found in model " + name);
			String value;
			String[] params;
			if(!sig.isBoolean()) { // non-boolean function
				params = new String[tuple.length-2];
				for(int i = 0; i < params.length; i++)
					params[i] = tuple[i+1];
				value = tuple[tuple.length-1];
			}
			else { // predicate
				value = "True";
				params = new String[tuple.length-1];
				for(int i = 0; i < params.length; i++)
					params[i] = tuple[i+1];
			}
			db.addVariable(new Database.Variable(functionName, params, value));
		}
	}
}
