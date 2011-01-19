import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map.Entry;

import edu.tum.cs.bayesnets.core.BNDatabase;


/**
 * 
 * @author jain
 */
public class bndb2inst {

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
		PrintStream out = new PrintStream(new FileOutputStream(new File(args[1])));
		
		out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		out.println("<instantiation>");
		for(Entry<String,String> e : db.getEntries()) {
			out.printf("<inst id=\"%s\" value=\"%s\"/>\n", e.getKey(), e.getValue());
		}
		out.println("</instantiation>");
	}

}
