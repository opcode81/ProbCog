import java.util.Iterator;

import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.learning.CPTLearner;
import edu.tum.cs.bayesnets.learning.DomainLearner;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.fipm.FIPMData;
import edu.tum.cs.srldb.fipm.Player;


public class BN_Players {
	public static void main(String[] args) {
		
		try {
			FIPMData data = new FIPMData();
			data.fetch("good_formations=1");
			data.doClustering();
			
			// **** learn Bayesian network***
						
			// add additional information
			Iterator<Object> iter; 
			for(iter = data.players.iterator(); iter.hasNext();) {
				Player p = (Player)iter.next();
				p.addAttribute("teamSuccess", p.team.scoreDiff == 0 ? "draw" : (p.team.scoreDiff < 0 ? "lose": "win"));
			}						
			
			// learn domains
			System.out.println("learning Bayesian Network (Players)...");
			String[] directDomains = new String[]{"position", "ballActions", "dribblingShare", "shotShare", "passShare", "backPassShare", "attackPassShare", "crossPassShare", "succPassShare", "goalScored", "teamSuccess", "positionEx", "ballAcquisitions", "ballLosses", "speed"};
			DomainLearner domainLearner = new DomainLearner(new BeliefNetworkEx("networks/player3.xml"), directDomains, null, null, null);
			for(iter = data.players.iterator(); iter.hasNext();) {
				domainLearner.learn(iter.next().getAttributes());
			}			
			domainLearner.finish();			
			
			// learn CPTs
			CPTLearner cptLearner = new CPTLearner(domainLearner);
			for(iter = data.players.iterator(); iter.hasNext();) {
				cptLearner.learn(iter.next().getAttributes());
			}
			cptLearner.finish();
			
			cptLearner.getNetwork().saveXMLBIF("networks/player3-learnt.xml");
			cptLearner.getNetwork().show();			
			
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}

}
