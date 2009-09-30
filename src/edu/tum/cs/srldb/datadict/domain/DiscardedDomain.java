/*
 * Created on Sep 29, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srldb.datadict.domain;

import kdl.prox3.dbmgr.DataTypeEnum;

public class DiscardedDomain extends Domain<String> {

	private DiscardedDomain() {
		super("#unused#");
	}
	
	private static DiscardedDomain singleton = null;
	
	public static DiscardedDomain getInstance() {
		if(singleton != null)
			return singleton;
		return singleton = new DiscardedDomain();
	}

	/**
	 * Comment for <code>serialVersionUID</code>
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public boolean contains(String value) {
		return false;
	}

	@Override
	public boolean containsString(String value) {
		return false;
	}

	@Override
	public DataTypeEnum getType() {
		return null;
	}

	@Override
	public String[] getValues() {
		return new String[0];
	}

	@Override
	public boolean isFinite() {
		return true;
	}

}
