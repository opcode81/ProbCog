package probcog.logic;

import java.util.Map;

import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;


public class Equality extends UngroundedFormula {
	public String left, right;

	public Equality(String left, String right) {
		this.left = left;
		this.right = right;
	}

	public String toString() {
		return left + "=" + right;
	}
	
	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) {
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) {
		// TODO it's difficult to determine the types of any constants appearing in Equality statements; they are ignored for now (in the hope that they also appear elsewhere) 
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?,?> db) throws Exception {
		String a = binding.get(left);
		if(a == null) a = left;
		String b = binding.get(right);
		if(b == null) b = right;
		return TrueFalse.getInstance(a.equals(b));
	}

	@Override
	public Formula toCNF() {
		throw new RuntimeException("Cannot convert ungrounded formula to CNF.");
	}
	
	@Override
	public Formula toNNF() {
		throw new RuntimeException("Cannot convert ungrounded formula to NNF.");
	}
}
