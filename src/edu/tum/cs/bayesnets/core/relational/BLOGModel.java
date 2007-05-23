package edu.tum.cs.bayesnets.core.relational;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BLOGModel extends RelationalBeliefNetwork {

	protected HashMap<String, Signature> signatures;
	
	public static String readTextFile(String filename) throws FileNotFoundException, IOException {
		File inputFile = new File(filename);
		FileReader fr = new FileReader(inputFile);
		char[] cbuf = new char[(int)inputFile.length()];
		fr.read(cbuf);
		String content = new String(cbuf);
		fr.close();
		return content;
	}
	
	public BLOGModel(String blogFile, String xmlbifFile) throws Exception {
		super(xmlbifFile);
		
		// read the blog file to obtain signatures
		signatures = new HashMap<String, Signature>();
		String blog = readTextFile(blogFile);
		Pattern pat = Pattern.compile("random\\s+(\\w+)\\s+(\\w+)\\s*\\((.*)\\)");
		Matcher matcher = pat.matcher(blog);
		while(matcher.find()) {
			String retType = matcher.group(1);
			String[] argTypes = matcher.group(3).trim().split("\\s*,\\s*");
			signatures.put(matcher.group(2), new Signature(retType, argTypes));
		}
	}	
	
	public Signature getSignature(String nodeName) {
		return signatures.get(nodeName);
	}
}
