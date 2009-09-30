package edu.tum.cs.srldb.datadict;

import java.io.PrintStream;
import java.io.Serializable;

import edu.tum.cs.srldb.IdentifierNamer;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.tools.StringTool;

public class DDRelation extends DDItem implements Serializable {

	private static final long serialVersionUID = 1L;
	protected IDDRelationArgument[] arguments;
	protected boolean[] singleVal;
	
	public DDRelation(String name, IDDRelationArgument arg1, IDDRelationArgument arg2) throws DDException {
		this(name, arg1, arg2, false, false);
	}

	public DDRelation(String name, IDDRelationArgument arg1, IDDRelationArgument arg2, boolean singleValue1, boolean singleValue2) throws DDException {
		this(name, new IDDRelationArgument[]{arg1, arg2}, new boolean[]{singleValue1, singleValue2});
	}
	
	public DDRelation(String name, IDDRelationArgument[] arguments) throws DDException{
		this(name, arguments, null);
	}

	public DDRelation(String name, IDDRelationArgument[] arguments, boolean[] singleVal) throws DDException {
		super(name);
		this.arguments = arguments;
		setFunctional(singleVal);				
	}
	
	@Override
	public boolean isObject() {
		return false;
	}
	
	public IDDRelationArgument[] getArguments() {
		return arguments;
	}
	
	public void MLNprintPredicateDeclarations(IdentifierNamer idNamer, PrintStream out) {
		// get the relation's argument domains in a comma-separated list of domain names enclosed in brackets 
		StringBuffer params = new StringBuffer();
		for(int i = 0; i < arguments.length; i++) {
			if(i > 0) 
				params.append(", ");				
			params.append(idNamer.getLongIdentifier("domain", Database.stdDomainName(arguments[i].getDomainName())));
			if(singleVal[i])
				params.append("!");
		}
		// output the main predicate declaration
		out.println(Database.stdPredicateName(getName()) + "(" + params + ")");
		// output additional declarations for each attribute of the relation
		for(DDAttribute attr : attributes.values()) {
			MLNprintAttributePredicateDeclaration(attr, params.toString(), idNamer, out);
		}
	}

	public void BLNprintPredicateDeclarations(IdentifierNamer idNamer, PrintStream out) {
		// get the relation's argument domains in a comma-separated list of domain names enclosed in brackets 
		StringBuffer params = new StringBuffer();
		for(int i = 0; i < arguments.length; i++) {
			if(i > 0) 
				params.append(", ");				
			params.append(idNamer.getLongIdentifier("domain", Database.stdDomainName(arguments[i].getDomainName())));
		}
		// output the main predicate declaration
		String predName = Database.stdPredicateName(getName());
		out.println("random boolean " + predName + "(" + params + ");");
		// output additional declarations for each attribute of the relation
		for(DDAttribute attr : attributes.values()) {
			BLNprintAttributePredicateDeclaration(attr, params.toString(), idNamer, out);
		}
		// output relation keys, if any
		for(int i = 0; i < this.singleVal.length; i++)
			if(singleVal[i]) {
				out.print("RelationKey " + predName + "(");
				for(int j = 0; j < this.singleVal.length; j++) {
					if(j > 0)
						out.print(", ");
					out.print(j == i ? "_" : String.format("a%d", j));
				}
				out.println(");");
			}
	}

	@Deprecated
	public void MLNprintRules(IdentifierNamer idNamer, PrintStream out) {
		MLNprintRule(idNamer, getName(), this.singleVal, out);
		for(DDAttribute attr : attributes.values()) {
			if(attr instanceof DDRelationAttribute)
				MLNprintRule(idNamer, attr.getName(), ((DDRelationAttribute)attr).singleVal, out); 
		}
	}
	
	@Deprecated
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
	
	/**
	 * sets the functionality of this relation, i.e. which arguments are functionally determined by the others
	 * @param singleVal  an array of booleans with one entry per argument of the relation, where each true value indicates that the corresponding argument can take on exactly one value given the others; may be null to indicate that the relation is not functional
	 * @throws DDException
	 */
	public void setFunctional(boolean[] singleVal) throws DDException {		
		if(singleVal == null)
			singleVal = new boolean[arguments.length];
		this.singleVal = singleVal;
		if(arguments.length != this.singleVal.length)
			throw new DDException("Single value array dimension differs from object array dimension");
	}
	
	public String toString() {
		StringBuffer args = new StringBuffer();
		for(int i = 0; i < arguments.length; i++) {
			if(i > 0) args.append(", ");
			args.append(arguments[i].getDomainName());
		}
		return String.format("DDRelation:%s(%s) [%s]", name, args.toString(), StringTool.join(", ", this.attributes.values()));
	}
}
