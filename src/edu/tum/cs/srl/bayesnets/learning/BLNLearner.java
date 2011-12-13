package edu.tum.cs.srl.bayesnets.learning;
import java.io.File;
import java.io.PrintStream;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.GenericDatabase;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.ABLModel;

public class BLNLearner implements IParameterHandler {	
	
	protected boolean showBN = false, learnDomains = false, ignoreUndefPreds = false, toMLN = false, debug = false, uniformDefault = false;
	protected String declsFile = null, bifFile = null, dbFile = null, outFileDecls = null, outFileNetwork = null;
	protected boolean noNormalization = false;
	protected boolean mergeDomains = false;
	protected ABLModel bn;
	protected Vector<GenericDatabase<?,?>> dbs = new Vector<GenericDatabase<?,?>>();
	protected ParameterHandler paramHandler;
	
	public BLNLearner() {
		paramHandler = new ParameterHandler(this);
	}
	
	public void readArgs(String[] args) throws IllegalArgumentException {
		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("-s"))
				showBN = true;
			else if(args[i].equals("-d"))
				learnDomains = true;
			else if(args[i].equals("-md"))
				mergeDomains = true;
			else if(args[i].equals("-i"))
				ignoreUndefPreds = true;
			else if(args[i].equals("-b"))
				declsFile = args[++i];
			else if(args[i].equals("-x"))
				bifFile = args[++i];
			else if(args[i].equals("-t"))
				dbFile = args[++i];
			else if(args[i].equals("-ob"))
				outFileDecls = args[++i];
			else if(args[i].equals("-ox"))
				outFileNetwork = args[++i];
			else if(args[i].equals("-mln"))
				toMLN = true;
			else if(args[i].equals("-nn"))
				noNormalization = true;					
			else if(args[i].equals("-ud"))
				uniformDefault = true;					
			else if(args[i].equals("-debug"))
				debug = true;
			else 
				throw new IllegalArgumentException("Unknown parameter: " + args[i]);
		}
		if(outFileDecls == null || outFileNetwork == null)
			throw new IllegalArgumentException("Not all output files given");
	}
	
	public void setABLModel(ABLModel abl) {
		bn = abl;
	}
	
	public void addTrainingDatabase(GenericDatabase<?,?> db) {
		dbs.add(db);
	}
	
	public void setLearnDomains(boolean enabled) {
		this.learnDomains = enabled;
	}
	
	public void setOutputFileNetwork(String filename) {
		this.outFileNetwork = filename;
	}
	
	public void setOutputFileDecls(String filename) {
		this.outFileDecls = filename;
	}
	
	public ABLModel learn() throws IllegalArgumentException {
		try {
			boolean verbose = true;
			String acronym = "ABL";

			if(bn == null) {
				if(bifFile == null) {
					throw new IllegalArgumentException("No network file given");
				}
				// 	create an ABL model			
				bn = new ABLModel(declsFile, bifFile);
			}
			
			// prepare it for learning
			bn.prepareForLearning();
			
			if(verbose) {
				System.out.println("Signatures:");
				for(Signature sig : bn.getSignatures()) {
					System.out.println("  " + sig);
				}
			}

			// read the training databases
			if(dbFile != null) {
				if(verbose) System.out.println("Reading data...");			
				String regex = new File(dbFile).getName();
				Pattern p = Pattern.compile( regex );
				File directory = new File(dbFile).getParentFile();
				if(directory == null)
					directory = new File(".");
				else 
					if(!directory.exists())
						throw new IllegalArgumentException("The directory '" + directory + "', which was specfied in the pattern, does not exist");
				if(verbose) System.out.printf("Searching for '%s' in '%s'...\n", regex, directory);
				for (File file : directory.listFiles()) { 
					if(p.matcher(file.getName()).matches()) {
						Database db = new Database(bn);
						if(verbose) System.out.printf("reading %s...\n", file.getAbsolutePath());
						db.readBLOGDB(file.getPath(), ignoreUndefPreds);
						//db.finalize(); // TODO determine whether to do this or not
						dbs.add(db);
					}
				}
			}
			if(dbs.isEmpty())
				throw new IllegalArgumentException("No training databases given");
			
			// check domains for overlaps and merge if necessary
			if(mergeDomains) {
				if(verbose) System.out.println("Checking domains...");
				for(GenericDatabase<?,?> db : dbs)
					db.checkDomains(verbose);
			}
			
			// learn domains
			if(learnDomains) {
				if(verbose) System.out.println("Learning domains...");
				DomainLearner domLearner = new DomainLearner(bn);
				for(GenericDatabase<?,?> db : dbs) {					
					domLearner.learn(db);					
				}
				domLearner.finish();
			}
			if(verbose) {
				System.out.println("Domains:");
				for(Signature sig : bn.getSignatures()) {	
					System.out.println("  " + sig.functionName + ": " + sig.returnType + " ");
				}
			}
			
			// learn parameters
			boolean learnParams = true;
			if(learnParams) {
				if(verbose) { 
					System.out.println("Learning parameters...");
					if(uniformDefault)
						System.out.println("  option: uniform distribution is assumed as default");
				}
				CPTLearner cptLearner = new CPTLearner(bn, uniformDefault, debug);
				paramHandler.addSubhandler(cptLearner);
				//cptLearner.setUniformDefault(true);
				int i = 1; 
				for(GenericDatabase<?,?> db : dbs) {
					if(verbose) System.out.printf("database %d/%d\n", i, dbs.size());
					cptLearner.learnTyped(db, true, true);
					++i;
				}
				if(!noNormalization)
					cptLearner.finish();
				// write learnt BLOG/ABL model
				if(outFileDecls != null) {
					if(verbose) System.out.println("Writing declarations to " + outFileDecls + "...");
					if(outFileNetwork != null)
						bn.setNetworkFilename(outFileNetwork);
					PrintStream out = new PrintStream(new File(outFileDecls));
					bn.write(out);			
					out.close();
				}
				// write parameters to Bayesian network template
				if(outFileNetwork != null) {
					if(verbose) System.out.println("Writing network to " + outFileNetwork + "...");
					bn.save(outFileNetwork);
				}
			}
			// write MLN
			if(toMLN) {
				String filename = outFileDecls + ".mln";
				if(verbose) System.out.println("Writing MLN " + filename);
				PrintStream out = new PrintStream(new File(outFileDecls + ".mln"));
				bn.toMLN(out, false, false, false);
			}
			// show bayesian network
			if(showBN) {
				if(verbose) System.out.println("Showing Bayesian network...");
				bn.show();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
		
		return bn;
	}
	
	public static void main(String[] args) {
		BLNLearner l = new BLNLearner();		
		try {
			l.readArgs(args);
			l.learn();
		}
		catch(IllegalArgumentException e) {
			String acronym = "ABL";
			System.out.println("\n usage: learn" + acronym + " [-b <" + acronym + " file>] <-x <network file>> <-t <training db pattern>> <-ob <" + acronym + " output>> <-ox <network output>> [-s] [-d]\n\n"+
			         "    -b      " + acronym + " file from which to read function signatures\n" +
		             "    -s      show learned fragment network\n" +
		             "    -d      learn domains\n" + 
		             "    -md     merge domains containing the same constants\n" + 
		             "    -i      ignore data on predicates not defined in the model\n" +
		             "    -ud     apply uniform distribution by default (for CPT columns with no examples)\n" +
		             "    -nn     no normalization (i.e. keep counts in CPTs)\n" +
		             "    -mln    convert learnt model to a Markov logic network\n" +
		             "    -debug  output debug information\n");			
			return;
		}
	}

	@Override
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
}
