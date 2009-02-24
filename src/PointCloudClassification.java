import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.Map.*;

import weka.classifiers.trees.J48;
import weka.classifiers.trees.j48.Rule;
import weka.classifiers.trees.j48.Rule.Condition;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import edu.tum.cs.srldb.datadict.domain.Domain;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.analysis.pointcloud.DataReader;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.IRelationArgument;
import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.Database.AttributeClustering;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDException;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DataDictionary.BLNStructure;
import edu.tum.cs.srldb.datadict.DDException;

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
	
	public static Database readData(String zolidata, String ulidata) throws FileNotFoundException, Exception {
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
		DDRelation isEllipseOf = dd.getRelation("isEllipseOf"); 
		if(isEllipseOf != null)
			isEllipseOf.setFunctional(new boolean[]{false, true});

		// finalize
		db.check();
		Database ret = db;

		// write training databases
		db.writeMLNDatabase(new PrintStream(new File(dbdir + "/train.db")));
		db.writeBLOGDatabase(new PrintStream(new File(dbdir + "/train.blogdb")));
		db.writeSRLDB(new FileOutputStream(new File(dbdir + "/train.srldb")));
		
		// read test data
		db = new Database(ret.getDataDictionary());
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

		writeIndividualTestDatabases(db, dbdir);
		
		return ret;
	}
	
	public static void writeBasicModels(Database db, String dbdir) throws DDException, FileNotFoundException {
		edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary();
		
		// MLN
		System.out.println("writing MLN structure...");
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
		System.out.println("writing BLN structures...");
		BLNStructure bs = dd.createBasicBLNStructure();		
		// naive Bayes: class -> attribute
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
		// relations depth, level and below
		Vector<BeliefNode> par = new Vector<BeliefNode>();
		par.add(bs.bn.addDecisionNode("!(c=c2)"));
		par.add(bs.getNode(componentT));
		par.add(bs.bn.addNode("#componentT(c2)"));
		par.add(bs.getNode(belongsTo));
		par.add(bs.getNode(objectT));
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
		// save the model structure with everything but ellipses
		bs.bn.savePMML(dbdir + "/pcc_rel.pmml");
		// dependencies of the isEllipseOf relation
		DDRelation isEllipseOf = dd.getRelation("isEllipseOf");
		if(isEllipseOf != null) {
			DDRelation ddrel = isEllipseOf;
			ddo = dd.getObject("ellipse");
			for(DDAttribute attr : ddo.getAttributes().values())
				bs.connect(attr, ddrel);
			bs.connect(objectT, ddrel);
		}
		// save the full model structure
		bs.bn.savePMML(dbdir + "/pcc_rel3.pmml");
		//bs.bn.show();
		// generate a model structure that considers only the relations but none of the attributes
		for(DDAttribute attr : dd.getAttributes()) {
			if(attr.isDiscarded())
				continue;
			if(attr.getOwner().getName().equals("ellipse"))
				continue;
			if(attr.getName().equals("objectT") || attr.getName().equals("componentT"))
				continue;
			String classAttr = attr.getOwner().getName() + "T";			
			bs.disconnect(dd.getAttribute(classAttr), attr);
		}
		bs.bn.savePMML(dbdir + "/pcc_noattrs.pmml");		
		
		// BLN
		PrintStream bln = new PrintStream(new File(dbdir + "/pcc.abl"));
		dd.writeBasicBLOGModel(bln);
	}
	
	public static void writeIndividualTestDatabases(Database db, String dbdir) throws DDException, IOException {
		// write individual test databases without the class attribute
		db.getDataDictionary().getAttribute("objectT").discard();
		for(Object o : db.getObjects()) {
			if(o.hasAttribute("objectT")) {
				Database objDB = new Database(db.getDataDictionary());
				objDB.addObject(o);
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
						objDB.addLink(l);
						printedLinks.add(l);
						l.BLOGprintFacts(s);
						l.MLNprintFacts(s2);
						IRelationArgument[] arguments = l.getArguments();
						for(int i = 0; i < arguments.length; i++)
							if(arguments[i] instanceof Object && arguments[i] != o) {
								Object component = (Object)arguments[i]; 
								objDB.addObject(component);
								component.BLOGprintFacts(s);
								component.MLNprintFacts(s2);
								// links of the component
								for(Link lc : db.getLinks(component)) {
									if(!printedLinks.contains(lc)) {
										objDB.addLink(lc);
										printedLinks.add(lc);
										lc.BLOGprintFacts(s);
										lc.MLNprintFacts(s2);
									}
								}			
							}
					}
				}
				objDB.writeSRLDB(new FileOutputStream(new File(dbname + ".srldb")));
			}
		}
	}
	
	public static void learnDecTree(String dbdir) throws FileNotFoundException, IOException, ClassNotFoundException, DDException, Exception {
		Database db = Database.fromFile(new FileInputStream(dbdir + "/train.srldb"));
		edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary();
		//the vector of attributes
		FastVector fvAttribs = new FastVector();
		HashMap<String,Attribute> mapAttrs = new HashMap<String,Attribute>();
		for(DDAttribute attribute : dd.getObject("object").getAttributes().values()){
			if(attribute.isDiscarded()){
				continue;
			}
			FastVector attValues = new FastVector();
			Domain dom = attribute.getDomain();
			for(String s : dom.getValues())
				attValues.addElement(s);
			Attribute attr = new Attribute(attribute.getName(), attValues);				
			fvAttribs.addElement(attr);
			mapAttrs.put(attribute.getName(), attr);
		}

		// learn decision tree
		Instances instances = new Instances("name",fvAttribs,10000);
		//for each object add an instance
		for(Object o : db.getObjects()){
			if (o.hasAttribute("objectT")){
				Instance instance = new Instance(fvAttribs.size());
				for(Entry<String,String> e : o.getAttributes().entrySet()) {
					if (!dd.getAttribute(e.getKey()).isDiscarded()){
						instance.setValue(mapAttrs.get(e.getKey()), e.getValue());
					}		
				}
				instances.add(instance);
			}		
		}
		
		//learn a J48 decision tree from the instances
		instances.setClass(mapAttrs.get("objectT"));
		J48 j48 = new J48();
		//j48.setMinNumObj(0); // there is no minimum number of objects that has to end up at each of the tree's leaf nodes 
		j48.buildClassifier(instances);
		System.out.println(j48.toString());
		ObjectOutputStream objstream = new ObjectOutputStream(new FileOutputStream(dbdir + "/pcc.j48"));
		objstream.writeObject(j48);
		objstream.close();
	}
	
	public static void main(String[] args) throws FileNotFoundException, Exception {
		String zolidata = "zoli6", ulidata = "uli6";
		String dbdir = zolidata + ulidata;
		
		//Database db = readData(zolidata, ulidata);
		Database db = Database.fromFile(new FileInputStream(dbdir + "/train.srldb"));
				
		//writeIndividualTestDatabases(db, dbdir);
		writeBasicModels(db, dbdir);
		learnDecTree(zolidata + ulidata);
	}
}
