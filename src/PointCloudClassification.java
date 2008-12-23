import java.io.FileNotFoundException;
import java.io.PrintStream;

import edu.tum.cs.analysis.pointcloud.DataReader;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.IdentifierNamer;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDObject;
import edu.tum.cs.srldb.datadict.IDDRelationArgument;

import java.io.File;
import java.util.Vector;

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
		
		Vector<File> xmls = new Vector<File>();
		System.out.println("reading XML data...");
		getXMLFiles(new File("data/training"), xmls);		
		for(File xml : xmls) {
			System.out.println("  " + xml.getPath());
			dr.readXML(xml);
		}
		Database db = dr.getDatabase();

		// unused attributes
		String[] unusedAttributes = new String[]{"components", "eig_max", "eig_min", "eig_mid", "avg_z", "avg_d", "flat", "long"};
		for(String a : unusedAttributes)
			db.getDataDictionary().getAttribute(a).discard();
		
		// clustering
		String[] clusteredAttributes = new String[]{"width", "avgNormalAngle", "length", "height", "sum_low_conf", "ratioShadow", "ratioAbove", "ratioObject", "ratioBelow", "eig_mid", "zero_conf", "avg_z", "avg_d", "eig_max", "eig_min", "little_conf", "thickness"};
		edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary(); 
		for(String a : clusteredAttributes) {			
 			DDAttribute attr = dd.getAttribute(a);
 			if(attr != null)
 				attr.setClustering(true);
		}
		db.doClustering();
		
		// finalize
		db.check();	
		// set relation properties, i.e. make belongsTo functional
		dd.getRelation("belongsTo").setFunctional(new boolean[]{false, true});

		// write database
		db.outputMLNDatabase(new PrintStream(new File("train.db")));
		
		// basic MLN
		PrintStream mln = new PrintStream(new File("pcc.mln"));
		db.outputBasicMLN(mln);
		
		// BLN structure
		BeliefNetworkEx bn = new BeliefNetworkEx();
		IdentifierNamer namer = new IdentifierNamer(dd);
		for(DDAttribute attr : dd.getAttributes()) {
			String nodeName = String.format("%s(%s)", attr.getName(), namer.getShortIdentifier("object", attr.getOwner().getName()));
			bn.addNode(nodeName);
		}
		for(edu.tum.cs.srldb.datadict.DDRelation rel : dd.getRelations()) {
			StringBuffer nodeName = new StringBuffer(rel.getName() + "(");
			IDDRelationArgument[] relargs = rel.getArguments();
			for(int i = 0; i < relargs.length; i++) {
				if(i > 0)
					nodeName.append(',');
				nodeName.append(namer.getShortIdentifier(rel.getName(), relargs[i].getDomainName()));
			}
			nodeName.append(')');
			bn.addNode(nodeName.toString());
		}
		
		bn.show();
		
		// write some formulas
		DDObject ddo = dd.getObject("object");
		mln.println("\n// foo formulas");
		for(DDAttribute dda : ddo.getAttributes().values())
			if(!dda.isDiscarded())
				mln.printf("objectT(o,+t) ^ %s(o,+v)\n", dda.getName());		
		mln.printf("belongsTo(c,o) ^ objectT(o,+ot) ^ componentT(c,+ct)\n");		
	}
}
