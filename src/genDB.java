import java.io.PrintStream;
import java.util.Properties;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.generator.AbstractDBGenerator;


public class genDB {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {			
			if(args.length != 3) {
				System.out.println("\n  usage: genDB <-m|-b> <Jython generator script> <output file>\n" +
						             "           -m   output MLN format\n" +
						             "           -b   output BLOG format\n\n" +
						             "         The Jython script must create a Database object named 'db' in the global scope.\n");
				return;
			}
			
			Properties props = new Properties();
			//props.put("python.path", "C:\\Progra~2\\jython-2.1\\Lib;datagen");
			//props.put("python.home", "C:\\Progra~2\\jython-2.1");
			props.put("python.path", "datagen:/usr/local/lehrstuhl/DIR/javaforlinux/jython2.1/Lib");
			PythonInterpreter.initialize(System.getProperties(), props, null);
			PythonInterpreter jython = new PythonInterpreter();
			jython.execfile(args[1]);
			Database db = (Database) jython.get("db").__tojava__(Database.class);
			db.check();
			PrintStream outDB = new PrintStream(new java.io.File(args[2]));
			if(args[0].equals("-m")) {
				db.outputMLNDatabase(outDB);
				//db.outputBasicMLN(new PrintStream(new java.io.File("mln/kitchen/meals_empty.mln")));
		    }
			else if(args[0].equals("-b"))
				db.outputBLOGDatabase(outDB);
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

}
