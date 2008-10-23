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
}
