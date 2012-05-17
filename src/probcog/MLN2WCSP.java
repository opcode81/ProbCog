package probcog;
/*
 * Created on Aug 3, 2009
 */
import java.io.PrintStream;

import probcog.srl.Database;
import probcog.srl.mln.MarkovLogicNetwork;
import probcog.srl.mln.MarkovRandomField;
import probcog.wcsp.WCSP;
import probcog.wcsp.WCSPConverter;


public class MLN2WCSP {

	public static void main(String[] args) throws Exception {
		if(args.length < 3) {
			System.out.println("usage: MLN2WCSP <MLN file> <evidence database file> <WCSP output file>");
			return;			
		}
		
		MarkovLogicNetwork mln = new MarkovLogicNetwork(args[0]);
		Database db = new Database(mln);
		db.readMLNDB(args[1]);
		MarkovRandomField mrf = mln.ground(db);
		WCSPConverter converter = new WCSPConverter(mrf);
		WCSP wcsp = converter.run();
		wcsp.writeWCSP(new PrintStream(args[2]), "WCSPFromMLN");
	}
}
