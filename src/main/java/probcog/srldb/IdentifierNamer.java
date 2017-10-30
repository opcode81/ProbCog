/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
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
package probcog.srldb;

import java.util.HashSet;
import java.util.Map;

import probcog.srldb.datadict.DDAttribute;
import probcog.srldb.datadict.DDRelation;
import probcog.srldb.datadict.DataDictionary;

import edu.tum.cs.util.datastruct.Map2D;

/**
 * Helper class to name identifiers such that within one category, each identifier is unique. 
 * @author Dominik Jain
 */
public class IdentifierNamer {
	protected Map2D<String, String, String> identifiers;
	protected Map2D<String, String, Integer> counts;
	protected HashSet<String> reservedWords;
	
	public IdentifierNamer(DataDictionary dd) {
		identifiers = new Map2D<String, String, String>();
		counts = new Map2D<String, String, Integer>();
		reservedWords = new HashSet<String>();
		// init reserved words: identifiers shouldn't coincide with predicate names
		for(DDAttribute attrib : dd.getAttributes()) {
			reservedWords.add(Database.stdPredicateName(attrib.getName()));				
		}
		for(DDRelation rel : dd.getRelations()) {
			reservedWords.add(Database.stdPredicateName(rel.getName()));
		}
	}
	
	protected boolean isAvailable(String context, String id) {
		if(reservedWords.contains(id))
			return false;
		Map<String, String> submap = identifiers.getSubmap(context);
		if(submap == null)
			return true;
		return !submap.containsValue(id);
	}
	
	public String getShortIdentifier(String category, String name) {
		return getIdentifier(category, name.toLowerCase(), true, false);			
	}

	public String getCountedShortIdentifier(String category, String name) {
		return getIdentifier(category, name.toLowerCase(), true, true);			
	}
	
	public String getLongIdentifier(String category, String name) {
		return getIdentifier(category, name, false, false);			
	}

	public String getCountedLongIdentifier(String category, String name) {
		return getIdentifier(category, name, false, true);			
	}
	
	public String getIdentifier(String category, String name, boolean shortName, boolean counted) {
		String id = identifiers.get(category, name);
		if(id != null) {
			if(!counted)
				return id;
			Integer count = counts.get(category, name);
			if(count == null) {
				count = Integer.valueOf(0);
				counts.put(count);
			}
			count++;
			if(count == 1)
				return id; 
			else
				return id + count;
		}
		String idProposal = name;
		for(int i = shortName ? 1 : name.length(); true; i++) {
			idProposal = i > name.length() ? idProposal + "_" : name.substring(0, i);
			if(isAvailable(category, idProposal)) {
				identifiers.put(category, name, idProposal);
				return idProposal;
			}				
		}
	}
	
	public void resetCounts() {
		counts = new Map2D<String, String, Integer>();
	}
}
