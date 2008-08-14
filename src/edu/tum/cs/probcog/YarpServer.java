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
	
	/**
	 * @param modelPoolFile an XML file containing a pool of models to serve
	 * @param portName name under which to register the service
	 * @throws IOException 
	 * @throws ParseException
	 * @throws Exception
	 */
    public YarpServer(String modelPoolFile, String portName) throws IOException, ParseException, Exception {
		super(modelPoolFile);	
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
    
    protected void checkNumParams(YarpRpcCall call, int n) throws Exception {
    	if(call.size() != n)
    		throw new Exception(String.format("Call to procedure '%s' requires exactly %d parameters.", call.procName(), n));
    }
    
    /**
     * handles a remote procedure call
     * @param call
     * @return the reply to send on this server's port
     * @throws Exception
     */
	public YarpRpcReply handleCall(YarpRpcCall call) {
		try {
	        YarpRpcReply result = new YarpRpcReply(call);        
	        if(call.procName().equals("query")) {
	        	checkNumParams(call, 3);
	        	// perform inference
	        	String modelName = call.get(0).toString();
	        	Vector<String> queries = queriesFromTuples(readListOfLists(call.get(1).asList()));
	        	Vector<String[]> evidence = readListOfLists(call.get(2).asList());
	        	Vector<InferenceResult> results = query(modelName, queries, evidence);
	        	// write results
	        	Bottle listOfResults = result;
	        	for(InferenceResult r : results) {
	        		Bottle tuple = listOfResults.addList();
	        		tuple.addString(r.functionName);
	        		for(String param : r.params)
	        			tuple.addString(param);
	        		tuple.addDouble(r.probability);
	        	}
	        }
	        else if(call.procName().equals("getPredicates")) {
	        	checkNumParams(call, 1);
	        	String modelName = call.get(0).toString();
	        	writeListOfLists(getPredicates(modelName), result);
	        }
	        else if(call.procName().equals(("getDomains"))) {
	        	checkNumParams(call, 1);
	        	String modelName = call.get(0).toString();
	        	writeListOfLists(getDomains(modelName), result);
	        }
	        else
	        	throw new Exception("Don't know how to handle calls to method '" + call.procName() + "'");
	        return result;
		}
        catch(Exception e) {
        	e.printStackTrace();
        	YarpRpcReply result = new YarpRpcReply(call);
        	result.addString("error");
        	result.addString(e.getMessage());
        	return result;
        }
    }
	
	/**
	 * starts the server loop
	 */
	public void run() {           
        while(true) {
        	System.out.println("Waiting for call...");
            YarpRpcCall cl = port.read();
            port.reply(handleCall(cl));
        }
	}
	
	public void test(YarpRpcCall call) {
		System.out.println("\nTest Call: " + call.toString());
		System.out.println("Result: " + handleCall(call).toString());
	}
	
	/**
	 * performs a test by querying the tableSetting model, printing the results
	 */
	public void test() {
		// query test
    	Vector<String[]> query = readListOfLispTuples("((sitsAtIn ?PERSON ?SEATING-LOCATION M) (usesAnyIn ?PERSON ?UTENSIL M))");
    	Vector<String[]> evidence = readListOfLispTuples("((takesPartIn P1 M) (name P1 Anna) (takesPartIn P2 M) (name P2 Bert) (takesPartIn P3 M) (name P3 Dorothy) (mealT M Breakfast))");
    	YarpRpcCall cl = new YarpRpcCall("query");
    	cl.addString("tableSetting");
    	writeListOfLists(query, cl.addList());
    	writeListOfLists(evidence, cl.addList());
    	test(cl);
    	// getPredicates test
    	cl = new YarpRpcCall("getPredicates");
    	cl.addString("tableSetting");
    	test(cl);
    	// getDomains test
    	cl = new YarpRpcCall("getDomains");
    	cl.addString("tableSetting");
    	test(cl);
	}
    
    public static void main(String[] args) {
    	// initialize YARP-RPC
        System.loadLibrary("jyarprpc");
        Network.init();
        try {
        	System.out.println("\nProbCog YARP Server\n");
        	// check arguments
        	String modelPoolFile = null;
        	boolean doTest = false;
        	for(int i = 0; i < args.length; i++) {
        		if(args[i].charAt(0) == '-') {
	        		if(args[i].equals("-?")) {
	        			System.out.println("usage: YarpServer [-test] [XML file containing pool of models]\n");
	        			return;
	        		}
	        		else if(args[i].equals("-test"))
	        			doTest = true;
        		}
        		else
        			modelPoolFile = args[i];
        	}
        	if(modelPoolFile == null) {
        		modelPoolFile = "/usr/wiss/jain/work/code/SRLDB/models/models.xml";
        		System.out.println("No model pool file specified, defaulting to " + modelPoolFile);
        	}
        	// run server
			YarpServer server = new YarpServer(modelPoolFile, "/rpc/probcog");
			if(doTest)
				server.test();
			server.run();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
    }
}
