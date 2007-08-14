package edu.tum.cs.srldb.datadict;

import java.util.Set;
import java.util.Map.Entry;

import edu.tum.cs.srldb.ConstantArgument;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.IRelationArgument;
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
		if(ddlink == null) { // this relation is not yet in the data dictionary
			// get a data dictionary definition for each argument
			IRelationArgument[] args = link.getArguments();
			IDDRelationArgument[] ddArgs = new IDDRelationArgument[args.length];
			int i = 0;
			for(IRelationArgument arg : args) {
				// if it's an object, check if we have a definition for that type of object
				if(arg instanceof Object) {
					Object obj = (Object) arg;
					ddArgs[i] = getObject(obj.objType());
				}
				// it's a constant (which is treated as an attribute value)
				else {
					// add an attribute with an AutomaticDomain if necessary
					String domName = "dom" + Database.upperCaseString(link.getName()) + i;
					DDAttribute ddattr = this.attributes.get(domName);
					if(ddattr == null) {				
						DDConstantArgument ddconst = new DDConstantArgument(link.getName() + i, new AutomaticDomain(domName)); 
						ddArgs[i] = ddconst;
						addAttribute(ddconst);
					}
				}
				i++;
			}
			// create the relation definition and add it to the dictionary
			ddlink = new DDRelation(link.getName(), ddArgs);  
			this.addRelation(ddlink);
		}
		// check the link's attributes (and extend their domains if necessary)
		checkItemAttributes(link, ddlink);
		// check for constant arguments (and extend the corresponding domains if necessary)
		int i = 0;
		for(IDDRelationArgument argtype : ddlink.getArguments()) {
			if(argtype instanceof DDConstantArgument) {
				Domain dom = getDomain(argtype.getDomainName());
				// extend the domain if applicable
				IRelationArgument arg = link.getArguments()[i];
				String value = arg.getConstantName();					
				if(!dom.containsString(value)) {
					if(dom instanceof AutomaticDomain) {
						((AutomaticDomain)dom).addValue(value);
					}
					else
						throw new DDException("argument " + (i+1) + " of " + link.toString() + " is invalid; not in domain " + dom.getName());
				}
			}
			i++;
		}
	}

	/**
	 * gets the data dictionary definition for the object with the given name, creates it if it isn't found
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
				//addAttribute(ddattr);
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
		
	public DDAttribute getAttribute(String name) throws DDException {
		DDAttribute ddattr = super.getAttribute(name);
		if(ddattr != null)
			return ddattr;
		AutomaticDomain dom =  new AutomaticDomain("dom" + Database.upperCaseString(name));
		ddattr = new DDAttribute(name, dom);
		this.addAttribute(ddattr);
		return ddattr;
	}
}
