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
			boolean showBN = false, learnDomains = false;
			String blogFile = null, bifFile = null, dbFile = null, outFile = null;
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-s"))
					showBN = true;
				else if(args[i].equals("-d"))
					learnDomains = true;
				else if(args[i].equals("-b"))
					blogFile = args[++i];
				else if(args[i].equals("-x"))
					bifFile = args[++i];
				else if(args[i].equals("-t"))
					dbFile = args[++i];
				else if(args[i].equals("-o"))
					outFile = args[++i];
			}
			if(bifFile == null || dbFile == null || outFile == null) {
				System.out.println("\n usage: learnBLOG [-b <BLOG file>] <-x <xml-BIF file>> <-t <training db>> <-o <output file>> [-s] [-d]\n\n"+
							         "    -b  BLOG file from which to read function signatures\n" +
						             "    -s  show learned Bayesian network\n" +
						             "    -d  learn domains\n");
				return;
			}
			// create a BLOG model
			BLOGModel bn;			
			if(blogFile != null)
				bn = new BLOGModel(blogFile, bifFile);
			else
				bn = new BLOGModel(bifFile);
			// read the training database
			System.out.println("Reading data...");
			Database db = new Database(bn);
			db.readBLOGDB(dbFile);		
			// check domains for overlaps and merge if necessary
			System.out.println("Checking domains...");
			db.checkDomains(true);
			// learn domains
			if(learnDomains) {
				System.out.println("Learning domains...");
				DomainLearner domLearner = new DomainLearner(bn);
				domLearner.learn(db);
				domLearner.finish();
			}
			// learn parameters
			System.out.println("Learning parameters...");
			CPTLearner cptLearner = new CPTLearner(bn);
			cptLearner.learnTyped(db, true, true);
			cptLearner.finish();
			System.out.println("Writing BLOG output...");
			PrintStream out = new PrintStream(new File(outFile));
			bn.write(out);			
			out.close();
			System.out.println("Writing XML-BIF output...");
			bn.saveXMLBIF(bifFile);
			if(showBN) {
				System.out.println("Showing Bayesian network...");
				bn.show();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
}
