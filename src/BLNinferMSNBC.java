import java.io.File;
import java.io.FileWriter;
import java.util.Vector;
import java.util.regex.Pattern;


import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.inference.BackwardSampling;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithChildren;
import edu.tum.cs.bayesnets.inference.BackwardSamplingWithPriors;
import edu.tum.cs.bayesnets.inference.LikelihoodWeightingWithUncertainEvidence;
import edu.tum.cs.bayesnets.inference.SmileBackwardSampling;
import edu.tum.cs.bayesnets.inference.SmileEPIS;
import edu.tum.cs.bayesnets.relational.core.AbstractGroundBLN;
import edu.tum.cs.bayesnets.relational.core.BayesianLogicNetwork;
import edu.tum.cs.bayesnets.relational.core.GroundBLN;
import edu.tum.cs.srl.bayesnets.ABL;
import edu.tum.cs.srl.bayesnets.bln.*;
import edu.tum.cs.srl.bayesnets.bln.py.BayesianLogicNetworkPy;
import edu.tum.cs.srl.bayesnets.inference.BNSampler;
import edu.tum.cs.srl.bayesnets.inference.CSPSampler;
import edu.tum.cs.srl.bayesnets.inference.GibbsSampling;
import edu.tum.cs.srl.bayesnets.inference.InferenceResult;
import edu.tum.cs.srl.bayesnets.inference.LiftedBackwardSampling;
import edu.tum.cs.srl.bayesnets.inference.LikelihoodWeighting;
import edu.tum.cs.srl.bayesnets.inference.SATIS;
import edu.tum.cs.srl.bayesnets.inference.SATISEx;
import edu.tum.cs.srl.bayesnets.inference.Sampler;
import edu.tum.cs.tools.Stopwatch;


public class BLNinferMSNBC {

	enum Algorithm {LikelihoodWeighting, LWU, CSP, GibbsSampling, EPIS, BackwardSampling, SmileBackwardSampling, BackwardSamplingPriors, Experimental, LiftedBackwardSampling, SATIS, SATISEx};
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			String blogFile = null;
			String bifFile = null;
			String blnFile = null;
			String dbFile = null;
			String query = null;
			int maxSteps = 1000;
			int maxTrials = 5000;
			Algorithm algo = Algorithm.LikelihoodWeighting;
			String[] cwPreds = null;
			boolean showBN = false;
			boolean usePython = false;
			boolean debug = false;
			boolean saveInstance = false;
			boolean skipFailedSteps = false;
			boolean removeDeterministicCPTEntries = false;
			
			// read arguments
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-b"))
					blogFile = args[++i];
				else if(args[i].equals("-x"))
					bifFile = args[++i];
				else if(args[i].equals("-l"))
					blnFile = args[++i];
				else if(args[i].equals("-q"))
					query = args[++i];
				else if(args[i].equals("-e"))
					dbFile = args[++i];				
				else if(args[i].equals("-s"))
					showBN = true;				
				else if(args[i].equals("-nodetcpt"))
					removeDeterministicCPTEntries = true;				
				else if(args[i].equals("-si"))
					saveInstance = true;				
				else if(args[i].equals("-skipFailedSteps"))
					skipFailedSteps = true;				
				else if(args[i].equals("-py"))
					usePython = true;				
				else if(args[i].equals("-cw"))
					cwPreds = args[++i].split(",");		
				else if(args[i].equals("-maxSteps"))
					maxSteps = Integer.parseInt(args[++i]);
				else if(args[i].equals("-maxTrials"))
					maxTrials = Integer.parseInt(args[++i]);
				else if(args[i].equals("-lw"))
					algo = Algorithm.LikelihoodWeighting;
				else if(args[i].equals("-lwu"))
					algo = Algorithm.LWU;
				else if(args[i].equals("-epis"))
					algo = Algorithm.EPIS;
				else if(args[i].equals("-csp"))
					algo = Algorithm.CSP;
				else if(args[i].equals("-gs"))
					algo = Algorithm.GibbsSampling;
				else if(args[i].equals("-bs"))
					algo = Algorithm.BackwardSampling;
				else if(args[i].equals("-sbs"))
					algo = Algorithm.SmileBackwardSampling;
				else if(args[i].equals("-bsp"))
					algo = Algorithm.BackwardSamplingPriors;
				else if(args[i].equals("-lbs"))
					algo = Algorithm.LiftedBackwardSampling;
				else if(args[i].equals("-exp"))
					algo = Algorithm.Experimental;
				else if(args[i].equals("-satis"))
					algo = Algorithm.SATIS;
				else if(args[i].equals("-satisex"))
					algo = Algorithm.SATISEx;
				else if(args[i].equals("-debug"))
					debug = true;
				else
					System.err.println("Warning: unknown option " + args[i] + " ignored!");
			}			
			if(bifFile == null || dbFile == null || blogFile == null || blnFile == null || query == null) {
				System.out.println("\n usage: inferBLN <-b <BLOG file>> <-x <xml-BIF file>> <-l <BLN file>> <-e <evidence db>> <-q <comma-sep. queries>> [options]\n\n"+
									 "    -maxSteps #      the maximum number of steps to take\n" +
									 "    -maxTrials #     the maximum number of trials per step for BN sampling algorithms\n" +
									 "    -skipFailedSteps failed steps (> max trials) should just be skipped\n" +
									 "    -lw              algorithm: likelihood weighting (default)\n" +
									 "    -lwu             algorithm: likelihood weighting with uncertain evidence (default)\n" +
							         "    -gs              algorithm: Gibbs sampling\n" +						
							         "    -exp             algorithm: Experimental\n" +
							         "    -satis           algorithm: SAT-IS\n" +
							         "    -satisex         algorithm: SAT-IS (extended with hard CPT constraints) \n" +
							         "    -bs              algorithm: backward sampling\n" +
							         "    -sbs             algorithm: SMILE backward sampling\n" +
							         "    -epis            algorithm: SMILE evidence prepropagation importance sampling\n" +
							         "    -py              use Python-based logic engine\n" +
							         "    -debug           debug mode with additional outputs\n" + 
							         "    -s           	   show ground network in editor\n" +
							         "    -si              save ground network instance in BIF format (.instance.xml)\n" +
							         "    -nodetcpt        remove deterministic CPT columns by replacing 0s with low prob. values\n" +
							         "    -cw <predNames>  set predicates as closed-world (comma-separated list of names)\n");
				return;
			}			

			// determine queries
			Pattern comma = Pattern.compile("\\s*,\\s*");
			String[] candQueries = comma.split(query);
			Vector<String> queries = new Vector<String>();
			String q = "";
			for(int i = 0; i < candQueries.length; i++) {
				if(!q.equals(""))
					q += ",";
				q += candQueries[i];
				if(balancedParentheses(q)) {
					queries.add(q);
					q = "";
				}
			}
			if(!q.equals(""))
				throw new IllegalArgumentException("Unbalanced parentheses in queries");

			// load relational model
			ABL blog = new ABL(blogFile, bifFile);
			
			// (on request) remove deterministic dependencies in CPTs
			if(removeDeterministicCPTEntries) {
				final double lowProb = 0.001; 
				for(BeliefNode node : blog.bn.getNodes()) {
					CPF cpf = node.getCPF();					
					for(int i = 0; i < cpf.size(); i++)
						if(cpf.getDouble(i) == 0.0)
							cpf.put(i, new ValueDouble(lowProb));
					cpf.normalizeByDomain();
				}
			}
			
			
			// read the testing databases
			System.out.println("Reading data...");
			String[] pathName = dbFile.split("/");
			String dirName=".";
			for(int p=0;p<pathName.length-1;p++) {
				dirName+="/"+pathName[p];
			}
			
			Pattern p = Pattern.compile( pathName[pathName.length-1] );
			FileWriter resFile = new FileWriter("results.csv" );
			
			for (File file : new File( dirName ).listFiles()) {
				if(p.matcher(file.getName()).matches()) {
					
					String testDBfile = dirName+"/"+file.getName();
									

					// instantiate ground model
					AbstractGroundBLN gbln;
					if(!usePython) {
						BayesianLogicNetwork bln = new BayesianLogicNetwork(blog, blnFile);
						gbln = new GroundBLN(bln, testDBfile);
					}
					else {
						BayesianLogicNetworkPy bln = new BayesianLogicNetworkPy(blog, blnFile);
						gbln = new edu.tum.cs.srl.bayesnets.bln.py.GroundBLN(bln, testDBfile);
					}
					if(cwPreds != null) {
						for(String predName : cwPreds)
							gbln.getDatabase().setClosedWorldPred(predName);
					}
					gbln.instantiateGroundNetwork();
					if(showBN) {
						gbln.getGroundNetwork().show();
					}
					if(saveInstance) {
						String baseName = bifFile.substring(0, bifFile.lastIndexOf('.'));
						gbln.getGroundNetwork().saveXMLBIF(baseName + ".instance.xml");
					}
			
					// run inference
					Stopwatch sw = new Stopwatch();
					sw.start();
					Sampler sampler = null;
					switch(algo) {
					case LikelihoodWeighting: 
						sampler = new LikelihoodWeighting(gbln); break;
					case LWU: 
						sampler = new BNSampler(gbln, LikelihoodWeightingWithUncertainEvidence.class); break;
					case CSP: 
						sampler = new CSPSampler(gbln); break;
					case GibbsSampling:	
						sampler = new GibbsSampling(gbln); break;
					case EPIS:
						sampler = new BNSampler(gbln, SmileEPIS.class); break;
					case SmileBackwardSampling:
						sampler = new BNSampler(gbln, SmileBackwardSampling.class); break;
					case BackwardSampling:
						sampler = new BNSampler(gbln, BackwardSampling.class); break;
					case BackwardSamplingPriors:
						sampler = new BNSampler(gbln, BackwardSamplingWithPriors.class); break;
					case Experimental:
						sampler = new BNSampler(gbln, BackwardSamplingWithChildren.class); break;
					case LiftedBackwardSampling:
						sampler = new LiftedBackwardSampling(gbln); break;
					case SATIS:
						sampler = new SATIS((GroundBLN)gbln); break;
					case SATISEx:
						sampler = new SATISEx((GroundBLN)gbln); break;
					default: 
						throw new Exception("algorithm not handled");
					}
					sampler.setDebugMode(debug);
					if(sampler instanceof BNSampler) {
						((BNSampler)sampler).setMaxTrials(maxTrials);
						((BNSampler)sampler).setSkipFailedSteps(skipFailedSteps);
					}
					Vector<InferenceResult> results = sampler.infer(queries, maxSteps, 100);
					sw.stop();

					for(InferenceResult res : results) {
						
						double max = res.probabilities[0];
						int maxIdx = 0;
			            for (int i = 1; i < res.probabilities.length; i++) {
			               if (res.probabilities[i] > max) {max = res.probabilities[i];maxIdx=i;}
			            }
			            
			            String truth = res.varName.substring(0, res.varName.length()-1).split("\\(")[1];
			            truth = truth.split("_")[3];
			            int eq=0; if(truth.equals(res.domainElements[maxIdx])) eq=1;
			            
			            if(max<1)
			            	resFile.write(res.varName +","+truth +"," + res.domainElements[maxIdx] + ","+max+ ","+eq+"\n");
						
					}
				}
			}
			resFile.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean balancedParentheses(String s) {
		int n = 0;
		for(int i = 0; i < s.length(); i++) {
			if(s.charAt(i) == '(')
				n++;
			else if(s.charAt(i) == ')')
				n--;
		}
		return n == 0;
	}
}
