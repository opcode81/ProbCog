
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;
import edu.tum.cs.srl.mln.MarkovRandomField;
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
		
		MarkovLogicNetwork mln = new MarkovLogicNetwork(args[0]);
		Database db = new Database(mln);
		db.readMLNDB(args[1]);
		MarkovRandomField mrf = mln.ground(db);
		WCSPConverter converter = new WCSPConverter(mrf);
		converter.run(args[2]);		
	}
}
