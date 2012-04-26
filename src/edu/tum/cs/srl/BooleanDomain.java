/*
 * Created on Apr 14, 2011
 */
package edu.tum.cs.srl;

public class BooleanDomain {
	public static String True = "True";
	public static String False = "False";
	public static String typeName = "Boolean";
	
	public static boolean isBooleanType(String typeName) {
		return BooleanDomain.typeName.equalsIgnoreCase(typeName); 
	}
	
	public static String getStandardValue(String value) throws Exception {
		if(value.equalsIgnoreCase(True))
			return True;
		if(value.equalsIgnoreCase(False))
			return False;
		throw new IllegalArgumentException(String.format("'%s' is not a valid representation of a Boolean value {'%s', '%s'}", value, True, False));
	}
}
