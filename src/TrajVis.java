import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JFrame;

import de.tum.in.fipm.kipm.gui.visualisation.JointTrajectoriesIsomap;
import de.tum.in.fipm.kipm.gui.visualisation.items.BodyPoseSequence;
import edu.tum.cs.vis.items.Trajectory;
import edu.tum.cs.vis.items.Legend;


public class TrajVis {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws NumberFormatException 
	 */
	public static void main(String[] args) throws NumberFormatException, IOException {
		boolean center = false;
		Vector<String> humanFiles = new Vector<String>();
		Vector<String> embedFiles = new Vector<String>();
		Vector<String> labelFiles = new Vector<String>();
		Vector<String> labelMapFiles = new Vector<String>();
		boolean drawMesh = true;
		boolean wire = false;
		boolean error = false;		
		
		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("-c")) {
				center = true;
			}
			else if(args[i].equals("-h")) {
				humanFiles.add(args[++i]);
			}
			else if(args[i].equals("-e")) {
				embedFiles.add(args[++i]);
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
			else {
				System.err.println("Error: unknown parameter " + args[i]);
				error = true;
			}
		}
		
		if(error) {
			System.out.println("usage: TrajVis [options]");
			System.out.println("  options: ");
			System.out.println("           -h <human data>     human joint data");
			System.out.println("           -e <embedded data>  low-dimensional (2D/3D) embedding");
			System.out.println("           -l <embedded data>  point labels");
			System.out.println("           -lm <embedded data>  label mapping");
			System.out.println("           -c   			   center embeddings around mean");
			System.out.println("           -nomesh			   do not draw kitchen mesh");	
			System.out.println("           -wire               draw human pose using lines only");	
			System.out.println("\n      -h and -e can be passed multiple times, -h at least once");
			return;
		}
		
		JointTrajectoriesIsomap m = new JointTrajectoriesIsomap("trajectoryData/pointcloud.vtk", 800, 400, 400);
		
		m.kitchen.setDrawMeshes(drawMesh);		
		
		if(!humanFiles.isEmpty()) {
			int[] colors = new int[]{0xffff00ff, 0xffffff00, 0xff00ffff};
			m.kitchen.readTrajectoryData(new File(humanFiles.get(0)));
			m.kitchen.bodyPoseSeq.setColor(colors[0]);
			m.kitchen.bodyPoseSeq.setMode(wire);
			for(int i = 1; i < humanFiles.size(); i++) {
				File f = new File(humanFiles.get(i));
				BodyPoseSequence bps = new BodyPoseSequence();
				bps.setMode(wire);
				bps.setColor(colors[i]);
				bps.readData(f);
				m.kitchen.addAnimated(bps);
			}
		}
		
		m.isomap.centerTrajectory = center;
		Trajectory traj = m.isomap.readTrajectory(new File(embedFiles.get(0)));
		if(labelFiles.size() > 0)
			traj.setLabels(new File(labelFiles.get(0)));
		Legend leg = new Legend();
		if(labelMapFiles.size() > 0){
			leg.loadLabels(new File(labelMapFiles.get(0)));
			m.isomap.add2D(leg);
		}
		int[] colors = new int[]{0xffffffff, 0xffffff00, 0xff00ffff};
		for(int i = 1; i < embedFiles.size(); i++) {
			traj = new Trajectory();
			traj.readAsc(new File(embedFiles.get(i)));
			traj.lineColor = colors[i % colors.length];
			if(center)
				traj.center();
			if(i < labelFiles.size())
				traj.setLabels(new File(labelFiles.get(i)));
			m.isomap.addAnimated(traj);
		}		
		//m.isomap.setHeight(400);
			
		JFrame frame = new JFrame();
		Dimension size = m.getPreferredSize();
		frame.setSize(new Dimension(size.width + 10, size.height + 30));
		frame.add(m);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);		
	}
}
