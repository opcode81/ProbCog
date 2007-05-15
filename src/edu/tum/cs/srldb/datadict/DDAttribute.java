package edu.tum.cs.srldb.datadict;

import edu.tum.cs.srldb.datadict.domain.BooleanDomain;
import edu.tum.cs.srldb.datadict.domain.Domain;
import kdl.prox3.dbmgr.DataTypeEnum;

public class DDAttribute implements Cloneable {
	protected String name;
	protected Domain domain;
	protected boolean doClustering;
	protected boolean discarded;
	protected DDItem owner;
	
	protected DDAttribute(String name) {
		this.name = name;
		this.domain = null;
		this.doClustering = false;
		this.discarded = false;
		this.owner = null;
	}
	
	public DDAttribute(String name, Domain domain) {
		this(name);
		this.domain = domain;
	}
	
	public DDAttribute(String name, Domain domain, boolean doClustering) {
		this(name, domain);
		this.doClustering = doClustering;
	}
	
	public String getName() {
		return name;
	}
	
	public DataTypeEnum getType() {
		return domain.getType();
	}
	
	public boolean requiresClustering() {
		return doClustering;
	}
	
	public Domain getDomain() {
		return domain;
	}
	
	public boolean isBoolean() {
		return domain instanceof BooleanDomain;
	}
	
	/**
	 * marks this attribute as discarded/unused<br>
	 * 
	 * An attribute may eventually be discarded even though it is defined, because,
	 * for example, it requires clustering and too few instances to actually perform
	 * clustering were found in the database.
	 *  
	 * @return
	 */
	public void discard() {
		discarded = true;
	}
	
	public boolean isDiscarded() {
		return this.discarded;
	}
	
	public DDAttribute clone() {
		try {
			return (DDAttribute)super.clone();
		}
		catch (CloneNotSupportedException e) { return null; }		
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public void setDomain(Domain domain) {
		this.domain = domain;
	}
	
	public void setOwner(DDItem item) throws DDException {
		if(owner == null || item == null)
			owner = item;
		else
			throw new DDException("Error: Cannot add attribute " + this.getName() + " to more than one item; previously added to " + this.owner.getName());
	}
	
	public DDItem getOwner() {
		return owner;
	}
}
