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

import probcog.exception.ProbCogException;
import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;
import probcog.srl.Signature;
import probcog.srl.taxonomy.Taxonomy;

import edu.tum.cs.util.StringTool;

/**
 * Represents a logical atom.
 * @author Dominik Jain
 */
public class Atom extends UngroundedFormula {

	public Collection<String> params;
	public String predName;

	public Atom(String predName, Collection<String> params) {
		this.predName = predName;
		this.params = params;
	}

	@Override
	public String toString() {
		return predName + "(" + StringTool.join(",", params) + ")";
	}

	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws ProbCogException {
        Signature sig = db.getSignature(predName);
        if(sig == null)
        	throw new ProbCogException("Unknown predicate '" + predName + "'");
		int i = 0;
		for(String param : params) {
			if(isVariable(param)) {
				String type;
				if(i < sig.argTypes.length)
					type = sig.argTypes[i];
				else
					type = sig.returnType;
				String oldtype = ret.put(param, type);
				if(oldtype != null && !type.equals(oldtype)) {
					Taxonomy taxonomy = db.getModel().getTaxonomy();
					if(taxonomy == null)
						throw new ProbCogException("The variable " + param + " is bound to more than one domain (and domains are incompatible): " + oldtype + " and " + type);
					else {
						boolean moreSpecific = taxonomy.query_isa(type, oldtype);
						boolean lessSpecific = taxonomy.query_isa(oldtype, type);
						if(!(moreSpecific || lessSpecific))
							throw new ProbCogException("The variable " + param + " is bound to more than one domain: " + oldtype + " and " + type);
						if(lessSpecific)
							ret.put(param, oldtype);
					}	
				}
			}
			++i;
		}
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) throws ProbCogException {
        Signature sig = m.getSignature(predName);
        if(sig == null)
        	throw new ProbCogException("Unknown predicate '" + predName + "'");
		int i = 0;
		for(String param : params) {
			if(!isVariable(param)) {
				String type;
				if(i < sig.argTypes.length)
					type = sig.argTypes[i];
				else
					type = sig.returnType;
				m.addGuaranteedDomainElement(type, param);
			}
			++i;
		}
	}

	public static boolean isVariable(String paramName) {
		return Character.isLowerCase(paramName.charAt(0));
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?, ?> db) throws ProbCogException {
		StringBuffer sb = new StringBuffer(predName + "(");
		int i = 0;
		for(String param : params) {
			if(i++ > 0)
				sb.append(',');
			String value = binding.get(param);
			if(value == null) { // if the binding contains no value for a parameter, it must be a constant
				if(isVariable(param))
					throw new ProbCogException("Cannot ground " + toString() + " with binding "  + binding + " - variable " + param + " unbound.");
				value = param;
			}
			sb.append(value);
		}
		sb.append(')');
		String strGA = sb.toString();
		GroundAtom ga = vars.get(strGA);
		if(ga == null)
			throw new ProbCogException("Could not find ground atom '" + strGA + "' in set of world variables.");
		return ga;
	}

	@Override
	public Formula toCNF() {
		return this;
	}
	
	@Override
	public Formula toNNF() {
		return this;
	}
}
