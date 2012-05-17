/*
 * Created on Jun 2, 2010
 */
package probcog.hmm.latent;

/**
 * represents a state in dwell-time HMM
 * @author jain
 */
public class State {
	public Integer label;
	public Integer dwellTime;
	
	public State(int label, int dwelltime) {
		this.label = label;
		this.dwellTime = dwelltime;
	}
	
	@Override
	public int hashCode() {
		return (label.hashCode() << 16) + dwellTime.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof State) {
			State s = (State) o;
			return s.label == this.label && s.dwellTime == this.dwellTime;
		}
		return false;
	}		
	
	@Override
	public String toString() {
		return "(" + label + "/" + dwellTime + ")";
	}
	
	public double getTransitionProbability(IDwellTimeHMM<?> hmm, int toLabel) {
		if(toLabel == -1) // remaining in same segment
			return hmm.getDwellProbability(this.label, this.dwellTime);
		else
			return hmm.getTransitionProbability(this.label, this.dwellTime, toLabel);
	}
}