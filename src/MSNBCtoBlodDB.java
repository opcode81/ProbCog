import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileReader;
import java.io.BufferedReader;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Link;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.DDRelation;
import edu.tum.cs.srldb.datadict.DataDictionary.BLNStructure;

public class MSNBCtoBlodDB {
	
	
	public static void readData(String datadir) throws FileNotFoundException, Exception {

		FileWriter fw_blogdb = null;
		
		Database db = new Database();
		
		edu.tum.cs.srldb.Object currentPage=null;
		Vector<Link> uncommitedLinks = new Vector<Link>();
		
		HashMap<String, String> id2cat = new HashMap<String, String>();
		id2cat.put("1","Frontpage");
		id2cat.put("2","News");
		id2cat.put("3","Tech");
		id2cat.put("4","Local");
		id2cat.put("5","Opinion");
		id2cat.put("6","On-air");
		id2cat.put("7","Misc");
		id2cat.put("8","Weather");
		id2cat.put("9","Msn-news");
		id2cat.put("10","Health");
		id2cat.put("11","Living");
		id2cat.put("12","Business");
		id2cat.put("13","Msn-sports");
		id2cat.put("14","Sports");
		id2cat.put("15","Summary");
		id2cat.put("16","Bbs");
		id2cat.put("17","Travel");
		
		
		try
		{
			
			File inputFile = new File("data/"+datadir+"/msnbc990928.seq");
			BufferedReader input =  new BufferedReader(new FileReader(inputFile));
			ArrayList<edu.tum.cs.srldb.Object> prevSegmInEpsiode = new ArrayList<edu.tum.cs.srldb.Object>();
			
			String line;
			int episode = 0;
			int pageCnt=0;
			
			// debug
			int numSamplesDrawn=0;
			
			int numTrain=1000;
			int numTest=100;
			String mode="train";
			
			while (( line = input.readLine()) != null) {
				
				Matcher matcher = Pattern.compile("^[[0-9]* ]+$").matcher(line.trim());
				if(!matcher.find()) {continue;}

				String[] pages = line.split(" ");
				if(pages.length<=4) continue;
				if(pages.length>=20) continue;

				// write train and test data
				if(mode.equals("train") && numSamplesDrawn == numTrain) {
					mode="test"; numSamplesDrawn=0;
				} else if(mode.equals("test") && numSamplesDrawn == numTest) {
					return;
				}
				numSamplesDrawn++;
				
				// start new buffers for each line
				prevSegmInEpsiode.clear();
				//prevPage=null;
				pageCnt=0;
				
				fw_blogdb     = new FileWriter("data/"+datadir+"/"+mode+"/data" + episode + ".blogdb" );

				
				for(String page:pages) {
					
					
					String label = id2cat.get(page);					
					String pageID="P_" + episode+"_"+ pageCnt++ +"_"+label;
					
					currentPage = new edu.tum.cs.srldb.Object(db, "page", pageID);
					currentPage.addAttribute("pageT", label);
					
					if(pageCnt==1) {
						currentPage.addAttribute("firstPage", "True");
						fw_blogdb.write("firstPage("+pageID+")=True;\n");
					} 
					else if(pageCnt==pages.length) { 
						currentPage.addAttribute("lastPage", "True");
						fw_blogdb.write("lastPage("+pageID+")=True;\n");
					}
					
					// do not write the class label of the last page in test mode
					if(mode.equals("train") || pageCnt<pages.length)
						fw_blogdb.write("pageT("+pageID+")="+label+";\n");
					
					// add precedes-relation from all the preceeding objects
					for(edu.tum.cs.srldb.Object prevPage : prevSegmInEpsiode) {
						fw_blogdb.write("precedes("+prevPage.getConstantName() + ","+pageID+")=True;\n");
						uncommitedLinks.add(new Link(db, "precedes", prevPage, currentPage));
					}
					prevSegmInEpsiode.add(currentPage);
					
					
					if(currentPage!=null)  currentPage.commit();
					
				}

				// commit links
				for(Link o : uncommitedLinks)
					o.commit();
				uncommitedLinks.clear();

				//////////////////////////////////////////////////////
				// write frame-level data
				
				edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary(); 
				db.check();	
				BLNStructure bs = dd.createBasicBLNStructure();	
				
				DDAttribute frametT_rel = dd.getAttribute("pageT"); 
				DDRelation prec_rel     = dd.getRelation("precedes");
				
				DDRelation first_rel    = dd.getRelation("firstPage");
				DDRelation last_rel     = dd.getRelation("lastPage");

				BeliefNode frameF1 = bs.getNode(frametT_rel);
				frameF1.setName("pageT(p1)");
				BeliefNode frameF2 = bs.bn.addNode("#pageT(p2)");

				BeliefNode _f1f2 = bs.bn.addDecisionNode("!(p1=p2)");
				BeliefNode precedes = bs.getNode(prec_rel);
				precedes.setName("precedes(p1, p2)");
				
				if(first_rel!=null) {
					BeliefNode firstF1 = bs.getNode(first_rel);
					firstF1.setName("firstPage(p1)");
					BeliefNode firstF2 = bs.bn.addNode("#firstPage(p2)");
					bs.bn.bn.connect(firstF1, precedes);
					bs.bn.bn.connect(firstF2, precedes);
				}
				if(last_rel!=null) {
					BeliefNode lastF1 = bs.getNode(last_rel);
					lastF1.setName("lastPage(p1)");
					BeliefNode lastF2 = bs.bn.addNode("#lastPage(p2)");
					bs.bn.bn.connect(lastF1,  precedes);
					bs.bn.bn.connect(lastF2,  precedes);
				}
				
				BeliefNode f1f2 = bs.bn.addDecisionNode("p1=p2");
				BeliefNode precedes2 = bs.bn.addNode("precedes(p1, p2)");
				bs.bn.bn.connect(f1f2,   precedes2);
				
				bs.bn.bn.connect(_f1f2,   precedes);
				bs.bn.bn.connect(frameF1, precedes);
				bs.bn.bn.connect(frameF2, precedes);
				
				// save
				bs.bn.savePMML("data/"+datadir+"/"+mode+ "/data.pmml");
				
				bs.bn.save("data/"+datadir+"/"+mode+ "/data.net");
				// BLN
				PrintStream bln = new PrintStream(new File("data/"+datadir+"/"+mode+ "/data.abl"));
				dd.writeBasicBLOGModel(bln);
				bln.close();

				
				fw_blogdb.close();
				episode++;


			}

		}
		catch ( IOException e ) { 
			  System.err.println( "Could not create CSV files" ); 
			} 
		catch ( Exception e ) { 
			  System.err.println( e.toString() ); 
			} 
	}
	
	public static void main(String[] args) throws FileNotFoundException, Exception {
		readData("msnbc");
	}
}
