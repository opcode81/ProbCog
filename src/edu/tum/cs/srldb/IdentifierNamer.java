package edu.tum.cs.srldb;

import java.util.HashMap;
import java.util.HashSet;

import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DataDictionary;

public class IdentifierNamer {
	protected HashMap<String, String> identifiers;
	protected HashMap<String, Integer> counts;
	protected HashSet<String> reservedWords;
	
	public IdentifierNamer(DataDictionary dd) {
		identifiers = new HashMap<String, String>();
		counts = new HashMap<String, Integer>();
		reservedWords = new HashSet<String>();
		// init reserved words: identifiers shouldn't coincide with predicate names
		for(DDAttribute attrib : dd.getAttributes()) {
			reservedWords.add(Database.stdPredicateName(attrib.getName()));				
		}
		for(DDRelation rel : dd.getRelations()) {
			reservedWords.add(Database.stdPredicateName(rel.getName()));
		}
	}
	
	public boolean isAvailable(String id) {
		return !identifiers.containsValue(id) && !reservedWords.contains(id);
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
		String key = category + "___" + name;
		String id = identifiers.get(key);
		if(id != null) {
			if(!counted)
				return id;
			Integer count = counts.get(key);
			if(count == null) {
				count = new Integer(0);
				counts.put(key, count);
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
			if(isAvailable(idProposal)) {
				identifiers.put(key, idProposal);
				return idProposal;
			}				
		}
	}
	
	public void resetCounts() {
		counts = new HashMap<String, Integer>();
	}
}