package probcog.srl.mln.inference;

import probcog.logic.GroundAtom;

public class InferenceResult implements Comparable<InferenceResult> {
	public GroundAtom ga;
	public double value;
	
	public InferenceResult(GroundAtom ga, double value) {
		this.value = value;	
		this.ga = ga;
	}
	
	public void print() {
		System.out.println(toString());
	}

	public int compareTo(InferenceResult o) {
		return this.ga.toString().compareTo(o.ga.toString());
	}
	
	@Override
	public String toString() {
		return String.format("  %.4f  %s", value, ga.toString());
	}
}
