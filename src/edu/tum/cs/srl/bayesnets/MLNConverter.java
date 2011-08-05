/*
 * Created on Nov 9, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets;

import java.util.Collection;

import edu.tum.cs.logic.Formula;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.mln.MarkovLogicNetwork;

/**
 * abstract base class for conversions of relational models to MLNs
 * @author jain
 */
public abstract class MLNConverter {
	public abstract void addGuaranteedDomainElements(String domain, Collection<String> elements);
	public abstract void addSignature(Signature sig);
	public abstract void beginCPT(RelationalNode node);
	public abstract void addFormula(edu.tum.cs.logic.Formula f, double weight) throws Exception;
	public abstract void addHardFormula(Formula f) throws Exception;
	public abstract void endCPT();
	public abstract void addFunctionalDependency(String predicate, Integer functionallyDeterminedArg);
	
	public static class MLNObjectWriter extends MLNConverter {
		public MarkovLogicNetwork mln;
		
		public MLNObjectWriter() {
			mln = new MarkovLogicNetwork();
		}

		@Override
		public void addFormula(Formula f, double weight) throws Exception {
			mln.addFormula(f, weight);
		}

		@Override
		public void addFunctionalDependency(String predicate, Integer functionallyDeterminedArg) {
			mln.addFunctionalDependency(predicate, functionallyDeterminedArg);
		}

		@Override
		public void addSignature(Signature sig) {
			mln.addSignature(sig);
		}

		@Override
		public void beginCPT(RelationalNode node) {
		}

		@Override
		public void endCPT() {
		}		
		
		public MarkovLogicNetwork getMLN() {
			return mln;
		}

		@Override
		public void addGuaranteedDomainElements(String domain, Collection<String> elements) {
			for(String e : elements)
				mln.addGuaranteedDomainElement(domain, e);
		}

		@Override
		public void addHardFormula(Formula f) throws Exception {
			mln.addHardFormula(f);			
		}	
	}
}
 
