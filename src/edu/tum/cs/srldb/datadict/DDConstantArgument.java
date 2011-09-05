package edu.tum.cs.srldb.datadict;

import java.io.Serializable;

import edu.tum.cs.srldb.datadict.domain.Domain;

/**
 * data dictionary definition of a type of relation partner that is a fixed set of constants 
 * @author jain
 */
public class DDConstantArgument extends DDAttribute implements IDDRelationArgument, Serializable {

	private static final long serialVersionUID = 1L;

	public DDConstantArgument(String name, Domain domain) {
		super(name, domain);		
	}
	
	public String getDomainName() {
		return this.domain.getName();
	}

}
