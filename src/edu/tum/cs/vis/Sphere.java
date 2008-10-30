package edu.tum.cs.vis;

public class Sphere implements Drawable {

	protected float x,y,z,radius;
	protected int color;
	
	public Sphere(float x, float y, float z, float radius, int color) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.radius = radius;
		this.color = color;
	}
	
	public void draw(Canvas c) {
		c.pushMatrix();
		c.fill(color);
		c.color(color);
		c.noStroke();
		c.translate(x,y,z);
		c.sphere(radius);
		c.popMatrix();
	}
}
