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
package probcog.srldb.datadict;

import java.io.PrintStream;
import java.io.Serializable;

import probcog.srldb.Database;
import probcog.srldb.IdentifierNamer;

import edu.tum.cs.util.StringTool;

/**
 * Data dictionary definition of an object type.
 * @author Dominik Jain
 */
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
