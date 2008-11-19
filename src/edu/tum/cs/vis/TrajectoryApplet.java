package edu.tum.cs.vis;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;

import edu.tum.cs.tools.MySQLConnection;
import edu.tum.cs.tools.Vector3f;

public class TrajectoryApplet extends AnimatedCanvas {
	private static final long serialVersionUID = 1L;
	/**
	 * whether the trajectory should be translated to be centered around the mean coordinate
	 */
	public boolean centerTrajectory = false;

	public TrajectoryApplet() {		
	}
	
	/**
	 * construct a trajectory from x,y,z data in a MySQL database table in Moritz' KIPM database
	 * @param table
	 */
	public TrajectoryApplet(String table) {
		String host = "atradig131";
		String db = "stt-human-import";
		String user = "tenorth";
		String password = "UEY9KbNb";

		// construct 3D trajectory
		Trajectory traj = new Trajectory();		
		try {
			MySQLConnection conn = new MySQLConnection(host, user, password, db);
			ResultSet rs = conn.select("select x,y,z from " + table + " where episode_nr=0 and occurrence_nr=1 order by instance_nr");
			while (rs.next()) {
				float x = rs.getFloat(1);
				float y = rs.getFloat(2);
				float z = rs.getFloat(3);
				System.out.printf("%f/%f/%f\n", x, y, z);
				traj.addPoint(x, y, z);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e.getClass().getCanonicalName() + ": " + e.getMessage());
		}
		init(traj);
	}
	
	public TrajectoryApplet(java.io.File matlabAsciiFile) throws IOException {
		readTrajectory(matlabAsciiFile);
	}
	
	public void readTrajectory(java.io.File matlabAsciiFile) throws NumberFormatException, IOException {
		readTrajectory(matlabAsciiFile, 0);
	}
	
	/**
	 * 
	 * @param matlabAsciiFile
	 * @param startLine  0-based line index indicating the first line to consider
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public void readTrajectory(java.io.File matlabAsciiFile, int startLine) throws NumberFormatException, IOException {
		Trajectory traj = new Trajectory();
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
			traj.addPoint(x, y, z);
		}
		init(traj);
	}

	public void init(Trajectory traj) {
		if(centerTrajectory)
			traj.center();
		//traj.findOscillations();
		//traj.mergePoints();
		//traj.getTransitionPoints();		

		// add coordinate system
		float sceneSize = traj.getMaxAbsCoord();
		add(new CoordinateSystem(sceneSize));	
		this.setSceneSize(sceneSize);
		
		// add trajectory
		addAnimated(traj);
		
		// init camera 
		useCamera = false;
		this.eye = new Vector3f(sceneSize, sceneSize, sceneSize/2);
		this.eyeUp = new Vector3f(sceneSize, sceneSize, -sceneSize);
		//this.eyeUp = new Vector3f(0,0,1);
		this.eyeUp.normalize();
		
		redraw();
	}
	
	public static void main(String[] args) {
		try {
			//Canvas applet = new TrajectoryApplet("STT_DETAILED_ABSTRACT_EXP_ISOMAP3D_INTERVAL");
			Canvas applet = new TrajectoryApplet("STT_DETAILED_ABSTRACT_EXP_ISOMAP3D_INTERVAL_LOCAL");
			//Canvas applet = new TrajectoryApplet(new File("/usr/wiss/jain/work/code/GP/sttFlorianJointsLatent.asc"));
			applet.runMain();
		}
		catch (Exception e) {
			e.printStackTrace();
		}		
	}
}
