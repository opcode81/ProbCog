package edu.tum.cs.srldb;

public class ConstantArgument implements IRelationArgument {

	protected String constantName;
	
	public ConstantArgument(String constant) {
		constantName = constant;
	}
	
	public String getConstantName() {
		return constantName;
	}

}
