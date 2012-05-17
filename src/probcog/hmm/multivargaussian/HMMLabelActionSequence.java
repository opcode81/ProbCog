package probcog.hmm.multivargaussian;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import probcog.analysis.actionrecognition.mocap.BodyPose;
import probcog.analysis.actionrecognition.mocap.BodyPoseSegmentSequence;
import probcog.hmm.Segment;
import probcog.hmm.SegmentSequence;

import be.ac.ulg.montefiore.run.distributions.SimpleMatrix;
import be.ac.ulg.montefiore.run.jahmm.ObservationVector;
import be.ac.ulg.montefiore.run.jahmm.OpdfMultiGaussian;
import edu.tum.cs.util.datastruct.Map2List;
import edu.tum.cs.util.datastruct.MultiIterator;

/*
 * Created on Oct 8, 2009
 */
public class HMMLabelActionSequence {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		BodyPoseSegmentSequence seq1 = new BodyPoseSegmentSequence("s1");
		seq1.readAsc(new File("sttNewFlorian1Joints.asc"), new File("sttNewFlorian1Joints_lAll.asc"));
		
		ObservationModel obsModel = new ObservationModel(5);
		obsModel.train2(seq1);
		
		BodyPoseSegmentSequence seq2 = new BodyPoseSegmentSequence("s1");
		seq2.readAsc(new File("sttNewFlorian2Joints.asc"), new File("sttNewFlorian2Joints_lAll.asc"));
		for(Segment<BodyPose> seg : seq2) {
			Integer trueClass = seg.label;
			double bestll = Double.MIN_VALUE;
			Integer bestClass = -1;
			for(Integer cls : obsModel.getLabels()) {
				seg.label = cls;
				double ll = obsModel.getSegmentProbability(seg);
				if(ll > bestll) {
					bestll = ll;
					bestClass = cls;
				}
			}
			System.out.printf("segment: prediction = %d, truth = %d\n", bestClass, trueClass);
		}
	}
	
	public static class ObservationModel {
		HashMap<Integer, OpdfMultiGaussian[]> gaussians;
		int steps;
		
		public ObservationModel(int steps) {
			gaussians = new HashMap<Integer, OpdfMultiGaussian[]>();
			this.steps = steps;
		}
		
		public Set<Integer> getLabels() {
			return gaussians.keySet();
		}
		
		public void train(Iterable<SegmentSequence<BodyPose>> trainingData) {
			MultiIterator<Segment<BodyPose>> mi = new MultiIterator<Segment<BodyPose>>();
			for(SegmentSequence<BodyPose> seq : trainingData) {
				mi.add(seq);
			}
			train2(mi);
		}
		
		public void train2(Iterable<Segment<BodyPose>> trainingData) {
			// collect data
			Map2List<Integer, Segment<BodyPose>> m = new Map2List<Integer, Segment<BodyPose>>(); 
			for(Segment<BodyPose> seg : trainingData) {
				m.add(seg.label, seg);
			}
			// build gaussians
			for(Entry<Integer,Vector<Segment<BodyPose>>> e : m.entrySet()) {
				Integer label = e.getKey();
				OpdfMultiGaussian[] gaussians = new OpdfMultiGaussian[steps]; 
				for(int i = 0; i < steps; i++) {
					double frac = (double)i/(steps-1);
					Vector<ObservationVector> v = new Vector<ObservationVector>();
					for(Segment<BodyPose> seg : e.getValue()) {
						v.add(interpolate(seg, frac));
					}
					gaussians[i] = new OpdfMultiGaussian(v.get(0).dimension());
					gaussians[i].fit(v);
				}
				this.gaussians.put(label, gaussians);
			}
		}
		
		/**
		 * returns a vector for the given normalized position via interpolation 
		 * @param frac normalized position between 0 and 1
		 * @return
		 */
		protected static BodyPose interpolate(Segment<BodyPose> seg, double frac) {
			double idx = frac * (seg.size()-1);
			double floor = Math.floor(idx);
			if(idx == floor)			
				return seg.get((int)idx);
			// interpolate linearly
			BodyPose v1 = seg.get((int)floor);
			BodyPose v2 = seg.get((int)floor+1);
			double v1frac = 1.0-(idx-floor);
			double v2frac = 1.0-v1frac;			
			return new BodyPose(v1.times(v1frac).plus(v2.times(v2frac)).values());
		}

		public static String matlabMatrix(double[][] m) {
			StringBuffer s = new StringBuffer("[");		
			for (int r = 0; r < SimpleMatrix.nbRows(m); r++) {
				for (int c = 0; c < SimpleMatrix.nbColumns(m); c++)
					s.append(" " + m[r][c]);
				
				s.append("; ");
			}
			s.append(']');
			return s.toString();		
		}
		
		public double getProbability(ObservationVector ov, Integer label, double t) {
			OpdfMultiGaussian[] gaussians = this.gaussians.get(label);
			double idx = t * (this.steps-1);
			double floor = Math.floor(idx);
			if(idx == floor) {
				String sCov = matlabMatrix(gaussians[(int)idx].covariance());
				System.out.println(sCov);
				return gaussians[(int)idx].probability(ov);
			}
			// obtain Gaussian via linear interpolation			
			OpdfMultiGaussian g1 = gaussians[(int)floor];
			OpdfMultiGaussian g2 = gaussians[(int)floor+1];
			double[] g1mean = g1.mean(), g2mean = g2.mean();
			double[][] g1covar = g1.covariance(), g2covar = g2.covariance();
			double g1frac = 1.0-(idx-floor);
			double g2frac = 1.0-g1frac;
			int dim = g1.dimension();
			double[] mean = new double[dim];
			double[][] covar = new double[dim][dim];
			for(int i = 0; i < dim; i++) {
				mean[i] = g1mean[i] * g1frac + g2mean[i] * g2frac;
				for(int j = i; j < dim; j++)
					covar[i][j] = covar[j][i] = g1covar[i][j] * g1frac * g1frac + g2covar[i][j] * g2frac * g2frac;
			}
			OpdfMultiGaussian g = new OpdfMultiGaussian(mean, covar);
			return g.probability(ov);
		}
		
		public double getSegmentProbability(Segment<BodyPose> seg) {
			double l = 0.0;
			for(int i = 0; i < seg.size(); i++)
				l += Math.log(this.getProbability(seg.get(i), seg.label, (double)i/(seg.size()-1)));
			return l;
		}
	}

}
