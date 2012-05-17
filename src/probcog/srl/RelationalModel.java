package probcog.srl;

import java.util.Collection;
import java.util.HashMap;

import probcog.srl.taxonomy.Taxonomy;



public interface RelationalModel {
	public void replaceType(String oldType, String newType);
	public HashMap<String, ? extends Collection<String>> getGuaranteedDomainElements();
	public void addGuaranteedDomainElement(String domain, String element);
	public Signature getSignature(String functionName);
	public Collection<Signature> getSignatures();
	public Collection<RelationKey> getRelationKeys(String relation);
	public Taxonomy getTaxonomy();
	public Collection<String> getPrologRules();
}
