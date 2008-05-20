package edu.tum.cs.tools;

public class StringTool {
	public static String join(String glue, Object[] elems) {
		StringBuffer res = new StringBuffer();
		for(int i = 0; i < elems.length; i++) {
			res.append(elems[i].toString());
			if(i < elems.length-1)
				res.append(glue);
		}
		return res.toString();
	}

	public static String join(String glue, Iterable<?> elems) {
		StringBuffer res = new StringBuffer();
		int i = 0; 
		for(Object elem : elems) {
			if(i++ > 0)
				res.append(glue);
			res.append(elem.toString());
		}
		return res.toString();
	}
	
	public static String join(String glue, double[] elems) {
		StringBuffer res = new StringBuffer();
		for(int i = 0; i < elems.length; i++) {
			res.append(elems[i]);
			if(i < elems.length-1)
				res.append(glue);
		}
		return res.toString();
	}
}
