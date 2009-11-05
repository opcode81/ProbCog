package edu.tum.cs.srl.bayesnets.inference;

import java.util.HashMap;
import java.util.Vector;

import weka.classifiers.trees.j48.Rule;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.bayesnets.inference.SATIS_BSampler;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.Negation;
import edu.tum.cs.logic.TrueFalse;
import edu.tum.cs.logic.sat.ClausalKB;
import edu.tum.cs.srl.bayesnets.ParentGrounder;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.RelationalNode;
import edu.tum.cs.srl.bayesnets.CPT2MLNFormulas.CPT2Rules;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.srl.bayesnets.bln.coupling.VariableLogicCoupling;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.Map2D;

/**
 * extended version of the SAT-IS algorithm, where the knowledge base is augmented with formulas 
 * based on 0 entries in probabilistic constraints, which factually represent deterministic 
 * constraints 
 * @author jain
 */
public class SATISEx extends SATIS {

	public SATISEx(GroundBLN bln) throws Exception {
		super(bln);
	}
	
	@Override
	protected ClausalKB getClausalKB() throws Exception {
		ClausalKB ckb = super.getClausalKB();
		
		// extend the KB with formulas based on a CPD analysis
		boolean exploitCSI = true; // whether to exploit context-specific independence (CSI)
		if(exploitCSI) {
			
			// gather the hard constraints for each fragment
			System.out.println("CSI analysis...");
			Map2D<RelationalNode, String, Vector<Formula>> constraints = new Map2D<RelationalNode, String, Vector<Formula>>();
			int numFormulas = 0;
			int numZeros = 0;
			RelationalBeliefNetwork rbn = this.gbln.getRBN();
			for(RelationalNode relNode : rbn.getRelationalNodes()) {
				if(!relNode.isFragment())
					continue;
				//System.out.println(relNode);
				CPT2Rules cpt2rules = new CPT2Rules(relNode);
				numZeros += cpt2rules.getZerosInCPT();				
				for(HashMap<String,String> constantAssignment : relNode.getConstantAssignments()) {
					// create formulas from rules					
					Vector<Formula> v = new Vector<Formula>();
					Rule[] rules = cpt2rules.learnRules(constantAssignment);					
					for(Rule rule : rules) {
						if(cpt2rules.getProbability(rule) == 0.0) {
							Formula f = cpt2rules.getConjunction(rule, constantAssignment);
							v.add(f);
							numFormulas++;
						}
					}
					// create key for this constant assignment
					StringBuffer sb = new StringBuffer();
					for(Integer i : relNode.getIndicesOfConstantParams())
						sb.append(constantAssignment.get(relNode.params[i]));
					String constantKey = sb.toString();
					// store
					constraints.put(relNode, constantKey, v);
				}				
			}
			System.out.printf("reduced %d zeros in CPTs to %d formulas\n", numZeros, numFormulas);
			
			// ground the constraints for the actual variables
			System.out.println("grounding constraints...");
			VariableLogicCoupling coupling = gbln.getCoupling();
			int sizeBefore = ckb.size();
			for(BeliefNode node : gbln.getRegularVariables()) { 
				RelationalNode template = gbln.getTemplateOf(node);
				System.out.println(node + " from " + template);
				Iterable<String> params = coupling.getOriginalParams(node);
				// get the constant key
				StringBuffer sb = new StringBuffer();
				int i = 0;
				Vector<Integer> constIndices = template.getIndicesOfConstantParams();
				for(String p : params) {
					if(constIndices.contains(i))
						sb.append(p);
					i++;
				}
				String constantKey = sb.toString();
				// check if there are any hard constraints for this template
				Vector<Formula> vf = constraints.get(template, constantKey);
				if(vf != null) {
					// construct the variable binding
					i = 0;
					String[] actualParams = new String[template.params.length];
					System.out.println(StringTool.join(" ", params));
					for(String param : params)
						actualParams[i++] = param;
					ParentGrounder pg = new ParentGrounder(gbln.getRBN(), template);
					HashMap<String,String> binding = pg.generateParameterBindings(actualParams, gbln.getDatabase());					
					// ground the formulas and add them to the KB
					for(Formula f : vf) {
						Formula gf = new Negation(f.ground(binding, coupling.getWorldVars(), null));
						Formula gfs = gf.simplify(gbln.getDatabase());
						if(gfs instanceof TrueFalse) {
							TrueFalse tf = (TrueFalse)gfs;
							if(!tf.isTrue())
								System.err.println("unsatisfiable formula" + gf);
							continue;
						}
						ckb.addFormula(gfs);
					}
				}
			}
			System.out.printf("added %d constraints from CPTs\n", ckb.size()-sizeBefore);
		}
		else 
			SATIS_BSampler.extendKBWithDeterministicConstraintsInCPTs(gbln.getGroundNetwork(), gbln.getCoupling(), ckb, gbln.getDatabase());
		
		return ckb;
	}
}
