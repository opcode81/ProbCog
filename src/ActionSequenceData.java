import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.analysis.actionrecognition.DataReader;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DataDictionary.BLNStructure;

public class ActionSequenceData {
	
	public static void getLabelFiles(File dir, Vector<File> ret) {
		File listFile[] = dir.listFiles();
        if(listFile != null) {
            for(File f : listFile) {
                if(f.isDirectory()) {
                    getLabelFiles(f, ret);
                } else {
                    if(f.getName().endsWith(".dat")) {
                    	ret.add(f);
                    }
                }
            }
        }		
	}
	
	public static void readData(String datadir) throws FileNotFoundException, Exception {

		String dataset = datadir;
		String dbdir = "models/"+dataset;
		new File(dbdir).mkdir();
		
		DataReader dr = new DataReader();
		
		// read training data from XML files
		Vector<File> labelFiles = new Vector<File>();
		System.out.println("reading training data...");
		getLabelFiles(new File("data/training_" + datadir), labelFiles);

		for(File labelFile : labelFiles) {
			System.out.println("  " + labelFile.getPath());
			dr.readLabelFile(labelFile);
		}
		Database db = dr.getDatabase();

		// attribute value clustering
		edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary(); 

		// set relation properties, i.e. make belongsTo functional
		dd.getRelation("objectActedOn").setFunctional(new boolean[]{false, true});
		//dd.getRelation("primaryObjectMoving").setFunctional(new boolean[]{false, true});
		//dd.getRelation("toLocation").setFunctional(new boolean[]{false, true});
		//dd.getRelation("fromLocation").setFunctional(new boolean[]{false, true});

		// finalize
		db.check();	

		// write training databases
		db.writeMLNDatabase(new PrintStream(new File(dbdir + "/train.db")));
		db.writeBLOGDatabase(new PrintStream(new File(dbdir + "/train.blogdb")));
		db.writeSRLDB(new FileOutputStream(new File(dbdir + "/train.srldb")));
		
		// MLN
		PrintStream mln = new PrintStream(new File(dbdir + "/actionrec.mln"));
		db.writeBasicMLN(mln);
		
//		DDObject ddo = dd.getObject("object");
		mln.println("\n// formulas");
		mln.printf("actionT(a,+at) ^ objectT(o,+ot) ^ objectActedOn(a,o)\n");
//		for(DDAttribute dda : ddo.getAttributes().values())
//			if(!dda.isDiscarded())
//				mln.printf("objectT(o,+t) ^ %s(o,+v)\n", dda.getName());		
//		
//		mln.printf("belongsTo(c,o) ^ objectT(o,+ot) ^ componentT(c,+ct)\n");
//		String[] componentRel = new String[]{"depth", "level", "below"};
//		
//		for(String rel : componentRel) 
//			mln.printf("%s(c,c2) ^ componentT(c,+ct) ^ componentT(c2,+ct2) ^ belongsTo(c,o) ^ objectT(o,+ot)\n", rel);
//		mln.printf("isEllipseOf(e,o) ^ objectT(o,+ot) ^ xOrientation(e,+xo) ^ yOrientation(e,+yo) ^ radDistRatio(e,+r)\n");
//		
		
		
		
		// BLN structure
		BLNStructure bs = dd.createBasicBLNStructure();	
		
		DDAttribute actT_rel     = dd.getAttribute("actionT"); 
		DDAttribute objT_rel     = dd.getAttribute("objectT");
		DDRelation objActOn_rel  = dd.getRelation("objectActedOn");
		DDRelation prec_rel      = dd.getRelation("precedes");
		DDRelation toLoc_rel     = dd.getRelation("toLocation");
		DDRelation priMov_rel    = dd.getRelation("primaryObjectMoving");
		
		BeliefNode actA1 = bs.getNode(actT_rel);
		actA1.setName("actionT(a1)");
		
		BeliefNode objO1 = bs.getNode(objT_rel);
		objO1.setName("objectT(o1)");
		
		BeliefNode objActOn1 = bs.getNode(objActOn_rel);
		objActOn1.setName("objectActedOn(a1, o1)");
		
		BeliefNode toLoc1 = bs.getNode(toLoc_rel);
		toLoc1.setName("toLocation(a1, o12)");
		
		BeliefNode priMov1 = bs.getNode(priMov_rel);
		priMov1.setName("primaryObjectMoving(a1, o13)");
		
		
		BeliefNode actA2     = bs.bn.addNode("#actionT(a2)");
		BeliefNode objO2     = bs.bn.addNode("#objectT(o2)");
		BeliefNode objActOn2 = bs.bn.addNode("#objectActedOn(a2, o2)");
		BeliefNode toLoc2    = bs.bn.addNode("#toLocation(a2, o22)");
		BeliefNode priMov2   = bs.bn.addNode("#primaryObjectMoving(a2, o23)");

		
		BeliefNode activity = bs.bn.addNode("activityT(act)");

		
		BeliefNode _a1a2 = bs.bn.addDecisionNode("!(a1=a2)");
		BeliefNode _o1o2 = bs.bn.addDecisionNode("!(o1=o2)");
		BeliefNode _o12o22 = bs.bn.addDecisionNode("!(o12=o22)");
		BeliefNode _o13o23 = bs.bn.addDecisionNode("!(o13=o23)");

		BeliefNode precedes = bs.getNode(prec_rel);
		precedes.setName("precedes(a1, a2, act)");
		
		bs.bn.bn.connect(_a1a2, precedes);
		bs.bn.bn.connect(_o1o2, precedes);
		bs.bn.bn.connect(_o12o22, precedes);
		bs.bn.bn.connect(_o13o23, precedes);
		
		
		bs.bn.bn.connect(actA1, precedes);
		bs.bn.bn.connect(actA2, precedes);
		bs.bn.bn.connect(activity, precedes);
		
		// objectActedOn
		bs.bn.bn.connect(actA1, objActOn1);
		bs.bn.bn.connect(actA2, objActOn2);
		bs.bn.bn.connect(objO1, objActOn1);
		bs.bn.bn.connect(objO2, objActOn2);

		bs.bn.bn.connect(objActOn1, precedes);
		bs.bn.bn.connect(objActOn2, precedes);

		
		
		// toLocation
		bs.bn.bn.connect(actA1, toLoc1);
		bs.bn.bn.connect(actA2, toLoc2);
		bs.bn.bn.connect(objO1, toLoc1);
		bs.bn.bn.connect(objO2, toLoc2);
		
		bs.bn.bn.connect(toLoc1, precedes);
		bs.bn.bn.connect(toLoc2, precedes);

		
		
		// primaryObjectMoving
		bs.bn.bn.connect(actA1, priMov1);
		bs.bn.bn.connect(actA2, priMov2);
		bs.bn.bn.connect(objO1, priMov1);
		bs.bn.bn.connect(objO2, priMov2);
		
		bs.bn.bn.connect(priMov1, precedes);
		bs.bn.bn.connect(priMov2, precedes);
		

		BeliefNode a1a2 = bs.bn.addDecisionNode("a1=a2");
		BeliefNode o1o2 = bs.bn.addDecisionNode("o1=o2");
		BeliefNode prec_a1a2 = bs.bn.addNode("precedes(a1, a2)");
		bs.bn.bn.connect(a1a2, prec_a1a2);
		bs.bn.bn.connect(o1o2, prec_a1a2);
		
		// save
		bs.bn.savePMML(dbdir + "/actionrec.pmml");
		//bs.bn.show();
		
		// BLN
		PrintStream bln = new PrintStream(new File(dbdir + "/actionrec.abl"));
		dd.writeBasicBLOGModel(bln);
		
		
		// read test data
		db.clear();
		labelFiles.clear();
		System.out.println("reading XML test data...");
		getLabelFiles(new File("data/test_" + datadir), labelFiles);	

		for(File labelFile : labelFiles) {
			System.out.println("  " + labelFile.getPath());
			dr.readLabelFile(labelFile);
		}
		
		// commit links...
		 dr.getDatabase();
		
		// write complete test databases
		System.out.println("writing test data...");
		db.writeMLNDatabase(new PrintStream(new File(dbdir + "/test.db")));
		db.writeBLOGDatabase(new PrintStream(new File(dbdir + "/test.blogdb")));
		db.writeSRLDB(new FileOutputStream(dbdir + "/test.srldb"));
		
		// write individual test databases without the class attribute
//		db.getDataDictionary().getAttribute("objectT").discard();
//		for(Object o : db.getObjects()) {
//			if(o.hasAttribute("objectT")) {
//				HashSet<Link> printedLinks = new HashSet<Link>();				
//				String dbname = dbdir + "/" + "test_" + o.getAttributeValue("objectT") + "_" + o.getConstantName();
//				
//				PrintStream blogdb_s = new PrintStream(new File(dbname + ".blogdb"));
//				PrintStream db_s = new PrintStream(new File(dbname + ".db"));
//				String comment = "// " + o.getAttributeValue("objectT");
//				
//				blogdb_s.println(comment);
//				db_s.println(comment);
//				o.BLOGprintFacts(blogdb_s);
//				o.MLNprintFacts(db_s);
//				// linked objects, i.e. components
//				for(Link l : db.getLinks(o)) {
//					if(!printedLinks.contains(l)) {
//						printedLinks.add(l);
//						l.BLOGprintFacts(blogdb_s);
//						l.MLNprintFacts(db_s);
//						IRelationArgument[] arguments = l.getArguments();
//						for(int i = 0; i < arguments.length; i++)
//							if(arguments[i] instanceof Object && arguments[i] != o) {
//								Object component = (Object)arguments[i]; 
//								component.BLOGprintFacts(blogdb_s);
//								component.MLNprintFacts(db_s);
//								// links of the component
//								for(Link lc : db.getLinks(component)) {
//									if(!printedLinks.contains(lc)) {
//										printedLinks.add(lc);
//										lc.BLOGprintFacts(blogdb_s);
//										lc.MLNprintFacts(db_s);
//									}
//								}			
//							}
//					}
//				}
//			}
//		}
		
		System.out.println("done.");
	}
	

	public static void main(String[] args) throws FileNotFoundException, Exception {
		readData("brownies");
//		learnDecTree("models/brownies");
	}
}
