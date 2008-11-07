import java.awt.Dimension;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;

import de.tum.in.fipm.kipm.gui.visualisation.JointTrajectoriesIsomap;


public class TrajVis {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws NumberFormatException 
	 */
	public static void main(String[] args) throws NumberFormatException, IOException {		
			if(args.length != 2) {
				System.out.println("usage: TrajVis <joint trajectories ascii file> <3d embedding ascii file>");
				return;
			}
			JointTrajectoriesIsomap m = new JointTrajectoriesIsomap("kitchen_data/pointcloud.vtk");
			String jointsFile = args[0]; // "/usr/wiss/jain/work/code/GP/sttFlorianJoints.asc";
			String embeddingFile = args[1]; // "/usr/wiss/jain/work/code/GP/sttFlorianJointsLatent.asc";
			m.kitchen.readTrajectoryData(new File(jointsFile));
			m.isomap.readTrajectory(new File(embeddingFile));
			JFrame frame = new JFrame();
			frame.setSize(new Dimension(820, 1055));
			frame.add(m);
			frame.setVisible(true);
	}

}
