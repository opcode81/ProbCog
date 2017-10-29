/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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
package probcog.clustering;

import weka.clusterers.Cobweb;

/**
 * The Cobweb hierarchical clusterer as implemented in WEKA.
 * @author Dominik Jain
 */
public class HierarchicalClusterer extends probcog.clustering.BasicClusterer<Cobweb> {

	public HierarchicalClusterer() {
		super(new Cobweb());
	}
	
	public HierarchicalClusterer(Cobweb clusterer) {
		super(clusterer);
	}

}
