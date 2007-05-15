package edu.tum.cs.bayesnets.learning;

import edu.ksu.cis.bnj.ver3.core.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import weka.core.Instance;
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
	 * @throws Exception
	 * @throws SQLException
	 */
	public void learn(String sqlQuery, String dbConnectString) throws Exception, SQLException {
		// connect to the database
        Connection conn = DriverManager.getConnection(dbConnectString);
      
        // execute the query
        Statement stmt = conn.createStatement(); 
        ResultSet rs = stmt.executeQuery(sqlQuery); 

		learn(rs);
	}

	/**
	 * completes the learning process, performing final processing.
	 * Only when this function has been called can you be sure that all the learnt 
	 * examples are reflected in the network's properties.
	 */
	public void finish() {
		if(!finished) {
			end_learning();
			finished = true;
		}
	}
	
	/**
	 * This function must be overridden by each subclass. It is called
	 * by finish to complete the learning process.
	 */
	protected abstract void end_learning();
	
	public abstract void learn(ResultSet rs) throws Exception;
}
