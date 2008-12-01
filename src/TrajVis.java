import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JFrame;

import de.tum.in.fipm.kipm.gui.visualisation.JointTrajectoriesIsomap;
import de.tum.in.fipm.kipm.gui.visualisation.items.BodyPoseSequence;
import edu.tum.cs.vis.Trajectory;


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
			else
				error = true;
		}
		
		if(error) {
			System.out.println("usage: TrajVis [options]");
			System.out.println("  options: ");
			System.out.println("           -h <human data>     human joint data");
			System.out.println("           -e <embedded data>  low-dimensional (2D/3D) embedding");
			System.out.println("           -c   			   center embeddings around mean");
			System.out.println("\n      -h and -e can be passed multiple times");
			return;
		}
		
		JointTrajectoriesIsomap m = new JointTrajectoriesIsomap("trajectoryData/pointcloud.vtk");
		
		if(!humanFiles.isEmpty()) {
			int[] colors = new int[]{0xffff00ff, 0xffffff00, 0xff00ffff};
			m.kitchen.readTrajectoryData(new File(humanFiles.get(0)));
			m.kitchen.bodyPoseSeq.setColor(colors[0]);
			for(int i = 1; i < humanFiles.size(); i++) {
				File f = new File(humanFiles.get(i));
				BodyPoseSequence bps = new BodyPoseSequence();
				bps.setColor(colors[i]);
				bps.readData(f);
				m.kitchen.addAnimated(bps);
			}
		}
		//m.kitchen.setWidth(800);
		//m.kitchen.setHeight(400);
		
		m.isomap.centerTrajectory = center;
		m.isomap.readTrajectory(new File(embedFiles.get(0)));
		int[] colors = new int[]{0xffffffff, 0xffffff00, 0xff00ffff};
		for(int i = 1; i < embedFiles.size(); i++) {
			Trajectory traj = new Trajectory();
			traj.readAsc(new File(embedFiles.get(i)));
			traj.lineColor = colors[i % colors.length];
			if(center)
				traj.center();
			m.isomap.addAnimated(traj);
		}
		//m.isomap.setWidth(800);
		//m.isomap.setHeight(400);
			
		JFrame frame = new JFrame();
		frame.setSize(new Dimension(820, 1055));
		frame.add(m);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);			
	}
}
