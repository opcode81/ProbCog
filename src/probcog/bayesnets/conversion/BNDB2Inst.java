package probcog.bayesnets.conversion;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Map.Entry;

import probcog.bayesnets.core.BNDatabase;



/**
 * converts a Bayesian network evidence database given in .bndb format to the .inst format
 * that is, for example, used by ACE and Samiam
 * @author jain
 */
public class BNDB2Inst {

	public static void convert(BNDatabase db, File instFile) throws FileNotFoundException {
		PrintStream out = new PrintStream(instFile);
		
		out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		out.println("<instantiation>");
		for(Entry<String,String> e : db.getEntries()) {
			out.printf("<inst id=\"%s\" value=\"%s\"/>\n", e.getKey(), e.getValue());
		}
		out.println("</instantiation>");
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("usage: bndb2inst <.bndb input file> <.inst output filename>");
			return;
		}
		
		BNDatabase db = new BNDatabase(new File(args[0]));
		convert(db, new File(args[1]));
	}

}
