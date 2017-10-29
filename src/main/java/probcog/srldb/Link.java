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
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Map.Entry;

import probcog.srl.directed.ABLModel;
import probcog.srl.mln.MLNWriter;
import probcog.srldb.datadict.DDAttribute;
import probcog.srldb.datadict.DDException;
import probcog.srldb.datadict.IDDRelationArgument;
import probcog.srldb.datadict.domain.BooleanDomain;

import edu.tum.cs.util.StringTool;

/**
 * Represents a link between two or more objects in a relational database. 
 * @author Dominik Jain
 */
public class Link extends Item implements Serializable {
	private static final long serialVersionUID = 1L;
	protected String linkName;
	protected IRelationArgument[] arguments;
	/**
	 * whether the link is present (if false, the link does not exist)
	 */
	protected boolean exists; 
		
	public Link(Database database, String linkName, IRelationArgument arg1, IRelationArgument arg2) {
		this(database, linkName, new IRelationArgument[]{arg1, arg2});
	}
	
	public Link(Database database, String linkName, IRelationArgument[] arguments) {
		this(database, linkName, arguments, true);
	}
	
	public Link(Database database, String linkName, IRelationArgument[] arguments, boolean exists) {
		super(database);
		this.linkName = linkName;
		this.arguments = arguments;
		this.exists = true;
	}
	
	public void setExists(boolean exists) {
		this.exists = exists;
	}
	
	/*public void addAttribute(String attribute, String value, DataTypeEnum type) {
		addAttribute(attribute, value, type, "L");
	}*/
	
	/**
	 * gets a string representation of the literal represented by this link
	 */
	public String getLogicalAtom() {
		return (exists ? "" : "!") + linkName + "(" + StringTool.join(", ", arguments) + ")";
	}
	
	public void MLNprintFacts(java.io.PrintStream out) throws DDException {
		String[] params = new String[this.arguments.length];
		for(int i = 0; i < params.length; i++)
			params[i] = MLNWriter.formatAsConstant(arguments[i].getConstantName());
		String allParams = StringTool.join(", ", params);
		String atom = linkName + "(" + allParams + ")";
		// print the relation fact
		if(!exists) out.print('!');
		out.println(atom);
		// if the link has boolean attributes, output further facts with the attribute
		// name as the predicate name
		for(String attribName : attribs.keySet()) {			
			DDAttribute ddAttr = database.getDataDictionary().getAttribute(attribName);
			//RelDatabase.AttributeData data = this.attributes.get(attribName);
			if(/*data != null) {
				if(data.type == DataTypeEnum.STR &&*/ ddAttr.isBoolean()) {
					out.print(((BooleanDomain)ddAttr.getDomain()).isTrue(attribs.get(attribName)) ? "" : "!");
					out.println(attribName + "(" + allParams + ")");
				//}
			}
			else
				throw new DDException("Non-boolean attributes of links not handled for MLNs");
		}
	}
	
	/**
	 * prints facts on this link object (for BLOG databases)
	 * @param out
	 * @throws Exception 
	 */
	public void BLOGprintFacts(PrintStream out) throws Exception {
		String[] params = new String[this.arguments.length];
		for(int i = 0; i < params.length; i++) {
			params[i] = Database.upperCaseString(arguments[i].getConstantName());
			if(!ABLModel.isValidEntityName(params[i]))
				throw new Exception("'" + params[i] + "' is not a valid entity name");
		}
		String allParams = StringTool.join(", ", params);
		String atom = linkName + "(" + allParams + ")";
		out.printf("%s = %s;\n", atom, exists ? probcog.srl.BooleanDomain.True : probcog.srl.BooleanDomain.False);		
		// attributes
		String linkObjects = allParams;
		for(Entry<String, String> entry : getAttributes().entrySet()) {
			String predName = Database.stdPredicateName(entry.getKey());
			DDAttribute ddAttrib = database.getDataDictionary().getAttribute(entry.getKey()); 
			if(ddAttrib.isDiscarded())
				continue;
			String value = Database.upperCaseString(entry.getValue());
			if(!ABLModel.isValidEntityName(value))
				throw new DDException("\"" + value + "\" is not a valid entity name");
			out.printf("%s(%s) = %s;\n", predName, linkObjects, value); 
		}
	}
	
	public String toString() {
		return getLogicalAtom();
	}
	
	/**
	 * adds this link to the database given at construction
	 * @throws DDException 
	 */
	public void commit() throws DDException {
		addTo(this.database);
	}
	
	/**
	 * adds this link to the given database
	 * @param db
	 * @throws DDException 
	 */
	public void addTo(Database db) throws DDException {
		if(db == this.database) // this is a commit
			immutable = true;
		// add the link to the database
		db.addLink(this);
		// get the objects that are linked here (this creates objects for constant arguments);
		// creating objects may be required depending on how the database is read (it is e.g. necessary for Proximity databases)
		for(int i = 0; i < this.arguments.length; i++)
			getArgumentObject(db, i);
	}
	
	public Object getArgumentObject(Database db, int i) throws DDException {
		if(arguments[i] instanceof ConstantArgument) {
			IDDRelationArgument argType = database.getDataDictionary().getRelation(this.linkName).getArguments()[i];				
			return db.getConstantAsObject(argType.getDomainName(), arguments[i].getConstantName());
		}
		else
			return (Object)arguments[i];
	}
	
	public String getName() {
		return this.linkName;
	}
	
	/**
	 * @return the relation arguments
	 */
	public IRelationArgument[] getArguments() {
		return this.arguments;
	}
	
	/**
	 * gets the relation arguments as objects, converting constant arguments
	 * to objects beforehand
	 * @return an array of objects
	 * @throws DDException 
	 */
	public Object[] getArgumentObjects() throws DDException {
		Object[] ret = new Object[arguments.length];
		for(int i = 0; i < arguments.length; i++) 
			ret[i] = getArgumentObject(this.database, i);		
		return ret;
	}
	
	public void setSecondArgument(IRelationArgument arg) {
		arguments[1] = arg;
	}
	
	public void setFirstArgument(IRelationArgument arg) {
		arguments[0] = arg;
	}
	
	public void setArguments(IRelationArgument[] arguments) {
		this.arguments = arguments;
	}
	
	public void printData() {
		super.printData();
		System.out.printf("  link: %s(", linkName);
		for(int i = 0; i < arguments.length; i++) {
			if(i > 0)
				System.out.print(", ");
			if(arguments[i] instanceof Item)
				System.out.printf("Item%d:", ((Item)arguments[i]).id);
			System.out.print(arguments[i]);
		}
		System.out.println(")");
	}
}
