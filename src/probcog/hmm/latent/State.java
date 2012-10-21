/*******************************************************************************
 * Copyright (C) 2010-2012 Dominik Jain.
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
package probcog.hmm.latent;

/**
 * represents a state in dwell-time HMM
 * @author Dominik Jain
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