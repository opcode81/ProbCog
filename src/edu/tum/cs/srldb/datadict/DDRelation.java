package edu.tum.cs.srldb.datadict;

import java.io.PrintStream;

import edu.tum.cs.srldb.IdentifierNamer;
import edu.tum.cs.srldb.Database;

public class DDRelation extends DDItem {

	protected IDDRelationArgument[] arguments;
	protected boolean[] singleVal;
	
	public DDRelation(String name, IDDRelationArgument arg1, IDDRelationArgument arg2) throws Exception {
		this(name, arg1, arg2, false, false);
	}

	public DDRelation(String name, IDDRelationArgument arg1, IDDRelationArgument arg2, boolean singleValue1, boolean singleValue2) throws Exception {
		this(name, new IDDRelationArgument[]{arg1, arg2}, new boolean[]{singleValue1, singleValue2});
	}
	
	public DDRelation(String name, IDDRelationArgument[] arguments) throws Exception {
		this(name, arguments, null);
	}

	public DDRelation(String name, IDDRelationArgument[] arguments, boolean[] singleVal) throws Exception {
		super(name);
		if(singleVal == null)
			singleVal = new boolean[arguments.length];
		if(arguments.length != singleVal.length)
			throw new Exception("single value array dimension differs from object array dimension");
		this.arguments = arguments;
		this.singleVal = singleVal;
	}
	
	@Override
	public boolean isObject() {
		return false;
	}
	
	public IDDRelationArgument[] getArguments() {
		return arguments;
	}
	
	public void MLNprintPredicateDeclarations(IdentifierNamer idNamer, PrintStream out) {
		// get the relation's argument domains in a comma-separated list of domain names eclosed in brackets 
		StringBuffer params = new StringBuffer();
		for(int i = 0; i < arguments.length; i++) {
			if(i > 0) 
				params.append(", ");				
			params.append(idNamer.getLongIdentifier("domain", Database.stdDomainName(arguments[i].getDomainName())));
		}
		// output the main predicate declaration
		out.println(Database.stdPredicateName(getName()) + "(" + params + ")");
		// output additional declarations for each attribute of the relation
		for(DDAttribute attr : attributes.values()) {
			MLNprintAttributePredicateDeclaration(attr, params.toString(), idNamer, out);
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
		for(int i = 0; i < arguments.length; i++) {			
			if(i > 0) 
				params.append(", ");				
			params.append(idNamer.getCountedShortIdentifier("var", arguments[i].getDomainName()));
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
		if(attrib instanceof DDRelationAttribute) {			
			DDRelationAttribute attr = (DDRelationAttribute) attrib;
			if(attr.singleVal == null)
				attr.singleVal = this.singleVal;
			else if(attr.singleVal.length != this.singleVal.length)
				throw new DDException("attribute's singleVal array has incorrect length; must match that of relation object");
		}
		else
			;//throw new DDException("this type of attribute cannot be used for relations; use DDRelationAttribute");
		super.addAttribute(attrib);
	}
}
