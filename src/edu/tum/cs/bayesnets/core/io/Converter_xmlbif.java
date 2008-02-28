package edu.tum.cs.bayesnets.core.io;
// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   Converter_xmlbif.java

import edu.ksu.cis.bnj.ver3.streams.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/**
 * Converter (Exporter/Importer) for the XML-BIF format (XML Bayesian Interchange Format)
 * This class need not be used directly. BeliefNetworkEx implements the loading and storing
 * of XML-BIF files using this class.  
 * (This class was obtained through decompilation of the BNJ plugin)
 */
public class Converter_xmlbif
    implements OmniFormatV1, Exporter, Importer
{

    public Converter_xmlbif()
    {
        w = null;
        curBeliefNode = 0;
    }

    public OmniFormatV1 getStream1()
    {
        return this;
    }

    public void load(InputStream stream, OmniFormatV1 writer)
    {
        _Writer = writer;
        _Writer.Start();
        bn_cnt = 0;
        bnode_cnt = 0;
        _nodenames = new HashMap();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
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
        for(int i = 0; i < max;)
        {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("BIF"))
                {
                    NamedNodeMap attrs = node.getAttributes();
                    if(attrs != null)
                    {
                        int amax = attrs.getLength();
                        for(int j = 0; j < amax; j++)
                        {
                            Node attr = attrs.item(j);
                            String aname = attr.getNodeName().toUpperCase();
                            if(aname.equals("VERSION"))
                                try
                                {
                                    int ver = (int)(Double.parseDouble(attr.getNodeValue()) * 100D);
                                    if(ver != 30)
                                        System.out.println("version " + ver + " is not supported");
                                }
                                catch(Exception exx) { }
                            else
                                System.out.println("property:" + aname + " not handled");
                        }

                    }
                    visitDocument(node);
                } else
                if(name.equals("NETWORK"))
                {
                    _Writer.CreateBeliefNetwork(bn_cnt);
                    visitModel(node);
                    bn_cnt++;
                } else
                {
                    throw new RuntimeException("Unhandled element " + name);
                }
                // fall through

            case 2: // '\002'
            case 3: // '\003'
            case 4: // '\004'
            case 5: // '\005'
            case 6: // '\006'
            case 7: // '\007'
            case 8: // '\b'
            case 9: // '\t'
            case 10: // '\n'
            default:
                i++;
                break;
            }
        }

    }

    public void visitModel(Node parent)
    {
        NodeList l = parent.getChildNodes();
        if(l == null)
            throw new RuntimeException("Unexpected end of document!");
        int max = l.getLength();
        for(int i = 0; i < max;)
        {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("NAME"))
                    _Writer.SetBeliefNetworkName(bn_cnt, getElementValue(node));
                else
                if(!name.equals("PRM_CLASS") && name.equals("VARIABLE"))
                {
                    _Writer.BeginBeliefNode(bnode_cnt);
                    visitVariable(node);
                    _Writer.EndBeliefNode();
                    bnode_cnt++;
                }
                // fall through

            case 2: // '\002'
            case 3: // '\003'
            case 4: // '\004'
            case 5: // '\005'
            case 6: // '\006'
            case 7: // '\007'
            case 8: // '\b'
            case 9: // '\t'
            case 10: // '\n'
            default:
                i++;
                break;
            }
        }

        for(int i = 0; i < max;)
        {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("DEFINITION") || name.equals("PROBABILITY"))
                    visitDefinition(node);
                // fall through

            case 2: // '\002'
            case 3: // '\003'
            case 4: // '\004'
            case 5: // '\005'
            case 6: // '\006'
            case 7: // '\007'
            case 8: // '\b'
            case 9: // '\t'
            case 10: // '\n'
            default:
                i++;
                break;
            }
        }

    }

    protected void visitVariable(Node parent)
    {
        NodeList l = parent.getChildNodes();
        String propType = "nature";
        NamedNodeMap attrs = parent.getAttributes();
        int max;
        if(attrs != null)
        {
            max = attrs.getLength();
            for(int i = 0; i < max; i++)
            {
                Node attr = attrs.item(i);
                String name = attr.getNodeName();
                String value = attr.getNodeValue();
                if(name.equals("TYPE"))
                {
                    propType = value;
                    if(value.equals("decision"))
                        _Writer.SetType("decision");
                    else
                    if(value.equals("utility"))
                        _Writer.SetType("utility");
                } else
                {
                    System.out.println("Unhandled variable property attribute " + name);
                }
            }

        }
        max = l.getLength();
        for(int i = 0; i < max;)
        {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("NAME"))
                {
                    String desc = getElementValue(node);
                    _nodenames.put(desc, new Integer(bnode_cnt));
                    _Writer.SetBeliefNodeName(desc);
                } else
                if(name.equals("OUTCOME") || name.equals("VALUE"))
                {
                    String value = getElementValue(node);
                    _Writer.BeliefNodeOutcome(value);
                } else
                if(name.equals("PROPERTY"))
                {
                    String assignment = getElementValue(node);
                    int eq = assignment.indexOf("=");
                    String var = assignment.substring(0, eq).trim().toUpperCase();
                    String val = assignment.substring(eq + 1).trim();
                    if(var.equals("POSITION"))
                    {
                        int cma = val.indexOf(",");
                        int left = val.indexOf("(");
                        int right = val.indexOf(")");
                        String X = val.substring(left + 1, cma).trim();
                        String Y = val.substring(cma + 1, right).trim();
                        _Writer.SetBeliefNodePosition(Integer.parseInt(X), Integer.parseInt(Y));
                    } else if (var.equals("DOMAINCLASS")) {
                    	String domainClassName = val.trim();
                    	_Writer.SetBeliefNodeDomainClass(domainClassName);
                    }
                }
                // fall through

            case 2: // '\002'
            case 3: // '\003'
            case 4: // '\004'
            case 5: // '\005'
            case 6: // '\006'
            case 7: // '\007'
            case 8: // '\b'
            case 9: // '\t'
            default:
                i++;
                break;
            }
        }

    }

    protected void visitDefinition(Node parent)
    {
        NodeList l = parent.getChildNodes();
        if(l == null)
            return;
        LinkedList parents = new LinkedList();
        int curNode = -1;
        String CPTString = "";
        int max = l.getLength();
        for(int i = 0; i < max;)
        {
            Node node = l.item(i);
            switch(node.getNodeType())
            {
            case 1: // '\001'
                String name = node.getNodeName();
                if(name.equals("FOR"))
                {
                    String cNode = getElementValue(node);
                    curNode = ((Integer)_nodenames.get(cNode)).intValue();
                } else
                if(name.equals("GIVEN"))
                    parents.add(_nodenames.get(getElementValue(node)));
                else
                if(name.equals("TABLE"))
                    CPTString = getElementValue(node);
                // fall through

            case 2: // '\002'
            case 3: // '\003'
            case 4: // '\004'
            case 5: // '\005'
            case 6: // '\006'
            case 7: // '\007'
            case 8: // '\b'
            case 9: // '\t'
            default:
                i++;
                break;
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

    public void save(OutputStream os)
    {
        w = new OutputStreamWriter(os);
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
        BeliefNames = new HashMap();
        AdjList = new HashMap();
        fwrite("<?xml version=\"1.0\" encoding=\"US-ASCII\"?>\n");
        fwrite("<!--\n");
        fwrite("Bayesian network in XMLBIF v0.3 (BayesNet Interchange Format)\n");
        fwrite("Produced by BNJ 3.0 (http://bndev.sourceforge.net/\n");
        fwrite("-->\n");
        fwrite("\t\t<!-- DTD for the XMLBIF 0.3 format -->\n");
        fwrite("<!DOCTYPE BIF [\n");
        fwrite("<!ELEMENT BIF ( NETWORK )*>\n");
        fwrite("<!ATTLIST BIF VERSION CDATA #REQUIRED>\n");
        fwrite("<!ELEMENT NETWORK ( NAME, ( PROPERTY | VARIABLE | DEFINITION )* )>\n");
        fwrite("<!ELEMENT NAME (#PCDATA)>\n");
        fwrite("<!ELEMENT VARIABLE ( NAME, ( OUTCOME |  PROPERTY )* ) >\n");
        fwrite("\t<!ATTLIST VARIABLE TYPE (nature|decision|utility) \"nature\">\n");
        fwrite("<!ELEMENT OUTCOME (#PCDATA)>\n");
        fwrite("<!ELEMENT DEFINITION ( FOR | GIVEN | TABLE | PROPERTY )* >\n");
        fwrite("<!ELEMENT FOR (#PCDATA)>\n");
        fwrite("<!ELEMENT GIVEN (#PCDATA)>\n");
        fwrite("<!ELEMENT TABLE (#PCDATA)>\n");
        fwrite("<!ELEMENT PROPERTY (#PCDATA)>\n");
        fwrite("]>\n");
        fwrite("<BIF VERSION=\"0.3\">\n");
    }

    public void CreateBeliefNetwork(int idx)
    {
        if(netDepth > 0)
        {
            netDepth = 0;
            fwrite("</NETWORK>\n");
        }
        fwrite("<NETWORK>\n");
        netDepth = 1;
    }

    public void SetBeliefNetworkName(int idx, String name)
    {
        fwrite("<NAME>" + name + "</NAME>\n");
    }

    public void BeginBeliefNode(int idx)
    {
        nodeType = "nature";
        internalNode = "";
        curBeliefNode = idx;
        AdjList.put(new Integer(curBeliefNode), new ArrayList());
    }

    public void SetType(String type)
    {
        if(!type.equals("chance"))
            nodeType = type;
    }

    public void SetBeliefNodePosition(int x, int y)
    {
        internalNode += "\t\t<PROPERTY>position = (" + x + "," + y + ")</PROPERTY>\n";
    }
    
	public void SetBeliefNodeDomainClass(String domainClassName) {
		internalNode += "\t\t<PROPERTY>domainclass = " + domainClassName + "</PROPERTY>\n";
	}

    public void BeliefNodeOutcome(String outcome)
    {
        internalNode += "\t\t<OUTCOME>" + outcome.replaceAll("<", "&lt;").replaceAll(">", "&gt;") + "</OUTCOME>\n";
    }

    public void SetBeliefNodeName(String name)
    {
        internalNode += "\t\t<NAME>" + name + "</NAME>\n";
        BeliefNames.put(new Integer(curBeliefNode), name);
    }

    public void MakeContinuous(String s)
    {
    }

    public void EndBeliefNode()
    {
        fwrite("\t<VARIABLE TYPE=\"" + nodeType + "\">\n");
        fwrite(internalNode);
        fwrite("\t</VARIABLE>\n");
    }

    public void Connect(int par_idx, int chi_idx)
    {
        ArrayList adj = (ArrayList)AdjList.get(new Integer(chi_idx));
        adj.add(new Integer(par_idx));
    }

    public void BeginCPF(int idx)
    {
        ArrayList adj = (ArrayList)AdjList.get(new Integer(idx));
        String name = (String)BeliefNames.get(new Integer(idx));
        fwrite("\t<DEFINITION>\n");
        fwrite("\t\t<FOR>" + name + "</FOR>\n");
        String gname;
        for(Iterator it = adj.iterator(); it.hasNext(); fwrite("\t\t<GIVEN>" + gname + "</GIVEN>\n"))
        {
            Integer given = (Integer)it.next();
            gname = (String)BeliefNames.get(given);
        }

        fwrite("\t\t<TABLE>");
    }

    public void ForwardFlat_CPFWriteValue(String x)
    {
        fwrite(x + " ");
    }

    public void EndCPF()
    {
        fwrite("\t\t</TABLE>");
        fwrite("\t</DEFINITION>");
    }

    public int GetCPFSize()
    {
        return 0;
    }

    public void Finish()
    {
        if(netDepth > 0)
        {
            netDepth = 0;
            fwrite("</NETWORK>\n");
        }
        fwrite("</BIF>\n");
        try
        {
            w.close();
        }
        catch(Exception exception) { }
    }

    public String getExt()
    {
        return "*.xml";
    }

    public String getDesc()
    {
        return "XML Bayesian Network Interchange Format";
    }

    private OmniFormatV1 _Writer;
    private int bn_cnt;
    private int bnode_cnt;
    private HashMap _nodenames;
    Writer w;
    public int netDepth;
    int curBeliefNode;
    HashMap BeliefNames;
    HashMap AdjList;
    String internalNode;
    String nodeType;
}
