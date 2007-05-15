package edu.tum.cs.bayesnets.core.relational;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class RelationalBeliefNetwork extends BeliefNetworkEx {
	protected HashMap<String,RelationalNode> relNodesByName;
	protected HashMap<Integer,RelationalNode> relNodesByIdx;
	
	public RelationalBeliefNetwork(String xmlbifFile) throws Exception {
		super(xmlbifFile);		
		// store node data
		BeliefNode[] nodes = bn.getNodes();
		relNodesByName = new HashMap<String, RelationalNode>();
		relNodesByIdx = new HashMap<Integer, RelationalNode>();
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode d = new RelationalNode(this, nodes[i]);			
			relNodesByName.put(d.name, d);
			relNodesByIdx.put(new Integer(d.index), d);
		}
	}
	
	public RelationalNode getRelationalNode(String name) {
		return relNodesByName.get(name);
	}
	
	public RelationalNode getRelationalNode(int idx) {
		return relNodesByIdx.get(new Integer(idx));
	}
	
	protected void writeCPTs(PrintStream out, CPF cpf, Discrete[] domains, int[] addr, int i) {
		if(i == addr.length) {
			out.print("[");
			int order = domains[0].getOrder();
			for(int j = 0; j < domains[0].getOrder(); j++) {
				addr[0] = j;
				int realAddr = cpf.addr2realaddr(addr);
				double value = ((ValueDouble)cpf.get(realAddr)).getValue();
				if(j > 0)
					out.print(",");
				out.print(value);
			}
			out.print("]");
		}
		else {
			for(int j = 0; j < domains[i].getOrder(); j++) {
				addr[i] = j;
				writeCPTs(out, cpf, domains, addr, i+1);
			}
		}
	}
	
	protected boolean isBooleanDomain(Discrete domain) {
		if(domain.getOrder() != 2)
			return false;
		if(domain.getName(0).equalsIgnoreCase("true") || domain.getName(1).equalsIgnoreCase("true"))
			return true;
		return false;
	}
	
	public void writeBLOGModel(PrintStream out) {
		BeliefNode[] nodes = bn.getNodes();
		
		// write type decls 
		Set<String> objTypes = new HashSet<String>();
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode node = getRelationalNode(i);
			Discrete domain = (Discrete)node.node.getDomain();
			if(!isBooleanDomain(domain))
				out.println("Type Dom" + getRelationalNode(i).name + ";");
			for(int j = 0; j < node.params.length; j++) {
				if(objTypes.contains(node.params[j]))
					continue;
				out.println("Type ObjType" + node.params[j] + ";");
				objTypes.add(node.params[j]);
			}
		}
		out.println();
		
		// write domains
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode node = getRelationalNode(i);
			Discrete domain = (Discrete)node.node.getDomain();
			if(!isBooleanDomain(domain)) {
				out.print("Guaranteed Dom" + node.name + " ");			
				for(int j = 0; j < domain.getOrder(); j++) {
					if(j > 0) out.print(", ");
					out.print(domain.getName(j));
				}
				out.println(";");
			}
		}
		out.println();
		
		// functions
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode node = getRelationalNode(i);
			Discrete domain = (Discrete)node.node.getDomain();
			out.print("random ");
			out.print(isBooleanDomain(domain) ? "Boolean" : ("Dom" + node.name));
			out.print(" " + node.name + "(");
			for(int j = 0; j < node.params.length; j++) {
				if(j > 0) out.print(", ");
				out.print("ObjType" + node.params[j]);
			}
			out.println(");");
		}
		out.println();
		
		// CPTs
		for(int i = 0; i < nodes.length; i++) {
			CPF cpf = nodes[i].getCPF();
			BeliefNode[] deps = cpf.getDomainProduct();
			out.print(nodes[i].getName() + " ~ TabularCPT[");
			Discrete[] domains = new Discrete[deps.length];
			StringBuffer args = new StringBuffer();
			int[] addr = new int[deps.length];
			for(int j = 0; j < deps.length; j++) {
				if(j > 0) {
					args.append(deps[j].getName());
					if(j < deps.length-1)
						args.append(", ");
				}
				domains[j] = (Discrete)deps[j].getDomain();
			}
			writeCPTs(out, cpf, domains, addr, 1);
			out.println("](" + args.toString() + ");");
		}
	}
}


