import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.relational.BLOGModel;
import edu.tum.cs.bayesnets.core.relational.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.learning.relational.CPTLearner;
import edu.tum.cs.bayesnets.learning.relational.Database;
import edu.tum.cs.bayesnets.learning.relational.DomainLearner;

public class learnBLOG {

	public static void main(String[] args) {
		try {
			if(args.length < 4) {
				System.out.println("\n usage: learnBLOG <BLOG input file> <xml BIF file> <training database file> <output file> [-s] [-d]\n\n"+
						             "    -s  show learned Bayesian network\n" +
						             "    -d  learn domains\n\n" +
						             "    The BLOG input file must contain all function/predicate declarations.\n");
				return;
			}
			boolean showBN = false, learnDomains = false;
			for(int i = 4; i < args.length; i++) {
				if(args[i].equals("-s"))
					showBN = true;
				if(args[i].equals("-d"))
					learnDomains = true;
			}
			RelationalBeliefNetwork bn = new BLOGModel(args[0], args[1]);
			Database db = new Database(bn);
			db.readBLOGDB(args[2]);
			if(learnDomains) {
				DomainLearner domLearner = new DomainLearner(bn);
				domLearner.learn(db);
				domLearner.finish();
			}
			CPTLearner cptLearner = new CPTLearner(bn);
			cptLearner.learnTyped(db, true);
			cptLearner.finish();
			PrintStream out = new PrintStream(new File(args[3]));
			bn.writeBLOGModel(out);
			if(showBN)
				bn.show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
}
