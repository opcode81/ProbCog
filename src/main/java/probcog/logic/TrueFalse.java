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

import java.util.Map;
import java.util.Set;

import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;

/**
 * Represents the constant formulas True and False.
 * @author Dominik Jain
 */
public class TrueFalse extends Formula {
	
	public static TrueFalse FALSE = new TrueFalse(false);
	public static TrueFalse TRUE = new TrueFalse(true);

	public static TrueFalse getInstance(boolean isTrue) {
		return isTrue ? TRUE : FALSE;
	}

	protected boolean isTrue;

	private TrueFalse(boolean isTrue) {
		this.isTrue = isTrue;
	}

	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) {
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?, ?> db) throws Exception {
		return this;
	}

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) {		
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		return isTrue;
	}

	public boolean isTrue() {
		return isTrue;
	}

	@Override
	public String toString() {
		return isTrue ? "True" : "False";
	}

	@Override
	public Formula toCNF() {
		return this;
	}
	
	@Override
	public Formula toNNF() {
		return this;
	}

    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
        return this;
    }
    
    public TrueFalse opposite() {
    	if(isTrue)
    		return FALSE;
    	return TRUE;
    }
}
