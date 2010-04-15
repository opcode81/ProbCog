import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JFrame;

import de.tum.in.fipm.kipm.gui.visualisation.JointTrajectoriesIsomap;
import de.tum.in.fipm.kipm.gui.visualisation.items.BodyPoseSequence;
import edu.tum.cs.util.datastruct.CollectionFilter;
import edu.tum.cs.vis.DrawableAnimated;
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
		boolean background = false;
		int minStep = 0;
		boolean greyscale = false;
		
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
		if(humanFiles.isEmpty() && embedFiles.isEmpty())
			error = true;
		
		System.out.println("TrajVis");
		if(error) {
			System.out.println("usage: TrajVis [options]");
			System.out.println("  options: ");
			System.out.println("           -h <human data>          human joint data");
			System.out.println("           -e <embedded data>       low-dimensional (2D/3D) embedding");
			System.out.println("           -l <label indices file>  point labels");
			System.out.println("           -lm <label names file>   label mapping");
			System.out.println("           -c                       center embeddings around mean");
			System.out.println("           -nomesh                  do not draw kitchen mesh");	
			System.out.println("           -wire                    draw human pose using lines only");
			System.out.println("           -bg                    	draw kitchen background");
			System.out.println("           -minStep <frame no.>     draw trajectory starting with given frame");
			System.out.println("           -bw     					black & white mode");
			System.out.println("\n      -h and -e can be passed multiple times; must pass one of them at least once");
			return;
		}
		
		int height = 1000;
		int humanHeight = height/2, embeddingHeight = height/2;
		if(humanFiles.isEmpty()) {
			embeddingHeight *= 2;
			humanHeight = 0;
		}
		if(embedFiles.isEmpty()) {
			embeddingHeight = 0;
			humanHeight *= 2;
		}
		JointTrajectoriesIsomap m = new JointTrajectoriesIsomap("trajectoryData/pointcloud.vtk", height, humanHeight, embeddingHeight);
		
		m.kitchen.setDrawMeshes(drawMesh);
		if(background)
			m.kitchen.drawBackground("/usr/wiss/tenorth/work/owl/gram_ias_human.pl");
		
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
		
		
		if(!embedFiles.isEmpty()) {
			if(greyscale)
				m.isomap.setBackgroundColor(0xffffffff);

			m.isomap.centerTrajectory = center;		
			Trajectory traj = m.isomap.readTrajectory(new File(embedFiles.get(0)));
			traj.colorMode = !greyscale ? Trajectory.ColorMode.Color : Trajectory.ColorMode.Grayscale; 
			
			if(labelFiles.size() > 0)
				traj.setLabels(new File(labelFiles.get(0)));		
			
			if(labelMapFiles.size() > 0) {
				Legend leg = new Legend(new File(labelMapFiles.get(0)), 5, 39);
				m.isomap.add2D(leg);
			}
			
			int[] colors = new int[]{0xffffffff, 0xffffff00, 0xff00ffff};
			for(int i = 1; i < embedFiles.size(); i++) {
				traj = new Trajectory();
				traj.colorMode = !greyscale ? Trajectory.ColorMode.Color : Trajectory.ColorMode.Grayscale;
				traj.readAsc(new File(embedFiles.get(i)));
				traj.lineColor = colors[i % colors.length];
				if(center)
					traj.center();
				if(i < labelFiles.size())
					traj.setLabels(new File(labelFiles.get(i)));
				m.isomap.addAnimated(traj);
			}		
			//m.isomap.setHeight(400);
			
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
