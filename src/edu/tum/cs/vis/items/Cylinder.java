package edu.tum.cs.vis.items;

import edu.tum.cs.vis.Canvas;
import edu.tum.cs.vis.Drawable;
import java.lang.*;
import edu.tum.cs.tools.Vector3f;

public class Cylinder implements Drawable {

	public Cylinder(Vector3f v1, Vector3f v2, float r){
		h = (float)v1.distance(v2);
		this.v1 = v1;
		this.v2 = v2;
		this.r = r;
	}
	
	public void draw(Canvas c) { 
		c.pushMatrix();
		c.noStroke();
		
		c.translate(v1.x, v1.y, v1.z);
		Vector3f dir = new Vector3f(v2);
		dir.subtract(v1);
		Vector3f cp = new Vector3f();
		Vector3f yAxis = new Vector3f(0,1,0);
		cp.cross(dir, yAxis);
		if(cp.distance(new Vector3f(0,0,0)) != 0){
			double theta = dir.angle(yAxis);
			c.rotateAxis(theta,cp);
		}		
		c.translate(0,h/2,0);
		cylinder(c,r,r,h,true,true);
		c.popMatrix();
	}
	
	protected int cylinder_detail=0;
	protected float[] cylinderX,cylinderZ;
	protected float h, r;
	protected Vector3f v1, v2;

	void cylinderDetail(int res) {
	  if (res<3) res=3; // force a minimum res
	  if (res != cylinder_detail) {
	    float delta = (float)Math.PI*2/res;//g.SINCOS_LENGTH/res;
	    cylinderX = new float[res];
	    cylinderZ = new float[res];
	    // calc unit circle in current resolution in XZ plane
	    for (int i = 0; i < res; i++) {
	      cylinderX[i] = (float)Math.cos(i*delta);//g.cosLUT[(int) (i*delta) % g.SINCOS_LENGTH];
	      cylinderZ[i] = (float)Math.sin(i*delta);//g.sinLUT[(int) (i*delta) % g.SINCOS_LENGTH];
	    }
	    cylinder_detail = res;
	  }
	}
	
	void cylinder(Canvas c, float r1, float r2, float h, boolean topCap, boolean bottomCap) {
		  if (cylinder_detail == 0) {
		    cylinderDetail(30);
		  }
		  h*=0.5;
		  if (topCap) {
		    c.beginShape(Canvas.TRIANGLE_STRIP);
		    for (int i = 0; i < cylinder_detail; i++) {
		      c.vertex(0,-h,0);
		      c.vertex(cylinderX[i]*r1, -h, cylinderZ[i]*r1);
		    }
		    c.vertex(0,-h,0);
		    c.vertex(cylinderX[0]*r1, -h, cylinderZ[0]*r1);
		    c.endShape();
		  }
		  c.beginShape(Canvas.TRIANGLE_STRIP);
		  for (int i = 0; i < cylinder_detail; i++) {
		    c.vertex(cylinderX[i]*r1, -h, cylinderZ[i]*r1);
		    c.vertex(cylinderX[i]*r2, h, cylinderZ[i]*r2);
		  }
		  c.vertex(cylinderX[0]*r1, -h, cylinderZ[0]*r1);
		  c.vertex(cylinderX[0]*r2, h, cylinderZ[0]*r2);
		  c.endShape();
		  if (bottomCap) {
		    c.beginShape(Canvas.TRIANGLE_STRIP);
		    for (int i = 0; i < cylinder_detail; i++) {
		      c.vertex(0,h,0);
		      c.vertex(cylinderX[i]*r2, h, cylinderZ[i]*r2);
		    }
		    c.vertex(0,h,0);
		    c.vertex(cylinderX[0]*r2, h, cylinderZ[0]*r2);
		    c.endShape();
		  }
		}

}
