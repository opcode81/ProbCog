import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Random;

import edu.tum.cs.vis.AnimatedCanvas;
import edu.tum.cs.vis.CoordinateSystem;
import edu.tum.cs.vis.Point;
import edu.tum.cs.vis.Trajectory;


public class CreateMoonTrajectory {

	/**
	 * @param args
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws FileNotFoundException {
		double radius = 5;
		MoonTrajectory traj = new MoonTrajectory(5, Math.PI * 70 / 180, 10, radius, radius * 0.025, radius * 0.005);
		traj.center();
		AnimatedCanvas c = new AnimatedCanvas();
		c.add(new CoordinateSystem((float)radius));
		c.addAnimated(traj);
		c.setSceneSize((float)radius);
		c.runMain();
		traj.saveAsc(new File("moons.asc"));
	}
	
	public static class MoonTrajectory extends Trajectory {
		double curRadius;
		double curAngle;
		double translateX, translateY;
		
		public MoonTrajectory(int loopsPerSide, double arcAngle, int stepsPerArc, double radius, double radialDeviation, double radialNoiseDeviation) {
			translateX = translateY = 0;			
			curAngle = Math.PI + arcAngle/2;
			curRadius = radius;
			addPoint();
			for(int i = 0; i < 2*loopsPerSide+1; i++)
				addArc(arcAngle, stepsPerArc, i % 2 == 1, radius, radialDeviation, radialNoiseDeviation);
			translateX = radius / 2;			
			addPoint();
			double x = getX();
			double y = getY();
			curAngle = -arcAngle / 2 + 2 * Math.PI;
			translateX = x - getPolarX();
			translateY = y - getPolarY();
			for(int i = 0; i < 2*loopsPerSide; i++)
				addArc(arcAngle, stepsPerArc, i % 2 == 0, radius, radialDeviation, radialNoiseDeviation);			
		}
		
		protected void addArc(double angle, int steps, boolean up, double radius, double radialDeviation, double radialNoiseDeviation) {
			double factor = up ? 1.0 : -1.0;
			double angleStep = (angle / steps) * factor;
			Random rand = new Random();
			radialDeviation = Math.abs(rand.nextGaussian() * radialDeviation / steps) * (rand.nextBoolean() ? 1 : -1);
			for(int i = 0; i < steps; i++) {				
				this.curAngle += angleStep;
				curRadius += radialDeviation + rand.nextGaussian() * radialNoiseDeviation;
				addPoint();
			}
		}
		
		protected void addPoint() {
			addPoint((float)getX(), 0.0f, (float)getY());			
		}
		
		protected double getPolarX() {
			return Math.cos(this.curAngle) * curRadius;
		}
		
		protected double getPolarY() {
			return Math.sin(this.curAngle) * curRadius;
		}
		
		protected double getX() {
			return getPolarX() + translateX;
		}
		
		protected double getY() {
			return getPolarY() + translateY;
		}
		
		public void saveAsc(File f) throws FileNotFoundException {
			PrintStream s = new PrintStream(f);
			for(Point p : this.points) {
				s.print(p.v.x);
				s.print(' ');
				s.print(p.v.z);
				s.print('\n');
			}
		}
	}

}
