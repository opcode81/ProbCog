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
package probcog.hmm;

import java.util.Vector;

import edu.tum.cs.util.datastruct.RepeatIterator;

/**
 * @author Dominik Jain
*/
public class Segment<T> extends Vector<T> {

	private static final long serialVersionUID = 1L;
	
	public int label;
	
	public Segment(int label) {
		this.label = label;
	}
	
	public Iterable<Integer> getLabels() {
		return new RepeatIterator<Integer>(label, this.size()); 
	}
} 