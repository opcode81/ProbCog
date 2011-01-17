import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Database.Variable;
import edu.tum.cs.srl.bayesnets.ABLModel;
import edu.tum.cs.srl.bayesnets.RelationalBeliefNetwork;
import edu.tum.cs.srl.bayesnets.bln.BayesianLogicNetwork;

/**
 * 
 */

/**
 * @author maierpa
 *
 */
public class blogdb2ergevid {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 5) {
			System.err.println("usage: blogdb2ergevid <bln decls> <bln fragments> <bln logic> <bn file> <blogdb file>");
			return;
		}
		
		PrintStream out = System.out;
		BeliefNetworkEx bn = new BeliefNetworkEx(args[3]);
		ABLModel abl = new ABLModel(args[0], args[1]);
		new BayesianLogicNetwork(abl, args[2]);
		Database db = new Database(abl);
		db.readBLOGDB(args[4]);
		
		Vector<String> ev = new Vector<String>();
		for(Variable var : db.getEntries()) {
			String varName = var.getName();
			int nodeIdx = bn.getNodeIndex(varName);
			Domain dom = bn.getNode(varName).getDomain();
			String value = var.value;
			int domIdx = -1;
			for(int i = 0; i < dom.getOrder(); i++) {
				if(dom.getName(i).equals(value)) {
					domIdx = i;
					break;
				}					
			}	
			ev.add(String.format(" %d %d", nodeIdx, domIdx));
		}
		out.println("/* Evidence */");
		out.println(ev.size());
		for(String l : ev)
			out.println(l);
	}

}
