import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Vector;

import edu.tum.cs.analysis.pointcloud.DataReader;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Database.AttributeClustering;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DataDictionary.BLNStructure;

public class PointCloudClassification {
	
	public static void getXMLFiles(File dir, Vector<File> ret) {
		File listFile[] = dir.listFiles();
        if(listFile != null) {
            for(File f : listFile) {
                if(f.isDirectory()) {
                    getXMLFiles(f, ret);
                } else {
                    if(f.getName().endsWith(".xml")) {
                    	ret.add(f);
                    }
                }
            }
        }		
	}
	
	public static void main(String[] args) throws FileNotFoundException, Exception {
		DataReader dr = new DataReader();
		dr.setRename("wideness", "width");
		dr.setRename("longness", "length");
		dr.setRename("object", "ratioObject");
		dr.setRename("above", "ratioAbove");
		dr.setRename("below", "ratioBelow");
		dr.setRename("shadow", "ratioShadow");
		dr.setRename("angle", "avgNormalAngle");		
		
		// read training data from XML files
		Vector<File> xmls = new Vector<File>();
		System.out.println("reading XML training data...");
		getXMLFiles(new File("data/training"), xmls);		
		for(File xml : xmls) {
			System.out.println("  " + xml.getPath());
			dr.readXML(xml);
		}
		Database db = dr.getDatabase();

		// discard unused attributes
		String[] unusedAttributes = new String[]{"components", "eig_max", "eig_min", "eig_mid", "avg_z", "avg_d", "flat", "long"};
		for(String a : unusedAttributes)
			db.getDataDictionary().getAttribute(a).discard();
		
		// attribute value clustering
		String[] clusteredAttributes = new String[]{"width", "avgNormalAngle", "length", "height", "sum_low_conf", "ratioShadow", "ratioAbove", "ratioObject", "ratioBelow", "eig_mid", "zero_conf", "avg_z", "avg_d", "eig_max", "eig_min", "little_conf", "thickness"};
		edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary(); 
		for(String a : clusteredAttributes) {			
 			DDAttribute attr = dd.getAttribute(a);
 			if(attr != null)
 				attr.setClustering(true);
		}
		HashMap<DDAttribute, AttributeClustering> clusteringMap = db.doClustering();
		
		// set relation properties, i.e. make belongsTo functional
		dd.getRelation("belongsTo").setFunctional(new boolean[]{false, true});

		// finalize
		db.check();	

		// write training databases
		db.writeMLNDatabase(new PrintStream(new File("train.db")));
		db.writeBLOGDatabase(new PrintStream(new File("train.blogdb")));
		
		// MLN
		PrintStream mln = new PrintStream(new File("pcc.mln"));
		db.writeBasicMLN(mln);
		DDObject ddo = dd.getObject("object");
		mln.println("\n// formulas");
		for(DDAttribute dda : ddo.getAttributes().values())
			if(!dda.isDiscarded())
				mln.printf("objectT(o,+t) ^ %s(o,+v)\n", dda.getName());		
		mln.printf("belongsTo(c,o) ^ objectT(o,+ot) ^ componentT(c,+ct)\n");		
		
		// BLN structure
		BLNStructure bs = dd.createBasicBLNStructure();		
		for(DDAttribute attr : dd.getAttributes()) {
			if(attr.isDiscarded())
				continue;
			if(attr.getName().equals("objectT") || attr.getName().equals("componentT"))
				continue;
			String classAttr = attr.getOwner().getName() + "T";			
			bs.connect(dd.getAttribute(classAttr), attr);
		}
		bs.connect(dd.getAttribute("componentT"), dd.getRelation("belongsTo"));
		bs.connect(dd.getAttribute("objectT"), dd.getRelation("belongsTo"));
		bs.bn.savePMML("pcc.pmml");
		//bs.bn.show();
		
		// BLN
		PrintStream bln = new PrintStream(new File("pcc.bln"));
		dd.writeBasicBLN(bln);
		
		// read test data
		db.clear();
		xmls.clear();
		System.out.println("reading XML test data...");
		getXMLFiles(new File("data/test"), xmls);		
		for(File xml : xmls) {
			System.out.println("  " + xml.getPath());
			dr.readXML(xml);
		}
		
		// cluster it using the same clusterers
		db.doClustering(clusteringMap);
		
		// write it
		db.writeMLNDatabase(new PrintStream(new File("test.db")));
		db.writeBLOGDatabase(new PrintStream(new File("test.blogdb")));		
	}
}
