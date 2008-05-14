package edu.tum.cs.logic;

public class Equality extends Formula {
	public String left, right;
	
	public Equality(String left, String right) {
		this.left = left;
		this.right = right;
	}
	
	public String toString() {
		return left + "=" + right;
	}
}
