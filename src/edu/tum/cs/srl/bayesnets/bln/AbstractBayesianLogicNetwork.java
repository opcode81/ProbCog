package edu.tum.cs.srl.bayesnets.bln;


import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.bayesnets.ABLModel;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;

public abstract class AbstractBayesianLogicNetwork extends ABLModel implements IParameterHandler {
	public RelationalBeliefNetwork rbn;
	public File logicFile;
	protected ParameterHandler paramHandler;
	/**
	 * whether to allow that some nodes aren't instantiated because no fragment is applicable (simply skip them if this is true)
	 */
	protected boolean allowPartialInstantiation;
	
	/**
	 * @param declsFile
	 * @param networkFile
	 * @param logicFile may be null
	 * @throws Exception
	 */
	public AbstractBayesianLogicNetwork(String declsFile, String networkFile, String logicFile) throws Exception {
		super(declsFile, networkFile); // reads declarations
		if(logicFile != null)
			setConstraintsFile(new File(logicFile));
		this.paramHandler = new ParameterHandler(this);
		this.rbn = this;
		initKB();
	}
	
	public AbstractBayesianLogicNetwork(String declsFile) throws Exception {
		super(declsFile); // reads declarations
		this.paramHandler = new ParameterHandler(this);
		this.rbn = this;
		initKB();
	}
	
	public void setAllowPartialInstantiation(boolean allow) {
		this.allowPartialInstantiation = allow;
	}

	protected abstract void initKB() throws Exception;
	
	public abstract AbstractGroundBLN ground(Database db) throws Exception;
	
	protected void setConstraintsFile(File f) {
		if(logicFile != null && !logicFile.getAbsoluteFile().equals(f.getAbsoluteFile())) // if we already have another constraints file, then issue a warning
			System.err.println("Notice: Previously declared constraints file " + logicFile + " is overridden by " + f);					
		logicFile = f;
	}
	
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	@Override
	public boolean readDeclaration(String line) throws Exception {
		if(super.readDeclaration(line))
			return true;
		// constraints file reference
		if(line.startsWith("constraints")) {
			Pattern pat = Pattern.compile("constraints\\s+([^;\\s]+)\\s*;?");
			Matcher matcher = pat.matcher(line);			
			if(matcher.matches()) {
				String filename = matcher.group(1);
				File f = findReferencedFile(filename);
				if(f == null)
					throw new Exception("Declared constraints file " + filename + " could not be found");					
				setConstraintsFile(f);
				return true;
			}
		}
		return false;
	}
}
