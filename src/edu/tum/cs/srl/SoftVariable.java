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

	@Override
	public boolean hasValue(String value) {
		String singleVal = this.value.getSingleValue();
		if(singleVal == null)
			return false;
		return value.equalsIgnoreCase(singleVal);
	}
}
