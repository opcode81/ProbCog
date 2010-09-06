package edu.tum.cs.logic;

import java.util.Map;

import edu.tum.cs.srl.Database;

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
	public void getVariables(Database db, Map<String, String> ret) {
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, Database db) throws Exception {
		String a = binding.get(left);
		if(a == null) a = left;
		String b = binding.get(right);
		if(b == null) b = right;
		return TrueFalse.getInstance(a == b);
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
