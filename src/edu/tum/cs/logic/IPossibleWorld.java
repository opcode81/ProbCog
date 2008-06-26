package edu.tum.cs.logic;

/**
 * the basic interface for possible worlds, which must assign a truth value to every variable (ground atom)
 * @author jain
 *
 */
public interface IPossibleWorld {
	public boolean isTrue(GroundAtom ga);
}
