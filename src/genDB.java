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
				System.out.println("\ngenDB - a database generator");
				System.out.println("\n  usage: genDB [options] <Jython generator script> <output base filename> [parameters to pass on to generator]\n" +
						             "           -m   output MLN database (.db)\n" +
						             "           -b   output BLOG database (.blogdb)\n" +
						             "           -p   output Proximity database (.proxdb.xml)\n" +
						             "           -s   print read database structure to stdout\n" + 
						             "           -bm  output basic MLN model (.basic.mln)\n" +
						             "           -bb  output basic BLN model declarations (.basic.blnd)\n\n" +
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

			// read args
			boolean writeBasicBLND = false, writeBasicMLN = false, writeBLOGDB = false, writeMLNDB = false, writeProxDB = false;
			boolean printDB = false;
			int i = 0; 
			for(; i < args.length; i++) {
				if(args[i].equals("-m"))
					writeMLNDB = true;
				else if(args[i].equals("-b"))
					writeBLOGDB = true;
				else if(args[i].equals("-p"))
					writeProxDB = true;
				else if(args[i].equals("-s"))
					printDB = true;
				else if(args[i].equals("-bm"))
					writeBasicMLN = true;
				else if(args[i].equals("-bb"))
					writeBasicBLND = true;
				else 
					break;
			}			
			
			jython.exec("import sys");
			String jythonscript = args[i++];
			jython.exec("sys.argv.append('" + jythonscript + "')");
			String outfilename = args[i++];
			for(; i < args.length; i++) {
				jython.exec("sys.argv.append('" + args[i] + "')");
			}
			
			jython.execfile(jythonscript);
			PyObject dbObj = jython.get("db");
			if(dbObj == null) {
				System.err.println("Error: Generator script does not define 'db' object!");
				return;
			}
			Database db = (Database) dbObj.__tojava__(Database.class);
			db.check();
			
			if(printDB)
				db.printData();
			
			if(writeMLNDB)
				db.writeMLNDatabase(new PrintStream(new java.io.File(outfilename + ".db")));
			if(writeBLOGDB)
				db.writeBLOGDatabase(new PrintStream(new java.io.File(outfilename + ".blogdb")));
			if(writeProxDB)
				db.writeProximityDatabase(new PrintStream(new java.io.File(outfilename + ".proxdb.xml")));
			if(writeBasicMLN)
				db.writeBasicMLN(new PrintStream(new File(outfilename + ".basic.mln")));
			if(writeBasicBLND)
				db.getDataDictionary().writeBasicBLOGModel(new PrintStream(new File(outfilename + ".basic.blnd")));
			
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
