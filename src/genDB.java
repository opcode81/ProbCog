import java.io.File;
import java.io.PrintStream;
import java.util.Properties;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import edu.tum.cs.srldb.Database;


public class genDB {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {			
			if(args.length < 3) {
				System.out.println("\ngenDB: A database generator for MLNs and BLOG models.");
				System.out.println("\n  usage: genDB <-m|-b|-bm> <Jython generator script> <output file> [parameters to pass on to generator]\n" +
						             "           -m   output MLN format\n" +
						             "           -b   output BLOG format\n" +
						             "           -bm  output basic MLN\n\n" +
						             "         The Jython script must create a Database object named 'db' in the global scope.\n");
				return;
			}
			
			Properties props = new Properties();
			//props.put("python.path", "C:\\Progra~2\\jython-2.1\\Lib;datagen");
			//props.put("python.path", "/usr/wiss/jain/work/code/SRLDB/bin:/usr/wiss/jain/work/code/SRLDB/python");
			String jythonpath = System.getenv("JYTHONPATH");
			if(jythonpath == null) {
				System.err.println("Warning: JYTHONPATH environment variable not set. If modules such as 'datagen' cannot be imported, either manually set sys.path in your generator scripts to include the appropriate directories or set this variable to include ProbCog's 'python' directory.");
				jythonpath = "";
			}
			else
				jythonpath += File.pathSeparator;
			jythonpath += System.getProperty("java.class.path");
			props.put("python.path", jythonpath);
			Properties sysprops = System.getProperties();
			PythonInterpreter.initialize(sysprops, props, null);
			PythonInterpreter jython = new PythonInterpreter();
			
			jython.exec("import sys");
			jython.exec("sys.argv.append('" + args[1] + "')");
			for(int i = 3; i < args.length; i++) {
				jython.exec("sys.argv.append('" + args[i] + "')");
			}
			
			jython.execfile(args[1]);
			PyObject dbObj = jython.get("db");
			if(dbObj == null) {
				System.err.println("Error: Generator script does not define 'db' object!");
				return;
			}
			Database db = (Database) dbObj.__tojava__(Database.class);
			db.check();
			PrintStream outDB = new PrintStream(new java.io.File(args[2]));
			if(args[0].equals("-m"))
				db.writeMLNDatabase(outDB);
			else if(args[0].equals("-b"))
				db.writeBLOGDatabase(outDB);
			else if(args[0].equals("-bm"))
				db.writeBasicMLN(outDB);
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

}
