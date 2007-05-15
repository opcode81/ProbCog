package edu.tum.cs.srldb.datadict;

import java.util.Set;
import java.util.Map.Entry;

import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Item;
import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.domain.AutomaticDomain;
import edu.tum.cs.srldb.datadict.domain.Domain;

public class AutomaticDataDictionary extends DataDictionary {

	public AutomaticDataDictionary() {
		super();		
	}
	
	public void checkObject(Object obj) throws DDException {
		DDObject ddobj = getObject(obj.objType());
		checkItemAttributes(obj, ddobj);
	}

	public void checkLink(Link link) throws DDException, Exception {
		DDRelation ddlink = getRelation(link.getName());
		if(ddlink == null) {
			Object[] objs = link.getObjects();
			DDObject[] ddObjs = new DDObject[objs.length];
			for(int i = 0; i < objs.length; i++) {
				ddObjs[i] = getObject(objs[i].objType());
			}
			ddlink = new DDRelation(link.getName(), ddObjs);  
			this.addRelation(ddlink);
		}
		checkItemAttributes(link, ddlink);
	}

	/**
	 * gets the object with the given name, creates it if it isn't found
	 * @param name
	 */
	public DDObject getObject(String name) throws DDException {
		DDObject ddobj = super.getObject(name);
		if(ddobj == null) {			 
			this.addObject(ddobj = new DDObject(name));
		}
		return ddobj;
	}
	
	protected void checkItemAttributes(Item item, DDItem ddItem) throws DDException {
		Set<String> existingAttributes = ddItem.getAttributes().keySet();
		for(Entry<String,String> attr : item.getAttributes().entrySet()) {
			String attribName = attr.getKey();
			String value = attr.getValue();
			if(!existingAttributes.contains(attribName)) {
				AutomaticDomain dom =  new AutomaticDomain("dom" + Database.upperCaseString(attribName));
				dom.addValue(value);
				DDAttribute ddattr = new DDAttribute(attribName, dom);
				ddItem.addAttribute(ddattr);
			}
			if(getAttribute(attribName).isDiscarded())
				continue;
			Domain domain = ddItem.getAttributes().get(attribName).getDomain();			
			if(!domain.containsString(value)) {
				if(domain instanceof AutomaticDomain)
					((AutomaticDomain)domain).addValue(value);
				else
					throw new DDException("invalid value " + value + " for attribute " + attribName + " of item " + ddItem.getName() + "; not in domain " + domain.getName());
			}
		}		
	}
	
	public DDAttribute getAttribute(String name) {
		DDAttribute ddattr = super.getAttribute(name);
		if(ddattr != null)
			return ddattr;
		AutomaticDomain dom =  new AutomaticDomain("dom" + Database.upperCaseString(name));
		ddattr = new DDAttribute(name, dom);
		this.attributes.put(name, ddattr);
		return ddattr;
	}
}
