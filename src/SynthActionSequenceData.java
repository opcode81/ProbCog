import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.analysis.actionrecognition.SynthDataReader;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DataDictionary.BLNStructure;

public class SynthActionSequenceData {
	
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
	
	public static void readData(String experiment, String exp_num) throws FileNotFoundException, Exception {

		String dbdir = "models/"+experiment+"/"+exp_num;
		new File("models/"+experiment).mkdir();
		new File(dbdir).mkdir();
		new File(dbdir+"/test").mkdir();
		new File(dbdir+"/train").mkdir();
		
		SynthDataReader dr = new SynthDataReader();
		
		// read training data from XML files
		Vector<File> labelFiles = new Vector<File>();
		System.out.println("reading training data...");
		getLabelFiles(new File("data/"+experiment+"/train_" + exp_num), labelFiles);

		Database db = new Database();
		
		int seq = 0;
		for(File labelFile : labelFiles) {
			
			System.out.println("  " + labelFile.getPath());
			dr.readLabelFile(labelFile);

			db = dr.getDatabase();
			edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary(); 

			// finalize
			db.check();	

			// write training databases
			db.writeMLNDatabase(new PrintStream(new File(dbdir + "/train/train"+seq+".db")));
			db.writeBLOGDatabase(new PrintStream(new File(dbdir + "/train/train"+seq+".blogdb")));
			db.writeSRLDB(new FileOutputStream(new File(dbdir + "/train/train"+seq+++".srldb")));
			
			// MLN
			PrintStream mln = new PrintStream(new File(dbdir + "/actionrec.mln"));
			db.writeBasicMLN(mln);
			
			
			// BLN structure
			BLNStructure bs = dd.createBasicBLNStructure();	
	
			// dependencies of the belongsTo relation
			DDAttribute actT_rel     = dd.getAttribute("actionT");
			DDAttribute activT_rel   = dd.getAttribute("activityT");
			DDRelation prec_rel      = dd.getRelation("precedes");
			
			
			BeliefNode actA1 = bs.getNode(actT_rel);
			actA1.setName("actionT(a1)");
			BeliefNode actA2 = bs.bn.addNode("#actionT(a2)");
			

			BeliefNode activity = bs.getNode(activT_rel);
			activity.setName("activityT(act)");
			
			BeliefNode _a1a2 = bs.bn.addDecisionNode("!(a1=a2)");
	
			BeliefNode precedes = bs.getNode(prec_rel);
			precedes.setName("precedes(a1, a2, act)");
			
			
			BeliefNode activityT = bs.getNode(activT_rel);
			
			bs.bn.bn.connect(activityT, precedes);
			bs.bn.bn.connect(_a1a2, precedes);
			bs.bn.bn.connect(actA1, precedes);
			bs.bn.bn.connect(actA2, precedes);
	
	
			BeliefNode a1a2 = bs.bn.addDecisionNode("a1=a2");
			BeliefNode prec_a1a2  = bs.bn.addNode("precedes(a1, a2, act)");
			bs.bn.bn.connect(a1a2, prec_a1a2);
			
			
			// save
			bs.bn.savePMML(dbdir + "/actionrec.pmml");
			
			// BLN
			PrintStream bln = new PrintStream(new File(dbdir + "/actionrec.abl"));
			dd.writeBasicBLOGModel(bln);
			db.clear();
		}
		
		// read test data
		db.clear();
		labelFiles.clear();
		System.out.println("reading XML test data...");
		getLabelFiles(new File("data/"+experiment+"/test_" + exp_num), labelFiles);	

		for(File labelFile : labelFiles) {
			System.out.println("  " + labelFile.getPath());
			dr.readLabelFile(labelFile);
		}
		
		// commit links, discard class attribute for the test data
		dr.getDatabase().getDataDictionary().getAttribute("activityT").discard();;
		
		// write complete test databases
		System.out.println("writing test data...");
		db.writeMLNDatabase(new PrintStream(new File(dbdir + "/test/test.db")));
		db.writeBLOGDatabase(new PrintStream(new File(dbdir + "/test/test.blogdb")));
		db.writeSRLDB(new FileOutputStream(dbdir + "/test/test.srldb"));
		
		
		System.out.println("done.");
	}
	
	public static void main(String[] args) throws FileNotFoundException, Exception {
		
		for(int num : new int[]{5, 10, 15, 20, 25, 30, 35, 40, 45, 50}) {
			readData("twoPlans_diffActions_noisy0.1", num+"");
		}
	}
}
