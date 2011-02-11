/*
 * Created on Jan 20, 2011
 */
package edu.tum.cs.bayesnets.inference;

import java.io.BufferedInputStream;
import java.io.File;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.bayesnets.conversion.BNDB2Inst;
import edu.tum.cs.bayesnets.core.BNDatabase;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.util.FileUtil;

/**
 * a simple wrapper for the ACE2.0 inference engine (arithmetic circuits evaluation)
 * @author jain
 */
public class ACE extends Sampler {
	protected File acePath = null;
	protected File bnFile, instFile;
	private String aceParams = "";
	protected double compileTime, evalTime;
	
	public ACE(BeliefNetworkEx bn) throws Exception {	
		super(bn);
		paramHandler.add("acePath", "setAcePath");
		paramHandler.add("aceParams", "setAceParams");
	}
	
	public void setAceParams(String aceParams) {
		this.aceParams = aceParams;
	}
	
	public void setAcePath(String path) throws Exception {
		this.acePath = new File(path);
		if(!acePath.exists() || !acePath.isDirectory())
			throw new Exception("The given path " + path + " does not exist or is not a directory");
	}
	
	protected BufferedInputStream runAce(String command, String params) throws Exception {
		String[] aParams = params.trim().split("\\s+");
		String[] cmd = new String[aParams.length+1];
		for(int i = 0; i < aParams.length; i++)
			cmd[i+1] = aParams[i];
		File cmdFile = new File(acePath + File.separator + command);
		if(!cmdFile.exists()) {
			cmdFile = new File(acePath + File.separator + command + ".bat");
			if(!cmdFile.exists())
				throw new Exception("Could not find " + command + " (or .bat) in " + acePath);
		}
		cmd[0] = cmdFile.toString();
		System.out.println("  " + Arrays.toString(cmd));
		Process p = Runtime.getRuntime().exec(cmd);
		BufferedInputStream is = new BufferedInputStream(p.getInputStream());
		p.waitFor();
		String error = FileUtil.readInputStreamAsString(p.getErrorStream());
		if(!error.isEmpty())
			throw new Exception("Error running ACE: " + error);
		return is;
	}
	
	protected void _initialize() throws Exception {
		if(acePath == null) 
			throw new Exception("No ACE 2.0 path was given. This inference method requires ACE2.0 and the location at which it is installed must be configured");
		
		// save belief network as .xbif
		bnFile = new File("ace.tmp.xbif");
		this.bn.save(bnFile.getPath());
		
		// compile arithmetic circuit using ace compiler
		if(verbose) System.out.println("compiling arithmetic circuit...");
		if(verbose && !aceParams.isEmpty()) System.out.println("  ACE params: " + this.aceParams);
		
		BufferedInputStream is = runAce("compile", this.aceParams + " " + bnFile.getName());
		String compileOutput = FileUtil.readInputStreamAsString(is);
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
	protected SampledDistribution _infer() throws Exception {
		// run Ace inference
		if(verbose) System.out.println("evaluating...");
		BufferedInputStream is = runAce("evaluate", bnFile.getName() + " " + instFile.getName());		
		
		NumberFormat format = NumberFormat.getInstance();
		
		// read running time
		String output = FileUtil.readInputStreamAsString(is);
		Pattern p = Pattern.compile("Total Inference Time \\(ms\\) : (\\d+)", Pattern.MULTILINE);
		Matcher m = p.matcher(output);
		if(m.find()) {
			evalTime = parseDouble(m.group(1))/*format.parse(m.group(1)).doubleValue()*/ / 1000.0;
			report(String.format("ACE evaluation time: %ss", evalTime));
		}
		
		// create output distribution
		File marginalsFile = new File(bnFile.getName() + ".marginals");
		if(verbose) System.out.println("reading results...");
		this.createDistribution();
		String results = FileUtil.readTextFile(marginalsFile);
		if(debug)
			System.out.println(results);
		String patFloat = "(?:\\d+([\\.,]\\d+)?(?:E[-\\d]+)?)";
		// * get probability of the evidence
		Pattern probEvid = Pattern.compile(String.format("p \\(e\\) = (%s)", patFloat));
		m = probEvid.matcher(results);
		if(!m.find())
			throw new Exception("Could not find 'p (e)' in results");
		if(m.group(1).equals("0E0"))
			throw new Exception("The probability of the evidence is 0");		
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
				throw new Exception("Marginal vector length for '" + varName + "' incorrect");
			for(int i = 0; i < v.length; i++)
				// here, it doesn't use the locale, always a '.' in there
				dist.values[nodeIdx][i] = parseDouble(v[i]); //format.parse(v[i]).doubleValue();
			cnt++;
		}		
		System.out.println(cnt + " marginals read");
		
		// clean up
		new File(bnFile.getName() + ".ac").delete();
		new File(bnFile.getName() + ".lmap").delete();
//		bnFile.delete();
//		instFile.delete();
//		marginalsFile.delete();
		
		return dist;
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
}
