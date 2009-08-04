import java.io.IOException;

import edu.tum.cs.wcsp.WCSPConverter;

/*
 * Created on Aug 3, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

public class MLN2WCSP {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		if(args.length < 3) {
			System.out.println("usage: MLN2WCSP <MLN file> <evidence database file> <WCSP output file> [scenario output file]");
			return;			
		}
		
		WCSPConverter converter = new WCSPConverter(args[0], args[1]);
		String scenarioFile = null;
		if(args.length >= 4)
			scenarioFile = args[3];
		converter.run(args[2], scenarioFile);		
	}
}
