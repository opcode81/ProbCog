package edu.tum.cs.srldb.datadict;

import java.io.PrintStream;
import java.util.*;
import java.util.Map.Entry;

import edu.tum.cs.srldb.datadict.domain.AutomaticDomain;
import edu.tum.cs.srldb.datadict.domain.Domain;
import edu.tum.cs.srldb.ConstantArgument;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.IRelationArgument;
import edu.tum.cs.srldb.Item;
import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.Object;

public class DataDictionary {
	protected HashMap<String, DDObject> objects;
	protected HashMap<String, DDRelation> relations;
	protected HashMap<String, DDAttribute> attributes;
	/**
	 * !!! this map is not guaranteed to contain all relevant entries (is not certain to be in sync)
	 */
	protected HashMap<String, Domain> domains;
	
	public DataDictionary() {
		objects = new HashMap<String, DDObject>();
		attributes = new HashMap<String, DDAttribute>();
		relations = new HashMap<String, DDRelation>();
		domains = new HashMap<String, Domain>();
	}
	
	public void addObject(DDObject obj) throws DDException {
		objects.put(obj.getName(), obj);
		addAttributes(obj);
	}

	/**
	 * adds the attributes of a given item (object or relation) to the global list of attributes
	 * @param item
	 * @throws DDException
	 */
	protected void addAttributes(DDItem item) throws DDException {
		for(DDAttribute attr : item.getAttributes().values()) {
			addAttribute(attr);
		}
	}	
	
	/**
	 * adds the given attribute to the data dictionary. This function should not be called
	 * unless an attribute is added to an item (object or link) after the item was added to
	 * the data dictionary, as all of an item's attributes are added automatically when 
	 * addObject or addRelation is called.
	 * @param attr
	 * @throws DDException if an attribute with the same name was already in the data dictionary
	 */
	public void addAttribute(DDAttribute attr) throws DDException {
		if(attributes.containsKey(attr.getName())) {				
			throw new DDException("Duplicate attribute " + attr.getName() + "; already defined for item " + attr.getOwner().getName());
		}
		attributes.put(attr.getName(), attr);
		domains.put(attr.getDomain().getName(), attr.getDomain());
	}
	
	public void addRelation(DDRelation rel) throws DDException {
		relations.put(rel.getName(), rel);
		addAttributes(rel);
		//System.out.println("datadict now contains " + relations.size());
	}
	
	public Collection<DDAttribute> getAttributes() {
		return attributes.values();
	}
	
	public DDObject getObject(String name) throws DDException {
		return objects.get(name);
	}

	public DDRelation getRelation(String name) {
		return relations.get(name);
	}
	
	public DDAttribute getAttribute(String name) throws DDException {
		return attributes.get(name);
	}
	
	public Domain getDomain(String name) {
		return domains.get(name);
	}

	public Collection<DDObject> getObjects() {
		return objects.values();
	}
	
	public Collection<DDRelation> getRelations() {
		return relations.values();
	}
	
	protected class DomainData {
		public Domain domain;
		public Vector<DDAttribute> occurrences = new Vector<DDAttribute>();
		public String[] values;
		public boolean wasReplaced = false;
		public DomainData(Domain domain) {
			this.domain = domain;
			values = domain.getValues();
		}
	}
	
	/**
	 * Checks the data dictionary for overlapping domains. Each value must be unique (i.e.
	 * it can only be contained in a single domain). Should a value be contained
	 * in two (or more) domains, all references to the second domain are replaced by the first.
	 * It is assumed that the first domain can be substituted - no merging takes place.
	 * Moreover, it is ensured that attribute names do not coincide with link names - 
	 * as both are eventually used as predicate symbols in the context of MLNs. 
	 */ 
	public void check() throws DDException {
		// get a hash map of all domains used 
		HashMap<String, DomainData> domains = new HashMap<String,DomainData>();
		for(DDAttribute attrib : this.attributes.values()) {
			Domain domain = attrib.getDomain();
			String domName = domain.getName();
			DomainData dd;
			if(!domains.containsKey(domName))
				domains.put(domName, dd = new DomainData(domain));
			else
				dd = domains.get(domName);
			dd.occurrences.add(attrib);
		}
		// check each attribute domain against all the other ones (for collisions of values)
		DomainData[] dd = new DomainData[domains.size()];
		domains.values().toArray(dd);
		domains = null;
		for(int i = 0; i < dd.length; i++) {			
			if(dd[i].wasReplaced) continue;
			// check all of the following domains 
			for(int j = i+1; j < dd.length; j++) {
				if(dd[j].wasReplaced) continue;
				// check whether any of the values in the first domain is in the other domain...
				for(int k = 0; k < dd[i].values.length; k++) {
					// ...and if so, replace all occurrences of the second domain with the first 
					if(dd[j].domain.containsString(dd[i].values[k])) {
						System.err.println("Warning: domain " + dd[i].domain.getName() + " already contains value '" + dd[i].values[k] + "' of domain " + dd[j].domain.getName() + "; replacing all occurrences of " + dd[j].domain.getName() + "!");						
						for(DDAttribute attrib : dd[j].occurrences)
							attrib.setDomain(dd[i].domain);							
						dd[j].wasReplaced = true;
						/*// if it's an automatic domain, merge
						if(dd[i].domain instanceof AutomaticDomain) {
							AutomaticDomain adom = (AutomaticDomain) dd[i].domain;
							for(int l = 0; l < dd[j].values.length; l++)
								adom.addValue(dd[j].values[l]);
						}*/
						break;
					}
				}
			}
		}
		// ensure that attribute names do not coincide with link names
		Set<String> attrNames = this.attributes.keySet();
		Set<String> linkNames = new HashSet<String>(this.relations.keySet());
		linkNames.retainAll(attrNames);
		if(!linkNames.isEmpty()) {
			throw new DDException("Error: Duplicate predicate name(s); the name(s) " + linkNames.toString() + " cannot be used for attributes and links simultaneously!");
		}
	}
	
	/**
	 * outputs an attribute list for each type of object and relation 
	 * @param out the stream to write to
	 */
	public void outputAttributeLists(PrintStream out) {
		for(DDObject obj : getObjects()) {
			obj.outputAttributeList(out);		
		}
		for(DDRelation rel : getRelations()) {
			rel.outputAttributeList(out);
		}
	}
	
	/**
	 * outputs a comma-separated list of all attribute names, regardless of the item to which the attributes belong
	 * @param out the stream to write to
	 */
	public void outputAttributeList(PrintStream out) {
		int i = 0;
		for(DDAttribute attr : this.attributes.values()) {
			if(attr.isDiscarded())
				continue;
			if(i++ > 0)
				out.print(",");
			out.print(Database.stdAttribName(attr.getName()));
		}		
	}
	
	public void checkObject(Object obj) throws DDException {
		DDObject ddobj = getObject(obj.objType());
		if(ddobj == null)
			throw new DDException("unknown object type " + obj.objType() + "; not in data dictionary!");
		checkItemAttributes(obj, ddobj);
	}
	
	public void checkLink(Link link) throws DDException, Exception {
		// check existence of corresponding link type in data dictionary
		DDRelation ddlink = getRelation(link.getName());
		if(ddlink == null)
			throw new DDException("unknown relation " + link.getName() + "; not in data dictionary!");
		// check number of arguments
		if(link.getArguments().length != ddlink.getArguments().length)
			throw new DDException("The link " + link.toString() + " has the wrong number of parameters!");
		// check argument types
		int i = 0;
		for(IRelationArgument arg : link.getArguments()) {
			IDDRelationArgument argtype = ddlink.getArguments()[i];
			if(arg instanceof Object) {
				Object o = (Object)arg;
				if(o.objType() != argtype.getDomainName())
					throw new DDException(String.format("Type mismatch for the %dth argument of %s; should be %s!", i+1, link.toString(), argtype.getDomainName()));
			}
			else {
				if(!(arg instanceof ConstantArgument)) {
					throw new DDException(String.format("Type mismatch for argument %d of %s; expected a constant argument!", i+1, link.toString()));
				}
				DDConstantArgument ddconst = (DDConstantArgument) argtype;
				if(!ddconst.getDomain().contains(arg.getConstantName()))
					throw new DDException(String.format("Domain of argument %d of %s does not contain %s!", i+1, link.toString(), arg.getConstantName()));
			}
			i++;		
		}
		// check attributes
		checkItemAttributes(link, ddlink);
	}
	
	/**
	 * checks compatibility of an item's attributes with the corresponding data dictionary item
	 * @param item 
	 * @param ddItem 
	 * @throws DDException
	 */
	protected void checkItemAttributes(Item item, DDItem ddItem) throws DDException {
		Set<String> allowedAttributes = ddItem.getAttributes().keySet();
		for(Entry<String,String> attr : item.getAttributes().entrySet()) {
			String attribName = attr.getKey();
			if(!allowedAttributes.contains(attribName))
				throw new DDException("undefined attribute " + attribName + " for item type " + ddItem.getName());
			if(getAttribute(attribName).isDiscarded())
				continue;
			Domain domain = ddItem.getAttributes().get(attribName).getDomain();
			String value = attr.getValue();
			if(!domain.containsString(value))
				throw new DDException("invalid value " + value + " for attribute " + attribName + " of item " + ddItem.getName() + "; not in domain " + domain.getName()); 
		}		
	}

}
