/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
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
package probcog.bayesnets.learning;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.exception.ProbCogException;
import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;

/**
 * the abstract base class for other learners, which provides some basic functionality
 * @author Dominik Jain
  */
public abstract class Learner {

	/**
	 * the Bayesian network, for which we are learning something
	 */
	protected BeliefNetworkEx bn;
	
	/**
	 * the array of nodes in the network
	 */
	protected BeliefNode[] nodes;
	
	/**
	 * whether the learning process has been completed
	 */
	protected boolean finished = false;
	
	/**
	 * constructs a new learner from a BeliefNetworkEx object
	 * @param bn
	 */
	public Learner(BeliefNetworkEx bn) {
		this.bn = bn;
		nodes = this.bn.bn.getNodes();
	}
	
	/**
	 * constructs a new learner from a BeliefNetwork object
	 * @param bn
	 */
	public Learner(BeliefNetwork bn) {
		this.bn = new BeliefNetworkEx(bn);
		nodes = this.bn.bn.getNodes();
	}
	
	/**
	 * returns the network the learner is working on. If this function is called after
	 * the learning process has been completed (i.e. finish() has been called), the
	 * network will contain the final result of learning &ndash; the modified network. 
	 * The returned BeliefNetworkEx object can subsequently be used to save the network, 
	 * display it in the editor, etc.
	 */
	public BeliefNetworkEx getNetwork() {
		return bn;
	}

	/**
	 * learns all the examples in the result set that is obtained by executing the given
	 * query.
	 * @param sqlQuery			an SQL query to execute in order to obtain a table (result set) of examples
	 * @param dbConnectString	the connect string to establish a connection to the database
	 * @throws ProbCogException
	 */
	public void learn(String sqlQuery, String dbConnectString) throws ProbCogException {
		try {
			// connect to the database
	        Connection conn = DriverManager.getConnection(dbConnectString);
	      
	        // execute the query
	        Statement stmt = conn.createStatement(); 
	        ResultSet rs = stmt.executeQuery(sqlQuery);

	        learn(rs);
		}
		catch (SQLException e) {
			throw new ProbCogException(e);
		}
	}

	/**
	 * completes the learning process, performing final processing.
	 * Only when this function has been called can you be sure that all the learnt 
	 * examples are reflected in the network's properties.
	 * @throws ProbCogException 
	 */
	public void finish() throws ProbCogException {
		if(!finished) {
			end_learning();
			finished = true;
		}
	}
	
	/**
	 * This function must be overridden by each subclass. It is called
	 * by finish to complete the learning process.
	 */
	protected abstract void end_learning() throws ProbCogException;
	
	public abstract void learn(ResultSet rs) throws ProbCogException;
}
