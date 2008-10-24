package edu.tum.cs.vis;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import processing.core.PApplet;
import processing.core.PFont;
import processing.xml.XMLElement;
import edu.tum.cs.tools.Vector3f;


public class Canvas extends PApplet implements MouseListener,
		MouseMotionListener {

	static final long serialVersionUID = 0;

	////////////////////////////////////////////////////////////////////////////////
	// DISPLAY PROPERTIES (ROTATION, ZOOM, ...)

	// init values for the display and mouse interaction
	float leftMouseX = -1.0f, leftMouseY = -1.0f, rightMouseX = -1.0f,
			rightMouseY = -1.0f, centerMouseY = -1.0f;
	float xRotDisplay = 24.4f, yRotDisplay = -14.8f, xShiftDisplay = 96.0f,
			zShiftDisplay = -315.5f, zoomDisplay = 2.12f;
	
	protected Vector3f eye, eyeTarget, eyeUp;

	// shift the human pose data into the world coordinate system
	//public static final float xShiftTrajectory = 1.35f;
	//public static final float yShiftTrajectory = 2.5f;
	//public static final float zShiftTrajectory = 1.05f;
	public static final float xShiftTrajectory = 0.0f;
	public static final float yShiftTrajectory = 0.0f;
	public static final float zShiftTrajectory = 0.0f;
	
	public static final boolean useCamera = false;

	////////////////////////////////////////////////////////////////////////////////
	// BUFFERS

	HashMap<String, float[]> boxPositions = new HashMap<String, float[]>();
	HashMap<String, float[]> boxDimensions = new HashMap<String, float[]>();
	HashMap<String, String> boxTypes = new HashMap<String, String>();

	HashMap<String, float[]> spherePositions = new HashMap<String, float[]>();
	HashMap<String, float[]> sphereDimensions = new HashMap<String, float[]>();

	ArrayList<float[]> meshpoints = new ArrayList<float[]>();
	ArrayList<int[]> meshtriangles = new ArrayList<int[]>();

	ArrayList<String> activeObjects = new ArrayList<String>();
	ArrayList<String> activeObjectClasses = new ArrayList<String>();

	@Deprecated
	ArrayList<float[]> points = new ArrayList<float[]>(); // positions on the ground
	ArrayList<float[]> ellipses = new ArrayList<float[]>(); // ellipses, e.g. for clusters
	ArrayList<float[]> trajectories = new ArrayList<float[]>(); // lists of points to be drawn as a trajectory
	ArrayList<int[]> jointPositionsWithPoseTime = new ArrayList<int[]>(); //contains vectors of 2 elements: 
	// [0] is the point whose trajectory will be drawn for each action
	// [1] is the time moment at which the human will be drawn for each action

	Vector<Drawable> items = new Vector<Drawable>();

	// TODO: change this mechanism
	ArrayList<int[]> colors = new ArrayList<int[]>(12); // the colors used in the vis. HumanTrajVis 

	////////////////////////////////////////////////////////////////////////////////
	// INDICES

	int jointPositionsIndex = 0; // index for the array list jointTrajectoriesData
	int actionIndex = 0; // index for the array lists actionData and trajectoryData

	int bodyPartToBeTracked = -1; // indicate which part of the pose (FIR, BEC etc) shall be tracked w/ trajectory

	int trajectoryStart = 0; // start and end index of a trajectory to be drawn
	int trajectoryEnd = 0;

	int episodeToBeVisualized; // episode chosen to be visualized

	// TODO: check if they are equivalent to traj_begin and traj_end
	int begin = 0; // beginning and end-index for the trajectory between two action places 
	int end = 0;

	////////////////////////////////////////////////////////////////////////////////
	// FLAGS

	boolean currentlyDrawingTrajectory = false;
	boolean playForward = false; // true for play and false for rewind
	boolean stepwisePlayback = false;
	boolean isHumanPoseVis; // true for the visualization HumanPose, false for the visualization JointTrajectories
	boolean entityDrawnInLastFrame = false; // is true after a entity was drawn and false after a trajectory was drawn.

	public void setup() {

		size(800, 600, P3D);
		lights();
		
		eye = new Vector3f(0.0f,-50f,0f);
		eyeUp = new Vector3f(0,0,1);
		eyeTarget = new Vector3f(0,0,0);
		xShiftDisplay = 0;
		zShiftDisplay = 0;

		PFont font = createFont("Verdana", 11);
		textFont(font);

		ellipseMode(RADIUS);
		frameRate(20);

		setColors();
		background(255, 255, 255);

		noLoop();
		draw();
	}

	public void draw() {

		background(60, 60, 60);
		cursor(CROSS);

		if(!useCamera) {
			pushMatrix();
			translate(width / 4.0f, height / 1.5f, -400.0f);
	
			lights();
			pushMatrix();	
			
			rotateZ(-PI / 2);
			rotateY(-PI / 2);
			translate(0.0f, zShiftDisplay, xShiftDisplay);
	
			rotateZ(radians(xRotDisplay));
			rotateY(radians(yRotDisplay));
		}

		scale(zoomDisplay);

		// draw the meshes
		drawMeshes();

		// draw the cupboards (boxes)
		drawBoxes();

		// draw the knobs (spheres)
		drawSpheres();

		// draw positions on the floor
		drawPoints();

		// draw places
		drawEllipses();

		//drawTable();
		
		drawItems();

		if(useCamera)
			setCamera();
		else {
			popMatrix();
	
			popMatrix();
		}
	}
	
	protected void setCamera() {
		//beginCamera();
		camera(eye.x, eye.y, eye.z, eyeTarget.x, eyeTarget.y, eyeTarget.z, eyeUp.x, eyeUp.y, eyeUp.z);
		//endCamera();
		
		System.out.println("eye: " + eye + " -> " + eyeTarget + "  up: " + eyeUp);
	}
	
	public void drawItems() {
		for(Drawable d : items)
			d.draw(this);		
	}

	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	// 
	// PRIMITIVE DRAWING FUNCTIONS (POINTS, BOXES, TRAJECTORIES,...)
	// 

	@Deprecated
	public void drawPoints() {
		pushMatrix();
		scale(100);
		//translate(0, 0, -0.1f);
		for (int i = 0; i < this.points.size(); i++) {
			float[] pt = this.points.get(i);
			float z = pt[2];
			//z = pt[1];
			translate(0, 0, z);
			noStroke();
			fill(Integer.valueOf(0xFF000000)
					+ Integer.valueOf(round(254 * 254 * this.points.get(i)[3])));
			ellipse(Float.valueOf(this.points.get(i)[0]), Float
					.valueOf(this.points.get(i)[1]), 0.05f, 0.05f);
			translate(0, 0, -z);
		}
		popMatrix();
	}

	public void drawEllipses() {

		hint(ENABLE_ACCURATE_TEXTURES);
		pushMatrix();
		translate(0, 0, -10f);
		for (int i = 0; i < this.ellipses.size(); i++) {
			noStroke();
			fill(Integer.valueOf(0xFF000000)
					+ Integer
							.valueOf(round(254 * 254 * this.ellipses.get(i)[4])));
			ellipse(100 * Float.valueOf(this.ellipses.get(i)[0]), 100 * Float
					.valueOf(this.ellipses.get(i)[1]), 200 * Float
					.valueOf(this.ellipses.get(i)[2]), 200 * Float
					.valueOf(this.ellipses.get(i)[3]));
		}

		popMatrix();

	}

	public void drawTrajectories(int index_begin, int index_end) {

		pushMatrix();
		translate(0, 0, -10f);

		if (trajectories.size() != 0)
			for (int i = index_begin; i < index_end - 1; i++) {

				float x1 = this.trajectories.get(i)[1];
				float y1 = this.trajectories.get(i)[2];
				float z1 = this.trajectories.get(i)[3];

				float x2 = this.trajectories.get(i + 1)[1];
				float y2 = this.trajectories.get(i + 1)[2];
				float z2 = this.trajectories.get(i + 1)[3];

				strokeWeight(3);
				stroke(Integer.valueOf(0xFF000000)
						+ Integer.valueOf(round(254 * 254 * this.trajectories
								.get(i)[4])));

				// debug: draw trajectories as sequences of points instead of lines 
				//		    stroke(200,150, 0);
				//		    fill(200,150, 0);
				//		    
				//		    pushMatrix();
				//			    translate(100*x1, 100*y1, 100*z1);
				//			    ellipse(0,0, 1f, 1f);
				//		    popMatrix();

				line(100 * x1, 100 * y1, 100 * z1, 100 * x2, 100 * y2, 100 * z2);
			}

		popMatrix();
	}

	public void drawBoxes() {

		// check if whole object classes wait to be drawn
		// if so, add the resp. objects to activeobjects

		for (String objclass : activeObjectClasses) {
			for (String elem : boxPositions.keySet()) {
				if (boxTypes.get(elem).equalsIgnoreCase(objclass)) {
					activeObjects.add(elem);
				}
			}
		}

		for (String elem : boxPositions.keySet()) {

			float[] boxpos = boxPositions.get(elem);
			float[] boxdim = boxDimensions.get(elem);

			if ((boxpos != null) && (boxdim != null)) {

				pushMatrix();

				scale(100);
				noStroke();

				if (activeObjects.contains(elem)) {
					fill(180, 0, 0);
				}
				else {
					int graylevel = 160 + 5 * (boxTypes.get(elem).hashCode() % 10);
					fill(graylevel);
				}

				translate(boxpos[0], boxpos[1], -boxpos[2]);
				box(boxdim[0], boxdim[1], boxdim[2]);
				popMatrix();
			}
		}
	}

	public void drawSpheres() {

		for (String objclass : activeObjectClasses) {
			for (String elem : spherePositions.keySet()) {
				if (boxTypes.get(elem).equalsIgnoreCase(objclass)) {
					activeObjects.add(elem);
				}
			}
		}

		for (String elem : spherePositions.keySet()) {

			float[] spherepos = spherePositions.get(elem);
			float[] spheredim = sphereDimensions.get(elem);

			if ((spherepos != null) && (spheredim != null)) {

				pushMatrix();

				scale(100);
				noStroke();

				if (activeObjects.contains(elem)) {
					fill(180, 0, 0, 200);
				}
				else {
					int graylevel = 160 + 5 * (boxTypes.get(elem).hashCode() % 10);
					fill(graylevel);
				}

				translate(spherepos[0], spherepos[1], -spherepos[2]);

				sphere(spheredim[0]);

				popMatrix();
			}
		}
	}

	public void drawTable() {

		pushMatrix();
		scale(100);
		noStroke();
		fill(160);

		final float width_x = 0.8046f;
		final float length_y = 1.1976f;

		final float min_x = 2.7976f;
		final float min_y = 1.4044f;
		final float min_z = 0.74177f;

		// draw table top
		translate(0, 0, -min_z + 0.01f);
		rect(min_x, min_y, width_x, length_y);

		// draw legs
		float x1 = min_x + width_x / 4;
		float y1 = min_y + length_y / 8;
		translate(x1, y1, min_z / 2);
		box(0.05f, 0.05f, min_z);
		translate(0, 6 * length_y / 8, 0);
		box(0.05f, 0.05f, min_z);
		translate(2 * width_x / 4, 0, 0);
		box(0.05f, 0.05f, min_z);
		translate(0, -(6 * length_y / 8), 0);
		box(0.05f, 0.05f, min_z);

		popMatrix();
	}

	public void drawMeshes() {

		pushMatrix();
		scale(100);
		strokeWeight(0.2f);
		stroke(90, 90, 90);
		noFill();

		beginShape(TRIANGLES);

		for (int i = 0; i < meshtriangles.size(); i++) {

			int p0 = meshtriangles.get(i)[0];
			int p1 = meshtriangles.get(i)[1];
			int p2 = meshtriangles.get(i)[2];

			vertex(meshpoints.get(p0)[0], meshpoints.get(p0)[1], meshpoints
					.get(p0)[2]);
			vertex(meshpoints.get(p1)[0], meshpoints.get(p1)[1], meshpoints
					.get(p1)[2]);
			vertex(meshpoints.get(p2)[0], meshpoints.get(p2)[1], meshpoints
					.get(p2)[2]);

		}
		endShape();
		popMatrix();
	}

	public void drawPlate(String goal, float ent_x, float ent_y, float ent_z) {

		pushMatrix();
		if (goal.equals("GRIP"))
			fill(255, 0, 0);
		else
			fill(0, 255, 0);

		translate(0, 0, -100 * ent_z);
		ellipse(100 * ent_x, 100 * ent_y, 10f, 10f);
		popMatrix();
	}

	public void drawCup(String goal, float ent_x, float ent_y, float ent_z) {
		pushMatrix();
		if (goal.equals("GRIP"))
			fill(255, 0, 0);
		else
			fill(0, 255, 0);

		translate(0, 0, -100 * ent_z);
		rect(100 * ent_x, 100 * ent_y, 10f, 10f);
		popMatrix();

	}

	public void add(Drawable item) {
		items.add(item);
	}

	@Deprecated
	public void addPointData(float x, float y, float z, int color) {

		float[] newPoint = new float[4];
		newPoint[0] = x;
		newPoint[1] = y;
		newPoint[2] = z;
		newPoint[3] = color;
		points.add(newPoint);
	}

	public void addEllipseData(float x, float y, float width, float height,
			int color) {

		float[] newEllipse = new float[5];
		newEllipse[0] = x;
		newEllipse[1] = y;
		newEllipse[2] = width;
		newEllipse[3] = height;
		newEllipse[4] = color;
		ellipses.add(newEllipse);
	}

	public void addTrajectoryData(int episode_traj, float x, float y, float z) {
		addTrajectoryData(episode_traj, x, y, z, 0.6f);
	}

	public void addTrajectoryData(int episode_traj, float x, float y, float z,
			float color) {

		float[] new_Point = new float[5];
		new_Point[0] = episode_traj;
		new_Point[1] = x;
		new_Point[2] = y;
		new_Point[3] = z;
		new_Point[4] = color;
		trajectories.add(new_Point);
	}

	public void addObject(String name, int color) {

		// interface to the visualization panel: 
		// put the box for the current object into the list of active objects

		activeObjects.add(name);

	}

	public void addObjectClass(String name, int color) {

		// interface to the visualization panel: 
		// put all boxes of the desired type into the list of active objects
		activeObjectClasses.add(name);

	}

	public void clearActiveObj() {
		this.activeObjects.clear();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	// 
	// READ MESHES AND THE GREY KITCHEN AS BACKGROUND
	// 

	public void readBoxData(XMLElement semMap) {

		// clear the buffers
		boxPositions.clear();
		boxDimensions.clear();

		for (int i = 0; i < semMap.getChildCount(); i++) {

			XMLElement cupb = semMap.getChild(i);

			float[][] front = new float[4][3];
			float[][] back = new float[4][3];

			for (int j = 0; j < cupb.getChildCount(); j++) {
				XMLElement part = cupb.getChild(j);

				if ((part.getName().equalsIgnoreCase("face"))
						&& (part.getStringAttribute("SIDE")
								.equalsIgnoreCase("front"))) {

					String[] coords0 = split(part.getChild(0).getContent(), " ");
					String[] coords1 = split(part.getChild(1).getContent(), " ");
					String[] coords2 = split(part.getChild(2).getContent(), " ");
					String[] coords3 = split(part.getChild(3).getContent(), " ");

					front[0][0] = Float.valueOf(coords0[0]);
					front[0][1] = Float.valueOf(coords0[1]);
					front[0][2] = Float.valueOf(coords0[2]);
					front[1][0] = Float.valueOf(coords1[0]);
					front[1][1] = Float.valueOf(coords1[1]);
					front[1][2] = Float.valueOf(coords1[2]);
					front[2][0] = Float.valueOf(coords2[0]);
					front[2][1] = Float.valueOf(coords2[1]);
					front[2][2] = Float.valueOf(coords2[2]);
					front[3][0] = Float.valueOf(coords3[0]);
					front[3][1] = Float.valueOf(coords3[1]);
					front[3][2] = Float.valueOf(coords3[2]);

				}
				else if ((part.getName().equalsIgnoreCase("face"))
						&& (part.getStringAttribute("SIDE")
								.equalsIgnoreCase("back"))) {

					String[] coords0 = split(part.getChild(0).getContent(), " ");
					String[] coords1 = split(part.getChild(1).getContent(), " ");
					String[] coords2 = split(part.getChild(2).getContent(), " ");
					String[] coords3 = split(part.getChild(3).getContent(), " ");

					back[0][0] = Float.valueOf(coords0[0]);
					back[0][1] = Float.valueOf(coords0[1]);
					back[0][2] = Float.valueOf(coords0[2]);
					back[1][0] = Float.valueOf(coords1[0]);
					back[1][1] = Float.valueOf(coords1[1]);
					back[1][2] = Float.valueOf(coords1[2]);
					back[2][0] = Float.valueOf(coords2[0]);
					back[2][1] = Float.valueOf(coords2[1]);
					back[2][2] = Float.valueOf(coords2[2]);
					back[3][0] = Float.valueOf(coords3[0]);
					back[3][1] = Float.valueOf(coords3[1]);
					back[3][2] = Float.valueOf(coords3[2]);

				}

			}

			// calculate the box positions and dimensions

			float maxX = max(max(max(front[0][0], front[1][0]), max(
					front[2][0], front[3][0])), max(
					max(back[0][0], back[1][0]), max(back[2][0], back[3][0])));
			float maxY = max(max(max(front[0][1], front[1][1]), max(
					front[2][1], front[3][1])), max(
					max(back[0][1], back[1][1]), max(back[2][1], back[3][1])));
			float maxZ = max(max(max(front[0][2], front[1][2]), max(
					front[2][2], front[3][2])), max(
					max(back[0][2], back[1][2]), max(back[2][2], back[3][2])));

			float minX = min(min(min(front[0][0], front[1][0]), min(
					front[2][0], front[3][0])), min(
					min(back[0][0], back[1][0]), min(back[2][0], back[3][0])));
			float minY = min(min(min(front[0][1], front[1][1]), min(
					front[2][1], front[3][1])), min(
					min(back[0][1], back[1][1]), min(back[2][1], back[3][1])));
			float minZ = min(min(min(front[0][2], front[1][2]), min(
					front[2][2], front[3][2])), min(
					min(back[0][2], back[1][2]), min(back[2][2], back[3][2])));

			float[] boxDim = new float[3];
			boxDim[0] = maxX - minX;
			boxDim[1] = maxY - minY;
			boxDim[2] = maxZ - minZ;
			boxDimensions.put(cupb.getStringAttribute("name"), boxDim);

			float[] boxPos = new float[3];
			boxPos[0] = minX + boxDim[0] / 2;
			boxPos[1] = minY + boxDim[1] / 2;
			boxPos[2] = minZ + boxDim[2] / 2;
			boxPositions.put(cupb.getStringAttribute("name"), boxPos);

			boxTypes.put(cupb.getStringAttribute("name"), cupb
					.getStringAttribute("type"));

		}
	}

	public void readMeshData(String file) {

		BufferedReader reader = createReader(file);
		try {

			String line;
			boolean pointFlag = false, triangleFlag = false;

			// offset needed when reading multiple meshes to a single array
			int ptOffset = meshpoints.size();

			while (true) {

				line = reader.readLine();
				if (line == null) {
					break;
				}

				// read point data
				if (pointFlag
						&& (line
								.matches("\\-?[\\d]*\\.?[\\d]*e?\\-?[\\d]* \\-?[\\d]*\\.?[\\d]*e?\\-?[\\d]* \\-?[\\d]*\\.?[\\d]*e?\\-?[\\d]*"))) {
					String[] coords = line.split(" ");
					if (coords.length == 3) {

						this.meshpoints.add(new float[] {
								Float.valueOf(coords[0]),
								Float.valueOf(coords[1]),
								-Float.valueOf(coords[2]) });
					}
					continue;
				}

				// read triangle data
				if (triangleFlag && (line.matches("3 [\\d]* [\\d]* [\\d]*"))) {
					String[] pts = line.split(" ");
					if (pts.length == 4) {

						this.meshtriangles.add(new int[] {
								Integer.valueOf(pts[1]) + ptOffset,
								Integer.valueOf(pts[2]) + ptOffset,
								Integer.valueOf(pts[3]) + ptOffset });

					}
					continue;
				}

				if (line.matches("POINTS.*")) {
					pointFlag = true;
					triangleFlag = false;
					continue;
				}

				if (line.matches("POLYGONS.*")) {
					pointFlag = false;
					triangleFlag = true;
					continue;
				}
			}

		}
		catch (IOException e) {
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	// 
	// EVENT HANDLER
	// 

	public void mousePressed(MouseEvent e) {

		// general: save mouse positions for calculating rotation and translation
		if (e.getButton() == MouseEvent.BUTTON1) {
			leftMouseX = e.getX();
			leftMouseY = e.getY();
		}
		else if (e.getButton() == MouseEvent.BUTTON3) {
			rightMouseX = e.getX();
			rightMouseY = e.getY();
		}
		else if (e.getButton() == MouseEvent.BUTTON2) {
			centerMouseY = e.getY();
		}

	}

	public void mouseReleased(MouseEvent e) {

		if (e.getButton() == MouseEvent.BUTTON1) { // reset the buffers
			leftMouseX = -1.0f;
			leftMouseY = -1.0f;
		}
		else if (e.getButton() == MouseEvent.BUTTON3) { // reset the buffers
			rightMouseX = -1.0f;
			rightMouseY = -1.0f;
		}
		else if (e.getButton() == MouseEvent.BUTTON2) {
			centerMouseY = -1.0f;
		}

	}

	public void mouseDragged(MouseEvent e) {

		if (leftMouseX != -1.0f) { // change rotation
			float dx = (e.getX() - leftMouseX) * 0.05f;
			float dy = (e.getY() - leftMouseY) * 0.05f;
			
			yRotDisplay -= (e.getY() - leftMouseY) * 0.05;
			xRotDisplay += (e.getX() - leftMouseX) * 0.05;
			leftMouseX = e.getX();
			leftMouseY = e.getY();
			
			// rotation around vertical axis
			eye.rotate(dx, eyeUp);

			// rotation around horizontal axis
			Vector3f dir = new Vector3f(eyeTarget);
			dir.subtract(eye);
			Vector3f horDir = new Vector3f();
			horDir.cross(eyeUp, dir);			
			eye.rotate(dy, horDir);
			eyeUp.rotate(dy, horDir);
			eyeUp.normalize();
		}
		else if (rightMouseX != -1.0f) { // change translation
			float dx = (e.getX() - rightMouseX) * 0.05f;
			float dy = (e.getY() - rightMouseY) * 0.05f;
			
			xShiftDisplay += (e.getY() - rightMouseY) * 0.5;
			zShiftDisplay += (e.getX() - rightMouseX) * 0.5;
			rightMouseX = e.getX();
			rightMouseY = e.getY();
			
			// horizontal translation
			Vector3f dir = new Vector3f(eyeTarget);
			dir.subtract(eye);
			Vector3f horDir = new Vector3f();
			horDir.cross(eyeUp, dir);
			horDir.normalize();
			horDir.scale(dx);
			
			// vertical translation
			Vector3f vertDir = new Vector3f(eyeUp);
			vertDir.normalize();
			vertDir.scale(dy);
			vertDir.negate();
			System.out.println("hor move: " + horDir);
			System.out.println("vert mode: " + vertDir);
			
			eye.add(horDir);
			eye.add(vertDir);
			eyeTarget.add(horDir);
			eyeTarget.add(vertDir);
		}
		else if (centerMouseY != -1.0f) { // zoom
			float dy = (e.getY() - centerMouseY) * 0.005f;			
			
			zoomDisplay += (e.getY() - centerMouseY) * 0.02;
			if (zoomDisplay < 0.01) {
				zoomDisplay = 0.01f;
			}
			centerMouseY = e.getY();
			
			Vector3f dir = new Vector3f(eyeTarget);
			dir.subtract(eye);
			dir.normalize();
			dir.scale(dy);
			
			eye.x += dir.x;
			eye.y += dir.y;
			eye.z += dir.z;
		}

		redraw();
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	// 
	// HELPER FUNCTIONS
	// 

	public int findTrajectoryIndex(float b21_x, float b21_y) {
		for (int i = begin; i < this.trajectories.size(); i++)
			if ((abs(this.trajectories.get(i)[1] - b21_x) < 0.1)
					&& (abs(this.trajectories.get(i)[2] - b21_y) < 0.1)) {
				return i;
			}
		return 0;
	}

	public int[] makeColor(int r, int g, int b) {
		int[] color = new int[3];
		color[0] = r;
		color[1] = g;
		color[2] = b;
		return color;
	}

	//fills the array colors with two colors for each action, one for the trajectory and one for the human pose
	public void setColors() {

		colors.add(makeColor(0, 0, 255));
		colors.add(makeColor(30, 144, 255));

		colors.add(makeColor(0, 255, 255));
		colors.add(makeColor(180, 255, 255));

		colors.add(makeColor(255, 215, 0));
		colors.add(makeColor(238, 221, 130));

		colors.add(makeColor(250, 128, 114));
		colors.add(makeColor(255, 160, 122));

		colors.add(makeColor(255, 105, 180));
		colors.add(makeColor(255, 228, 225));

		colors.add(makeColor(186, 85, 211));
		colors.add(makeColor(221, 160, 221));
	}

	//final Timer timer = new Timer(1, new ActionListener() {

}
