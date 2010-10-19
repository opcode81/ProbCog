/*
 * Created on Oct 4, 2010
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.probcog;

import java.util.Vector;

import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;
import edu.tum.cs.srl.mln.inference.InferenceAlgorithm;
import edu.tum.cs.srl.mln.inference.MCSAT;

public class MLNModel extends Model {

	protected MarkovLogicNetwork mln;
	protected Database db;
	protected MarkovRandomField mrf;
	
	public MLNModel(String name, String mln) throws Exception {
		super(name);
		this.mln = new MarkovLogicNetwork(mln);
	}

	@Override
	protected String _getConstantType(String constant) {
		return db.getConstantType(constant);
	}

	@Override
	protected Vector<InferenceResult> _infer(Iterable<String> queries) throws Exception {
		InferenceAlgorithm ia = new MCSAT(mrf);
		Vector<InferenceResult> res = new Vector<InferenceResult>();
		int maxSteps = getIntParameter("maxSteps", 5000);
		for(edu.tum.cs.srl.mln.inference.InferenceResult r : ia.infer(queries, maxSteps)) {
			InferenceResult r2 = new InferenceResult(r.ga.predicate, r.ga.args, r.value);
			res.add(r2);
		}
		return res;
	}

	@Override
	protected void _setEvidence(Iterable<String[]> evidence) throws Exception {
		db = new Database(mln);
		for(String[] tuple : evidence) {
			String functionName = tuple[0];
			Signature sig = mln.getSignature(functionName);
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
				params = new String[tuple.length-2];
				for(int i = 0; i < params.length; i++)
					params[i] = tuple[i+1];
				value = tuple[tuple.length-1];
			}
			db.addVariable(new Database.Variable(functionName, params, value, mln));
		}
	}

	@Override
	public Vector<String[]> getDomains() {
		throw new RuntimeException("not implemented"); // TODO 
	}

	@Override
	public Vector<String[]> getPredicates() {		
		return getPredicatesFromSignatures(mln.getSignatures());
	}

	@Override
	public void instantiate() throws Exception {
		mrf = mln.ground(db);		
	}
}
