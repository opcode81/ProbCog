import java.io.FileNotFoundException;
import java.io.PrintStream;

import edu.tum.cs.analysis.pointcloud.DataReader;
import edu.tum.cs.srldb.Database;
import java.io.File;

public class PointCloudClassification {
	public static void main(String[] args) throws FileNotFoundException, Exception {
		DataReader dr = new DataReader();
		dr.readXML(new File("data/1227032361941146-sr4k_t.pcd.xml"));
		Database db = dr.getDatabase();
		
		// clustering
		db.getDataDictionary().getAttribute("height").setClustering(true);
		db.doClustering();
		
		// finalize
		db.check();
		
		db.outputMLNDatabase(new PrintStream(new File("train.db")));
		db.outputBasicMLN(new PrintStream(new File("basic.mln")));
	}
}
