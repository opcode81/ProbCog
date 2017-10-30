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
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import probcog.exception.ProbCogException;
import probcog.srldb.datadict.DDAttribute;
import probcog.srldb.datadict.DDException;
import probcog.srldb.datadict.domain.AutomaticDomain;
import probcog.srldb.datadict.domain.BooleanDomain;
import probcog.srldb.datadict.domain.Domain;

/**
 * Base class for items appearing in a database.
 * @author Dominik Jain
 */
public abstract class Item implements Serializable {
	private static final long serialVersionUID = 1L;
	protected HashMap<String,String> attribs;
	protected static int GUID = 1;
	protected int id;
	protected Database database;
	/** whether this object is immutable (e.g. because it has been committed to a database) */
	protected boolean immutable = false;
	
	public Item(Database database) {
		this.attribs = new HashMap<String, String>();
		this.id = GUID++;
		this.database = database;
	}
	
	public void addAttribsFromResultSet(ResultSet rs, boolean callNext) throws ProbCogException {
		checkMutable();
		boolean erroneous = false;
		try {			
			ResultSetMetaData rsmd = rs.getMetaData();
			int numCols = rsmd.getColumnCount();
			if(callNext)
				if(!rs.next())
					return;
			for(int i = 1; i <= numCols; i++) {
				String val = rs.getString(i);
				if(val == null)
					erroneous = true;
				addAttribute(rsmd.getColumnName(i), val);
			}
		}
		catch(SQLException e) {
			e.printStackTrace();
		}
		if(erroneous)
			throw new ProbCogException("Result set contains null entry!");
	}
	
	/**
	 * adds an attribute to the object; if the attribute was previously defined, the old value is overwritten
	 * @param attribute  the name of the attribute
	 * @param value  the attribute value
	 * @throws DDException 
	 * @throws DDException 
	 */
	public void addAttribute(String attribute, String value) throws DDException {
		checkMutable();
		DDAttribute attrib = database.getDataDictionary().getAttribute(attribute);
		if(attrib != null) {
			Domain<?> domain = attrib.getDomain();
			if(domain instanceof AutomaticDomain)
				((AutomaticDomain)domain).addValue(value);
		}
		attribs.put(attribute, value);
	}
	
	/**
	 * adds all the attributes given in the map
	 * @param attributes a map of (key, value) pairs 
	 */
	public void addAttributes(Map<String,String> attributes) throws DDException {
		checkMutable();
		for(Entry<String,String> entry : attributes.entrySet()) {
			addAttribute(entry.getKey(), entry.getValue());
		}
	}
	
	protected void checkMutable() throws DDException {
		if(immutable) 
			throw new DDException("This object can no longer be modified, probably because it has already been committed.");
	}
	
	/**
	 * prints the item's attributes
	 */
	public void print() {
		Set<String> keys = attribs.keySet();
		for(Iterator<String> i = keys.iterator(); i.hasNext();) {
			String key = i.next();
			String value = attribs.get(key);
			System.out.println(key + ": " + value);
		}
	}
	
	public int getInt(String attrName) {
		return Integer.parseInt(attribs.get(attrName));
	}

	public double getDouble(String attrName) {
		return Double.parseDouble(attribs.get(attrName));
	}
	
	public String getString(String attrName) {
		return attribs.get(attrName);
	}
	
	public Database getDatabase() {
		return database;
	}
	
	public Map<String,String> getAttributes() {
		return attribs;
	}
	
	public Set<String> getAttributeNames() {
		return attribs.keySet(); 
	}
	
	public String getAttributeValue(String attribName) {
		return getString(attribName);
	}
	
	public boolean getBoolean(String attribName) {
		return BooleanDomain.getInstance().isTrue(getString(attribName));
	}
	
	public boolean hasAttribute(String attribName) {
		return attribs.containsKey(attribName);
	}
	
	public int getGUID() {
		return this.id;
	}
	
	public void printData() {
		System.out.println("Item " + id);
		if(attribs.size() > 0) {
			System.out.println("  attributes:");
			for(Entry<String,String> e : this.attribs.entrySet())
				System.out.printf("    %s = %s\n", e.getKey(), e.getValue());
		}
	}
}
