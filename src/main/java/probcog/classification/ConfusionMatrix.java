/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain, Martin Schuster.
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
package probcog.classification;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Represents a simple confusion matrix for classification tasks.
 * @author Dominik Jain
 * @author Martin Schuster
 */
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
		if(classification.equals(groundTruth))
			correct++;
	}
	
	public void printMatrix() {
		PrintStream out = System.out;
		for(Entry<TClass, HashMap<TClass,Integer>> e : matrix.entrySet()) {
			out.printf("%s: %s\n", e.getKey().toString(), e.getValue().toString());
		}
		out.printf("correct: %.2f%% (%d/%d)", (double)correct/instances*100, correct, instances);
	}
}
