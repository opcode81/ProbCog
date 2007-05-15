package edu.tum.cs.srldb;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.domain.BooleanDomain;

public class Link extends Item {
	protected String linkName;
	protected Object[] objects;
		
	public Link(Database database, String linkName, Object o1, Object o2) {
		this(database, linkName, new Object[]{o1, o2});
	}
	
	public Link(Database database, String linkName, Object[] objects) {
		super(database);
		this.linkName = linkName;
		this.objects = objects;		
	}
	
	/*public void addAttribute(String attribute, String value, DataTypeEnum type) {
		addAttribute(attribute, value, type, "L");
	}*/
	
	protected String getLinkParams() {
		String linkParams = "(";
		for(int i = 0; i < objects.length; i++) {
			if(i > 0)
				linkParams += ", ";
			linkParams += objects[i].MLNid();
		}
		linkParams += ")";		
		return linkParams;
	}
	
	public String getLogicalAtom() {
		return linkName + getLinkParams();
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
					out.println(attribName + getLinkParams());
				//}
			}
		}
	}
	
	public String toString() {
		return getLogicalAtom();
	}
	
	/**
	 * adds this link to the database given at construction
	 */
	public void commit() {
		addTo(this.database);
	}
	
	/**
	 * adds this link to the given database
	 * @param db
	 */
	public void addTo(Database db) {
		db.addLink(this);
	}
	
	public String getName() {
		return this.linkName;
	}
	
	public Object[] getObjects() {
		return this.objects;
	}
	
	public void setSecondObject(Object o) {
		objects[1] = o;
	}
	
	public void setFirstObject(Object o) {
		objects[0] = o;
	}
	
	public void setObjects(Object[] objs) {
		this.objects = objs;
	}
}
