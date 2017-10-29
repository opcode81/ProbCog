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

import java.util.Iterator;
import java.util.Vector;

import edu.tum.cs.util.datastruct.Map2List;
import edu.tum.cs.util.datastruct.MultiIterator;

/**
 * @author Dominik Jain
*/
public class SegmentSequence<T> implements Iterable<Segment<T>> {
	protected String name;
	protected Vector<Segment<T>> seq = new Vector<Segment<T>>();
	protected Map2List<Integer, Segment<T>> entriesByLabel = new Map2List<Integer, Segment<T>>();
	
	/**
	 * for building
	 */
	int prevLabel = -1;
	Segment<T> currentSegment = null;

	public SegmentSequence(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public void add(Segment<T> s) {
		assert(s.label >= 0);
		seq.add(s);		
		entriesByLabel.add(s.label, s);
	}
	
	public void deleteSegments(Integer label) {
		for(Segment<?> s : entriesByLabel.remove(label))
			seq.remove(s);
	}

	/**
	 * for stepwise building of the segment sequence; adds the frame to the sequence, starting a new segment if the label changed
	 * @param label the label of the frame
	 * @param item the frame to add
	 */
	public void build(int label, T item) {
		assert(label >= 0);
		if(label != prevLabel) { 
			currentSegment = new Segment<T>(label);					
			add(currentSegment);
		}
		currentSegment.add(item);
		prevLabel = label;
	}
	
	/**
	 * manually ends the current segment, i.e. the next frame that is added with the build method will definitely be added to a new segment
	 */
	public void buildEndSegment() {
		prevLabel = -1;
	}

	public Iterator<Segment<T>> iterator() {			
		return this.seq.iterator();
	}
	
	public Vector<Segment<T>> getSegments(int label) {
		return entriesByLabel.get(label);
	}
	
	public Segment<T> get(int idx) {
		return seq.get(idx);
	}
	
	public int size() {
		return seq.size();
	}
	
	public Iterable<T> getDataPointSequence() {
		MultiIterator<T> mi = new MultiIterator<T>();
		for(Segment<T> s : seq)
			mi.add(s);
		return mi;
	}
	
	public Iterable<Integer> getLabelSequence() {
		MultiIterator<Integer> mi = new MultiIterator<Integer>();
		for(Segment<T> s : seq)
			mi.add(s.getLabels());
		return mi;
	}
	
	public int getNumDataPoints() {
		int n = 0;
		for(Segment<T> s : this.seq)
			n += s.size();
		return n;
	}
	
	public T getDataPoint(int index) {
		Iterator<Segment<T>> iter = iterator();
		while(iter.hasNext()) {
			Segment<T> seg = iter.next();
			if(index < seg.size())
				return seg.get(index);
			index -= seg.size();
		}
		throw new IllegalArgumentException("SegmentSequence has no element at index " + index);
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer("SegmentSequence '" + name + "' [ ");
		for(Segment<T> s: this.seq) {
			sb.append(s.label);
			sb.append(" ");
		}
		sb.append("]");
		return sb.toString();
	}
	
	public SegmentSequence<T> filter(ISegmentSequenceFilter<T> filter) {
		SegmentSequence<T> ret = new SegmentSequence<T>(this.name);
		int i = 0; 
		for(Segment<T> seg : this) {
			for(int j = 0; j < seg.size(); j++) {
				T newItem = filter.apply(this, i++);
				if(newItem == null)
					continue;
				ret.build(seg.label, newItem);
			}
		}
		return ret;
	}
}
