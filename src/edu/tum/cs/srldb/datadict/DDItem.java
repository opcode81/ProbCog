package edu.tum.cs.srldb.datadict;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.IdentifierNamer;

public abstract class DDItem implements Serializable {
	private static final long serialVersionUID = 1L;
	protected String name;
	protected HashMap<String, DDAttribute> attributes;
	
	public DDItem(String name) {
		this.name = name;
		attributes = new HashMap<String, DDAttribute>();
	}
	
	public String getName() {
		return name;
	}
	
	public void addAttribute(DDAttribute attr) throws DDException {
		attr.setOwner(this);
		attributes.put(attr.getName(), attr);
	}
	
	/**
	 * @return a hashmap containing all attributes for this item with attribute names as keys
	 */
	public HashMap<String, DDAttribute> getAttributes() {
		return attributes;
	}	
	
	public abstract boolean isObject();
	
	public void discardAllAttributesExcept(String[] keep) {
		for(Entry<String,DDAttribute> entry : attributes.entrySet()) {
			boolean discard = true;
			for(int i = 0; i < keep.length; i++)
				if(entry.getKey().equals(keep[i])) {
					discard = false;
					break;
				}	
			if(discard)
				entry.getValue().discard();
		}
	}
	
	public void MLNprintUnitClauses(IdentifierNamer idNamer, PrintStream out) {	
		for(DDAttribute attr : attributes.values()) {
			if(attr.isDiscarded() || attr.isBoolean())
				continue;
			String idCategory = attr.getName();
			idNamer.resetCounts();
			out.print(Database.stdPredicateName(attr.getName()) + "(" + idNamer.getCountedShortIdentifier(idCategory, this.getName()));
			out.print(", +");
			out.println(idNamer.getCountedShortIdentifier(attr.getName(), attr.getDomain().getName()) + ")");
		}
	}
	
	protected void outputAttributeList(PrintStream out) {
		Collection<DDAttribute> attributes = getAttributes().values();
		if(attributes.isEmpty())
			return;
		out.print(getName() + "_attr_names = [");
		int i = 0;
		for(DDAttribute attrib : attributes) {
			if(attrib.isDiscarded())
				continue;
			if(i++ > 0)
				out.print(", ");
			out.print("'" + Database.stdAttribName(attrib.getName()) + "'"); 
		}
		out.println("]");
	}
	
	protected void MLNprintAttributePredicateDeclaration(DDAttribute attr, String objectOfAttribute, IdentifierNamer idNamer, PrintStream out) {
		if(attr.isDiscarded())
			return;
		out.print(Database.stdPredicateName(attr.getName()) + "(" + objectOfAttribute);
		if(attr.isBoolean()) {
			out.println(")");
			return;
		}
		out.print(", ");
		out.print(idNamer.getLongIdentifier("domain", Database.stdDomainName(attr.getDomain().getName())));		
		out.println("!)");
	}

	protected void BLNprintAttributePredicateDeclaration(DDAttribute attr, String objectOfAttribute, IdentifierNamer idNamer, PrintStream out) {
		if(attr.isDiscarded())
			return;
		out.print("random ");
		if(attr.isBoolean())
			out.print("boolean");
		else
			out.print(idNamer.getLongIdentifier("domain", Database.stdDomainName(attr.getDomain().getName())));
		out.println(" " + Database.stdPredicateName(attr.getName()) + "(" + objectOfAttribute + ");");
	}
}
