package edu.tum.cs.probcog;

import java.io.IOException;
import java.util.Collection;
import java.util.Vector;

import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.yarprpc.Bottle;
import edu.tum.cs.yarprpc.Network;
import edu.tum.cs.yarprpc.YarpRpcCall;
import edu.tum.cs.yarprpc.YarpRpcReply;
import edu.tum.cs.yarprpc.YarpRpcServer;

public class YarpServer extends Server {
	
	/**
	 * the port through which communication takes place
	 */
	YarpRpcServer port;
	
    public YarpServer(String portName) throws IOException, ParseException, Exception {
		super();	
		port = new YarpRpcServer(portName);
	}

    /**
     * reads the contents of a bottle that is assumed to contain a list of lists of strings
     * @param listOfLists the bottle containing the data
     * @return a collection of string arrays
     */
    public static Vector<String[]> readListOfLists(Bottle listOfLists) {
    	Vector<String[]> ret = new Vector<String[]>();
    	for(int i = 0; i < listOfLists.size(); i++) {
    		Bottle list = listOfLists.get(i).asList();
    		String[] tuple = new String[list.size()];
    		for(int j = 0; j < list.size(); j++)
    			tuple[j] = list.get(j).toString();
    		ret.add(tuple);
    	}
    	return ret;
    }
    
    /**
     * writes the contents of a collection of String arrays to the given bottle as a list of lists
     * @param listOfLists the data to write
     * @param target the bottle to write to
     */
    public static void writeListOfLists(Collection<String[]> listOfLists, Bottle target) {
    	for(String[] l : listOfLists) {
    		Bottle b = target.addList();
    		for(String item : l)
    			b.addString(item);
    	}
    }
    
    /**
     * handles an RPC call
     * @param call
     * @return the reply to send on this server's port
     * @throws Exception
     */
	public YarpRpcReply handleCall(YarpRpcCall call) throws Exception {
        YarpRpcReply result = new YarpRpcReply( call );
        
        if(call.procName().equals("query")) {
        	// perform inference
        	Vector<String> queries = queriesFromTuples(readListOfLists(call.get(0).asList()));
        	Vector<String[]> evidence = readListOfLists(call.get(1).asList());
        	Vector<InferenceResult> results = query("tableSetting", queries, evidence);
        	// write results
        	Bottle listOfResults = result.addList();
        	for(InferenceResult r : results) {
        		Bottle tuple = listOfResults.addList();
        		tuple.addString(r.functionName);
        		for(String param : r.params)
        			tuple.addString(param);
        		tuple.addDouble(r.probability);
        	}
        }
        else {
        	System.err.println("Error: Don't know how to handle calls to method '" + call.procName() + "'");
            result.addString("error");
        }
        return result;
    }
	
	public void run() throws Exception {           
        boolean doTest = true;
        if(doTest) {
        	Vector<String[]> query = readListOfLispTuples("((sitsAtIn ?PERSON ?SEATING-LOCATION M) (usesAnyIn ?PERSON ?UTENSIL M))");
        	Vector<String[]> evidence = readListOfLispTuples("((takesPartIn P1 M) (name P1 Anna) (takesPartIn P2 M) (name P2 Bert) (takesPartIn P3 M) (name P3 Dorothy) (mealT M Breakfast))");
        	YarpRpcCall cl = new YarpRpcCall("query");
        	writeListOfLists(query, cl.addList());
        	writeListOfLists(evidence, cl.addList());
        	YarpRpcReply reply = handleCall(cl);
        	System.out.println("Test result: " + reply.toString());
        }
        while(true) {
        	System.out.println("Waiting for call...");
            YarpRpcCall cl = port.read();
            port.reply(handleCall(cl));
        }
	}
    
    public static void main(String[] args) {
        System.loadLibrary("jyarprpc");
        Network.init();
        try {
			new YarpServer("/rpc/probcog").run();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
    }
}
