package edu.tum.cs.bayesnets.relational.inference;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.SampledDistribution;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx.WeightedSample;
import edu.tum.cs.bayesnets.relational.core.*;
import edu.tum.cs.bayesnets.relational.core.BayesianLogicNetwork.GroundFormula;
import edu.tum.cs.bayesnets.relational.core.BayesianLogicNetwork.State;
import edu.tum.cs.bayesnets.relational.learning.Database;
import edu.tum.cs.tools.Stopwatch;

public class GroundBLN {
	protected BeliefNetworkEx groundBN;
	protected BayesianLogicNetwork bln;
	protected Vector<String> hardFormulaNodes;
	
	public GroundBLN(BayesianLogicNetwork bln, String databaseFile) throws Exception {
		this.bln = bln;
		
		System.out.println("reading evidence...");
		Database db = new Database(bln.rbn);
		db.readBLOGDB(databaseFile);
		
		System.out.println("generating network...");
		groundBN = new BeliefNetworkEx();
		
		// ground regular probabilistic nodes (i.e. ground atoms)
		System.out.println("  regular nodes");
		RelationalBeliefNetwork rbn = bln.rbn;
		int[] order = rbn.getTopologicalOrder();
		for(int i = 0; i < order.length; i++) {
			int nodeNo = order[i];
			RelationalNode node = rbn.getRelationalNode(nodeNo);
			Collection<String[]> parameterSets = ParameterGrounder.generateGroundings(node, db);
			for(String[] params : parameterSets) {
				// add the node itself to the network
				String mainNodeName = node.getVariableName(params);
				BeliefNode groundNode = groundBN.addNode(mainNodeName, node.node.getDomain());
				// add edges from the parents
				ParentGrounder pg = rbn.getParentGrounder(node);
				Vector<Map<Integer, String[]>> groundings = pg.getGroundings(params, db);
				if(groundings.size() != 1) {
					System.err.println("Warning: Cannot ground structure because of multiple parent sets for node " + mainNodeName);
					continue;
				}
				Map<Integer, String[]> grounding = groundings.firstElement();
				for(Entry<Integer, String[]> entry : grounding.entrySet()) {
					if(entry.getKey() != nodeNo) {
						RelationalNode parent = rbn.getRelationalNode(entry.getKey());
						groundBN.connect(parent.getVariableName(entry.getValue()), mainNodeName);
					}
				}
				// transfer the CPF		
				// TODO this might fail because of incorrect ordering of parents
				groundNode.getCPF().setValues(node.node.getCPF().getValues());
			}
		}		
		
		// ground formulaic nodes
		System.out.println("  formulaic nodes");
		hardFormulaNodes = new Vector<String>();
		bln.generateGroundFormulas(databaseFile);
		for(GroundFormula gf : bln.iterGroundFormulas()) {
			Vector<String> vGA = gf.getGroundAtoms();
			// create a node for the ground formula
			String nodeName = "GF" + gf.idxGF;
			hardFormulaNodes.add(nodeName);
			BeliefNode node = groundBN.addNode(nodeName);			
			// add edges from ground atoms
			Vector<String> GAs = gf.getGroundAtoms();
			BeliefNode[] parents = new BeliefNode[GAs.size()];
			int i = 0;
			for(String strGA : GAs) {
				BeliefNode parent = groundBN.getNode(strGA);
				if(parent == null) { // if the atom cannot be found, e.g. attr(X,Value), it might be a functional, so remove the last argument and try again, e.g. attr(X) (=Value)
					String parentName = strGA.substring(0, strGA.lastIndexOf(",")) + ")";
					parent = groundBN.getNode(parentName);
					if(parent == null)
						throw new Exception("Could not find node for ground atom " + strGA);
				}
				groundBN.bn.connect(parent, node);
				parents[i++] = parent;
			}
			// fill CPT according to formula semantics
			// TODO try to reuse CPFs generated for previous formulas with same formula index
			fillFormulaCPF(gf, node.getCPF(), parents, GAs);
		}
		
		//groundBN.show();
	} 
	
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, BeliefNode[] parents, Vector<String> parentGAs) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		int[] addr = new int[nodes.length];
		fillFormulaCPF(gf, cpf, parents, parentGAs, 0, addr);
	}
	
	protected void fillFormulaCPF(GroundFormula gf, CPF cpf, BeliefNode[] parents, Vector<String> parentGAs, int iParent, int[] addr) throws Exception {
		// if all parents have been set, determine the truth value of the formula and 
		// fill the corresponding column of the CPT 
		State state = bln.getState();
		if(iParent == parents.length) {
			// get truth value of formula
			double value = gf.isTrue(state) ? 1 : 0;
			/*
			for(String ga : parentGAs)
				System.out.print(ga + " = " + state.get(ga) + ", ");
			System.out.println(" -> " + value);
			*/
			// write to CPF
			addr[0] = 0;
			cpf.put(addr, new ValueDouble(value));
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
	
	public SampledDistribution infer(String[][] evidence, String[] queries, int numSamples, int infoInterval) {
		// create full evidence
		String[][] fullEvidence = new String[evidence.length+this.hardFormulaNodes.size()][2];
		for(int i = 0; i < evidence.length; i++) {
			fullEvidence[i][0] = evidence[i][0];
			fullEvidence[i][1] = evidence[i][1];
		}
		{
			int i = evidence.length;
			for(String node : hardFormulaNodes) {
				fullEvidence[i][0] = node;
				fullEvidence[i][1] = "True";
				i++;
			}
		}			
		
		// sample
		Stopwatch sw = new Stopwatch();
		SampledDistribution dist = new SampledDistribution(groundBN);
		Random generator = new Random();
		System.out.println("sampling...");
		sw.start();
		for(int i = 1; i <= numSamples; i++) {
			if(i % infoInterval == 0)
				System.out.println("  step " + i);
			WeightedSample s = groundBN.getWeightedSample(fullEvidence, generator); // TODO check if this is inefficient
			dist.addSample(s);
		}
		sw.stop();
		System.out.println(String.format("time taken: %.2fs (%.4fs per sample)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples));
		
		// determine query nodes and print their distributions
		Pattern[] patterns = new Pattern[queries.length];
		for(int i = 0; i < queries.length; i++) {
			String p = queries[i];
			p = Pattern.compile("([,\\(])([a-z][^,\\)]*)").matcher(p).replaceAll("$1.*?");
			p = p.replace("(", "\\(").replace(")", "\\)") + ".*";			
			patterns[i] = Pattern.compile(p);			
		}
		BeliefNode[] nodes = groundBN.bn.getNodes();		
		for(int i = 0; i < nodes.length; i++)
			for(int j = 0; j < patterns.length; j++)				
				if(patterns[j].matcher(nodes[i].getName()).matches()) {
					dist.printNodeDistribution(System.out, i);
					break;
				}
		return dist;
	}
	
	public static void main(String[] args) {
		try { 
			BayesianLogicNetwork bln = new BayesianLogicNetwork(new BLOGModel("relxy.blog", "relxy.xml"), "relxy.bln");
			GroundBLN gbln = new GroundBLN(bln, "relxy.blogdb");
			gbln.infer(new String[][]{{"prop1(X)", "A1"},{"prop2(Y)", "A1"}}, new String[]{"rel(x,y)"}, 1000, 100);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
