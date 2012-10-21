/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.srl.directed;

import java.util.Collection;

import probcog.logic.Formula;
import probcog.srl.Signature;
import probcog.srl.mln.MarkovLogicNetwork;


/**
 * Abstract base class for conversions of relational models to MLNs.
 * @author Dominik Jain
 */
public abstract class MLNConverter {
	public abstract void addGuaranteedDomainElements(String domain, Collection<String> elements);
	public abstract void addSignature(Signature sig);
	public abstract void beginCPT(RelationalNode node);
	public abstract void addFormula(probcog.logic.Formula f, double weight) throws Exception;
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
 
