import java.io.PrintStream;
import java.util.Properties;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.generator.AbstractDBGenerator;


public class KitchenMeals {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Properties props = new Properties();
			props.put("python.path", "C:\\Progra~2\\jython-2.1\\Lib;datagen");
			props.put("python.home", "C:\\Progra~2\\jython-2.1");
			PythonInterpreter.initialize(System.getProperties(), props, null);
			PythonInterpreter jython = new PythonInterpreter();
			jython.execfile("datagen/gen_meals.py");
			Database db = (Database) jython.get("db").__tojava__(Database.class);
			db.check();
			db.outputMLNDatabase(new PrintStream(new java.io.File("mln/kitchen/meals.db")));
			db.outputBasicMLN(new PrintStream(new java.io.File("mln/kitchen/meals_empty.mln")));
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

}
