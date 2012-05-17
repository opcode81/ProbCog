package probcog.srldb;

import java.io.Serializable;

public class ConstantArgument implements IRelationArgument, Serializable {

	private static final long serialVersionUID = 1L;
	protected String constantName;
	
	public ConstantArgument(String constant) {
		constantName = constant;
	}
	
	public String getConstantName() {
		return constantName;
	}
	
	public String toString() {
		return "CONST:" + constantName;
	}

}
