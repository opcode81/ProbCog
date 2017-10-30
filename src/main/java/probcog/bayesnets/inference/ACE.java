/*******************************************************************************
 * Copyright (C) 2011-2012 Dominik Jain, Paul Maier.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.bayesnets.inference;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.bayesnets.conversion.BNDB2Inst;
import probcog.bayesnets.core.BNDatabase;
import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.exception.ProbCogException;
import edu.tum.cs.util.FileUtil;

/**
 * A simple wrapper for the ACE2.0 inference engine (arithmetic circuits evaluation).
 * @author Dominik Jain
 */
public class ACE extends Sampler {
	protected File acePath = null;
	protected File bnFile, instFile;
	private String aceParams = "";
	protected double compileTime, evalTime;
	
	public ACE(BeliefNetworkEx bn) throws ProbCogException {	
		super(bn);
		paramHandler.add("acePath", "setAcePath");
		paramHandler.add("aceParams", "setAceParams");
	}
	
	public void setAceParams(String aceParams) {
		this.aceParams = aceParams;
	}
	
	public void setAcePath(String path) throws ProbCogException {
		this.acePath = new File(path);
		if(!acePath.exists() || !acePath.isDirectory())
			throw new ProbCogException("The given path " + path + " does not exist or is not a directory");
	}
	
	protected BufferedInputStream runAce(String command, String params) throws ProbCogException {
		String[] aParams = params.trim().split("\\s+");
		String[] cmd = new String[aParams.length+1];
		for(int i = 0; i < aParams.length; i++)
			cmd[i+1] = aParams[i];
		File cmdFile = new File(acePath + File.separator + command);
		if(!cmdFile.exists()) {
			cmdFile = new File(acePath + File.separator + command + ".bat");
			if(!cmdFile.exists())
				throw new ProbCogException("Could not find " + command + " (or .bat) in " + acePath);
		}
		cmd[0] = cmdFile.toString();
		System.out.println("  " + Arrays.toString(cmd));
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedInputStream is = new BufferedInputStream(p.getInputStream());
			p.waitFor();
			String error = FileUtil.readInputStreamAsString(p.getErrorStream());
			if(!error.isEmpty())
				throw new ProbCogException("Error running ACE: " + error);
			return is;
		}
		catch (IOException | InterruptedException e) {
			throw new ProbCogException(e);
		}
	}
	
	protected void _initialize() throws ProbCogException {
		if(acePath == null) 
			throw new ProbCogException("No ACE 2.0 path was given. This inference method requires ACE2.0 and the location at which it is installed must be configured");
		
		// save belief network as .xbif
		bnFile = new File("ace.tmp.xbif");
		this.bn.save(bnFile.getPath());
		
		// compile arithmetic circuit using ace compiler
		if(verbose) System.out.println("compiling arithmetic circuit...");
		if(verbose && !aceParams.isEmpty()) System.out.println("  ACE params: " + this.aceParams);
		
		BufferedInputStream is = runAce("compile", this.aceParams + " " + bnFile.getName());
		String compileOutput;
		try {
			compileOutput = FileUtil.readInputStreamAsString(is);
		} 
		catch (IOException e) {
			throw new ProbCogException(e);
		}
		if(debug)
			System.out.println(compileOutput);		
		Pattern p = Pattern.compile("(?:Compile|Complie) Time \\(s\\) : (.*?)$", Pattern.MULTILINE);
		Matcher m = p.matcher(compileOutput);
		if(m.find()) {
			compileTime = parseDouble(m.group(1));
			report(String.format("ACE compile time: %ss", compileTime));
		}
		
		// write evidence to .inst file
		instFile = new File("ace.tmp.inst");
		BNDB2Inst.convert(new BNDatabase(this.bn, this.evidenceDomainIndices), instFile);
	}
	
	@Override
	protected void _infer() throws ProbCogException {
		// run Ace inference
		if(verbose) System.out.println("evaluating...");
		BufferedInputStream is = runAce("evaluate", bnFile.getName() + " " + instFile.getName());		
		
		//NumberFormat format = NumberFormat.getInstance();
		
		// read running time
		String output;
		try {
			output = FileUtil.readInputStreamAsString(is);
		} 
		catch (IOException e) {
			throw new ProbCogException(e);
		}
		Pattern p = Pattern.compile("Total Inference Time \\(ms\\) : (\\d+)", Pattern.MULTILINE);
		Matcher m = p.matcher(output);
		if(m.find()) {
			evalTime = parseDouble(m.group(1))/*format.parse(m.group(1)).doubleValue()*/ / 1000.0;
			report(String.format("ACE evaluation time: %ss", evalTime));
		}
		
		// create output distribution
		SampledDistribution dist = createDistribution();
		File marginalsFile = new File(bnFile.getName() + ".marginals");
		if(verbose) System.out.println("reading results...");
		String results;
		try {
			results = FileUtil.readTextFile(marginalsFile);
		} 
		catch (IOException e) {
			throw new ProbCogException(e);
		}
		if(debug)
			System.out.println(results);
		String patFloat = "(?:\\d+([\\.,]\\d+)?(?:E[-\\d]+)?)";
		// * get probability of the evidence
		Pattern probEvid = Pattern.compile(String.format("p \\(e\\) = (%s)", patFloat));
		m = probEvid.matcher(results);
		if(!m.find())
			throw new ProbCogException("Could not find 'p (e)' in results");
		if(m.group(1).equals("0E0"))
			throw new ProbCogException("The probability of the evidence is 0");		
		Number numPE = parseDouble(m.group(1)); //format.parse(m.group(1));
		dist.Z = numPE.doubleValue();
		System.out.println("probability of the evidence: " + dist.Z);
		// * get posteriors
		Pattern patMarginal = Pattern.compile(String.format("p \\((.*?) \\| e\\) = \\[(%s(?:, %s)+)\\]", patFloat, patFloat)); 
		m = patMarginal.matcher(results);
		int cnt = 0;
		while(m.find()) {
			String varName = m.group(1);			
			String[] v = m.group(2).split(", ");
			int nodeIdx = this.getNodeIndex(bn.getNode(varName));
			if(v.length != dist.values[nodeIdx].length)
				throw new ProbCogException("Marginal vector length for '" + varName + "' incorrect");
			for(int i = 0; i < v.length; i++)
				// here, it doesn't use the locale, always a '.' in there
				dist.values[nodeIdx][i] = parseDouble(v[i]); //format.parse(v[i]).doubleValue();
			cnt++;
		}		
		System.out.println(cnt + " marginals read");
		((ImmediateDistributionBuilder)distributionBuilder).setDistribution(dist);
		
		// clean up
		new File(bnFile.getName() + ".ac").delete();
		new File(bnFile.getName() + ".lmap").delete();
		bnFile.delete();
		instFile.delete();
		marginalsFile.delete();
	}
	
	public double getAceCompileTime() {
		return compileTime;
	}
	
	public double getAceEvalTime() {
		return evalTime;
	}
	
	protected static double parseDouble(String s) {
		return Double.parseDouble(s.replace(',', '.'));
	}
	
	protected IDistributionBuilder createDistributionBuilder() {
		return new ImmediateDistributionBuilder();
	}
}
