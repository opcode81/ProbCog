// based on the class Converter_xmlbif from KSU's BNJ (Bayesian Network Tools in Java)

package edu.tum.cs.bayesnets.core.io;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.streams.*;
import java.io.*;
import java.util.*;
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
    protected HashMap<String, Integer> nodeIndices;
	protected HashMap<String, NodeData> nodeData;
	protected Writer w;
    public int netDepth;
	protected int curNodeIdx;
	protected HashMap<Integer, String> nodeNames;
	protected HashMap adjList;
	protected String cpf, cpfNodeName;
    protected NodeData curNode;
	HashMap<String, Node> cptParents;
	
	
    public Converter_pmml()
    {
        w = null;
        curNodeIdx = 0;
    }

    public OmniFormatV1 getStream1()
    {
        return this;
    }

	// ------------- LOADING ---------------
	
    public void load(InputStream stream, OmniFormatV1 writer)
    {
        _Writer = writer;
        _Writer.Start();
        bn_cnt = 0;
        bnode_cnt = 0;
        nodeIndices = new HashMap<String, Integer>();
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
					cptParents = new HashMap<String, Node>();					
                    visitDocument(node);
					
					// process cpt definitions for nodes that were gathered along the way
					Set<String> nodeNames = cptParents.keySet();
					for(Iterator<String> iter = nodeNames.iterator(); iter.hasNext();) {
						String nodeName = iter.next();
						visitDefinition(cptParents.get(nodeName), nodeName);
					}
					cptParents = null;			
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
                    _Writer.BeginBeliefNode(bnode_cnt);
                    visitDataField(node);
                    _Writer.EndBeliefNode();
                    bnode_cnt++;
                }
			}
        }
    }

    protected void visitDataField(Node parent)
    {        
		// process attributes
		
        NamedNodeMap attrs = parent.getAttributes();
		String nodeName = "";
        int max;
        if(attrs != null) {
            max = attrs.getLength();
            for(int i = 0; i < max; i++) {
                Node attr = attrs.item(i);
                String name = attr.getNodeName();
                String value = attr.getNodeValue();
                if(name.equals("name")) {
					nodeName = value;
                    nodeIndices.put(nodeName, new Integer(bnode_cnt));
                    _Writer.SetBeliefNodeName(nodeName);					
                } 
				/*else {
                    System.out.println("Unhandled variable property attribute " + name);
                }*/
            }
        }
		
		if(nodeName.equals(""))
			throw new RuntimeException("Missing DataField attribute 'name'!");
			
		
		// process child nodes
        
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
							int xPos = 0, yPos = 0, have = 0;
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
							cptParents.put(nodeName, n);						
					}
				}
                break;
            }
        }
    }

    protected void visitDefinition(Node parent, String nodeName)
    {
        NodeList l = parent.getChildNodes();
        if(l == null)
            return;
        LinkedList parents = new LinkedList();
        int curNode = nodeIndices.get(nodeName).intValue();
        String CPTString = "";
        int max = l.getLength();
        for(int i = 0; i < max; i++)
        {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("X-Given"))
                    parents.add(nodeIndices.get(getElementValue(node)));
                else
                if(name.equals("X-Table"))
                    CPTString = getElementValue(node);
            }
        }

        if(curNode >= 0)
        {
            int p;
            for(Iterator i = parents.iterator(); i.hasNext(); _Writer.Connect(p, curNode))
                p = ((Integer)i.next()).intValue();

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

	
	
	// -------------------- SAVING --------------------
	
	protected class NodeData {
		public String cpfData, subElements, nodeType, opType, name, domainClassName;
		int xPos, yPos;
		public NodeData() {
			cpfData = new String();
			subElements = new String();
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
        adjList = new HashMap();
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
		nodeData = new HashMap<String,NodeData>();
		fwrite("\t<DataDictionary>\n");
        netDepth = 1;
    }

    public void SetBeliefNetworkName(int idx, String name)
    {
        //fwrite("<NAME>" + name + "</NAME>\n");
    }

    public void BeginBeliefNode(int idx) {
		curNode = new NodeData();
        curNodeIdx = idx;
        adjList.put(new Integer(curNodeIdx), new ArrayList());
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
		curNode.name = name;
        nodeNames.put(new Integer(curNodeIdx), name);
    }

    public void MakeContinuous(String s) {
    }

    public void EndBeliefNode() {
		nodeData.put(curNode.name, curNode);
    }

    public void Connect(int par_idx, int chi_idx) {
        ArrayList adj = (ArrayList)adjList.get(new Integer(chi_idx));
        adj.add(new Integer(par_idx));
    }

    public void BeginCPF(int idx) {
        ArrayList adj = (ArrayList)adjList.get(new Integer(idx));
        cpfNodeName = (String)nodeNames.get(new Integer(idx));
        cpf = "\t\t\t\t<X-Definition>\n";
        //cpf += "\t\t\t<FOR>" + curNodeName + "</FOR>\n";
        String gname;
        for(Iterator it = adj.iterator(); it.hasNext(); cpf += "\t\t\t\t\t<X-Given>" + gname + "</X-Given>\n")
        {
            Integer given = (Integer)it.next();
            gname = (String)nodeNames.get(given);
        }
        cpf += "\t\t\t\t\t<X-Table>";
    }

    public void ForwardFlat_CPFWriteValue(String x)
    {
        cpf += x + " ";
    }

    public void EndCPF()
    {
        cpf += "</X-Table>\n";
		cpf += "\t\t\t\t</X-Definition>\n";		
		NodeData d = (NodeData)nodeData.get(cpfNodeName);
		d.cpfData = cpf;
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
		        fwrite("\t\t<DataField name=\"" + nd.name + "\" optype=\"" + nd.opType + "\">\n");
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
