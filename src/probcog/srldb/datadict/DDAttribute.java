package probcog.srldb.datadict;

import java.io.Serializable;

import probcog.clustering.BasicClusterer;
import probcog.clustering.ClusterNamer;
import probcog.clustering.EMClusterer;
import probcog.clustering.SimpleClusterer;
import probcog.srldb.Database;
import probcog.srldb.Item;
import probcog.srldb.Database.AttributeClustering;
import probcog.srldb.datadict.domain.AutomaticDomain;
import probcog.srldb.datadict.domain.BooleanDomain;
import probcog.srldb.datadict.domain.DiscardedDomain;
import probcog.srldb.datadict.domain.Domain;
import probcog.srldb.datadict.domain.OrderedStringDomain;

import weka.clusterers.Clusterer;

import kdl.prox3.dbmgr.DataTypeEnum;

public class DDAttribute implements Cloneable, Serializable {
	private static final long serialVersionUID = 1L;
	protected String name;
	protected Domain<?> domain;
	/**
	 * whether this attribute is scheduled for clustering
	 */
	protected ClusteringTask clusteringTask;
	/**
	 * whether this attribute is actually discarded/unused
	 */
	protected boolean discarded;
	/**
	 * the item that owns this attribute (usually a DDObject)
	 */
	protected DDItem owner;
	
	protected class ClusteringTask {
		public Integer numClusters = null;
		public ClusterNamer<Clusterer> namer = null;
		
		public String toString() {
			return String.format("%s", numClusters == null ? "auto" : numClusters.toString());
		}
		
		public AttributeClustering perform(Iterable<Item> items) throws Exception {
			DDAttribute attrib = DDAttribute.this;
			Domain<?> domain = getDomain();
			AttributeClustering ac;
			// if the domain was specified by a user as an ordered list of strings, use K-Means
			// with the corresponding number of clusters, naming the clusters using the strings 
			// (using the strings in ascending order of cluster centroid)
			if(domain instanceof OrderedStringDomain) {
				SimpleClusterer c = new SimpleClusterer();
				((SimpleClusterer)c).setNumClusters(domain.getValues().length);
				ac = Database.clusterAttribute(attrib, items, c, new ClusterNamer.Fixed(((OrderedStringDomain)domain).getValues()));
			}
			// if the domain was generated automatically (no user input), either use EM 
			// clustering to determine a suitable number of clusters or, if the number is given,
			// K-means, and use default names (attribute name followed by index)
			else if(domain instanceof AutomaticDomain) {
				BasicClusterer<?> c;
				if(numClusters == null) {
					c = new EMClusterer();
					System.out.println("  applying EM clustering to " + attrib);
				}
				else {
					c = new SimpleClusterer();
					((SimpleClusterer)c).setNumClusters(numClusters);
					System.out.printf("  applying %d-means clustering to " + attrib, numClusters);
				}
				ClusterNamer<Clusterer> namer = this.namer;
				if(namer == null)
					namer = new ClusterNamer.SimplePrefix(attrib.getName());
				ac = Database.clusterAttribute(attrib, items, c, namer);					
			}
			else
				throw new DDException("Don't know how to perform clustering for target domain " + " (" + domain.getClass() + ")");
			return ac;
		}
	}	
	
	
	protected DDAttribute(String name) {
		this.name = name;
		this.domain = null;
		clusteringTask = null;
		this.discarded = false;
		this.owner = null;
	}
	
	public DDAttribute(String name, Domain<?> domain) {
		this(name);
		this.domain = domain;
	}
	
	public DDAttribute(String name, Domain<?> domain, boolean doClustering) {
		this(name, domain);
		setClustering(doClustering);
	}
	
	/**
	 * @param doClustering whether this attribute should be scheduled for clustering, 
	 * replacing all its values in instances with the respective clustering result
	 */
	public void setClustering(boolean doClustering, ClusterNamer<Clusterer> namer) {
		if(doClustering) {
			clusteringTask = new ClusteringTask();
			clusteringTask.namer = namer;
		}
		else
			clusteringTask = null;		
	}
	
	public void setClustering(boolean doClustering) {
		setClustering(doClustering, null);
	}
	
	public void setClustering(Integer numClusters, ClusterNamer<Clusterer> namer) {
		clusteringTask = new ClusteringTask();
		clusteringTask.numClusters = numClusters;
		clusteringTask.namer = namer;
	}
	
	public void setClustering(Integer numClusters) {
		setClustering(numClusters, null);
	}

	public AttributeClustering doClustering(Iterable<Item> items) throws Exception {
		return clusteringTask.perform(items);
	}
	
	public String getName() {
		return name;
	}
	
	public DataTypeEnum getType() {
		return domain.getType();
	}
	
	public boolean requiresClustering() {
		return clusteringTask != null;
	}
	
	public Domain<?> getDomain() {
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
	 */
	public void discard() {
		discarded = true;
		domain = DiscardedDomain.getInstance(); // avoid wasting space on domain data
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
	
	public void setDomain(Domain<?> domain) {
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
	
	public String toString() {
		return String.format("DDAttribute:%s[domain=%s/size=%d, discarded=%s, clustering=%s]", name, domain.getClass().getSimpleName(), domain.getValues().length, Boolean.toString(discarded), clusteringTask == null ? "none" : clusteringTask.toString());
	}
}
