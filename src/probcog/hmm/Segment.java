/*
 * Created on Apr 19, 2010
 */
package probcog.hmm;

import java.util.Vector;

import edu.tum.cs.util.datastruct.RepeatIterator;

/**
 * @author jain
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