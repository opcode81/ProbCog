
import edu.tum.cs.wcsp.WCSPConverter;

/*
 * Created on Aug 3, 2009
 */

public class MLN2WCSP {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		if(args.length < 3) {
			System.out.println("usage: MLN2WCSP <MLN file> <evidence database file> <WCSP output file>");
			return;			
		}
		
		WCSPConverter converter = new WCSPConverter(args[0], args[1]);
		converter.run(args[2]);		
	}
}
