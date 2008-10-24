package edu.tum.cs.vis;

import edu.tum.cs.tools.Vector3f;

public class Point implements Drawable {

	public Vector3f v;
	public float size;
	public int color;
	
	public Point(float x, float y, float z, int color, float size) {
		this(x,y,z);
		this.color = color;
		this.size = size;
	}
	
	public Point(float x, float y, float z) {
		this.v = new Vector3f(x,y,z);
	}
	
	public Point(Point p) {
		copyPos(p);
		color = p.color;
	}
	
	public void draw(Canvas c) {
	    c.pushMatrix();
		c.translate(0,0,v.z);
	    c.noStroke(); // do not draw outline
	    c.fill(color);	    
	    c.ellipse(v.x, v.y, size, size);
	    c.popMatrix();
	}
	
	@Override
	public String toString() {
		return String.format("(%f/%f/%f)", v.x, v.y, v.z);
	}

	/*

	

	
	public double length() {
		return Math.sqrt(x*x+y*y+z*z);
	}
	
	public double angleTo(Point p) {
		float dot = x*p.x + y*p.y + z*p.z;
		return Math.acos(dot / length() / p.length());  
	}
	*/


	/**
	 * copies the position of another point into this
	 * @param p2
	 */
	public void copyPos(Point p2) {
		/*x = p2.x;
		y = p2.y;
		z = p2.z;*/
		v = new Vector3f(p2.v);
	}
	
	
}
