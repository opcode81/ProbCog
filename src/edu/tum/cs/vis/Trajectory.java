package edu.tum.cs.vis;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.PriorityQueue;
import java.util.Vector;

import edu.tum.cs.tools.Hashmap2List;
import edu.tum.cs.tools.Vector3f;

public class Trajectory implements Drawable, DrawableAnimated {

	public Vector<Point> points;
	public double minDistance = Double.MAX_VALUE;
	public float pointSize = 0.0f, sphereSize = 120.0f;
	public int pointColor = 0xffcbcbcb, sphereColor = 0x99ffff00; 
	public float minx, miny, minz, maxx, maxy, maxz;
	public float range;
	public int lineColor = 0xffffffff;
	
	protected Hashmap2List<Integer, Drawable> animationEffects;
	
	public Trajectory() {
		animationEffects = new Hashmap2List<Integer, Drawable>();
		points = new Vector<Point>();
		resetStats();
	}
	
	protected void resetStats() {
		minx = Float.MAX_VALUE; miny = Float.MAX_VALUE; minz = Float.MAX_VALUE; 
		maxx = Float.MIN_VALUE; maxy = Float.MIN_VALUE; maxz = Float.MIN_VALUE;
		range = 0;
	}
	
	protected void updateStats(float x, float y, float z) {
		minx = Math.min(x, minx);
		miny = Math.min(y, miny);
		minz = Math.min(z, minz);
		maxx = Math.max(x, maxx);
		maxy = Math.max(y, maxy);
		maxz = Math.max(z, maxz);
		float xrange = maxx - minx;
		float yrange = maxy - miny;
		float zrange = maxz - minz;
		range = Math.max(Math.max(xrange, yrange), zrange);
	}
	
	public void addPoint(float x, float y, float z) {
		points.add(new Point(x, y, z, pointColor, pointSize));
		updateStats(x,y,z);
	}
	
	public float getMaxAbsCoord() {
		float x = Math.max(Math.abs(minx), Math.abs(maxx));
		float y = Math.max(Math.abs(miny), Math.abs(maxy));
		float z = Math.max(Math.abs(minz), Math.abs(maxz));
		float xy = Math.max(x, y);
		return Math.max(xy, z);
	}
	
	public void draw(Canvas c, int step) {
		c.pushMatrix();
		pointSize = range / 150;
		sphereSize = pointSize * 2;
		//System.out.println(pointSize);
		Point prev = null;
		int s = 0;
		for(Point p : points) {
			if(p.size == 0.0f)
				p.size = pointSize;
			if(s++ > step)
				break;
			p.draw(c);
			if(prev != null) { // draw line connecting previous point with current point
				//c.stroke(255,255,255);
				//System.out.printf("%f %f %f -> %f %f %f\n",prev.x, prev.y, prev.z, p.x, p.y, p.z);
				//c.line(prev.v.x, prev.v.y, prev.v.z, p.v.x, p.v.y, p.v.z);
				c.drawLine(prev.v, p.v, lineColor);
			}
			prev = p;
		}
		
		// draw sphere for current pos
		new Sphere(prev.v.x, prev.v.y, prev.v.z, sphereSize, sphereColor).draw(c);
		
		// draw animation effects if any
		Vector<Drawable> effects = animationEffects.get(step);
		if(effects != null) {
			System.out.println("drawing effects for step " + step);
			for(Drawable d : effects)
				d.draw(c);
		}
		
		// draw frame number
		c.fill(0xffffffff);
		c.textMode(Canvas.SCREEN);
		c.textAlign(Canvas.LEFT);
		c.text(String.format("%d/%d", 1+step, 1+this.getMaxStep()), 5, 5+11);
		
		// set eye to target last drawn point		
		c.eyeTarget.set(prev.v);
		c.popMatrix();
	}
	
	public int getNumSteps() {
		return points.size();
	}
	
	public void draw(Canvas c) {
		draw(c, this.getMaxStep());
	}
	
	/**
	 * saves the point sequence to a Matlab-style ASCII file
	 * @param f
	 * @throws FileNotFoundException 
	 */
	public void saveAsc(java.io.File f) throws FileNotFoundException {
		PrintStream s = new PrintStream(f);
		for(Point p : this.points) {
			s.print(p.v.x);
			s.print(' ');
			s.print(p.v.y);
			s.print(' ');
			s.print(p.v.z);
			s.print('\n');
		}
	}
	
	public void readAsc(java.io.File matlabAsciiFile) throws NumberFormatException, IOException {
		readAsc(matlabAsciiFile, 0);
	}
	
	/**
	 * @param matlabAsciiFile
	 * @param startLine  0-based line index indicating the first line to consider
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public void readAsc(java.io.File matlabAsciiFile, int startLine) throws NumberFormatException, IOException {
		BufferedReader r = new BufferedReader(new FileReader(matlabAsciiFile));
		String line;
		int iLine = 0;
		while((line = r.readLine()) != null) {
			if(iLine++ < startLine)
				continue;
			String[] parts = line.trim().split("\\s+");
			float x = Float.parseFloat(parts[0]);
			float y = Float.parseFloat(parts[1]);
			float z;
			if(parts.length == 3)
				z = Float.parseFloat(parts[2]);
			else 
				z = 0;
			this.addPoint(x, y, z);
		}
	}
	
	public double getMinDistance(){
		// minimum distance between two adjacent points
		double distance = 0;
		int i = 0;
		for (Point p: points){
			if(i >0){
				distance = p.v.distance(points.get(i-1).v);
				if (distance < minDistance)
					minDistance = distance;
			}
			i++;
		}
		System.out.println(minDistance);
		return minDistance;
	}

	/**
	 * merges points that are close to each other
	 */
	public void mergePoints() {
		System.out.println("Merging points...");
		float proximity_threshold = 20;		
		float direction_threshold = 40; // in degrees
		
		int i = 0;
		java.util.PriorityQueue<Double> min_distances = new PriorityQueue<Double>();
		for(Point p : points) {
			if(i >= 3) {		
				// check previous points for proximity
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
				if(min_distance < proximity_threshold) {
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
						//System.out.println("angle = " + angle * 180/Math.PI);
						dirSimilar = angle < Math.PI*direction_threshold/180;
					}
					
					if(dirSimilar) { // merge p and p2
						// animation events
						PointPair pp = new PointPair(new Point(p.v, 0xffff00ff, pointSize), new Point(p2.v, 0xffff00ff, pointSize), 0xffff0000);
						animationEffects.put(i, pp);
						animationEffects.put(min_distance_point_idx, pp);
						
						// actual merge
						Vector3f newPos = new Vector3f((p.v.x+p2.v.x) / 2, (p.v.y+p2.v.y) / 2, (p.v.z+p2.v.z) / 2);
						p.v = newPos;
						p2.v = newPos;
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
	
	public void findOscillations(){
		int i = 0;
		int j = 0;
		double thresh = 7000 * getMinDistance();
		//double thresh = 150;
		int num = 0;
		int minnum = 5;
		Vector<Point> vec = new Vector<Point>();
		for(Point p : points){
			if(i>1){
				num = 0;
				j = i;
				vec.clear();
				while(j > 0 && p.v.distance(points.get(j).v) < thresh){
					vec.add(points.get(j));
					j--;
					num++;
				}
				if(num > minnum){
					Point medPoint = new Point(0,0,0,pointColor,pointSize); 
					for(Point medP : vec){
						medPoint.v.add(medP.v);
					}
					medPoint.v.scale(1/(float)num);
					float medSize = (float)Math.log(num) * range/150;
					for(Point oscP : vec){
						oscP.copyPos(medPoint);
						oscP.color = 0xff0000ff;
						oscP.size = medSize;
					}
				}
			}
			i++;
		}
	}
	
	/**
	 * centers the trajectory around the mean position
	 */
	public void center() {
		// determine mean point
		float x = 0, y = 0, z = 0;
		for(Point p : points) {
			x += p.v.x;
			y += p.v.y;
			z += p.v.z;
		}
		x /= points.size();
		y /= points.size();
		z /= points.size();
		Vector3f mean = new Vector3f(x, y, z);
		// subtract mean from all points and update stats
		resetStats();
		for(Point p : points) { 
			p.v.sub(mean);
			updateStats(p.v.x, p.v.y, p.v.z);
		}
	}
	public void getTransitionPoints(){
		int i = 0;
		double threshDist = 2.0f;
		double threshAngle = Math.PI * 50/180;
		for(Point p : points){
			if(i < points.size() - 2 && i > 2){
				for (int j = i+1; j < points.size()-2; j++){
					Point p2 = points.get(j);
					if (p.v.distance(p2.v) == 0){
						Point succP1 = points.get(i+1);
						Point succP2 = points.get(j+1);
						if(p.v.distance(succP1.v) != 0 && p.v.distance(succP2.v) != 0){
							Vector3f dir1 = new Vector3f(p.v);
							dir1.subtract(succP1.v);
							Vector3f dir2 = new Vector3f(p.v);
							dir2.subtract(succP2.v);
							double angle = dir1.angle(dir2);
							double zeroAngle = succP1.v.angle(succP2.v);
							if (succP1.v.distance(succP2.v) > threshDist && angle > threshAngle){
								System.out.println(">>>>>>> Got a transition point");
								p.size = 150;
								p.color = 0xff00ff00;
							}
						}			
						succP1 = points.get(i-1);
						succP2 = points.get(j-1);
						if(p.v.distance(succP1.v) != 0 && p.v.distance(succP2.v) != 0){
							Vector3f dir1 = new Vector3f(p.v);
							dir1.subtract(succP1.v);
							Vector3f dir2 = new Vector3f(p.v);
							dir2.subtract(succP2.v);
							double angle = dir1.angle(dir2);
							double zeroAngle = succP1.v.angle(succP2.v);
							if (succP1.v.distance(succP2.v) > threshDist && angle > threshAngle){
								System.out.println(">>>>>>> Got a transition point");
								p.size = 150;
								p.color = 0xff00ff00;
							}
						}
					}
				}
			}
			i++;	
		}
	}

	public int getMaxStep() {
		return getNumSteps()-1;
	}
	
	public class PointPair implements Drawable {

		protected Point p1, p2;
		protected int linecolor;
		
		public PointPair(Point p1, Point p2, int linecolor) {
			this.p1 = p2;
			this.p2 = p2;
			this.linecolor = linecolor;
		}
		
		public void draw(Canvas c) {
			p1.size = pointSize;
			p2.size = pointSize;
			p1.draw(c);
			p2.draw(c);
			c.drawLine(p1.v, p2.v, linecolor);
		}		
	}
}
