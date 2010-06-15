import java.awt.Dimension;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Vector;

import javax.swing.JFrame;

import de.tum.in.fipm.kipm.gui.visualisation.JointTrajectoriesIsomap;
import de.tum.in.fipm.kipm.gui.visualisation.items.BodyPoseSequence;
import edu.tum.cs.analysis.actionrecognition.mocap.BodyPose;
import edu.tum.cs.analysis.actionrecognition.mocap.BodyPoseSegmentSequence;
import edu.tum.cs.analysis.actionrecognition.mocap.TUMKitchenDataset;
import edu.tum.cs.util.datastruct.CollectionFilter;
import edu.tum.cs.vis.DrawableAnimated;
import edu.tum.cs.vis.items.Legend;
import edu.tum.cs.vis.items.Trajectory;


public class TrajVis {

	protected static class DBDisplay {
		public BodyPoseSegmentSequence ss;
		public Integer jointIndex;
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		boolean center = false;
		int numLowDimVis = 0;
		int numBodyVis = 0;
		Vector<String> humanFiles = new Vector<String>();
		Vector<String> embedFiles = new Vector<String>();
		Vector<String> labelFiles = new Vector<String>();
		Vector<String> labelMapFiles = new Vector<String>();
		Vector<DBDisplay> dbDisplays = new Vector<DBDisplay>();
		boolean drawMesh = true;
		boolean wire = false;
		boolean error = false;
		boolean background = false;
		int minStep = 0;
		boolean greyscale = false;
		
		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("-c")) {
				center = true;
			}
			else if(args[i].equals("-h")) {
				humanFiles.add(args[++i]);
				numBodyVis++;
			}
			else if(args[i].equals("-db")) {
				String[] parts = args[++i].split(":");
				if(parts.length < 3 || parts.length > 4)
					throw new IllegalArgumentException(args[i] + " " + args[i+1]);
				DBDisplay d = new DBDisplay();
				d.ss = new BodyPoseSegmentSequence(parts[0], Integer.parseInt(parts[1]) == 1, parts[2]);
				if(parts.length == 4) {
					d.jointIndex = TUMKitchenDataset.getJointIndex(parts[3]);
					if(d.jointIndex == null)
						throw new IllegalArgumentException("Unknown joint name " + parts[3]);
					numLowDimVis++;
				}
				else
					d.jointIndex = null;
				numBodyVis++;
				dbDisplays.add(d);
			}
			else if(args[i].equals("-e")) {
				embedFiles.add(args[++i]);
				numLowDimVis++;
			}
			else if(args[i].equals("-l")) {
				labelFiles.add(args[++i]);
			}
			else if(args[i].equals("-lm")) {
				labelMapFiles.add(args[++i]);
			}
			else if(args[i].equals("-nomesh")) {
				drawMesh = false;
			}
			else if(args[i].equals("-wire")) {
				wire = true;
			}
			else if(args[i].equals("-bg")) {
				background = true;
			}
			else if(args[i].equals("-bw")) {
				greyscale = true;
			}
			else if(args[i].equals("-minStep")) {
				minStep = Integer.parseInt(args[++i]);
			}
			else {
				System.err.println("Error: unknown parameter " + args[i]);
				error = true;
			}
		}
		if(numBodyVis == 0 && numLowDimVis == 0)
			error = true;
		
		System.out.println("TrajVis");
		if(error) {
			System.out.println("usage: TrajVis [options]");
			System.out.println("  options: ");
			System.out.println("           -h <.asc file>           human joint data from .asc file");
			System.out.println("           -db <web-id>:<relative 0|1>:<label field>:<joint name>\n" +
					           "                                    read human joint data from database and visualize one of the joints as low-dim. data (or none if joint name not given)");
			System.out.println("           -e <.asc file>           low-dimensional (2D/3D) data from .asc file");
			System.out.println("           -l <label indices file>  point labels");
			System.out.println("           -lm <label names file>   label mapping");
			System.out.println("           -c                       center embeddings around mean");
			System.out.println("           -nomesh                  do not draw kitchen mesh");	
			System.out.println("           -wire                    draw human pose using lines only");
			System.out.println("           -bg                      draw kitchen background");
			System.out.println("           -minStep <frame no.>     draw trajectory starting with given frame");
			System.out.println("           -bw                      black & white mode");
			System.out.println("\n      -h, -db and -e can be passed multiple times; must pass one of them at least once\n");
			return;
		}
		
		int height = 1000;
		int humanHeight = height/2, embeddingHeight = height/2;
		if(numBodyVis == 0) {
			embeddingHeight *= 2;
			humanHeight = 0;
		}
		if(numLowDimVis == 0) {
			embeddingHeight = 0;
			humanHeight *= 2;
		}		
		
		InputStream meshInput = humanFiles.getClass().getResourceAsStream("/meshdata/unclassified300p.vtk"); // from kipmdata.jar
		//InputStream meshInput = new FileInputStream("trajectoryData/pointcloud.vtk");
		JointTrajectoriesIsomap m = new JointTrajectoriesIsomap(meshInput, height, humanHeight, embeddingHeight);
		
		m.kitchen.setDrawMeshes(drawMesh);
		if(background)
			m.kitchen.drawBackground("/usr/wiss/tenorth/work/owl/gram_ias_human.pl");
		
		// human body motion display
		if(numBodyVis > 0) {
			int[] colors = new int[]{0xffff00ff, 0xffffff00, 0xff00ffff};	
			int i = 0;
			for(i = 0; i < humanFiles.size(); i++) {
				BodyPoseSequence bps;
				if(i == 0)
					bps = m.kitchen.bodyPoseSeq;
				else {
					bps = new BodyPoseSequence();
					m.kitchen.addAnimated(bps);
				}								 
				bps.setMode(wire);
				bps.setColor(colors[i]);
				File f = new File(humanFiles.get(i));
				bps.readData(f);
			}
			for(DBDisplay d : dbDisplays) {
				BodyPoseSequence bps;
				if(i == 0)
					bps = m.kitchen.bodyPoseSeq;
				else {
					bps = new BodyPoseSequence();
					m.kitchen.addAnimated(bps);
				}
				bps.setMode(wire);
				bps.setColor(colors[i]);
				for(BodyPose p : d.ss.getDataPointSequence())
					bps.addPose(0.0f, 0.0f, null, p.getFloatArray());
				++i;
			}
		}
		
		// 2D/3D trajectory display
		if(numLowDimVis > 0) {
			if(greyscale)
				m.isomap.setBackgroundColor(0xffffffff);
			m.isomap.centerTrajectory = center;	
			
			int[] colors = new int[]{0xffffffff, 0xffffff00, 0xff00ffff};
			int i;
			for(i = 0; i < embedFiles.size(); i++) {
				Trajectory traj = new Trajectory();
				traj.readAsc(new File(embedFiles.get(i)));
				traj.colorMode = !greyscale ? Trajectory.ColorMode.Color : Trajectory.ColorMode.Grayscale;
				traj.lineColor = colors[i % colors.length];
				if(i < labelFiles.size())
					traj.setLabels(new File(labelFiles.get(i)));
				m.isomap.addTrajectory(traj);
			}
			for(DBDisplay d : dbDisplays) {
				if(d.jointIndex != null) {
					//System.out.printf("Adding BodyPoseSegmentSequence %s, length %d\n", d.ss.getName(), d.ss.getNumDataPoints());
					Trajectory traj = d.ss.toTrajectory(d.jointIndex);
					traj.colorMode = !greyscale ? Trajectory.ColorMode.Color : Trajectory.ColorMode.Grayscale;
					traj.lineColor = colors[i % colors.length];
					traj.setLabels(d.ss.getLabelSequence());
					m.isomap.addTrajectory(traj);
					++i;
				}
			}			

			if(labelMapFiles.size() > 0) {
				Legend leg = new Legend(new File(labelMapFiles.get(0)), 5, 39);
				m.isomap.add2D(leg);
			}
			
			for(Trajectory t : new CollectionFilter<Trajectory, DrawableAnimated>(m.isomap.getAnimatedItems(), Trajectory.class)) {
				t.minStep = minStep;
			}
		}
			
		JFrame frame = new JFrame();
		Dimension size = m.getPreferredSize();
		frame.setSize(new Dimension(size.width + 10, size.height + 30));
		frame.add(m);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);		
	}
}
