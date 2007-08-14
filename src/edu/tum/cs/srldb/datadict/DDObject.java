package edu.tum.cs.srldb.datadict;

import java.io.PrintStream;

import edu.tum.cs.srldb.IdentifierNamer;
import edu.tum.cs.srldb.Database;

public class DDObject extends DDItem implements IDDRelationArgument {
	public DDObject(String name) {
		super(name);
	}
	
	public boolean isObject() {
		return true;
	}
	
	public void MLNprintPredicateDeclarations(IdentifierNamer idNamer, PrintStream out) {
		out.println("// " + this.getName());			
		for(DDAttribute attr : attributes.values()) {
			if(attr.isDiscarded())
				continue;
			out.print(Database.stdPredicateName(attr.getName()) + "(" + idNamer.getLongIdentifier("domain", Database.stdDomainName(this.getName())));
			if(attr.isBoolean()) {
				out.println(")");
				continue;
			}
			out.print(", ");
			out.println(idNamer.getLongIdentifier("domain", Database.stdDomainName(attr.getDomain().getName())) + ")");
		}
		out.println();
	}
	
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
}
