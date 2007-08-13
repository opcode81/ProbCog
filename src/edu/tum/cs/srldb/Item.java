package edu.tum.cs.srldb;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.domain.AutomaticDomain;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;
import edu.tum.cs.srldb.datadict.domain.Domain;

public abstract class Item {
	protected HashMap<String,String> attribs;
	protected static int GUID = 1;
	protected int id;
	protected Database database;
	
	public Item(Database database) {
		this.attribs = new HashMap<String, String>();
		this.id = GUID++;
		this.database = database;
	}
	
	public void addAttribsFromResultSet(ResultSet rs, boolean callNext) throws Exception {
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
			throw new Exception("Result set contains null entry!");
	}
	
	/**
	 * adds an attribute to the object; if the attribute was previously defined, the old value is overwritten
	 * @param attribute  the name of the attribute
	 * @param value  the attribute value
	 */
	public void addAttribute(String attribute, String value) {
		DDAttribute attrib = database.getDataDictionary().getAttribute(attribute);
		if(attrib != null) {
			Domain domain = attrib.getDomain();
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
		for(Entry<String,String> entry : attributes.entrySet()) {
			addAttribute(entry.getKey(), entry.getValue());
		}
	}
	
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
}
