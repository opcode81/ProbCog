package edu.tum.cs.srl;

import java.util.Collection;
import java.util.HashMap;


public interface RelationalModel {
	public void replaceType(String oldType, String newType);
	public HashMap<String, String[]> getGuaranteedDomainElements();
	public Signature getSignature(String functionName);
	public Collection<RelationKey> getRelationKeys(String relation);
}
