import java.io.File;
import java.io.PrintStream;

import edu.tum.cs.bayesnets.relational.core.ABL;
import edu.tum.cs.bayesnets.relational.core.BLOGModel;
import edu.tum.cs.bayesnets.relational.core.RelationalNode.Signature;
import edu.tum.cs.bayesnets.relational.learning.CPTLearner;
import edu.tum.cs.bayesnets.relational.learning.Database;
import edu.tum.cs.bayesnets.relational.learning.DomainLearner;

public class learnBLOG {	
	
	public static enum Mode {
		BLOG, ABL
	}
	
	public static void learn(Mode mode, String[] args) {
		try {
			String acronym = mode == Mode.ABL ? "ABL" : "BLOG";
			
			boolean showBN = false, learnDomains = false, ignoreUndefPreds = false;
			String blogFile = null, bifFile = null, dbFile = null, outFile = null;
			boolean noNormalization = false;
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-s"))
					showBN = true;
				else if(args[i].equals("-d"))
					learnDomains = true;
				else if(args[i].equals("-i"))
					ignoreUndefPreds = true;
				else if(args[i].equals("-b"))
					blogFile = args[++i];
				else if(args[i].equals("-x"))
					bifFile = args[++i];
				else if(args[i].equals("-t"))
					dbFile = args[++i];
				else if(args[i].equals("-o"))
					outFile = args[++i];
				else if(args[i].equals("-nn"))
					noNormalization = true;					
			}			
			if(bifFile == null || dbFile == null || outFile == null) {
				System.out.println("\n usage: learn" + acronym + " [-b <" + acronym + " file>] <-x <xml-BIF file>> <-t <training db>> <-o <output file>> [-s] [-d]\n\n"+
							         "    -b  " + acronym + " file from which to read function signatures\n" +
						             "    -s  show learned Bayesian network\n" +
						             "    -d  learn domains\n" + 
						             "    -i  ignore data on predicates not defined in the model\n" +
						             "    -nn no normalization (i.e. keep counts in CPTs)\n");
				return;
			}
			// create a BLOG model
			BLOGModel bn;
			if(mode == Mode.BLOG) {
				if(blogFile != null)
					bn = new BLOGModel(blogFile, bifFile);
				else
					bn = new BLOGModel(bifFile);
			}
			else {
				if(blogFile != null)
					bn = new ABL(blogFile, bifFile);
				else
					bn = new BLOGModel(bifFile);
			}
			// prepare it for learning
			bn.prepareForLearning();
			// read the training database
			System.out.println("Reading data...");
			Database db = new Database(bn);
			db.readBLOGDB(dbFile, ignoreUndefPreds);		
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
			System.out.println("Domains:");
			for(Signature sig : bn.getSignatures()) {
				System.out.println("  " + sig.functionName + ": " + sig.returnType);
			}
			// learn parameters
			boolean learnParams = true;
			if(learnParams) {
				System.out.println("Learning parameters...");
				CPTLearner cptLearner = new CPTLearner(bn);
				//cptLearner.setUniformDefault(true);
				cptLearner.learnTyped(db, true, true);
				if(!noNormalization)
					cptLearner.finish();
				// write learnt BLOG/ABL model
				System.out.println("Writing "+ acronym + " output to " + outFile + "...");
				PrintStream out = new PrintStream(new File(outFile));
				bn.write(out);			
				out.close();
				// write parameters to Bayesian network template
				int dotpos = bifFile.lastIndexOf('.');
				bifFile = bifFile.substring(0, dotpos) + ".learnt.xml";
				System.out.println("Writing XML-BIF output to " + bifFile + "...");
				bn.saveXMLBIF(bifFile);
			}
			// show bayesian network
			if(showBN) {
				System.out.println("Showing Bayesian network...");
				bn.show();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
	public static void main(String[] args) {
		learn(Mode.BLOG, args);
	}
}
