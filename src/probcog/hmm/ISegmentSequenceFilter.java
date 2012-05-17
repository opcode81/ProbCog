/*
 * Created on Jun 10, 2010
 */
package probcog.hmm;

public interface ISegmentSequenceFilter<T> {
	public T apply(SegmentSequence<T> seq, int i);
}
