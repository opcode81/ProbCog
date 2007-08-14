package edu.tum.cs.srldb;

import java.io.PrintStream;
import java.util.*;
import java.util.Map.Entry;

import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.DDItem;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DataDictionary;
import edu.tum.cs.srldb.datadict.domain.Domain;
import edu.tum.cs.srldb.datadict.domain.OrderedStringDomain;
import edu.tum.cs.tools.SimpleClusterer;

public class Database implements Cloneable {
	
	protected HashSet<Link> links;
	protected HashSet<Object> objects;	
	protected DataDictionary datadict;
	
	/**
	 * creates a relational database
	 * @param dd the datadictionary that this database must conform to
	 */
	public Database(DataDictionary dd) {
		links = new HashSet<Link>();
		objects = new HashSet<Object>();
		datadict = dd;
	}

	/**
	 * performs K-Means clustering on an attribute that is defined for objects in the vector objects; The number of clusters is determined by the number of names in clusterNames
	 * @param objects a vector of objects, some of which (but not necessarily all) must have the attribute 
	 * @param attribute the attribute whose values are to be clustered
	 * @param clusterNames an array of cluster names; it is assumed that that the array is in ascending order of centroid mean; i.e. clusterNames[0] is used as the name for the cluster with the smallest centroid value 
	 * @throws DDException if problems with data dictionary conformity are discovered 
	 * @throws Exception if there are no instances of the attribute, i.e. the attribute is undefines for all objects 
	 */
	public static void clusterAttribute(Collection<Object> objects, DDAttribute attribute, String[] clusterNames) throws DDException, Exception {
		String attrName = attribute.getName();
		System.out.println("  " + attrName);
		// create clusterer and collect instances
		SimpleClusterer clusterer = new SimpleClusterer();
		int instances = 0;
		for(Object obj : objects) {
			String value = obj.getAttributeValue(attrName);
			if(value == null)
				continue;
			clusterer.addInstance(Double.parseDouble(value));
			instances++;
		}
		// check number of instances
		if(instances < clusterNames.length) {
			System.err.println("Warning: attribute " + attrName + " was discarded because there are too few instances for clustering");
			attribute.discard();
		}
		if(instances == 0) 
			throw new Exception("no instances could be clustered for attribute " + attrName);
		// build clusterer
		clusterer.buildClusterer(clusterNames.length);
		// classify
		int[] centroidIndices = clusterer.getSortedCentroidIndices();
		for(Object obj : objects) {
			String value = obj.attribs.get(attribute.getName()); 
			if(value != null) {
				int i = clusterer.classify(Double.parseDouble(value));
				//obj.attribs.put("_" + attribute, obj.attribs.get(attribute));
				obj.attribs.put(attrName, clusterNames[centroidIndices[i]]);
			}
		}
	}
	
	public void outputMLNDatabase(PrintStream out) throws Exception {
		out.println("// *** mln database ***\n");
		// links
		out.println("// links");
		for(Link link : links)
			link.MLNprintFacts(out);
		// objects
		Counters cnt = new Counters();
		for(Object obj : objects) {			
			out.println("// " + obj.objType() + " #" + cnt.inc(obj.objType()));
			obj.MLNprintFacts(out);
		}
	}
	
	public void outputBLOGDatabase(PrintStream out) {
		for(Object obj : objects) {
			for(Entry<String, String> entry : obj.getAttributes().entrySet()) {
				out.printf("%s(%s) = %s;\n", entry.getKey(), obj.getConstantName(), this.upperCaseString(entry.getValue())); 
			}
		}
		for(Link link : links) {
			out.printf("%s = True;\n", link.getLogicalAtom());
		}
	}
	
	/**
	 * outputs the basic MLN for this database, which contains domain definitions, predicate declarations and rules of mutual exclusion   
	 * @param out the stream to write to
	 */
	public void outputBasicMLN(PrintStream out) {
		out.println("// Markov Logic Network\n\n");
		IdentifierNamer idNamer = new IdentifierNamer(datadict);
		// domains
		out.println("// ***************\n// domains\n// ***************\n");
		HashSet<String> printedDomains = new HashSet<String>(); // the names of domains that have already been printed
		// - check all attributes for finite domains
		for(DDAttribute attrib : datadict.getAttributes()) {
			if(attrib.isDiscarded())
				continue;
			Domain domain = attrib.getDomain();
			if(domain == null || attrib.isBoolean() || !domain.isFinite()) // boolean domains aren't handled because a boolean attribute value is not specified as a constant but rather using negation of the entire predicate
				continue;
			// we have a finite domain -> output this domain if it hasn't already been printed
			String name = domain.getName();
			if(!printedDomains.contains(name)) {
				// check if the domain is empty
				String[] values = domain.getValues();
				if(values.length == 0) {
					System.err.println("Warning: domain " + domain.getName() + " is empty and was discarded");
					continue;
				}
				// print the domain name
				out.print(idNamer.getLongIdentifier("domain", domain.getName()) + " = {");
				// print the values (must start with upper-case letter)				
				for(int i = 0; i < values.length; i++) {
					if(i > 0)
						out.print(", ");
					out.print(stdAttribStringValue(values[i]));				
				}
				out.println("}");
				printedDomains.add(name);
			}			
		}
		// predicate declarations
		out.println("\n\n// *************************\n// predicate declarations\n// *************************\n");
		for(DDObject obj : datadict.getObjects()) {
			obj.MLNprintPredicateDeclarations(idNamer, out);			
		}
		out.println("// Relations");
		for(DDRelation rel : datadict.getRelations()) {
			rel.MLNprintPredicateDeclarations(idNamer, out);
		}	
		// rules
		out.println("\n\n// ******************\n// rules\n// ******************\n");
		for(DDObject obj : datadict.getObjects()) {
			obj.MLNprintRules(idNamer, out);
		}
		out.println("\n// mutual exclusiveness and exhaustiveness: relations");
		for(DDRelation rel : datadict.getRelations()) {
			rel.MLNprintRules(idNamer, out);
		}
		// unit clauses
		out.println("\n// unit clauses");
		for(DDObject obj : datadict.getObjects()) {
			obj.MLNprintUnitClauses(idNamer, out);
		}
		for(DDRelation rel : datadict.getRelations()) {
			rel.MLNprintUnitClauses(idNamer, out);
		}
	}	
	
	/**
	 * outputs the data contained in this database to an XML database file for use with Proximity
	 * @param out the stream to write to
	 * @throws Exception
	 */
	public void outputProximityDatabase(java.io.PrintStream out) throws Exception {
		out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		out.println("<!DOCTYPE PROX3DB SYSTEM \"prox3db.dtd\">");
		out.println("<PROX3DB>");
		// objects
		out.println("  <OBJECTS>");
		for(Object obj : objects) {
			out.println("    <OBJECT ID=\"" + obj.id + "\"/>");			
		}
		out.println("  </OBJECTS>");
		// links
		out.println("  <LINKS>");
		for(Link link : links) {
			if(link.getArguments().length != 2)
				System.err.println("Warning: non-binary link/relation found - using first two objects only");
			Object o1 = ((Object)link.getArguments()[0]);
			Object o2 = ((Object)link.getArguments()[1]);
			out.println("    <LINK ID=\"" + link.id + "\" O1-ID=\"" + o1.id + "\" O2-ID=\"" + o2.id + "\"/>");			
		}
		out.println("  </LINKS>");
		// attributes		
		out.println("  <ATTRIBUTES>");
		// - regular attributes
		for(DDAttribute attrib : datadict.getAttributes()) {
			if(attrib.isDiscarded())
				continue;
			String attribName = attrib.getName();
			System.out.println("  attribute " + attribName);
			out.println("    <ATTRIBUTE NAME=\"" + Database.stdAttribName(attribName) + "\" ITEM-TYPE=\"" + (attrib.getOwner().isObject() ? "O" : "L") + "\" DATA-TYPE=\"" + attrib.getType() + "\">");
			Iterator iItem = attrib.getOwner().isObject() ? objects.iterator() : links.iterator();
			while(iItem.hasNext()) {
				Item item = (Item) iItem.next();
				if(item.hasAttribute(attribName)) {
					out.println("      <ATTR-VALUE ITEM-ID=\"" + item.id + "\">");
					out.println("        <COL-VALUE>" + Database.stdAttribStringValue(item.attribs.get(attribName)) + "</COL-VALUE></ATTR-VALUE>");
				}
			}
			out.println("    </ATTRIBUTE>");
		}
		// - special attribute objtype for objects
		out.println("    <ATTRIBUTE NAME=\"objtype\" ITEM-TYPE=\"O\" DATA-TYPE=\"str\">");
		for(Object obj : objects) {
			out.println("      <ATTR-VALUE ITEM-ID=\"" + obj.id + "\">");
			out.println("        <COL-VALUE>" + Database.stdAttribStringValue(obj.objType()) + "</COL-VALUE></ATTR-VALUE>");
		}
		out.println("    </ATTRIBUTE>");
		// - special attribute link_tag for links
		out.println("    <ATTRIBUTE NAME=\"link_tag\" ITEM-TYPE=\"L\" DATA-TYPE=\"str\">");
		for(Link link : links) {
			out.println("      <ATTR-VALUE ITEM-ID=\"" + link.id + "\">");
			out.println("        <COL-VALUE>" + link.getName() + "</COL-VALUE></ATTR-VALUE>");
		}
		out.println("    </ATTRIBUTE>");
		out.println("  </ATTRIBUTES>");
		// done
		out.println("</PROX3DB>");		
	}
	
	/**
	 * returns a string where the first letter is lower case
	 * @param s the string to convert
	 * @return the string s with the first letter converted to lower case
	 */
	public static String lowerCaseString(String s) { 
		char[] c = s.toCharArray();
		c[0] = Character.toLowerCase(c[0]);
		return new String(c);
	}

	/**
	 * returns a string where the first letter is upper case
	 * @param s the string to convert
	 * @return the string s with the first letter converted to upper case
	 */
	public static String upperCaseString(String s) { 
		char[] c = s.toCharArray();
		c[0] = Character.toUpperCase(c[0]);
		return new String(c);
	}
	
	public static String stdAttribName(String attribName) {
		return lowerCaseString(attribName);
	}

	public static String stdPredicateName(String name) {
		return lowerCaseString(name);
	}
	
	public static String stdDomainName(String domainName) {
		return lowerCaseString(domainName);
	}
	
	public static String stdAttribStringValue(String strValue) {
		// make sure the value's first character is upper case
		char[] value = strValue.toCharArray();
		value[0] = Character.toUpperCase(value[0]);
		// if there are spaces in the value, remove them and make the following letters upper case
		int len = 1;
		for(int i = 1; i < value.length;) {
			if(value[i] == ' ') {
				value[len++] = Character.toUpperCase(value[++i]);
				i++;
			}
			else
				value[len++] = value[i++];
		}
		return new String(value, 0, len);
	}
	
	/**
	 * verifies compatibility of the data with the data dictionary
	 *
	 */
	public void check() throws DDException, Exception {
		// check objects
		for(Object obj : objects) {
			datadict.checkObject(obj);
		}		
		// check relations
		for(Link link : this.links) {
			datadict.checkLink(link);
		}
		// check data dictionary consistency (non-overlapping domains, etc.)
		datadict.check();
	}
	
	public void doClustering() throws DDException, Exception {
		for(DDAttribute attrib : this.datadict.getAttributes()) {
			if(attrib.requiresClustering()) {
				Domain domain = attrib.getDomain(); 
				if(domain.getClass() != OrderedStringDomain.class) {
					throw new DDException("don't know how to perform clustering for target domain " + domain);
				}
				clusterAttribute(objects, attrib, ((OrderedStringDomain)domain).getValues()); 
			}
		}			
	}

	public Database clone() {
		try {
			return (Database)super.clone();
		}
		catch (CloneNotSupportedException e) { return null; }		
	}
	
	public Collection<Link> getLinks() {
		return links;
	}
	
	public Collection<Object> getObjects() {
		return objects;	
	}
	
	public void addObject(Object obj) {
		objects.add(obj);
	}

	public void addLink(Link l) {
		links.add(l);
	}
	
	public DataDictionary getDataDictionary() {
		return datadict;
	}
	
	public static class Counters {
		protected HashMap<String, Integer> counters;
		public Counters() {
			counters = new HashMap<String, Integer>();
		}
		public Integer inc(String name) {
			Integer c = counters.get(name);
			if(c == null)
				counters.put(name, c=new Integer(1));
			else
				counters.put(name, c=new Integer(c+1));
			return c;
		}
		public String toString() {
			return counters.toString();
		}
	}
}
