import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Vector;

import javax.swing.JFrame;

import de.tum.in.fipm.kipm.gui.visualisation.JointTrajectoriesIsomap;
import de.tum.in.fipm.kipm.gui.visualisation.items.BodyPoseSequence;
import edu.tum.cs.analysis.actionrecognition.mocap.BodyPose;
import edu.tum.cs.analysis.actionrecognition.mocap.BodyPoseSegmentSequence;
import edu.tum.cs.analysis.actionrecognition.mocap.LabelMapping;
import edu.tum.cs.analysis.actionrecognition.mocap.TUMKitchenDataset;
import edu.tum.cs.util.datastruct.CollectionFilter;
import edu.tum.cs.vis.Canvas;
import edu.tum.cs.vis.DrawableAnimated;
import edu.tum.cs.vis.items.Legend;
import edu.tum.cs.vis.items.StringSequence;
import edu.tum.cs.vis.items.Trajectory;


public class TrajVis {

	protected static class DBDisplay {
		public BodyPoseSegmentSequence ss;
		public Integer jointIndex;
		public String labelField;
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		boolean center = false;
		int numLowDimVis = 0;
		int numBodyVis = 0;
		int cutBeginning = 0;
		Vector<String> humanFiles = new Vector<String>();
		Vector<String> embedFiles = new Vector<String>();
		Vector<String> labelFiles = new Vector<String>();
		Vector<String> labelMapFiles = new Vector<String>();
		Vector<DBDisplay> dbDisplays = new Vector<DBDisplay>();
		String testLabelFile = null;
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
				d.labelField = parts[2];
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
			else if(args[i].equals("-tl")) {
				testLabelFile = args[++i];
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
			else if(args[i].equals("-cut")) {
				cutBeginning = Integer.parseInt(args[++i]);
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
		boolean haveLowDimVis = numLowDimVis > 0;
		if(numBodyVis == 0) {
			embeddingHeight *= 2;
			humanHeight = 0;
		}
		if(!haveLowDimVis) {
			embeddingHeight = 0;
			humanHeight *= 2;
		}		
		
		JointTrajectoriesIsomap m = new JointTrajectoriesIsomap(height, humanHeight, embeddingHeight);
		
		m.kitchen.setDrawMeshes(drawMesh);
		if(background)
			m.kitchen.drawBackground("/usr/wiss/tenorth/work/owl/gram_ias_human.pl");
		
		LabelMapping labelMapping = null;
		
		// human body motion display
		if(numBodyVis > 0) {
			int[] bodyColors = new int[]{0xffff00ff, 0xffffff00, 0xff00ffff};	
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
				bps.setColor(bodyColors[i]);
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
				bps.setColor(bodyColors[i]);
				for(BodyPose p : d.ss.getDataPointSequence())
					bps.addPose(0.0f, 0.0f, null, p.getFloatArray());
				
				if(labelMapping == null)
					labelMapping = TUMKitchenDataset.getLabelMapping(d.labelField);
				
				Vector<String> labelSeq = new Vector<String>();
				for(Integer l : d.ss.getLabelSequence())
					labelSeq.add(labelMapping.getString(l));
				StringSequence labelDisplay = new StringSequence(labelSeq, m.kitchen.getWidth()-5, 40+16*i, Canvas.RIGHT, bodyColors[i], null);
				m.kitchen.add2DAnimated(labelDisplay);
				
				++i;
			}
		}

		if(labelMapping == null && labelMapFiles.size() > 0)
			labelMapping = new LabelMapping(new File(labelMapFiles.get(0)));
		
		if(testLabelFile != null) {
			Vector<String> labelSeq = new Vector<String>();
			BufferedReader r = new BufferedReader(new FileReader(testLabelFile));
			String line;
			while((line = r.readLine()) != null)			
				labelSeq.add(labelMapping.getString(Integer.parseInt(line)));
			m.kitchen.add2DAnimated(new StringSequence(labelSeq, m.kitchen.getWidth()-5, 40+16*numBodyVis, Canvas.RIGHT, 0xffff0000, null));			
		}
		
		// 2D/3D trajectory display		
		if(haveLowDimVis) {
			if(greyscale)
				m.isomap.setBackgroundColor(0xffffffff);
			m.isomap.centerTrajectory = center;
			
			Vector<Trajectory> trajectories = new Vector<Trajectory>();			
			int legendPosX = 5, legendPosY = 39;
			
			// collect trajectories
			for(int i = 0; i < embedFiles.size(); i++) {
				Trajectory traj = new Trajectory();
				traj.readAsc(new File(embedFiles.get(i)));
				if(i < labelFiles.size())
					traj.setLabels(new File(labelFiles.get(i)));
				trajectories.add(traj);				
			}
			for(DBDisplay d : dbDisplays) {
				if(d.jointIndex != null) {
					Trajectory traj = d.ss.toTrajectory(d.jointIndex);
					traj.setLabels(d.ss.getLabelSequence());
					trajectories.add(traj);
				}
			}
			
			// set some attributes and add to display
			int[] colors = new int[]{0xffffffff, 0xffffff00, 0xff00ffff};
			int i = 0;
			for(Trajectory traj : trajectories) {
				traj.lineColor = colors[i++ % colors.length];
				traj.setColorMode(!greyscale ? Trajectory.ColorMode.Color : Trajectory.ColorMode.Grayscale);
				m.isomap.addTrajectory(traj);
			}
			
			if(labelMapping != null) {
				m.isomap.add2D(new Legend(labelMapping.getStrings(), legendPosX, legendPosY, trajectories.get(0).getLabelColors()));
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
