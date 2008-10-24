package edu.tum.cs.vis;

public class Point implements Drawable {

	public float x,y,z, size;
	public int color;
	
	public Point(float x, float y, float z, int color, float size) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.color = color;
		this.size = size;
	}
	
	public Point(Point p) {
		copyPos(p);
		color = p.color;
	}
	
	public void draw(Canvas c) {
	    c.pushMatrix();
		c.translate(0,0,z);
	    c.noStroke(); // do not draw outline
	    c.fill(color);	    
	    c.ellipse(x, y, size, size);
	    c.popMatrix();
	}
	
	@Override
	public String toString() {
		return String.format("(%f/%f/%f)", x, y, z);
	}
	
	public double distance(Point p2) {
		float xd = x-p2.x;
		float yd = y-p2.y;
		float zd = z-p2.z;
		return Math.sqrt(xd*xd+yd*yd+zd*zd);
	}
	
	public void subtract(Point p) {
		x -= p.x;
		y -= p.y;
		z -= p.z;		
	}
	
	public double length() {
		return Math.sqrt(x*x+y*y+z*z);
	}
	
	public double angleTo(Point p) {
		float dot = x*p.x + y*p.y + z*p.z;
		return Math.acos(dot / length() / p.length());  
	}

	/**
	 * copies the position of another point into this
	 * @param p2
	 */
	public void copyPos(Point p2) {
		x = p2.x;
		y = p2.y;
		z = p2.z;
	}
	
	
}
