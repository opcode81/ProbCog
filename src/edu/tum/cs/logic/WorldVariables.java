package edu.tum.cs.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import edu.tum.cs.tools.StringTool;

/**
 * contains the set of variables of a propositionalized first-order knowledge base, 
 * i.e. a set of ground atoms, where each is assigned a unique index 
 * (which can be used to represent a possible world as an array of booleans) 
 * 
 * @author jain
 */
public class WorldVariables {
	protected HashMap<String, GroundAtom> vars;
	protected HashMap<Integer, Block> var2block;
	protected HashMap<Integer, GroundAtom> varsByIndex;
	
	/**
	 * constructs an empty set of variables
	 */
	public WorldVariables() {
		vars = new HashMap<String, GroundAtom>();
		var2block = new HashMap<Integer, Block>();
		varsByIndex = new HashMap<Integer, GroundAtom>();
	}
	
	/**
	 * adds a variable (ground atom)
	 * @param gndAtom
	 */
	public void add(GroundAtom gndAtom) {
		gndAtom.setIndex(vars.size());
		vars.put(gndAtom.toString(), gndAtom);
		varsByIndex.put(gndAtom.index, gndAtom);
	}
	
	/**
	 * adds a block of mutually exclusive and exhaustive ground atoms that collectively define a single non-boolean variable
	 * (each individual logical atom will be added to the set of logical variables)
	 * @param block
	 */
	public Block addBlock(Collection<GroundAtom> block) {
		Block b = new Block(block);
		for(GroundAtom ga : block) {
			add(ga);
			var2block.put(ga.index, b);
		}
		return b;
	}
	
	/**
	 * retrieves the variable (ground atom) that corresponds to the given string representation
	 * @param gndAtom
	 * @return
	 */
	public GroundAtom get(String gndAtom) {
		return vars.get(gndAtom);
	}
	
	public GroundAtom get(Integer index) {
		return varsByIndex.get(index);
	}
	
	public Block getBlock(Integer idxGA) {
		return var2block.get(idxGA);
	}
	
	public int size() {
		return vars.size();
	}
	
	public String toString() {
		return "<" + StringTool.join(" ", vars.keySet()) + ">";		
	}
	
	public static class Block implements Iterable<GroundAtom> {
		protected Collection<GroundAtom> gndAtoms;
		protected GroundAtom trueOne;
		
		public Block(Collection<GroundAtom> list) {
			gndAtoms = list;
			trueOne = null;
		}
		
		public Iterator<GroundAtom> iterator() {
			return gndAtoms.iterator();
		}
		
		public GroundAtom getTrueOne(IPossibleWorld w) {
			//if(trueOne == null) {
				for(GroundAtom ga : gndAtoms)
					if(ga.isTrue(w)) {
						trueOne = ga;
						break;
					}
			//}
			return trueOne;
		}
		
		/*
		public void setTrueOne(GroundAtom ga) {
			trueOne = ga;
		}*/
		
		public int size() {
			return gndAtoms.size();
		}
	}
}
