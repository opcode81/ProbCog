package edu.tum.cs.srldb.datadict;

import java.io.PrintStream;

import edu.tum.cs.srldb.IdentifierNamer;
import edu.tum.cs.srldb.Database;

public class DDRelation extends DDItem {

	protected DDObject[] objects;
	protected boolean[] singleVal;
	
	public DDRelation(String name, DDObject obj1, DDObject obj2) throws Exception {
		this(name, obj1, obj2, false, false);
	}

	public DDRelation(String name, DDObject obj1, DDObject obj2, boolean singleValue1, boolean singleValue2) throws Exception {
		this(name, new DDObject[]{obj1, obj2}, new boolean[]{singleValue1, singleValue2});
	}
	
	public DDRelation(String name, DDObject[] objects) throws Exception {
		this(name, objects, null);
	}

	public DDRelation(String name, DDObject[] objects, boolean[] singleVal) throws Exception {
		super(name);
		if(singleVal == null)
			singleVal = new boolean[objects.length];
		if(objects.length != singleVal.length)
			throw new Exception("single value array dimension differs from object array dimension");
		this.objects = objects;
		this.singleVal = singleVal;
	}
	
	@Override
	public boolean isObject() {
		return false;
	}
	
	public DDObject[] getObjects() {
		return objects;
	}
	
	public void MLNprintPredicateDeclarations(IdentifierNamer idNamer, PrintStream out) {
		StringBuffer params = new StringBuffer("(");
		for(int i = 0; i < objects.length; i++) {
			if(i > 0) 
				params.append(", ");				
			params.append(idNamer.getLongIdentifier("domain", Database.stdDomainName(objects[i].getName())));
		}
		params.append(")");
		out.println(Database.stdPredicateName(getName()) + params);
		for(DDAttribute attr : attributes.values()) {
			out.println(Database.stdPredicateName(attr.getName()) + params); 
		}
	}

	public void MLNprintRules(IdentifierNamer idNamer, PrintStream out) {
		MLNprintRule(idNamer, getName(), this.singleVal, out);
		for(DDAttribute attr : attributes.values()) {
			if(attr instanceof DDRelationAttribute)
				MLNprintRule(idNamer, attr.getName(), ((DDRelationAttribute)attr).singleVal, out); 
		}
	}
	
	protected void MLNprintRule(IdentifierNamer idNamer, String predicate, boolean[] singleVal, PrintStream out) {
		StringBuffer params = new StringBuffer("(");
		int single = 0;
		idNamer.resetCounts();
		for(int i = 0; i < objects.length; i++) {			
			if(i > 0) 
				params.append(", ");				
			params.append(idNamer.getCountedShortIdentifier("var", objects[i].getName()));
			if(singleVal[i]) {
				params.append("!");
				single++;
			}
		}
		if(single == 0)
			return;
		params.append(")");
		out.println(Database.stdPredicateName(predicate) + params);		
	}
	
	@Override
	public void addAttribute(DDAttribute attrib) throws DDException {
		if(!(attrib instanceof DDRelationAttribute))
			throw new DDException("this type of attribute cannot be used for relations; use DDRelationAttribute");
		DDRelationAttribute attr = (DDRelationAttribute) attrib;
		if(attr.singleVal == null)
			attr.singleVal = this.singleVal;
		else if(attr.singleVal.length != this.singleVal.length)
			throw new DDException("attribute's singleVal array has incorrect length; must match that of relation object");
		super.addAttribute(attrib);
	}
}
