package probcog.srl.directed.inference;

import java.util.HashMap;
import java.util.Vector;

import probcog.bayesnets.inference.SATIS_BSampler;
import probcog.logic.Formula;
import probcog.logic.Negation;
import probcog.logic.TrueFalse;
import probcog.logic.sat.ClausalKB;
import probcog.srl.directed.RelationalBeliefNetwork;
import probcog.srl.directed.RelationalNode;
import probcog.srl.directed.CPT2MLNFormulas.CPT2Rules;
import probcog.srl.directed.bln.GroundBLN;
import probcog.srl.directed.bln.coupling.VariableLogicCoupling;

import weka.classifiers.trees.j48.Rule;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.util.datastruct.Map2D;

/**
 * extended version of the SAT-IS algorithm, where the knowledge base is augmented with formulas 
 * based on 0 entries in probabilistic constraints, which factually represent deterministic 
 * constraints 
 * @author jain
 */
public class SATISEx extends SATIS {
	/**
	 * whether to exploit context-specific independence (CSI) when extending the KB
	 */
	boolean exploitCSI = false;

	public SATISEx(GroundBLN bln) throws Exception {
		super(bln);
		this.paramHandler.add("useCSI", "useCSI");
	}
	
	public void useCSI(boolean active) {
		exploitCSI = active;
	}
	
	@Override
	public ClausalKB getClausalKB() throws Exception {
		ClausalKB ckb = super.getClausalKB();
		
		// extend the KB with formulas based on a CPD analysis		
		if(!exploitCSI)
			SATIS_BSampler.extendKBWithDeterministicConstraintsInCPTs(gbln.getGroundNetwork(), gbln.getCoupling(), ckb, gbln.getDatabase());
		else {			
			// gather the hard constraints for each fragment
			System.out.println("CSI analysis...");
			Map2D<RelationalNode, String, Vector<Formula>> constraints = new Map2D<RelationalNode, String, Vector<Formula>>();
			int numFormulas = 0;
			int numZeros = 0;
			int numDirectTranslations = 0;
			RelationalBeliefNetwork rbn = this.gbln.getRBN();
			for(RelationalNode relNode : rbn.getRelationalNodes()) {
				if(!relNode.isFragment())
					continue;
				//System.out.println(relNode);
				CPT2Rules cpt2rules = null;									
				for(HashMap<String,String> constantAssignment : relNode.getConstantAssignments()) {
					Vector<Formula> v = new Vector<Formula>();
					if(relNode.hasAggregator()) {
						Formula f = relNode.toFormula(constantAssignment);
						if(f == null)
							throw new Exception("Relational node " + relNode + " could not be translated to a formula");
						// TODO could fall back to direct reading of CPT in ground network
						v.add(f);			
						numDirectTranslations++;
					}
					else {
						if(cpt2rules == null) {
							cpt2rules = new CPT2Rules(relNode);
							numZeros += cpt2rules.getZerosInCPT();
						}
						// create formulas from rules							
						Rule[] rules = cpt2rules.learnRules(constantAssignment);					
						for(Rule rule : rules) {
							if(cpt2rules.getProbability(rule) == 0.0) {
								Formula f = cpt2rules.getConjunction(rule, constantAssignment);
								v.add(new Negation(f));
								numFormulas++;
							}
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
			System.out.printf("reduced %d zeros in CPTs to %d formulas; %d direct translations\n", numZeros, numFormulas, numDirectTranslations);
			
			// ground the constraints for the actual variables
			System.out.println("grounding constraints...");
			VariableLogicCoupling coupling = gbln.getCoupling();
			int sizeBefore = ckb.size();
			for(BeliefNode node : gbln.getRegularVariables()) { 
				RelationalNode template = gbln.getTemplateOf(node);
				//System.out.println(node + " from " + template);
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
					// get parameter binding				
					i = 0;
					String[] actualParams = new String[template.params.length];						
					for(String param : params)
						actualParams[i++] = param;				
					HashMap<String,String> binding = template.getParameterBinding(actualParams, gbln.getDatabase());				
					// ground the formulas and add them to the KB
					for(Formula f : vf) {
						//System.out.println("grounding " + f + " with " + binding);
						Formula gf = f.ground(binding, coupling.getWorldVars(), gbln.getDatabase());
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
			System.out.printf("added %d constraints\n", ckb.size()-sizeBefore);
		}
		
		return ckb;
	}
}
