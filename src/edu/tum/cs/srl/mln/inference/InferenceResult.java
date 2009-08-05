package edu.tum.cs.srl.mln.inference;

import edu.tum.cs.logic.GroundAtom;

public class InferenceResult {
	public GroundAtom ga;
	public double value;
	
	public InferenceResult(GroundAtom ga, double value) {
		this.value = value;	
		this.ga = ga;
	}
	
	public void print() {
		System.out.println(String.format("  %.4f %s", value, ga.toString()));
	}
}
