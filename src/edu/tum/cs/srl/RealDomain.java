/*
 * Created on Apr 14, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl;

public class RealDomain {
	public static String typeName = "Real";
	
	public static boolean isRealType(String typeName) {
		return RealDomain.typeName.equalsIgnoreCase(typeName); 
	}
}
