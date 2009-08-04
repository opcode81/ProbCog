package edu.tum.cs.classification;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map.Entry;

public class ConfusionMatrix<TClass> {
	HashMap<TClass, HashMap<TClass,Integer>> matrix;
	int instances = 0;
	int correct = 0;
	
	public ConfusionMatrix() {
		matrix = new HashMap<TClass, HashMap<TClass,Integer>>();
	}
	
	public void addCase(TClass classification, TClass groundTruth) {
		HashMap<TClass,Integer> list = matrix.get(classification);
		if(list == null) {
			list = new HashMap<TClass,Integer>();
			matrix.put(classification, list);
		}
		Integer cnt = list.get(groundTruth);
		if(cnt == null)
			cnt = 1;
		else
			cnt++;
		list.put(groundTruth, cnt);
		instances++;
		if(classification == groundTruth)
			correct++;
	}
	
	public void print() {
		PrintStream out = System.out;
		for(Entry<TClass, HashMap<TClass,Integer>> e : matrix.entrySet()) {
			out.printf("%s: %s\n", e.getKey().toString(), e.getValue().toString());
		}
		out.printf("correct: %.2f%% (%d/%d)", (double)correct/instances*100, correct, instances);
	}
}
