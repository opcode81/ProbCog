import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.analysis.pointcloud.DataReader;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.IRelationArgument;
import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.Database.AttributeClustering;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
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
	
	public static void readData(String zolidata, String ulidata) throws FileNotFoundException, Exception {
		String dataset = zolidata+ulidata;
		String dbdir = dataset;
		new File(dbdir).mkdir();
		
		DataReader dr = new DataReader();
		dr.setRename("wideness", "widthFrac");
		dr.setRename("longness", "lengthFrac");
		dr.setRename("thickness", "thicknessFrac");
		dr.setRename("object", "ratioObject");
		dr.setRename("above", "ratioAbove");
		dr.setRename("below", "ratioBelow");
		dr.setRename("shadow", "ratioShadow");
		dr.setRename("angle", "avgNormalAngle");	
		dr.setRename("orientation_x", "xOrientation");
		dr.setRename("orientation_y", "yOrientation");
		dr.setRename("orientation_z", "zOrientation");
		dr.setRename("rad_dist_ratio", "radDistRatio");
		
		// read training data from XML files
		Vector<File> xmls = new Vector<File>();
		System.out.println("reading XML training data...");
		getXMLFiles(new File("data/training_" + zolidata), xmls);
		getXMLFiles(new File("data/training_" + ulidata), xmls);
		for(File xml : xmls) {
			System.out.println("  " + xml.getPath());
			dr.readXML(xml);
		}
		Database db = dr.getDatabase();

		// discard unused attributes
		// "ratioObject", "ratioShadow", "ratioBelow"
		String[] unusedAttributes = new String[]{"ratioAbove", "ratioObject", "ratioShadow", "ratioBelow", "eig_max", "eig_min", "eig_mid", "avg_z", "avg_d", "flat", "long", "sum_low_conf"};
		for(String a : unusedAttributes)
			db.getDataDictionary().getAttribute(a).discard();
		
		// attribute value clustering
		String[] clusteredAttributes = new String[]{"components", "radDistRatio", "xOrientation", "yOrientation", "zOrientation", "widthFrac", "avgNormalAngle", "lengthFrac", "height", "sum_low_conf", "ratioShadow", "ratioAbove", "ratioObject", "ratioBelow", "eig_mid", "zero_conf", "avg_z", "avg_d", "eig_max", "eig_min", "little_conf", "thicknessFrac"};		
		edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary(); 
		for(String a : clusteredAttributes) {			
 			DDAttribute attr = dd.getAttribute(a);
 			if(attr != null)
 				attr.setClustering(true);
		}
		dd.getAttribute("area").setClustering(2);
		dd.getAttribute("density").setClustering(2);
		dd.getAttribute("volume").setClustering(2);
		HashMap<DDAttribute, AttributeClustering> clusteringMap = db.doClustering();
		
		// set relation properties, i.e. make belongsTo functional
		dd.getRelation("belongsTo").setFunctional(new boolean[]{false, true});
		dd.getRelation("isEllipseOf").setFunctional(new boolean[]{false, true});

		// finalize
		db.check();	

		// write training databases
		db.writeMLNDatabase(new PrintStream(new File(dbdir + "/train.db")));
		db.writeBLOGDatabase(new PrintStream(new File(dbdir + "/train.blogdb")));
		db.writeSRLDB(new FileOutputStream(new File(dbdir + "/train.srldb")));
		
		// MLN
		PrintStream mln = new PrintStream(new File(dbdir + "/pcc.mln"));
		db.writeBasicMLN(mln);
		DDObject ddo = dd.getObject("object");
		mln.println("\n// formulas");
		for(DDAttribute dda : ddo.getAttributes().values())
			if(!dda.isDiscarded())
				mln.printf("objectT(o,+t) ^ %s(o,+v)\n", dda.getName());		
		mln.printf("belongsTo(c,o) ^ objectT(o,+ot) ^ componentT(c,+ct)\n");
		String[] componentRel = new String[]{"depth", "level", "below"};
		for(String rel : componentRel) 
			mln.printf("%s(c,c2) ^ componentT(c,+ct) ^ componentT(c2,+ct2) ^ belongsTo(c,o) ^ objectT(o,+ot)\n", rel);
		mln.printf("isEllipseOf(e,o) ^ objectT(o,+ot) ^ xOrientation(e,+xo) ^ yOrientation(e,+yo) ^ radDistRatio(e,+r)\n");
		
		// BLN structure
		BLNStructure bs = dd.createBasicBLNStructure();		
		for(DDAttribute attr : dd.getAttributes()) {
			if(attr.isDiscarded())
				continue;
			if(attr.getOwner().getName().equals("ellipse"))
				continue;
			if(attr.getName().equals("objectT") || attr.getName().equals("componentT"))
				continue;
			String classAttr = attr.getOwner().getName() + "T";			
			bs.connect(dd.getAttribute(classAttr), attr);
		}
		// dependencies of the belongsTo relation
		DDAttribute componentT = dd.getAttribute("componentT"); 
		DDAttribute objectT = dd.getAttribute("objectT");
		DDRelation belongsTo = dd.getRelation("belongsTo");
		bs.connect(componentT, belongsTo);
		bs.connect(objectT, belongsTo);
		// dependencies of the isEllipseOf relation
		DDRelation ddrel = dd.getRelation("isEllipseOf");
		ddo = dd.getObject("ellipse");
		for(DDAttribute attr : ddo.getAttributes().values())
			bs.connect(attr, ddrel);
		bs.connect(objectT, ddrel);
		// relations depth, level and below
		Vector<BeliefNode> par = new Vector<BeliefNode>();
		par.add(bs.bn.addDecisionNode("!(c=c2)"));
		par.add(bs.getNode(componentT));
		par.add(bs.bn.addNode("#componentT(c2)"));
		par.add(bs.getNode(belongsTo));
		par.add(bs.getNode(objectT));
		Vector<BeliefNode> chi = new Vector<BeliefNode>();		
		BeliefNode par2 = bs.bn.addDecisionNode("c=c2");
		for(String rel : componentRel) {
			String nodeName = String.format("%s(c,c2)", rel);
			BeliefNode relNode = bs.bn.addNode(nodeName);
			for(BeliefNode parent : par) 
				bs.bn.bn.connect(parent, relNode);
			relNode = bs.getNode(dd.getRelation(rel));
			relNode.setName(nodeName);
			bs.bn.bn.connect(par2, relNode);
		}		
		// save
		bs.bn.savePMML(dbdir + "/pcc_rel3.pmml");
		//bs.bn.show();
		
		// BLN
		PrintStream bln = new PrintStream(new File(dbdir + "/pcc.abl"));
		dd.writeBasicBLOGModel(bln);
		
		// read test data
		db.clear();
		xmls.clear();
		System.out.println("reading XML test data...");
		getXMLFiles(new File("data/test_" + zolidata), xmls);	
		getXMLFiles(new File("data/test_" + ulidata), xmls);
		for(File xml : xmls) {
			System.out.println("  " + xml.getPath());
			dr.readXML(xml);
		}
		
		// cluster it using the same clusterers
		db.doClustering(clusteringMap);
		
		// write complete test databases
		System.out.println("writing test data...");
		db.writeMLNDatabase(new PrintStream(new File(dbdir + "/test.db")));
		db.writeBLOGDatabase(new PrintStream(new File(dbdir + "/test.blogdb")));
		db.writeSRLDB(new FileOutputStream(dbdir + "/test.srldb"));
		
		// write individual test databases without the class attribute
		db.getDataDictionary().getAttribute("objectT").discard();
		for(Object o : db.getObjects()) {
			if(o.hasAttribute("objectT")) {
				HashSet<Link> printedLinks = new HashSet<Link>();				
				String dbname = dbdir + "/" + "test_" + o.getAttributeValue("objectT") + "_" + o.getConstantName();
				PrintStream s = new PrintStream(new File(dbname + ".blogdb"));
				PrintStream s2 = new PrintStream(new File(dbname + ".db"));
				String comment = "// " + o.getAttributeValue("objectT");
				s.println(comment);
				s2.println(comment);
				o.BLOGprintFacts(s);
				o.MLNprintFacts(s2);
				// linked objects, i.e. components
				for(Link l : db.getLinks(o)) {
					if(!printedLinks.contains(l)) {
						printedLinks.add(l);
						l.BLOGprintFacts(s);
						l.MLNprintFacts(s2);
						IRelationArgument[] arguments = l.getArguments();
						for(int i = 0; i < arguments.length; i++)
							if(arguments[i] instanceof Object && arguments[i] != o) {
								Object component = (Object)arguments[i]; 
								component.BLOGprintFacts(s);
								component.MLNprintFacts(s2);
								// links of the component
								for(Link lc : db.getLinks(component)) {
									if(!printedLinks.contains(lc)) {
										printedLinks.add(lc);
										lc.BLOGprintFacts(s);
										lc.MLNprintFacts(s2);
									}
								}			
							}
					}
				}
			}
		}
		
		System.out.println("done.");
	}
	
	public static void learnDecTree(String dbdir) throws FileNotFoundException, IOException, ClassNotFoundException {
		Database db = Database.fromFile(new FileInputStream(dbdir + "/train.srldb"));
	}
	
	public static void main(String[] args) throws FileNotFoundException, Exception {
		//readData("zoli3", "uli3");
		learnDecTree("zoli3uli3");
	}
}
