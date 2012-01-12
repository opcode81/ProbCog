import java.io.File;
import java.io.PrintStream;

import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;

public class BLOGDB2MLNDB {

	public static void main(String[] args) throws Exception {
		if(args.length != 3) {
			System.out.println("usage: BLNDB2MLNDB <bln decls file> <blogdb file> <mln db output file>");
			return;
		}
		String declsFile = args[0];
		String blogdbFile = args[1];
		String outFile = args[2];
		
		BayesianLogicNetwork bln = new BayesianLogicNetwork(declsFile);
		Database db = new Database(bln);
		db.readBLOGDB(blogdbFile);
		db.writeMLNDatabase(new PrintStream(new File(outFile)));
	}

}
