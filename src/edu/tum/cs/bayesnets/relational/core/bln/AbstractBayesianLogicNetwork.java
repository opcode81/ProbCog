package edu.tum.cs.bayesnets.relational.core.bln;

import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;

public abstract class AbstractBayesianLogicNetwork {
	public RelationalBeliefNetwork rbn;
	public String logicFile;
	
	public AbstractBayesianLogicNetwork(RelationalBeliefNetwork rbn, String logicFile) {
		this.rbn = rbn;
		this.logicFile = logicFile;
	}
}
