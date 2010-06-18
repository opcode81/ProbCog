import java.util.Vector;

import edu.tum.cs.analysis.actionrecognition.mocap.BodyPoseSegmentSequence;
import edu.tum.cs.analysis.actionrecognition.mocap.LabelMapping;
import edu.tum.cs.analysis.actionrecognition.mocap.TUMKitchenDataset;
import edu.tum.cs.clustering.multidim.MultiDimClusterer;
import edu.tum.cs.vis.TrajectoryApplet;
import edu.tum.cs.vis.items.Trajectory;

/*
 * Created on Jun 17, 2010
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

public class TrajVisSegments {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		String[] sequenceIDs = new String[]{"1-0", "1-1", "1-2", "1-3", "1-4"};
		String labelField = "righthand";
		String jointName = "HAR";
		boolean clustering = true; 
		int numClusters = 8;
		
		TrajectoryApplet canvas = new TrajectoryApplet();
		
		boolean relativeCoordinates = true;
		Vector<BodyPoseSegmentSequence> seqs = new Vector<BodyPoseSegmentSequence>();
		for(String webID : sequenceIDs) 
			seqs.add(new BodyPoseSegmentSequence(webID, relativeCoordinates, new String[]{jointName}, labelField));

		LabelMapping labmap = TUMKitchenDataset.getLabelMapping(labelField);

		int colors[] = new int[]{0xffff0000, 0xff00ff00};
		//String[] labels = new String[]{"reach", "put"};
		String[] labels = new String[]{"put"};		
		for(int i = 0; i < labels.length; i++) {
			MultiDimClusterer<?> clusterer = null;
			if(clustering)
				clusterer = BodyPoseSegmentSequence.getDataClusterer(labmap.getInt(labels[i]), seqs, numClusters);
			for(BodyPoseSegmentSequence ss : seqs) {
				Vector<Trajectory> trajs = ss.toTrajectories(0, labmap.getInt(labels[i]), clusterer);
				for(Trajectory traj : trajs) {
					if(clusterer == null)
						traj.setPointColor(colors[i]);					
					canvas.addTrajectory(traj);
				}
			}
		}
		
		canvas.runMain();
	}

}
