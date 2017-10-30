package probcog.srl.mln.inference;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import probcog.srl.Database;
import probcog.srl.mln.MarkovLogicNetwork;
import probcog.srl.mln.MarkovRandomField;

public class MCSATTest {
	
	static double[] resultProbabilities(List<InferenceResult> results) {
		double[] probs = new double[results.size()];
		int i = 0;
		for (InferenceResult result : results) {
			probs[i++] = result.value;
		}
		return probs;
	}
	
	@Test
	public void test() throws Exception {
		MarkovLogicNetwork mln = new MarkovLogicNetwork("src/test/resources/models/smokers/wts.smoking.mln");
		Database db = new Database(mln);
		db.readMLNDB("src/test/resources/models/smokers/smoking-test-peopleonly.db");
		MarkovRandomField mrf = mln.ground(db);
		MCSAT mcsat = new MCSAT(mrf);
		mcsat.setParameterByName("maxSteps", 10000);
		mcsat.setParameterByName("verbose", false);
		mcsat.setParameterByName("random", new Random(10L));
		List<InferenceResult> results = mcsat.infer(Arrays.asList("Smokes", "Cancer"));
		// exact results: [0.3537297423387627, 0.3537297423387627, 0.5902867352545694, 0.5902867352545694]
		double[] expectedResults = {0.4093, 0.407, 0.5842, 0.5805};
		double[] actualResults = resultProbabilities(results);
		System.out.println(Arrays.toString(actualResults));
		Assert.assertArrayEquals(expectedResults, actualResults, 0.01);
	}
}
