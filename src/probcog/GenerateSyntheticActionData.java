package probcog;
import java.util.*;
import java.io.*;

/**
 * @author tenorth
 */
public class GenerateSyntheticActionData {

	protected Random rand;
	
	GenerateSyntheticActionData() {
		rand = new Random(375957361);
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {

		GenerateSyntheticActionData dataGen = new GenerateSyntheticActionData();
		
		String dataset = "twoPlans_diffActions_noisy0.1";
		new File("data/"+dataset).mkdir();
		
		for(int num : new int[]{5, 10, 15, 20, 25, 30, 35, 40, 45, 50}) {
		
			new File("data/"+dataset+"/train_"+num).mkdir();
			new File("data/"+dataset+"/test_"+num).mkdir();
			
			dataGen.writeDataToFile(dataGen.generateExample1Tree(), "data/"+dataset+"/train_"+num+"/synth1_", num, 0, 0.1, 5);
			dataGen.writeDataToFile(dataGen.generateExample2Tree(), "data/"+dataset+"/train_"+num+"/synth2_", num, num, 0.1, 5);
			
	//		dataGen.writeDataToFile(dataGen.generateExample1Tree(), "data/"+dataset+"/test_"+num+"/synth1_", 1, 0, 0.1, 5);
	//		dataGen.writeDataToFile(dataGen.generateExample2Tree(), "data/"+dataset+"/test_"+num+"/synth2_", 1, 1, 0.1, 5);
			
		}
	}


	private void writeDataToFile(PartialOrderNode tree, String filename, int numSamples, int startNum, double noiseProb, int numNoiseAct) throws IOException {
		
		for(int i=startNum;i<startNum+numSamples;i++) {
			
			ArrayList<PartialOrderNode> data = this.sampleFromTree(tree, noiseProb, numNoiseAct);
			Writer output = new BufferedWriter(new FileWriter(new File(filename+(i-startNum)+".dat")));
		    try {
		      for(PartialOrderNode p : data)
		    	  output.write( p.toString() + "\n" );
		    }
		    finally {
		      output.close();
		    }
		}
	}

	
	ArrayList<PartialOrderNode> sampleFromTree(PartialOrderNode root, double noiseProb, int numNoiseAct) {
	
		ArrayList<PartialOrderNode> res = new ArrayList<PartialOrderNode>();
		ArrayList<PartialOrderNode> possibleNextNodes = new ArrayList<PartialOrderNode>();
		
		// create a set of noise actions
		ArrayList<PartialOrderNode> noiseNodes = new ArrayList<PartialOrderNode>();
		for(int i=0;i<numNoiseAct;i++) {
			noiseNodes.add(new PartialOrderNode("x"+i));
		}
		
		
		possibleNextNodes.add(root);		
		while(!possibleNextNodes.isEmpty()) {
		
			// with a probability of noiseProb, randomly draw one of the noise nodes
			// noise nodes can occur multiple times, so they are not removed from the list
			if(rand.nextDouble()<noiseProb) {
				PartialOrderNode drawn = noiseNodes.get(rand.nextInt(noiseNodes.size()));
				res.add(0,drawn);
			}
			
			// draw a random node out of the set of possible ones
			PartialOrderNode drawn = possibleNextNodes.get(rand.nextInt(possibleNextNodes.size()));
			
			// add it to the front of the res sequence
			res.add(0,drawn);
			
			// add the children to the list of possible nodes
			for(PartialOrderNode c : drawn.getPred()) {
				possibleNextNodes.add(c);				
			}
			possibleNextNodes.remove(drawn);
		}
		return res;
	}
	
	
	PartialOrderNode generateExample1Tree() {

		//
		//  n1  n3
		//  |   |
		//  n2  n4
		//   \  /
		//    n5  n6
		//     \  /
		//      n7
		//      |
		//      n8
		//
		
		PartialOrderNode n1 = new PartialOrderNode("n1"); 
		PartialOrderNode n2 = new PartialOrderNode("n2");
		PartialOrderNode n3 = new PartialOrderNode("n3");
		PartialOrderNode n4 = new PartialOrderNode("n4");
		PartialOrderNode n5 = new PartialOrderNode("n5");
		PartialOrderNode n6 = new PartialOrderNode("n6");
		PartialOrderNode n7 = new PartialOrderNode("n7");
		PartialOrderNode n8 = new PartialOrderNode("n8");
		
		n2.addPred(n1);		n4.addPred(n3);
		
		n5.addPred(n2);		n5.addPred(n4);
		
				n7.addPred(n5);		n7.addPred(n6);
				
						n8.addPred(n7);
		return n8;
	}
	

	PartialOrderNode generateExample2Tree() {

		//
		//  nA  nD
		//  |   |
		//  nB  nE
		//  |   |
		//  nC  nF
		//   \  /
		//    nG
		//    |
		//    nH
		//
		
		PartialOrderNode nA = new PartialOrderNode("nA"); 
		PartialOrderNode nB = new PartialOrderNode("nB");
		PartialOrderNode nC = new PartialOrderNode("nC");
		PartialOrderNode nD = new PartialOrderNode("nD");
		PartialOrderNode nE = new PartialOrderNode("nE");
		PartialOrderNode nF = new PartialOrderNode("nF");
		PartialOrderNode nG = new PartialOrderNode("nG");
		PartialOrderNode nH = new PartialOrderNode("nH");

		nB.addPred(nA);nE.addPred(nD);
		nC.addPred(nB);nF.addPred(nE);
		nG.addPred(nC);nG.addPred(nF);
		nH.addPred(nG);

		return nH;
	}
	

	protected class PartialOrderNode {
		
		protected ArrayList<PartialOrderNode> pred;
		protected String name;

		PartialOrderNode() {
			this("");
		}
		
		public String toString() {
			return this.name;
		}
		
		PartialOrderNode(String _name) {
			pred = new ArrayList<PartialOrderNode>();
			name=_name;
		}

		void addPred(PartialOrderNode s) {
			this.pred.add(s);
		}
		
		void delPred(PartialOrderNode s) {
			this.pred.remove(s);
		}
		
		ArrayList<PartialOrderNode> getPred() {
			return this.pred;
		}
		
		boolean isPred(PartialOrderNode s) {
			return this.pred.contains(s);
		}
		
	}
	
}
