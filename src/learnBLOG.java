import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.relational.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.learning.relational.CPTLearner;



public class learnBLOG {

	public static void main(String[] args) {
		try {
			if(args.length < 3) {
				System.out.println("\n usage: learnBLOG <xml BIF file> <training database file> <output file> [-s]");
				return;
			}
			RelationalBeliefNetwork bn = new RelationalBeliefNetwork(args[0]);
			CPTLearner cptLearner = new CPTLearner(bn);
			cptLearner.learn(args[1]);
			cptLearner.finish();
			PrintStream out = new PrintStream(new File(args[2]));
			bn.writeBLOGModel(out);
			if(args.length >= 4 && args[3].equals("-s"))
				bn.show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
}
