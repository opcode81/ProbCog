import java.io.File;
import java.io.PrintStream;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.ABL;
import edu.tum.cs.srl.bayesnets.BLOGModel;
import edu.tum.cs.srl.bayesnets.learning.CPTLearner;
import edu.tum.cs.srl.bayesnets.learning.DomainLearner;

public class learnBLOG {	
	
	public static enum Mode {
		BLOG, ABL
	}
	
	public static void learn(Mode mode, String[] args) {
		try {
			String acronym = mode == Mode.ABL ? "ABL" : "BLOG";
			
			boolean showBN = false, learnDomains = false, ignoreUndefPreds = false, basicBLN = false, toMLN = false, debug = false, uniformDefault = false;
			String blogFile = null, bifFile = null, dbFile = null, outFileBLOG = null, outFileNetwork = null;
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
				else if(args[i].equals("-ob"))
					outFileBLOG = args[++i];
				else if(args[i].equals("-ox"))
					outFileNetwork = args[++i];
				else if(args[i].equals("-bln"))
					basicBLN = true;
				else if(args[i].equals("-mln"))
					toMLN = true;
				else if(args[i].equals("-nn"))
					noNormalization = true;					
				else if(args[i].equals("-ud"))
					uniformDefault = true;					
				else if(args[i].equals("-debug"))
					debug = true;					
			}			
			if(bifFile == null || dbFile == null || outFileBLOG == null || outFileNetwork == null) {
				System.out.println("\n usage: learn" + acronym + " [-b <" + acronym + " file>] <-x <network file>> <-t <training db pattern>> <-ob <" + acronym + " output>> <-ox <network output>> [-s] [-d]\n\n"+
							         "    -b      " + acronym + " file from which to read function signatures\n" +
						             "    -s      show learned fragment network\n" +
						             "    -d      learn domains\n" + 
						             "    -i      ignore data on predicates not defined in the model\n" +
						             "    -ud     apply uniform distribution by default (for CPT columns with no examples)\n" +
						             "    -nn     no normalization (i.e. keep counts in CPTs)\n" +
						             "    -bln    output basic .bln file\n" + 
						             "    -mln    convert learnt model to a Markov logic network\n" +
						             "    -debug  output debug information\n");
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
			
			System.out.println("Signatures:");
			for(Signature sig : bn.getSignatures()) {
				System.out.println("  " + sig);
			}

			// read the training databases
			System.out.println("Reading data...");
			Vector<Database> dbs = new Vector<Database>();
			String regex = new File(dbFile).getName();
			Pattern p = Pattern.compile( regex );
			File directory = new File(dbFile).getParentFile();
			if(directory == null || !directory.exists())
				directory = new File(".");
			System.out.printf("Searching for '%s' in '%s'...\n", regex, directory);
			for (File file : directory.listFiles()) { 
				if(p.matcher(file.getName()).matches()) {
					Database db = new Database(bn);
					System.out.printf("reading %s...\n", file.getAbsolutePath());
					db.readBLOGDB(file.getPath(), ignoreUndefPreds);
					dbs.add(db);
				}
			}
			
			// check domains for overlaps and merge if necessary
			System.out.println("Checking domains...");
			for(Database db : dbs)
				db.checkDomains(true);
			
			// learn domains
			if(learnDomains) {
				System.out.println("Learning domains...");
				DomainLearner domLearner = new DomainLearner(bn);
				for(Database db : dbs) {					
					domLearner.learn(db);					
				}
				domLearner.finish();
			}
			System.out.println("Domains:");
			for(Signature sig : bn.getSignatures()) {	
				System.out.println("  " + sig.functionName + ": " + sig.returnType + " ");
			}
			// learn parameters
			boolean learnParams = true;
			if(learnParams) {
				System.out.println("Learning parameters...");
				CPTLearner cptLearner = new CPTLearner(bn, uniformDefault, debug);
				//cptLearner.setUniformDefault(true);
				for(Database db : dbs)
					cptLearner.learnTyped(db, true, true);
				if(!noNormalization)
					cptLearner.finish();
				// write learnt BLOG/ABL model
				System.out.println("Writing "+ acronym + " output to " + outFileBLOG + "...");
				PrintStream out = new PrintStream(new File(outFileBLOG));
				bn.write(out);			
				out.close();
				// write parameters to Bayesian network template
				System.out.println("Writing network output to " + outFileNetwork + "...");
				bn.save(outFileNetwork);
			}
			// write basic BLN 
			if(basicBLN) {
				String filename = outFileBLOG + ".bln";
				System.out.println("Writing BLN " + filename);
				PrintStream out = new PrintStream(new File(outFileBLOG + ".bln"));
				bn.toMLN(out, true, false, false);
			}
			// write MLN
			if(toMLN) {
				String filename = outFileBLOG + ".mln";
				System.out.println("Writing MLN " + filename);
				PrintStream out = new PrintStream(new File(outFileBLOG + ".mln"));
				bn.toMLN(out, false, false, false);
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
