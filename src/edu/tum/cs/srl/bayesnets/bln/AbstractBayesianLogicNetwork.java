package edu.tum.cs.srl.bayesnets.bln;

import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;

public abstract class AbstractBayesianLogicNetwork {
	public RelationalBeliefNetwork rbn;
	public String logicFile;
	
	public AbstractBayesianLogicNetwork(RelationalBeliefNetwork rbn, String logicFile) {
		this.rbn = rbn;
		this.logicFile = logicFile;
	}
}
