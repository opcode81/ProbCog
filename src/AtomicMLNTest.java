import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.DataDictionary;


public class AtomicMLNTest {
	public static void main(String[] args) {
		try {
			System.out.println("fetching data...");			
			
			// load MySQL driver
			Class.forName("com.mysql.jdbc.Driver").newInstance();

			// connect to the fipmmain database
	        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost/test?user=Dominik&password=nirvana");
	      
			// get a list of all the game databases
	        Statement stmt = conn.createStatement(); 
	        ResultSet rs = stmt.executeQuery("SELECT if(A=1,'true','false') as A, if(B=1,'true','false') as B FROM dectree");	        
	        
			// process all the listed game databases...	        		
	        Database database = new Database(new DataDictionary());
			while(rs.next()) {
		
				// get object
				Object obj = new Object(database);
				obj.addAttribsFromResultSet(rs, false);
			
			}
			
			// output MLN	
			System.out.println("writing MLN database...");
			
			PrintStream out = new PrintStream(new File("mln/atomic_events/data.db"));
			database.outputMLNDatabase(out);
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
}
