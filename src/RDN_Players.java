import java.io.File;
import java.io.PrintStream;
import edu.tum.cs.srldb.fipm.FIPMData;

public class RDN_Players {
	public static void main(String[] args) {
		
		try {
			FIPMData data = new FIPMData();
			//data.fetch("good_formations=1");
			data.fetch("db='fipm02'");
			data.doClustering();
			
			System.out.println("checking compliance with data dictionary...");
			data.database.check();
			
			System.out.println("writing Proximity database...");
			PrintStream out = new PrintStream(new File("proximity/fipm.proxdb.xml"));
			data.database.writeProximityDatabase(out);
			
			System.out.println("done!");
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
}
