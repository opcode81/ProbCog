/*
 * Created on Mar 1, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.srl.bayesnets.inference;

import org.jdom.Element;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;



import kdl.prox.qgraph2.*;
import kdl.prox.db.Container;
import kdl.prox.db.DB;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.inference.SampledDistribution;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.Variable;
import edu.tum.cs.srl.bayesnets.bln.GroundBLN;
import edu.tum.cs.util.datastruct.Pair;

public class QGraphInference extends Sampler {
	protected int port;
	protected Query evidQuery = new Query("Evidence","Evidence");
	
	/*Maps from the node/link names to their respective conditions */
	protected HashMap<String,Element> evidenceVarMap = new HashMap<String,Element>();
	protected HashMap<String,Element> evidenceLinkMap = new HashMap<String,Element>();
	public QGraphInference(GroundBLN gbln) throws Exception {
		super(gbln);
		this.paramHandler.add("port", "setPort");
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	/**
	 * Builds a Test Condition Element, to check if an item's (Vertex or Edge) attribute has a certain value.
	 * @param attr Attribute to be checked
	 * @param value Value it should have
	 * @param type is either "eq"-> == ,"le"-> <=,"ge"-> >= or "ne" -> !=.
	 */	
	protected Element buildTestElement(String attr,String value,String type) {
		Element opEle = new Element("operator");
		opEle.addContent(type);
		Element attrEle = new Element("attribute-name");
		attrEle.addContent(attr);
        Element valueEle = new Element("value");
        valueEle.addContent(value);
        Element testEle = new Element("test");
        testEle.addContent(opEle);
        testEle.addContent(attrEle);
        testEle.addContent(valueEle);
		return testEle;	
	}
	/**
	 * Adds a Testcondition to an existing Element.
	 * @param type is either "eq"-> == ,"le"-> <=,"ge"-> >= or "ne" -> !=.
	 */	
	protected Element addTestElement(String attr,String value,String type,Element oldEl) {
		Element andEl = new Element("and");
		Element newEl = buildTestElement(attr,value,type);
		andEl.addContent(oldEl);
		andEl.addContent(newEl);
		return andEl;
	}
	
	protected boolean isFixedDomainElement(String domName, String element) {
		HashMap<String, String[]> guaranteedDomElems = gbln.getRBN().getGuaranteedDomainElements();
		return false;
	}
	
	protected  Element makeCondition(Element testElement) {
		testElement.detach();
		Element cond = new Element("condition");
		return cond.addContent(testElement);


	}
	
	protected String keyString(String v1,String v2) {
		return (v1+"__linked_to__"+v2);
	}
	protected String[] getKeyString(String key){
		return key.split("__linked_to__");
	}
	
	
	protected void poseQuery(Query q) throws Exception{
		if(!DB.isOpen()) kdl.prox.db.DB.open("localhost:30000");
		Element query = QueryXMLUtil.queryToGraphQueryEle(q);
		QueryGraph2CompOp.queryGraph(query, null, "Result", true);
	}
	
	protected int countMatches(Query q) throws Exception{
		if(!DB.isOpen()) kdl.prox.db.DB.open("localhost:30000");
		Element query = QueryXMLUtil.queryToGraphQueryEle(q);
		QueryGraph2CompOp.queryGraph(query, null, "result_temp", true);
		Container c = DB.getContainer("result_temp");
		Integer count =  c.getSubgraphCount();
		Container root = DB.getRootContainer();
		root.deleteChild("result_temp");
		return count;
	}
	protected String getFunctionObjName(String fname, String[] args){
		StringBuffer name = new StringBuffer(fname);
		name.append("(");
		for(int i=0;i<args.length;i++){
			if(i > 0) name.append(",");
			name.append(args[i]);
		}
		name.append(")_obj");
		return name.toString();
	}
	
	/**Builds a query from two maps, containing the condition elements for nodes and edges, respectively
	 * @param VarMap Conditions for nodes
	 * @param LinkMap Conditions for edges 
	 */ 
	protected Query buildQuery(HashMap<String,Element> VarMap,HashMap<String,Element> LinkMap){
		HashMap<String,QGVertex> vertexMap = new HashMap<String,QGVertex>();
		Query q = new Query("QGraphInference", "QGraphInference");	
		for (Map.Entry<String,Element> e : VarMap.entrySet()){
			QGVertex v = new QGVertex(e.getKey(),makeCondition(e.getValue()),null);
			vertexMap.put(e.getKey(),v);
			q.addVertex(v);
		}
		for (Map.Entry<String,Element> el : LinkMap.entrySet()){
			String v1name = getKeyString(el.getKey())[0];
			String v2name = getKeyString(el.getKey())[1];
			QGEdge e = new QGEdge(el.getKey(),makeCondition(el.getValue()),null,v1name,v2name,"false"); // is it directed ?
			
			ArrayList<String> addList = new ArrayList<String>();
			if(!vertexMap.containsKey(v1name)) addList.add(v1name);
			if(!vertexMap.containsKey(v2name)) addList.add(v2name);
			for(String name : addList){
				QGVertex v = new QGVertex(name,null,null);
				vertexMap.put(name, v);
				q.addVertex(v);
			}
			e.setVertex1(vertexMap.get(v1name));
			e.setVertex2(vertexMap.get(v2name));
			q.addEdge(e);
		}
		
		return q;
	}
	

	
	
	/* Maps an Atom to a corresponding graph.
	 * The graph is stored in a map from the node or link names to the restriction element.
	 * @param varMap Conditions for the nodes.
	 * @param linkMap Conditions for the edges.
	 */
	protected void buildConditionMap(HashMap<String,Element> varMap,HashMap<String,Element> linkMap,String fname, String[] args,String value, Signature sig){

			if(args.length==1 ) {
				if (!sig.isBoolean()) {
				//not(bool) p(A) = v-> This is a vertex cond: A [p=v]			
				if(varMap.containsKey(args[0])){
					Element oldEl = varMap.remove(args[0]);
					Element newEl = addTestElement(fname, value, "eq", oldEl);
					varMap.put(args[0],newEl);
				}
				else{
					Element cond = buildTestElement(fname,value,"eq");
					//cond = addTestElement("objtype", sig.argTypes[0], "eq", cond);
					varMap.put(args[0],cond);
					}
				}
			}
			else if(args.length==2) {
				if (!sig.isBoolean()) {
				//not(bool) p(A,B) = v ->This is an edge cond: Link(A,B) [p=v]
				if(linkMap.containsKey(keyString(args[0],args[1]))){
					Element oldEl = linkMap.remove(keyString(args[0],args[1]));
					Element newEl = addTestElement(fname, value, "eq", oldEl);
					linkMap.put(keyString(args[0],args[1]),newEl);
				}
				else{
					Element lcond = buildTestElement(fname,value,"eq");
					linkMap.put(keyString(args[0],args[1]), lcond);
					//Element v1cond = buildTestElement("objtype", sig.argTypes[0], "eq");
					//Element v2cond = buildTestElement("objtype", sig.argTypes[1], "eq");
					//varMap.put(args[0], v1cond);
					//varMap.put(args[1], v2cond);
					
				}
			}
				else{ 
					//boolean signature
					//boolean p(A,B) = True -> This is an edge cond: Link(A,B) [link_tag = p]
						if(value.equalsIgnoreCase("True")){
							if(linkMap.containsKey(keyString(args[0],args[1]))){
								Element oldEl = linkMap.remove(keyString(args[0],args[1]));
								Element newEl = addTestElement("link_tag",fname, "eq", oldEl);
								linkMap.put(keyString(args[0],args[1]),newEl);
							}
							else{
								Element cond = buildTestElement("link_tag",fname,"eq");
								linkMap.put(keyString(args[0],args[1]), cond);
							}
						}
						else{throw new Error("Cannot assert the non-existence of a relation");}
				}
			}
			else if(args.length>2){
				String obj = getFunctionObjName(fname, args);
				String [] newArgs = new String[2];
				String [] argTypes = new String[2];
				for(int i=0;i< args.length;i++) {
					newArgs[0] = args[i];
					newArgs[1] = obj;
					argTypes[0] = sig.argTypes[i];
					argTypes[1] = obj;
					Signature newSig = new Signature(fname+"_"+i,sig.returnType,argTypes);
					buildConditionMap(varMap, linkMap, fname+"_"+i, newArgs, value, newSig);
				}
			}
			else{ throw new NotImplementedException();}

	}
	
	
	@Override
	protected SampledDistribution _infer() throws Exception {
		
		BeliefNetworkEx bn = this.gbln.getGroundNetwork();
		BeliefNode[] nodes = bn.getNodes();		
		
		Database db = this.gbln.getDatabase();	
		//TODO: flatten DB
		
		// build initial qgraph from evidence
		// translate function signatures into nodes and links with respective conditions(=constraints)
		for(Variable var : db.getEntries()) {
			System.out.println("evidence: " + var);
			Signature sig = gbln.getRBN().getSignature(var.functionName);
			buildConditionMap(evidenceVarMap, evidenceLinkMap, var.functionName, var.params, var.value, sig);
		}

		Query q = buildQuery(evidenceVarMap, evidenceLinkMap);
		File f = new File("/Users/thakluh/Documents/Studium/HiwiCotesys/proximity/queries/test_query.xml");
		Element query = QueryXMLUtil.queryToGraphQueryEle(q);
		QueryXMLUtil.graphQueryEleToFile(query, f);
		//poseQuery(q);
		Double matches = (double) countMatches(q);
		System.out.println("NUMBER OF MATCHES IS: "+matches);
		
		
		SampledDistribution dist = new SampledDistribution(bn);
		
		dist.Z = (double) countMatches(q);
		
		for(Integer idxVar : this.queryVars) {
			BeliefNode var = nodes[idxVar];
			System.out.println("Var : "+var.getName());
			Pair<String, String[]> p = Signature.parseVarName(var.getName());
			Signature sig = gbln.getRBN().getSignature(p.first);
			// sig.argTypes[0]; type of first argument of the function
			
			Discrete domain = (Discrete)var.getDomain();
			
			double sum = 0;
			if(sig.isBoolean()){
				HashMap<String,Element> linkMap = new HashMap<String,Element>(evidenceLinkMap);
				HashMap<String,Element> varMap = new HashMap<String,Element>(evidenceVarMap);
				buildConditionMap(varMap, linkMap, sig.functionName, p.second, "True", sig);
				Query qv = buildQuery(varMap, linkMap);
				File ff = new File("/Users/thakluh/Documents/Studium/HiwiCotesys/proximity/queries/test_query_BOOL.xml");
				Element qquery = QueryXMLUtil.queryToGraphQueryEle(qv);
				QueryXMLUtil.graphQueryEleToFile(qquery, ff);
				double value = (double) countMatches(qv);
				dist.values[idxVar][0] = value;
				dist.values[idxVar][1] = dist.Z - value;
				
			}
			else{
				
				for(int i = 0; i < domain.getOrder(); i++) {
					HashMap<String,Element> linkMap = new HashMap<String,Element>(evidenceLinkMap);
					HashMap<String,Element> varMap = new HashMap<String,Element>(evidenceVarMap);
					
					buildConditionMap(varMap, linkMap, sig.functionName,p.second, domain.getName(i), sig);
					Query qv = buildQuery(varMap, linkMap);
					
					
					System.out.println("Dom El: "+domain.getName(i));
					double value = (double) countMatches(qv); // number of qgraph matches
					dist.values[idxVar][i] = value;
					//dist.values[idxVar][i] = 0.5;
				}
			}
		}

		
		return dist;
	}

}
