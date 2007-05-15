import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.relational.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.learning.relational.CPTLearner;



public class BLOG_Kitchen {

	public static void main(String[] args) {
		try {
			String dir = "blog/kitchen/";
			RelationalBeliefNetwork bn = new RelationalBeliefNetwork(dir + "meal_utensil.xml");
			CPTLearner cptLearner = new CPTLearner(bn);
			cptLearner.learn(dir + "meal_utensil.blogdb");
			cptLearner.finish();
			PrintStream out = new PrintStream(new File(dir + "test.blog"));
			//bn.writeBLOGModel(out);
			bn.show();
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
}
