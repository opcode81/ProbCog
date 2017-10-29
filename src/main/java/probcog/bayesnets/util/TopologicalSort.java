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
package probcog.bayesnets.util;

import java.util.HashMap;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.util.graph.core.Graph;
import edu.ksu.cis.util.graph.core.Vertex;

/**
 * 
 * @author Dominik Jain
 */
public class TopologicalSort {
	protected BeliefNetwork bn;
	
	public TopologicalSort(BeliefNetwork bn) {
		this.bn = bn;
	}
	
	public TopologicalOrdering run() throws Exception {
		return run(false);
	}
	
	public TopologicalOrdering run(boolean createTierMap) throws Exception {
		Graph g = bn.getGraph();
		BeliefNode[] nodes = bn.getNodes();
		HashMap<BeliefNode, Integer> tierMap = null;
		if(createTierMap)
			tierMap = new HashMap<BeliefNode,Integer>(nodes.length);
		// obtain in-degree of each node/vertex
		Vertex[] vertices = g.getVertices();
		int[] indeg = new int[vertices.length];
		for(Vertex v : vertices) {
			for(Vertex child : g.getChildren(v)) {
				assert vertices[child.loc()] == child;
				indeg[child.loc()]++;
			}
		}
		// successively extract nodes with in-degree 0, decrementing the degree of nodes reached via them
		Vector<Vector<Integer>> ret = new Vector<Vector<Integer>>();
		int numExtracted = 0;
		Integer numLevel = 0;
		int prevExtracted = -1;
		boolean debug = false;
		while(numExtracted < vertices.length) {
			if(prevExtracted == numExtracted)
				throw new Exception(String.format("Topological ordering could not be obtained because of cycles in the network (%d nodes remain).", vertices.length-numExtracted));
			prevExtracted = numExtracted;
			if(debug) System.out.println(numExtracted + " of " + vertices.length);
			Vector<Integer> level = new Vector<Integer>();
			int[] indeg2 = new int[indeg.length];			
			for(int i = 0; i < indeg.length; i++) {
				indeg2[i] += indeg[i];
				if(indeg[i] == 0) {
					numExtracted++;
					indeg2[i] = -1;
					level.add(i);
					if(createTierMap) 
						tierMap.put(nodes[i], numLevel);
					for(Vertex child : g.getChildren(vertices[i]))
						indeg2[child.loc()]--;
				}
				else if(debug)
					System.out.println(String.format("  %d %s", indeg[i], nodes[i].getName()));				
			}
			indeg = indeg2;
			ret.add(level);
			numLevel++;
		}		
		return new TopologicalOrdering(ret, tierMap);
	}
}
