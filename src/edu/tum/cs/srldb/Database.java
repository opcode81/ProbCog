package edu.tum.cs.srldb;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import edu.tum.cs.clustering.BasicClusterer;
import edu.tum.cs.clustering.ClusterNamer;
import edu.tum.cs.clustering.SimpleClusterer;
import edu.tum.cs.srldb.datadict.AutomaticDataDictionary;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DataDictionary;
import edu.tum.cs.srldb.datadict.domain.AutomaticDomain;
import edu.tum.cs.srldb.datadict.domain.Domain;
import edu.tum.cs.srldb.datadict.domain.OrderedStringDomain;

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
	 * creates a relational database with an automatically generated data dictionary
	 */
	public Database() {
		this(new AutomaticDataDictionary());
	}

	/**
	 * performs clustering on an attribute that is defined for objects in the vector objects; The number of clusters is determined by the number of names in clusterNames
	 * @param attribute the attribute whose values are to be clustered
	 * @param objects a vector of objects, some of which (but not necessarily all) must have the attribute
	 * @param clusterer a clusterer used to perform the clustering 
	 * @param clusterNamer a namer for the resulting clusters, which is used to redefine the attribute's domain and to update all the attribute values  
	 * @throws DDException if problems with data dictionary conformity are discovered 
	 * @throws Exception if there are no instances of the attribute, i.e. the attribute is undefines for all objects 
	 */
	public static AttributeClustering clusterAttribute(DDAttribute attribute, Collection<Object> objects, BasicClusterer<? extends weka.clusterers.Clusterer> clusterer, ClusterNamer<weka.clusterers.Clusterer> clusterNamer) throws DDException, Exception {
		String attrName = attribute.getName();		
		// create clusterer and collect instances
		int instances = 0;
		for(Object obj : objects) {
			String value = obj.getAttributeValue(attrName);
			if(value == null)
				continue;
			clusterer.addInstance(Double.parseDouble(value));
			instances++;
		}
		// build clusterer
		clusterer.buildClusterer();
		// get cluster names
		String[] clusterNames = clusterNamer.getNames(clusterer.getWekaClusterer());
		// check number of instances
		if(instances < clusterNames.length) {
			System.err.println("Warning: attribute " + attrName + " was discarded because there are too few instances for clustering");
			attribute.discard();
			return null;
		}
		if(instances == 0) 
			throw new Exception("The domain is empty; No instances could be clustered for attribute " + attrName);
		// apply cluster assignment to attribute values
		AttributeClustering ac = new AttributeClustering();
		ac.clusterer = clusterer;
		ac.newDomain = new OrderedStringDomain(attribute.getDomain().getName(), clusterNames);
		applyClustering(attribute, objects, ac);
		return ac;
	}
	
	public static void applyClustering(DDAttribute attribute, Collection<Object> objects, AttributeClustering ac) throws NumberFormatException, Exception {
		// apply cluster assignment to attribute values
		String attrName = attribute.getName();
		for(Object obj : objects) {
			String value = obj.attribs.get(attrName); 
			if(value != null) {
				int i = ac.clusterer.classify(Double.parseDouble(value));
				obj.attribs.put(attrName, ac.newDomain.getValues()[i]);
			}
		}
		// redefine attribute domain
		attribute.setDomain(ac.newDomain);
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
			// attributes
			String linkObjects = link.getLinkParams();
			for(Entry<String, String> entry : link.getAttributes().entrySet()) {
				out.printf("%s(%s) = %s;\n", entry.getKey(), linkObjects, this.upperCaseString(entry.getValue())); 
			}
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
				String domIdentifier = idNamer.getLongIdentifier("domain", domain.getName());
				out.print(domIdentifier + " = {");
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
		/*
		for(DDObject obj : datadict.getObjects()) {
			obj.MLNprintRules(idNamer, out);
		}		
		out.println("\n// mutual exclusiveness and exhaustiveness: relations");
		for(DDRelation rel : datadict.getRelations()) {
			rel.MLNprintRules(idNamer, out);
		}*/
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
	
	public static class AttributeClustering {
		public BasicClusterer<?> clusterer;
		public Domain<?> newDomain;
	}
	
	/**
	 * performs clustering on the attributes for which it was specified in the data dictionary
	 * @throws DDException
	 * @throws Exception
	 */	
	public HashMap<DDAttribute, AttributeClustering> doClustering(HashMap<DDAttribute, AttributeClustering> clusterers) throws DDException, Exception {
		System.out.println("clustering...");
		if(clusterers == null)
			clusterers = new HashMap<DDAttribute, AttributeClustering>();
		for(DDAttribute attrib : this.datadict.getAttributes()) {			
			if(attrib.requiresClustering()) {				
				System.out.println("  " + attrib.getName());
				AttributeClustering ac;
				ac = clusterers.get(attrib);
				if(ac != null) {
					applyClustering(attrib, objects, ac);
					continue;
				}
				Domain<?> domain = attrib.getDomain();
				// if the domain was specified by a user as an ordered list of strings, use K-Means
				// with the corresponding number of clusters, naming the clusters using the strings 
				// (using the strings in ascending order of cluster centroid)
				if(domain instanceof OrderedStringDomain) {
					SimpleClusterer c = new SimpleClusterer();
					((SimpleClusterer)c).setNumClusters(domain.getValues().length);
					ac = clusterAttribute(attrib, objects, c, new ClusterNamer.Fixed(((OrderedStringDomain)domain).getValues()));
				}
				// if the domain was generated automatically (no user input), use hierarchical 
				// clustering to determine a suitable number of clusters and use default
				// names (attribute name followed by index)
				else if(domain instanceof AutomaticDomain) {
					SimpleClusterer c = new SimpleClusterer();					
					((SimpleClusterer)c).setNumClusters(4);
					//HierarchicalClusterer c = new HierarchicalClusterer();
					ac = clusterAttribute(attrib, objects, c, new ClusterNamer.SimplePrefix(attrib.getName()));
				}
				else
					throw new DDException("Don't know how to perform clustering for target domain " + " (" + domain.getClass() + ")");
				clusterers.put(attrib, ac);
			}
		}			
		return clusterers;
	}
	
	public HashMap<DDAttribute, AttributeClustering> doClustering() throws DDException, Exception {
		return doClustering(null);
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
	
	public void addObject(Object obj) throws DDException {		
		if(objects.add(obj))
			this.datadict.onCommitObject(obj);
	}

	public void addLink(Link l) throws DDException {
		if(links.add(l))
			this.datadict.onCommitLink(l);
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
	
	/**
	 * empties this database
	 */
	public void clear() {
		this.objects.clear();
		this.links.clear();
	}
}
