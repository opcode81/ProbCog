package edu.tum.cs.vis;

import java.util.Vector;

public class Trajectory implements Drawable {

	public Vector<Point> points;
	
	public Trajectory() {
		points = new Vector<Point>();
	}
	
	public void addPoint(float x, float y, float z) {
		points.add(new Point(x, y, z, 0xff, 5.0f));
	}
	
	public void draw(Canvas c) {
		Point prev = null;
		for(Point p : points) {
			p.draw(c);
			if(prev != null) { // draw line connecting previous point with current point
				c.stroke(255,255,255);
				//System.out.printf("%f %f %f -> %f %f %f\n",prev.x, prev.y, prev.z, p.x, p.y, p.z);
				c.line(prev.x, prev.y, prev.z, p.x, p.y, p.z);
			}
			prev = p;
		}
	}
}
