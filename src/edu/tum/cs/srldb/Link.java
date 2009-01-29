package edu.tum.cs.srldb;
import java.io.PrintStream;
import java.util.Map.Entry;

import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;

public class Link extends Item {
	protected String linkName;
	protected IRelationArgument[] arguments;
		
	public Link(Database database, String linkName, IRelationArgument arg1, IRelationArgument arg2) {
		this(database, linkName, new IRelationArgument[]{arg1, arg2});
	}
	
	public Link(Database database, String linkName, IRelationArgument[] arguments) {
		super(database);
		this.linkName = linkName;
		this.arguments = arguments;		
	}
	
	/*public void addAttribute(String attribute, String value, DataTypeEnum type) {
		addAttribute(attribute, value, type, "L");
	}*/

	/**
	 * gets the parameters (i.e. the constants) that this link connects in a comma-separated string, e.g. "Foo, Bar" 
	 */
	protected String getLinkParams() {
		StringBuffer linkParams = new StringBuffer();
		for(int i = 0; i < arguments.length; i++) {
			if(i > 0)
				linkParams.append(", ");
			linkParams.append(Database.upperCaseString(arguments[i].getConstantName()));
		}	
		return linkParams.toString();
	}
	
	public String getLogicalAtom() {
		return linkName + "(" + getLinkParams() + ")";
	}
	
	public void MLNprintFacts(java.io.PrintStream out) throws DDException {
		// print the relation fact
		out.println(getLogicalAtom());
		// if the link has boolean attributes, output further facts with the attribute
		// name as the predicate name
		for(String attribName : attribs.keySet()) {			
			DDAttribute ddAttr = database.getDataDictionary().getAttribute(attribName);
			//RelDatabase.AttributeData data = this.attributes.get(attribName);
			if(/*data != null) {
				if(data.type == DataTypeEnum.STR &&*/ ddAttr.isBoolean()) {
					out.print(((BooleanDomain)ddAttr.getDomain()).isTrue(attribs.get(attribName)) ? "" : "!");
					out.println(attribName + "(" + getLinkParams() + ")");
				//}
			}
			else
				throw new DDException("Non-boolean attributes of links not handled for MLNs");
		}
	}
	
	/**
	 * prints facts on this link object (for BLOG databases)
	 * @param out
	 * @throws DDException
	 */
	public void BLOGprintFacts(PrintStream out) throws DDException {
		out.printf("%s = True;\n", getLogicalAtom());
		// attributes
		String linkObjects = getLinkParams();
		for(Entry<String, String> entry : getAttributes().entrySet()) {
			DDAttribute ddAttrib = database.getDataDictionary().getAttribute(entry.getKey()); 
			if(ddAttrib.isDiscarded())
				continue;
			out.printf("%s(%s) = %s;\n", entry.getKey(), linkObjects, Database.upperCaseString(entry.getValue())); 
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
		db.addLink(this);
	}
	
	public String getName() {
		return this.linkName;
	}
	
	public IRelationArgument[] getArguments() {
		return this.arguments;
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
}
