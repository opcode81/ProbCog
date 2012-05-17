/*
 * Created on Jun 15, 2011
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package probcog.logic.sat.weighted;

/**
 * an interface for Maximum SAT
 * @author jain
 */
public interface IMaxSAT {
	public void run() throws Exception;
	public void setMaxSteps(int steps);
	public probcog.logic.PossibleWorld getBestState();
	public String getAlgorithmName();
}
