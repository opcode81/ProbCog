package edu.tum.cs.vis;

import java.util.PriorityQueue;
import java.util.Vector;

import edu.tum.cs.tools.Vector3f;

public class Trajectory implements Drawable, DrawableAnimated {

	public Vector<Point> points;
	
	public Trajectory() {
		points = new Vector<Point>();
	}
	
	public void addPoint(float x, float y, float z) {
		points.add(new Point(x, y, z, 0xffcbcbcb, 5.0f));
	}
	
	public void draw(Canvas c, int step) {
		Point prev = null;
		int s = 0;
		for(Point p : points) {
			if(s++ > step)
				break;
			p.draw(c);
			if(prev != null) { // draw line connecting previous point with current point
				c.stroke(255,255,255);
				//System.out.printf("%f %f %f -> %f %f %f\n",prev.x, prev.y, prev.z, p.x, p.y, p.z);
				c.line(prev.v.x, prev.v.y, prev.v.z, p.v.x, p.v.y, p.v.z);
			}
			prev = p;
		}
		
		//c.eyeTarget.set(prev.v);
	}
	
	public int getNumSteps() {
		return points.size();
	}
	
	public void draw(Canvas c) {
		draw(c, getNumSteps()-1);
	}

	public void segment() {
		int i = 0;
		java.util.PriorityQueue<Double> min_distances = new PriorityQueue<Double>();
		for(Point p : points) {
			
			// check previous points for proximity			
			if(i >= 3) {				 
				double min_distance = Double.MAX_VALUE;
				int min_distance_point_idx = -1;
				for(int j = i-3; j >= 0; j--) {
					Point p2 = points.get(j);
					double dist = p.v.distance(p2.v);
					if(dist < min_distance) {
						min_distance = dist;
						min_distance_point_idx = j;
					}
				}
				min_distances.add(min_distance);
				
				// merge if distance to closest previous point is small enough
				if(min_distance < 20) {
					Point p2 = points.get(min_distance_point_idx);
					
					// ... and directions are similar
					boolean dirSimilar = false;
					if(min_distance_point_idx == 0)
						dirSimilar = true;
					else {
						Vector3f dir1 = new Vector3f(p.v);
						dir1.subtract(points.get(i-1).v);
						Vector3f dir2 = new Vector3f(p2.v);
						dir2.subtract(points.get(min_distance_point_idx-1).v);
						double angle = dir1.angle(dir2);
						System.out.println("angle = " + angle * 180/Math.PI);
						dirSimilar = angle < Math.PI*40/180;
					}
					
					if(dirSimilar) { // merge p and p2
						p.copyPos(p2);
						p.color = 0xffff0000;
						p2.color = 0xffff0000;
					}
				}					
			}
			
			i++;
		}
		
		// print minimum distances
		Double prev_d = null;
		double max_diff = Double.MIN_VALUE;
		double max_diff_value = 0;
		while(!min_distances.isEmpty()) {
			double d = min_distances.remove();
			if(prev_d != null) {
				double diff = d-prev_d;
				if(diff > max_diff) {
					max_diff = diff;
					max_diff_value = d;
				}
			}
			prev_d = d;
			//System.out.println(d);
		}
		System.out.println("max diff: " + max_diff + " @ " + max_diff_value);
		
	}
}
