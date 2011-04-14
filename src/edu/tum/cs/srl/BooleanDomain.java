/*
 * Created on Apr 14, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl;

public class BooleanDomain {
	public static String True = "True";
	public static String False = "False";
	public static String typeName = "Boolean";
	
	public static boolean isBooleanType(String typeName) {
		return BooleanDomain.typeName.equalsIgnoreCase(typeName); 
	}
}
