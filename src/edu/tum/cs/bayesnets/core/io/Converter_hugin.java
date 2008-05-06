package edu.tum.cs.bayesnets.core.io;

import java.io.OutputStream;
import java.io.PrintStream;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.ksu.cis.bnj.ver3.streams.Exporter;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;

public class Converter_hugin implements Exporter {

	public String getDesc() {
		return "Hugin";
	}

	public String getExt() {
		return "*.net;*.hugin";
	}

	public void save(BeliefNetwork bn, OutputStream out) {
		BeliefNetworkEx bnx = new BeliefNetworkEx(bn);
		PrintStream ps = new PrintStream(out);
		ps.println("net\n{\n  node_size = (70 28);\n}\n");
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			BeliefNode node = nodes[i];
			ps.printf("node N%d\n{\n", i);
			ps.printf("  label = \"%s\";\n", node.getName());
			ps.printf("  position = (%d %d);\n", node.getOwner().getx(), node.getOwner().gety());
			ps.printf("  states = (");
			Discrete dom = (Discrete)node.getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				if(j > 0) ps.print(" ");
				ps.printf("\"%s\"", dom.getName(j));
			}
			ps.println(");\n}\n");			
		}		
		for(int i = 0; i < nodes.length; i++) {
			BeliefNode node = nodes[i];
			CPF cpf = node.getCPF();
			ps.printf("potential (N%d", i);
			BeliefNode[] domprod = cpf.getDomainProduct();
			if(domprod.length > 1) {
				ps.print(" |");
				for(int j = 1; j < domprod.length; j++) {
					ps.printf(" N%d", bnx.getNodeIndex(domprod[j]));
				}
			}
			ps.printf(")\n{\n  data = ");
			int[] addr = new int[domprod.length];
			writeCPF(cpf, 1, addr, ps);
			ps.println("\n}\n");
		}
	}
	
	protected void writeCPF(CPF cpf, int i, int[] addr, PrintStream ps) {
		BeliefNode[] domprod = cpf.getDomainProduct();
		if(i == addr.length) {
			ps.print("(");
			BeliefNode n = domprod[0];
			Discrete dom = (Discrete)n.getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[0] = j;
				ValueDouble v = (ValueDouble)cpf.get(addr);
				if(j > 0) ps.print(" ");
				ps.print(v.getValue());
			}
			ps.print(")");
			return;
		}
		ps.print("(");
		BeliefNode n = domprod[i];
		Discrete dom = (Discrete)n.getDomain();
		for(int j = 0; j < dom.getOrder(); j++) {
			addr[i] = j;
			writeCPF(cpf, i+1, addr, ps);
		}
		ps.print(")");
	}
}
