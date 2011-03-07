package edu.tum.cs.srl.bayesnets.bln;

import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;

public abstract class AbstractBayesianLogicNetwork implements IParameterHandler {
	public RelationalBeliefNetwork rbn;
	public String logicFile;
	protected ParameterHandler paramHandler;
	
	public AbstractBayesianLogicNetwork(RelationalBeliefNetwork rbn, String logicFile) {
		this.rbn = rbn;
		this.logicFile = logicFile;
		this.paramHandler = new ParameterHandler(this);
	}
	
	public AbstractBayesianLogicNetwork(RelationalBeliefNetwork rbn) {
		this(rbn, null);
	}
	
	public abstract AbstractGroundBLN ground(Database db) throws Exception;
	
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
}
