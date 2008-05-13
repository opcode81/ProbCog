package edu.tum.cs.logic;

public class Literal extends Formula {
	public boolean isTrue;
	public Atom atom;
	
	public Literal(boolean isTrue, Atom atom) {
		this.atom = atom;
		this.isTrue = isTrue;
	}
	
	public String toString() {
		return isTrue ? atom.toString() : "!" + atom;
	}
}
