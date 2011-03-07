package edu.tum.cs.srl;



public class SoftVariable extends AbstractVariable<ValueDistribution> {

	public SoftVariable(String functionName, String[] params, ValueDistribution value) {
		super(functionName, params, value);
	}
	
	@Override
	public boolean isTrue() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getPredicate() {
		throw new RuntimeException("impossible");
	}

	@Override
	public boolean isBoolean() {
		return false;
	}

	@Override
	public ValueDistribution getValue() {
		return value;
	}
	
	public double getValue(String domElement) {
		return value.getValue(domElement);
	}
}
