package edu.tum.cs.srl.bayesnets.bln.py;

import java.util.Arrays;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.BLOGModel;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.srl.bayesnets.bln.AbstractGroundBLN;
import edu.tum.cs.srl.bayesnets.inference.LikelihoodWeighting;
import edu.tum.cs.util.Stopwatch;
import edu.tum.cs.util.datastruct.Pair;

public class GroundBLN extends AbstractGroundBLN {
	protected BeliefNetworkEx groundBN;
	protected Vector<String> hardFormulaNodes;
	protected Database db;
	
	public GroundBLN(BayesianLogicNetworkPy bln, String databaseFile) throws Exception {
		super(bln, databaseFile);
	}
	
	public GroundBLN(BayesianLogicNetworkPy bln, Database db) throws Exception {
		super(bln, db);
	}
	
	@Override
	public void groundFormulaicNodes() throws Exception {
		BayesianLogicNetworkPy bln = (BayesianLogicNetworkPy)this.bln;
		// ground formulaic nodes
		System.out.println("    grounding formulas...");
		bln.generateGroundFormulas(this.databaseFile);
		GroundFormulaIteration gfIter = bln.iterGroundFormulas();
		System.out.printf("      %d formulas instantiated\n", gfIter.getCount());
		System.out.println("    instantiating nodes and CPFs...");
		Stopwatch sw_structure = new Stopwatch();
		Stopwatch sw_cpt = new Stopwatch();
		for(GroundFormula gf : gfIter) {
			// create a node for the ground formula
			sw_structure.start();
			String nodeName = "GF" + gf.idxGF;
			System.out.println(nodeName + ": " + gf);
			Vector<String> GAs = gf.getGroundAtoms();
			Pair<BeliefNode, BeliefNode[]> nodeData = addHardFormulaNode(nodeName, GAs);
			BeliefNode node = nodeData.first;
			// add edges from ground atoms						
			sw_structure.stop();
			// fill CPT according to formula semantics
			// TODO try to reuse CPFs generated for previous formulas with same formula index
			sw_cpt.start();
			fillFormulaCPF(gf, node.getCPF(), nodeData.second, GAs);
			sw_cpt.stop();
		}
		System.out.println("    structure time: " + sw_structure.getElapsedTimeSecs() + "s");
		System.out.println("    cpf time: " + sw_cpt.getElapsedTimeSecs() + "s");		
	}
	
	/**
	 * fills the CPF of a formulaic node
	 * @param gf	the ground formula to evaluate for all possible settings
	 * @param cpf	the CPF of the formulaic node to fill
	 * @param parents	the parents of the formulaic node
	 * @param parentGAs	the ground atom string names of the parents (in case the node names do not match them)
	 * @throws Exception
	 */
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, BeliefNode[] parents, Vector<String> parentGAs) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		int[] addr = new int[nodes.length];
		fillFormulaCPF(gf, cpf, parents, parentGAs, 0, addr);
	}
	
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, BeliefNode[] parents, Vector<String> parentGAs, int iParent, int[] addr) throws Exception {
		// if all parents have been set, determine the truth value of the formula and 
		// fill the corresponding column of the CPT 
		State state = ((BayesianLogicNetworkPy)bln).getState();
		if(iParent == parents.length) {
			// get truth value of formula
			double value = gf.isTrue(state) ? 1 : 0;
			/*
			for(String ga : parentGAs)
				System.out.print(ga + " = " + state.get(ga) + ", ");
			System.out.println(" -> " + value);
			*/
			// write to CPF
			// - true
			addr[0] = 0;
			cpf.put(addr, new ValueDouble(value));
			// - false
			addr[0] = 1;
			cpf.put(addr, new ValueDouble(1.0-value));
			return;
		}
		// otherwise get the next ground atom and consider all of its groundings
		BeliefNode parent = parents[iParent];
		String parentGA = parentGAs.get(iParent);
		Discrete domain = (Discrete)parent.getDomain();
		boolean isBoolean = RelationalBeliefNetwork.isBooleanDomain(domain);		
		// - get the domain index that corresponds to setting the atom to true
		int trueIndex = 0;
		if(!isBoolean) {
			int iStart = parentGA.lastIndexOf(',')+1;
			int iEnd = parentGA.lastIndexOf(')');
			String outcome = parentGA.substring(iStart, iEnd);
			trueIndex = domain.findName(outcome);
			if(trueIndex == -1) 
				throw new Exception("'" + outcome + "' not found in domain of " + parentGA);			
		}
		// - recursively consider all settings
		for(int i = 0; i < domain.getOrder(); i++) {
			// TODO make this faster -- contrary to my expectations, the order of the connect calls does not cause there to be a corresponding ordering in the CPF (seems arbitrary, possibly node index or something)
			// find the correct address index
			BeliefNode[] domProd = cpf.getDomainProduct();
			int iDomProd = -1;
			for(int j = 1; j < domProd.length; j++)
				if(domProd[j] == parent) {
					iDomProd = j;
					break;
				}
			if(iDomProd == -1)
				throw new Exception("Parent could not be found in domain product.");
			// set address 
			addr[iDomProd] = i;
			// set state for logical reasoner
			if(i == trueIndex)
				state.set(parentGA, true);
			else
				state.set(parentGA, false);
			// recurse
			fillFormulaCPF(gf, cpf, parents, parentGAs, iParent+1, addr);
		}
	}
	
	/**
	 * gets the ground Bayesian network
	 * @return
	 */
	public BeliefNetworkEx getGroundBN() {
		return this.groundBN;
	}
	
	public static void main(String[] args) {
		try { 
			int test = 1;
			
			if(test == 0) {
				String dir = "/usr/wiss/jain/work/code/SRLDB/bln/test/";
				BayesianLogicNetworkPy bln = new BayesianLogicNetworkPy(new BLOGModel(dir + "relxy.blog", dir + "relxy.xml"), dir + "relxy.bln");
				GroundBLN gbln = new GroundBLN(bln, dir + "relxy.blogdb");
				Stopwatch sw = new Stopwatch();
				sw.start();				
				new LikelihoodWeighting(gbln).infer(Arrays.asList("rel(X,Y)"), 1000, 100);
				//gbln.inferAIS(new String[][]{{"prop1(X)", "A1"},{"prop2(Y)", "A1"}}, 1000);
				sw.stop();
				System.out.println("Inference time: " + sw.getElapsedTimeSecs() + " seconds");
			}
			if(test == 1) {
				String dir = "/usr/wiss/jain/work/code/SRLDB/blog/kitchen/meal_goods2/";
				BayesianLogicNetworkPy bln = new BayesianLogicNetworkPy(new BLOGModel(dir + "meals_any_names.blog", dir + "meals_any.learnt.xml"), dir + "meals_any.bln");
				GroundBLN gbln = new GroundBLN(bln, dir + "query2.blogdb");
				gbln.show();
				new LikelihoodWeighting(gbln).infer(Arrays.asList("prop1(X)","prop2(Y)"), 1000, 100);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onAddGroundAtomNode(RelationalNode relNode, String[] params,
			BeliefNode instance) {
		// TODO Auto-generated method stub
		
	}
}
