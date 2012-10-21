/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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
package probcog.bayesnets.core.io;

import java.io.OutputStream;
import java.io.PrintStream;

import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.streams.Exporter;

/**
 * somewhat limited export filter for the Hugin file format (supports discrete belief nodes only)
 * @author Dominik Jain
 *
 */
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
			ps.println(";\n}\n");
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
				if(j > 0) ps.print(" ");
				ps.print(cpf.getDouble(addr));
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
