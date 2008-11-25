import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JFrame;

import de.tum.in.fipm.kipm.gui.visualisation.JointTrajectoriesIsomap;
import de.tum.in.fipm.kipm.gui.visualisation.items.BodyPoseSequence;


public class TrajVis {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws NumberFormatException 
	 */
	public static void main(String[] args) throws NumberFormatException, IOException {
		boolean center = false;
		Vector<String> files = new Vector<String>();
		
		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("-c")) {
				center = true;
			}
			else
				files.add(args[i]);
		}
		
		if(files.size() < 2) {
			System.out.println("usage: TrajVis <joint trajectories ascii file> <2d/3d embedding ascii file> [additional joints files]");
			System.out.println("  options: -c   center around mean coordinate");
			return;
		}
		
		JointTrajectoriesIsomap m = new JointTrajectoriesIsomap("trajectoryData/pointcloud.vtk");
		
		String jointsFile = files.get(0); // "/usr/wiss/jain/work/code/GP/sttFlorianJoints.asc";
		String embeddingFile = files.get(1); // "/usr/wiss/jain/work/code/GP/sttFlorianJointsLatent.asc";
		
		m.kitchen.readTrajectoryData(new File(jointsFile));
		for(int i = 2; i < files.size(); i++) {
			File f = new File(files.get(i));
			BodyPoseSequence bps = new BodyPoseSequence();
			bps.readData(f);
			m.kitchen.addAnimated(bps);
		}
		//m.kitchen.setWidth(800);
		//m.kitchen.setHeight(400);
		
		m.isomap.centerTrajectory = center;
		m.isomap.readTrajectory(new File(embeddingFile));
		//m.isomap.setWidth(800);
		//m.isomap.setHeight(400);
			
		JFrame frame = new JFrame();
		frame.setSize(new Dimension(820, 1055));
		frame.add(m);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);			
	}
}
