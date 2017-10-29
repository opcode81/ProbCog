/*******************************************************************************
 * Copyright (C) 2007-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
// based on the class Converter_xmlbif from KSU's BNJ (Bayesian Network Tools in Java)

package probcog.bayesnets.core.io;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.streams.*;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/**
 * Converter (Exporter/Importer) for a PMML-based format (PMML 3.0 with custom extensions)
 * This class need not be used directly. BeliefNetworkEx implements the loading and storing
 * of PMML files using this class. Converters of this kind form the basis for BNJ export/import 
 * plugins &ndash; the PMML plugin is made available, too. 
 * (This class is largely based upon Converter_xmlbif, which is part of BNJ)
 * @author Dominik Jain
 */
public class Converter_pmml
    implements OmniFormatV1, Exporter, Importer
{
    protected OmniFormatV1 _Writer;
    protected int bn_cnt;
    private int bnode_cnt;
    
    // saving    
	protected HashMap<Integer, NodeData> nodeData;
	protected Writer w;
    public int netDepth;
	protected int curNodeIdx;
	//protected HashMap adjList;
	
	// loading
	protected HashMap<Integer, String> nodeNames;
	//protected HashMap<String, Integer> nodeIndices;	
	protected HashMap<Integer, Integer> nodeIndices; // maps node IDs to node indices
    protected NodeData curNode;
	HashMap<Integer, Node> cptTags;
	
	// omiformat
	protected StringBuffer cpf;
	protected int cpfNodeID;
	
	
    public Converter_pmml()
    {
        w = null;
        curNodeIdx = 0;
    }

    public OmniFormatV1 getStream1()
    {
        return this;
    }

    // ************************************************
	// ***************** LOADING **********************
    // ************************************************
	
    public void load(InputStream stream, OmniFormatV1 writer)
    {
        _Writer = writer; // this is usually an instance of OmniFormatV1_Reader
        _Writer.Start();
        bn_cnt = 0;
        bnode_cnt = 0;
        nodeIndices = new HashMap<Integer, Integer>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        //factory.setValidating(true);
        factory.setNamespaceAware(true);
        org.w3c.dom.Document doc;
        try
        {
            DocumentBuilder parser = factory.newDocumentBuilder();
            doc = parser.parse(stream);
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
        visitDocument(doc);
        System.gc();
    }

    public void visitDocument(Node parent)
    {
        NodeList l = parent.getChildNodes();
        if(l == null)
            throw new RuntimeException("Unexpected end of document!");
        int max = l.getLength();
        for(int i = 0; i < max; i++) {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("PMML"))
                {
					// process PMML attributes
                    NamedNodeMap attrs = node.getAttributes();
                    if(attrs != null) {
                        int amax = attrs.getLength();
                        for(int j = 0; j < amax; j++) {
                            Node attr = attrs.item(j);
                            String aname = attr.getNodeName().toUpperCase();
                            if(aname.equals("version"))
                                try {
                                    if(!aname.equals("3.0"))
                                        throw new RuntimeException("PMML version " + aname + " is not supported");
                                }
                                catch(Exception e) { }
                            //else
                              //  System.out.println("property:" + aname + " not handled");
                        }
                    }
					
					// read child nodes
					cptTags = new HashMap<Integer, Node>();					
                    visitDocument(node);
					
					// process cpt definitions for nodes that were gathered along the way
                    //System.out.println("processing CPTs");
					for(Entry<Integer,Node> e : cptTags.entrySet()) {
						visitDefinition(e.getValue(), e.getKey());
					}
					cptTags = null;			
                } 
				else if(name.equals("DataDictionary")) {
                    _Writer.CreateBeliefNetwork(bn_cnt);
                    visitDataDict(node);
                    bn_cnt++;
                }
				/*else {
                    throw new RuntimeException("Unhandled element " + name);
                }*/
            }
        }

    }

    public void visitDataDict(Node parent)
    {
		// process children (DataFields)		
        NodeList l = parent.getChildNodes();
        if(l == null)
            throw new RuntimeException("Unexpected end of document!");
        int max = l.getLength();
        for(int i = 0; i < max; i++) {        
            Node node = l.item(i);
            switch(node.getNodeType()) {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("DataField")) {                    
                    visitDataField(node);                    
                    bnode_cnt++;
                }
			}
        }
    }

    /**
     * a <DataField> contains all the data on one node in the BN
     * @param parent
     */
    protected void visitDataField(Node parent)
    {        
		// read attributes
        NamedNodeMap attrs = parent.getAttributes();
		String nodeName = null;
		Integer nodeID = null; 
        int max;        
        if(attrs != null) {
            max = attrs.getLength();
            for(int i = 0; i < max; i++) {
                Node attr = attrs.item(i);
                String attrName = attr.getNodeName();
                String value = attr.getNodeValue();
                if(attrName.equals("name")) {
					nodeName = value;
                    //nodeIndices.put(nodeName, new Integer(bnode_cnt));                    					
                }
                if(attrName.equals("id")) {
					nodeID = Integer.parseInt(value);                   					
                }
				/*else {
                    System.out.println("Unhandled variable property attribute " + name);
                }*/
            }
        }
		
		if(nodeName == null || nodeID == null)
			throw new RuntimeException("Missing DataField attribute 'name' or 'id'!");
		
		nodeIndices.put(nodeID, new Integer(bnode_cnt));
		
		_Writer.BeginBeliefNode(bnode_cnt);
		_Writer.SetBeliefNodeName(nodeName);	
		
		// process child tags
        
		NodeList l = parent.getChildNodes();
        max = l.getLength();		
        for(int i = 0; i < max; i++) {
            Node node = l.item(i);
            switch(node.getNodeType()) {
            case 1: // '\001'
                String name = node.getNodeName();
				if(name.equals("Value")) {
					attrs = node.getAttributes();
					for(int j = attrs.getLength()-1; j >= 0; j--) {
						Node attr = attrs.item(j);
						if(attr.getNodeName().equals("value"))
							_Writer.BeliefNodeOutcome(attr.getNodeValue());							
					}						
				}
				else if(name.equals("Extension")) {	
					NodeList l_ext = node.getChildNodes();
					for(int j = 0; j < l_ext.getLength(); j++) {
						Node n = l_ext.item(j);						
						if(n.getNodeName().equals("X-NodeType")) {
							_Writer.SetType(getElementValue(n));
						}
						else if(n.getNodeName().equals("X-Position")) {
							attrs = n.getAttributes();
							int xPos = 0, yPos = 0;
							for(int k = attrs.getLength()-1; k >= 0; k--) {
								Node attr = attrs.item(k);
								if(attr.getNodeName().equals("x"))
									xPos = Integer.parseInt(attr.getNodeValue());
								else if(attr.getNodeName().equals("y"))
									yPos = Integer.parseInt(attr.getNodeValue());
							}
							_Writer.SetBeliefNodePosition(xPos, yPos);
						}
						else if(n.getNodeName().equals("X-Definition"))
							cptTags.put(nodeID, n); // remember the X-Definition node for later				
					}
				}
                break;
            }
        }
        
        _Writer.EndBeliefNode();
    }

    protected void visitDefinition(Node definition, int nodeID)
    {
        NodeList l = definition.getChildNodes();
        if(l == null)
            return;
        LinkedList<Integer> parents = new LinkedList<Integer>();
        int curNode = nodeIndices.get(nodeID); //nodeIndices.get(nodeName).intValue();
        String CPTString = "";
        int max = l.getLength();
        for(int i = 0; i < max; i++)
        {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("X-Given")) {                	
                    parents.add(nodeIndices.get(Integer.parseInt(getElementValue(node))));
                }
                else
                	if(name.equals("X-Table"))
                		CPTString = getElementValue(node);
            }
        }

        if(curNode >= 0)
        {
            for(Integer p : parents) {            	
            	_Writer.Connect(p, curNode);
            }

            _Writer.BeginCPF(curNode);
            StringTokenizer tok = new StringTokenizer(CPTString);
            int maxz = tok.countTokens();
            for(int c = 0; c < maxz; c++)
            {
                String SSS = tok.nextToken();
                _Writer.ForwardFlat_CPFWriteValue(SSS);
            }

            _Writer.EndCPF();
        }
    }

    protected String getElementValue(Node parent)
    {
        NodeList l = parent.getChildNodes();
        if(l == null)
            return null;
        StringBuffer buf = new StringBuffer();
        int max = l.getLength();
        for(int i = 0; i < max; i++)
        {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 3: // '\003'
                buf.append(node.getNodeValue());
                break;

            default:
                System.out.println("Unhandled node " + node.getNodeName());
                break;

            case 1: // '\001'
            case 8: // '\b'
                break;
            }
        }

        return buf.toString().trim();
    }

    // ************************************************
	// ***************** SAVING ***********************
    // ************************************************
	
	protected class NodeData {
		public String cpfData, subElements, nodeType, opType, name, domainClassName;
		int index;
		int xPos, yPos;
		Vector<Integer> parents;
		public NodeData() {
			cpfData = new String();
			subElements = new String();
			parents = new Vector<Integer>();
		}
	}

	public void save(BeliefNetwork bn, OutputStream os) {
        w = new OutputStreamWriter(os);
        OmniFormatV1_Writer.Write(bn, this);
    }

    public void fwrite(String x)
    {
        try
        {
            w.write(x);
            w.flush();
        }
        catch(Exception e)
        {
            System.out.println("unable to write?");
        }
    }

    public void Start()
    {
        netDepth = 0;
        nodeNames = new HashMap<Integer, String>();
        //adjList = new HashMap();
        fwrite("<?xml version=\"1.0\" encoding=\"US-ASCII\"?>\n");
        fwrite("<!-- Bayesian network in a PMML-based format -->\n");
        fwrite("<PMML version=\"3.0\" xmlns=\"http://www.dmg.org/PMML-3_0\">\n");
		fwrite("\t<Header copyright=\"Technische Universitaet Muenchen\" />\n");
    }

    public void CreateBeliefNetwork(int idx)
    {
        if(netDepth > 0)
        {
            netDepth = 0;
			fwrite("\t</DataDictionary>\n");
        }
		nodeData = new HashMap<Integer,NodeData>();
		fwrite("\t<DataDictionary>\n");
        netDepth = 1;
    }

    public void SetBeliefNetworkName(int idx, String name)
    {
        //fwrite("<NAME>" + name + "</NAME>\n");
    }

    public void BeginBeliefNode(int idx) {
		curNode = new NodeData();
		curNode.index = idx;
        curNodeIdx = idx;
        //adjList.put(new Integer(curNodeIdx), new ArrayList());
    }

    public void SetType(String type)
    {
		curNode.nodeType = type;
		if(type.equals("utility"))
			curNode.opType = "continuous";
		else
			curNode.opType = "categorical";
    }

    public void SetBeliefNodePosition(int x, int y) {
        curNode.xPos = x;
		curNode.yPos = y;
    }
    
	public void SetBeliefNodeDomainClass(String domainClassName) {
		curNode.domainClassName = domainClassName;
	}

    public void BeliefNodeOutcome(String outcome) {
        curNode.subElements += "\t\t\t<Value value=\"" + outcome.replaceAll("<", "&lt;").replaceAll(">", "&gt;") + "\" />\n";
    }

    public void SetBeliefNodeName(String name) {
    	//System.out.println(name);
		curNode.name = name;
        nodeNames.put(new Integer(curNodeIdx), name);
    }

    public void MakeContinuous(String s) {
    }

    public void EndBeliefNode() {
		nodeData.put(curNode.index, curNode);
    }

    public void Connect(int par_idx, int chi_idx) {
    	nodeData.get(chi_idx).parents.add(par_idx);
    }

    public void BeginCPF(int idx) {
    	//System.out.println("CPF: " + nodeData.get(idx).name);
        cpfNodeID = idx;
        cpf = new StringBuffer("\t\t\t\t<X-Definition>\n");
        String gname;
        for(Integer given : nodeData.get(idx).parents)
        {
            //Integer given = (Integer)it.next();
            gname = (String)nodeNames.get(given);
            cpf.append("\t\t\t\t\t<X-Given>" + given + "</X-Given> <!-- " + gname + " -->\n");
        }
        cpf.append("\t\t\t\t\t<X-Table>");
    }

    public void ForwardFlat_CPFWriteValue(String x)
    {
        cpf.append(x + " ");
    }

    public void EndCPF()
    {
        cpf.append("</X-Table>\n");
		cpf.append("\t\t\t\t</X-Definition>\n");		
		NodeData d = (NodeData)nodeData.get(cpfNodeID);
		d.cpfData = cpf.toString();
		//System.out.println("done.");
    }

    public int GetCPFSize()
    {
        return 0;
    }

    public void Finish()
    {
        if(netDepth > 0)
        {
			// output all node data
			Iterator<NodeData> i = nodeData.values().iterator();
			while(i.hasNext()) {
				NodeData nd = i.next();
		        fwrite("\t\t<DataField name=\"" + nd.name + "\" optype=\"" + nd.opType + "\" id=\"" + nd.index + "\">\n");
				fwrite("\t\t\t<Extension>\n");
				fwrite("\t\t\t\t<X-NodeType>" + nd.nodeType + "</X-NodeType>\n");
				if (nd.domainClassName != null)
					fwrite("\t\t\t\t<X-NodeDomainClass>" + nd.domainClassName + "</X-NodeDomainClass>\n");
				fwrite("\t\t\t\t<X-Position x=\"" + nd.xPos + "\" y=\"" + nd.yPos + "\" />\n");
				fwrite(nd.cpfData);
				fwrite("\t\t\t</Extension>\n");
		        fwrite(nd.subElements);				
		        fwrite("\t\t</DataField>\n");
			}
			
            netDepth = 0;
            fwrite("\t</DataDictionary>\n");
        }
        fwrite("</PMML>\n");
        try
        {
            w.close();
        }
        catch(Exception exception) { }
    }

	// --------- UI related ---------
	
    public String getExt() {
        return "*.pmml";
    }

    public String getDesc() {
        return "PMML 3.0";
    }
}
