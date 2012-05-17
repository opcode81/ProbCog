package probcog.bayesnets.conversion;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.learning.CPTLearner;
import probcog.srldb.Database;
import probcog.srldb.Object;
import probcog.srldb.datadict.DDAttribute;
import probcog.srldb.datadict.DDException;
import probcog.srldb.datadict.DDObject;
import probcog.srldb.datadict.DataDictionary;
import probcog.srldb.datadict.domain.AutomaticDomain;
import probcog.srldb.datadict.domain.BooleanDomain;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.ksu.cis.util.graph.core.Graph;
import edu.ksu.cis.util.graph.core.Vertex;

/**
 * creates an srldb.Database by sampling a Bayesian network
 * @author jain
 */
public class BN2SRLDB {
	protected BeliefNetworkEx bn;
	protected Database db;
	protected HashSet<String> booleanConversion;
	protected HashMap<String,String> undoConversion;
	
	public BN2SRLDB(BeliefNetworkEx bn) {
		this.bn = bn;
		this.db = null;
		this.booleanConversion = null;
	}
	
	public void setBooleanConversion(String attrName) {
		if(booleanConversion == null) {
			 booleanConversion = new HashSet<String>();
		}
		booleanConversion.add(attrName);
	}
	
	public Database getDB(int numSamples) throws DDException, Exception {
		return getDB(numSamples, new Random());
	}
	
	protected boolean isBooleanNode(BeliefNode node) {
		Domain nodeDomain = node.getDomain();
		return nodeDomain.getOrder() == 2 && (nodeDomain.getName(0).equalsIgnoreCase("true") || nodeDomain.getName(0).equalsIgnoreCase("false"));
	}
	
	public Database getDB(int numSamples, Random generator) throws DDException, Exception {
		// create data dictionary with a single object
		DataDictionary datadict = new DataDictionary();
		DDObject ddObj = new DDObject(Object.class.getSimpleName());
		// - add all nodes as attributes
		BeliefNode[] nodes = bn.bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {			
			probcog.srldb.datadict.domain.Domain domain;
			// check if the node is boolean...
			if(isBooleanNode(nodes[i])) {
				domain = BooleanDomain.getInstance();
				ddObj.addAttribute(new DDAttribute(nodes[i].getName(), domain));
			}
			else { // it's not boolean
				String name = nodes[i].getName();
				// add an attribute with an automatic domain 
				domain = new AutomaticDomain("dom" + nodes[i].getName());
				DDAttribute ddAttr = new DDAttribute(nodes[i].getName(), domain);
				ddObj.addAttribute(ddAttr);
				// check if we need to convert this node's values to boolean attributes
				if(booleanConversion != null && booleanConversion.contains(name)) {
					// add a boolean attribute for each outcome in the node's domain
					Domain nodeDomain = nodes[i].getDomain();
					for(int j = 0; j < nodeDomain.getOrder(); j++) {
						ddObj.addAttribute(new DDAttribute(nodeDomain.getName(j), BooleanDomain.getInstance()));
					}
					// mark the original attribute as discarded so it doesn't get included in any outputs
					// (we still keep the attribute added because we may need to use it for CPT learning)
					ddAttr.discard();
				}
			}			
		}
		datadict.addObject(ddObj);			
		
		// create database
		db = new Database(datadict);			
		
		// generate samples and add as objects
		for(int i = 0; i < numSamples; i++) {
			HashMap<String,String> sample = bn.getSample(generator);
			Object obj = new Object(db, "object");
			if(booleanConversion != null) {				
				for(String attrName : booleanConversion) {
					String value = sample.get(attrName);
					sample.put(value, "true");
					//sample.remove(attrName);
				}
			}
			obj.addAttributes(sample);
			obj.commit();
			System.out.println(sample);
		}
		
		db.check();

		return db;
	}
	
	public void relearnBN() throws Exception {
		if(db == null)
			throw new Exception("No sampled data available for learning; call getDB() first!");
		// relearn new Bayesian network CPTs from the samples
		CPTLearner cptLearner = new CPTLearner(bn);
		for(Object obj : db.getObjects()) {
			cptLearner.learn(obj.getAttributes());
		}
		cptLearner.finish();
	}
	
	protected void writeNodeLiteralAllCombs(PrintStream out, BeliefNode n, int varidx) {
		if(isBooleanNode(n))
			out.print("*" + Database.stdPredicateName(n.getName()) + "(o)");
		else
			out.print(Database.stdPredicateName(n.getName()) + "(o,+a" + varidx + ")");
	}
	
	public void writeMLNFormulas(PrintStream out) {		
		Graph g = bn.bn.getGraph();
		Vertex[] vertices = g.getVertices();
		BeliefNode[] nodes = bn.bn.getNodes();		
		for(int i = 0; i < vertices.length; i++) {
			Vertex[] parents = g.getParents(vertices[i]);
			if(parents.length == 0)
				continue;
			int varidx = 0;
			for(int j = 0; j < parents.length; j++) {
				BeliefNode n = nodes[parents[j].loc()];
				if(j > 0)
					out.print(" ^ ");
				writeNodeLiteralAllCombs(out, n, varidx++);
			}
			//if(parents.length > 0)
			out.print(" => ");
			writeNodeLiteralAllCombs(out, nodes[vertices[i].loc()], varidx);
			out.println();
		}
	}
}
