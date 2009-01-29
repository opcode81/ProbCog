package edu.tum.cs.srldb.datadict;

import java.io.Serializable;

import edu.tum.cs.srldb.datadict.domain.Domain;

public class DDConstantArgument extends DDAttribute implements IDDRelationArgument, Serializable {

	private static final long serialVersionUID = 1L;

	public DDConstantArgument(String name, Domain domain) {
		super(name, domain);		
	}
	
	public String getDomainName() {
		return this.domain.getName();
	}

}
