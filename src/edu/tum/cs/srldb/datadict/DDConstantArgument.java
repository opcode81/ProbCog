package edu.tum.cs.srldb.datadict;

import edu.tum.cs.srldb.datadict.domain.Domain;

public class DDConstantArgument extends DDAttribute implements
		IDDRelationArgument {

	public DDConstantArgument(String name, Domain domain) {
		super(name, domain);		
	}
	
	public String getDomainName() {
		return this.domain.getName();
	}

}
