/*******************************************************************************
 * Copyright (C) 2010-2012 Dominik Jain.
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

import java.util.Map;
import java.util.Vector;

import probcog.exception.ProbCogException;
import probcog.srl.BooleanDomain;
import probcog.srl.Database;
import probcog.srl.Signature;
import probcog.srl.Variable;
import probcog.srl.mln.MarkovLogicNetwork;
import probcog.srl.mln.MarkovRandomField;
import probcog.srl.mln.inference.InferenceAlgorithm;
import probcog.srl.mln.inference.MCSAT;

/**
 * Represents a Markov logic network model for use in the ProbCog service.
 * @author Dominik Jain
 */
public class MLNModel extends Model {

	protected MarkovLogicNetwork mln;
	protected Database db;
	protected MarkovRandomField mrf;
	
	public MLNModel(String name, String mln) throws ProbCogException {
		super(name);
		this.mln = new MarkovLogicNetwork(mln);
	}

	@Override
	protected String _getConstantType(String constant) {
		return db.getConstantType(constant);
	}
	
	@Override
	public void beginSession(Map<String, Object> params) throws ProbCogException {
		super.beginSession(params);
		db = new Database(mln);
	}

	@Override
	protected Vector<InferenceResult> _infer(Iterable<String> queries) throws ProbCogException {
		InferenceAlgorithm ia = new MCSAT(mrf);
		paramHandler.addSubhandler(ia);
		Vector<InferenceResult> res = new Vector<InferenceResult>();
		for(probcog.srl.mln.inference.InferenceResult r : ia.infer(queries)) {
			InferenceResult r2 = new InferenceResult(r.ga.predicate, r.ga.args, r.value);
			res.add(r2);
		}
		return res;
	}

	@Override
	protected void _setEvidence(Iterable<String[]> evidence) throws ProbCogException {
		for(String[] tuple : evidence) {
			String functionName = tuple[0];
			Signature sig = mln.getSignature(functionName);
			if(sig == null)
				throw new ProbCogException("Function '" + functionName + "' appearing in evidence not found in model " + name);
			String value;
			String[] params;
			if(sig.argTypes.length == tuple.length-1) {
				params = new String[tuple.length-1];
				for(int i = 0; i < params.length; i++)
					params[i] = tuple[i+1];
				value = BooleanDomain.True;
			}
			else {
				params = new String[tuple.length-2];
				for(int i = 0; i < params.length; i++)
					params[i] = tuple[i+1];
				value = BooleanDomain.getStandardValue(tuple[tuple.length-1]);				
			}
			db.addVariable(new Variable(functionName, params, value, mln));
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
	public void instantiate() throws ProbCogException {
		mrf = mln.ground(db);		
	}
}
