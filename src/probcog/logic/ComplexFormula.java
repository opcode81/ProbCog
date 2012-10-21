/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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
package probcog.logic;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;

/**
 * Represents a complex logical formula.
 * @author Dominik Jain
 */
public abstract class ComplexFormula extends Formula {
	public Formula[] children;
	
	public ComplexFormula(Collection<Formula> children) {
		this.children = children.toArray(new Formula[children.size()]);
	}
	
	public ComplexFormula(Formula ... children) {
		this.children = children;
	}
	
	/*public ComplexFormula(Formula[] children) {
		this.children = children;
	}*/
	
	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws Exception {
		for(Formula f : children)
			f.getVariables(db, ret);
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) throws Exception {
		for(Formula f : children)
			f.addConstantsToModel(m);
	}
	
	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?, ?> db) throws Exception {
		Vector<Formula> groundChildren = new Vector<Formula>();
		for(Formula child : children) {
			groundChildren.add(child.ground(binding, vars, db));
		}
		return this.getClass().getConstructor(Collection.class).newInstance(groundChildren);
	}
	
	public void getGroundAtoms(Set<GroundAtom> ret) {
		for(Formula child : children)
			child.getGroundAtoms(ret);
	}
}
