package edu.tum.cs.bayesnets.util;

import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.util.graph.core.Graph;
import edu.ksu.cis.util.graph.core.Vertex;

public class TopologicalSort {
	protected Graph g;
	
	public TopologicalSort(BeliefNetwork bn) {
		this.g = bn.getGraph();
	}
	
	public TopologicalOrdering run() {
		// obtain in-degree of each node/vertex
		Vertex[] vertices = g.getVertices();
		int[] indeg = new int[vertices.length];
		for(Vertex v : vertices) {
			for(Vertex child : g.getChildren(v)) {
				assert vertices[child.loc()] != child;
				indeg[child.loc()]++;
			}
		}
		// successively extract nodes with in-degree 0, decrementing the degree of nodes reached via them
		Vector<Vector<Integer>> ret = new Vector<Vector<Integer>>();
		int numExtracted = 0;
		while(numExtracted < vertices.length) {
			System.out.println(numExtracted + " of " + vertices.length);
			Vector<Integer> level = new Vector<Integer>();
			int[] indeg2 = new int[indeg.length];			
			for(int i = 0; i < indeg.length; i++) {
				indeg2[i] += indeg[i];
				if(indeg[i] == 0) {
					numExtracted++;
					indeg2[i] = -1;
					level.add(i);
					for(Vertex child : g.getChildren(vertices[i]))
						indeg2[child.loc()]--;
				}
			}
			indeg = indeg2;
			ret.add(level);
		}		
		return new TopologicalOrdering(ret);
	}
}
