package edu.tum.cs.srldb.datadict;

import java.io.PrintStream;
import java.io.Serializable;

import edu.tum.cs.srldb.IdentifierNamer;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.util.StringTool;

public class DDObject extends DDItem implements IDDRelationArgument, Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * @param name the name of the class of objects that this data dictionary object represents
	 */
	public DDObject(String name) {
		super(name);
	}
	
	public boolean isObject() {
		return true;
	}
	
	public void MLNprintPredicateDeclarations(IdentifierNamer idNamer, PrintStream out) {
		out.println("// " + this.getName());
		String objectDomain = idNamer.getLongIdentifier("domain", Database.stdDomainName(this.getName()));
		for(DDAttribute attr : attributes.values()) {
			MLNprintAttributePredicateDeclaration(attr, objectDomain, idNamer, out);
		}
		out.println();
	}
	
	public void BLNprintPredicateDeclarations(IdentifierNamer idNamer, PrintStream out) {
		out.println("// " + this.getName());
		String objectDomain = idNamer.getLongIdentifier("domain", Database.stdDomainName(this.getName()));
		for(DDAttribute attr : attributes.values()) {
			BLNprintAttributePredicateDeclaration(attr, objectDomain, idNamer, out);
		}
		out.println();
	}
	
	@Deprecated
	public void MLNprintRules(IdentifierNamer idNamer, PrintStream out) {	
		out.println("// mutual exclusiveness and exhaustiveness: " + getName() + " attributes");
		for(DDAttribute attr : attributes.values()) {
			if(attr.isDiscarded() || attr.isBoolean())
				continue;
			idNamer.resetCounts();
			out.print(Database.stdPredicateName(attr.getName()) + "(" + idNamer.getCountedShortIdentifier("var", this.getName()));
			out.print(", ");
			out.println(idNamer.getCountedShortIdentifier("var", attr.getDomain().getName()) + "!)");
		}
	}
	
	public String getDomainName() {
		return name;
	}
	
	public String toString() {
		return String.format("DDObject:%s [%s]", name, StringTool.join(", ", this.attributes.values()));
	}
}
