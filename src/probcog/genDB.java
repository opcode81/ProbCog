package probcog;
import java.io.File;
import java.io.PrintStream;
import java.util.Properties;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import probcog.srldb.Database;

public class genDB {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			boolean writeBasicBLND = false, writeBasicMLN = false, writeBLOGDB = false, writeMLNDB = false, writeProxDB = false;
			boolean printDB = false;
			int numRuns = 1;
			int i = 0;

			boolean abort = false;
			if(args.length < 3) {
				abort = true;
			}
			else {
				// read args
				int tasks = 0;
				for(; i < args.length; i++) {
					if(args[i].equals("-m")) {
						writeMLNDB = true;
						++tasks;
					}
					else if(args[i].equals("-b")) {
						writeBLOGDB = true;
						++tasks;
					}
					else if(args[i].equals("-p")) {
						writeProxDB = true;
						++tasks;
					}
					else if(args[i].equals("-s")) {
						printDB = true;
						++tasks;
					}
					else if(args[i].equals("-bm")) {
						writeBasicMLN = true;
						++tasks;
					}
					else if(args[i].equals("-bb")) {
						writeBasicBLND = true;
						++tasks;
					}
					else if(args[i].equals("-multi")) {
						numRuns = Integer.parseInt(args[++i]);
					}
					else 
						break;
				}
				if(tasks == 0) {
					abort = true;
					System.err.println("\nError: No tasks were specified. Add at least one option.\n");
				}
			}			

			if(abort) {
				System.out.println("\ngenDB - a database generator");
				System.out.println("\n  usage: genDB [options] <Jython generator script> <output base filename> [parameters to pass on to generator]\n" +
						             "           -m         output MLN database (.db)\n" +
						             "           -b         output BLOG database (.blogdb)\n" +
						             "           -p         output Proximity database (.proxdb.xml)\n" +
						             "           -s         print read database structure to stdout\n" + 
						             "           -bm        output basic MLN model (.basic.mln)\n" +
						             "           -bb        output basic BLN model declarations (.basic.blnd)\n" +
						             "           -multi N   create N databases with the given parameters (appending numbers to the base filename)\n\n" +
						             "         The Jython script must create a Database object named 'db' in the global scope.\n");
				return;
			}
			
			String jythonscript = args[i++];
			String baseOutfilename = args[i++];
			int firstScriptArgIndex = i;
			
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
			
			int numDigits = (int)Math.ceil(Math.log10((double)numRuns));
			String multiOutfileFormat = String.format("%s%%0%dd", baseOutfilename, numDigits);			
			
			for(int run = 1; run <= numRuns; run++) {
				if(numRuns > 1)
					System.out.printf("gerating database %d/%d...", run, numRuns);
				PythonInterpreter jython = new PythonInterpreter();			
				jython.exec("import sys");				
				jython.exec("sys.argv = ['" + jythonscript + "']");				
				for(int j = firstScriptArgIndex; j < args.length; j++) {
					jython.exec("sys.argv.append('" + args[j] + "')");
				}
				
				jython.execfile(jythonscript);
				PyObject dbObj = jython.get("db");
				if(dbObj == null) {
					throw new Exception("\nGenerator script does not define 'db' object!");
				}
				Database db = (Database) dbObj.__tojava__(Database.class);
				db.check();
				
				if(printDB)
					db.printData();
				
				String outfilename;
				if(numRuns == 1)
					outfilename = baseOutfilename;
				else
					outfilename = String.format(multiOutfileFormat, run); 
				
				if(writeMLNDB) {
					File file = new java.io.File(outfilename + ".db");
					System.out.println("Writing MLN database to " + file);
					db.writeMLNDatabase(new PrintStream(file));
				}
				if(writeBLOGDB) {
					File file = new java.io.File(outfilename + ".blogdb");
					System.out.println("Writing BLN database to " + file);
					db.writeBLOGDatabase(new PrintStream(file));
				}
				if(writeProxDB) {
					File file = new java.io.File(outfilename + ".proxdb.xml");
					System.out.println("Writing Proximity database to " + file);
					db.writeProximityDatabase(new PrintStream(file));
				}
				if(writeBasicMLN) {
					File file = new File(outfilename + ".basic.mln");
					System.out.println("Writing basic MLN declarations to " + file);
					db.writeBasicMLN(new PrintStream(file));
				}
				if(writeBasicBLND) {
					File file = new File(outfilename + ".basic.blnd");
					System.out.println("Writing basic BLN declarations to " + file);
					db.getDataDictionary().writeBasicBLOGModel(new PrintStream(file));
				}
			}
			
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
