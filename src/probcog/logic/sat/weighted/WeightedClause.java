/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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
package probcog.logic.sat.weighted;


import probcog.logic.Formula;
import probcog.logic.sat.Clause;


/**
 * Represents a weighted clause.
 * @author Dominik Jain
 */
public class WeightedClause extends Clause {

    public double weight;
    /**
     * whether the clause is to be considered a hard constraint
     */
    public boolean isHard;

    /**
     * Constructor to instantiate a weighted clause
     * @param f
     * @param weight
     * @param hard
     * @throws java.lang.Exception
     */
    public WeightedClause(Formula f, double weight, boolean hard) throws Exception {
    	super(f);
        this.weight = weight;
        this.isHard = hard;        
    }

    public String toString() {
        return weight + " " + super.toString();
    }
}

