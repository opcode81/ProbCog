
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


public class BlogbdToLDCRF {


	public static void main(String[] args) throws IOException {

		//if(args.length<1) {return;}
		
		
		// 'seqID' -> ['pred'->'val']
		HashMap<String, HashMap<String, String>> data = new HashMap<String, HashMap<String,String>>();
		
		// 'activity' -> <['a1','a2']>
		HashMap<String, ArrayList<String[]>> constraints = new HashMap<String, ArrayList<String[]>>();
		
		
		
		BufferedReader input =  new BufferedReader(new FileReader("/home/tenorth/work/actionrec/srldb/models/set-the-table/stt-human/new-1.blogdb"));
		//BufferedReader input =  new BufferedReader(new FileReader(args[0]));
		String line="";
		
		
		// read the input file
        while (( line = input.readLine()) != null){
        	
        	// remove comments
        	if((line.trim().startsWith("//")) || (line.trim().equals(""))) continue;
        	
        	else if(line.trim().startsWith("precedes")) {
        		
        		// do stuff... -> activity-specific ordering
        		Matcher matcher = Pattern.compile(".+\\(([a-zA-Z0-9_]+)[ \t\n\f\r]*,[ \t\n\f\r]*([a-zA-Z0-9_]+)[ \t\n\f\r]*,[ \t\n\f\r]*([a-zA-Z0-9_]+)\\)=([a-zA-Z0-9_]+);").matcher(line.trim());

                matcher.find();
                String action1=matcher.group(1);
                String action2=matcher.group(2);
                String activity=matcher.group(3);
                
                if(!constraints.containsKey(activity)) {
                	constraints.put(activity, new ArrayList<String[]>());
                }
                constraints.get(activity).add(new String[]{action1, action2});
        		
        	} else {
        		
        		// add to the data hashmap
        		Matcher matcher = Pattern.compile("([a-zA-Z0-9_]+)\\(([a-zA-Z0-9_]+)\\)=([a-zA-Z0-9_]+);").matcher(line.trim());

                matcher.find();
                String pred=matcher.group(1);
                String seqID=matcher.group(2);
                String val=matcher.group(3);
                
                if(!data.containsKey(seqID)) {
                	data.put(seqID, new HashMap<String, String>());
                }
                data.get(seqID).put(pred, val);
                
        	}

        }
		

        // for each activity:
        ArrayList<String> out = new ArrayList<String>();
        for(String act : constraints.keySet()) {
        	
            // put the segments into a vector (heuristic: iterate along the ArrayList and put the elements
            // into that order -> works fine as long as the constraints in the list are in the right order)
        	out.clear();
        	out.add(constraints.get(act).get(0)[0]);
        	for(String[] pair : constraints.get(act)) {
        		out.add(pair[1]);
        	}
        	
        	// check if all the actions are covered
        	for(String seq : data.keySet()) {
        		if(!out.contains(seq))
        			out.add(seq);	// TODO: better throw something?
        	}
        	
        	// swap two actions until the arraylist is sorted
        	// (i.e. until no more swaps occurred)
        	boolean swapped=true; int i=0;
        	while(swapped) {
        		
        		// check all constraints and swap if needed
        		swapped=false;
        		for(String[] c : constraints.get(act)) {
        			if(!constraintMet(c, out)) {
        				out.set(out.indexOf(c[0]), c[1]);
        				out.set(out.lastIndexOf(c[1]), c[0]);
        				swapped=true;
        			}
        		}
        		i++;
        		if(i==10000) {
        			return; // TODO: inconsistent constraints detected, throw something
        		}
        	}
       	
        	// write the vector including all the features to a file
        	PrintWriter outfile = new PrintWriter(new FileWriter(act+".csv"));

        	// data  ::  'seqID' -> ['pred'->'val']

        	// matrix dimensions
			outfile.print(data.get(out.get(0)).keySet().size()+","+out.size()+"\n");
        	
        	// for all predicates
        	for(String p : data.get(out.get(0)).keySet()) {
        		
        		// for all segments
        		for(String segm : out) {
        			if(data.get(segm)!=null && data.get(segm).get(p)!=null)
        				outfile.print(data.get(segm).get(p)+",");
        			else
        				outfile.print(0+",");
        		}
        		outfile.print("\n");
        	}
        	outfile.close();
        	
        	// write the label files
        	outfile = new PrintWriter(new FileWriter(act+".labels.csv"));
        	outfile.print("1,"+out.size()+"\n");
        	for(String segm : out) {
        		outfile.print(segm+",");
        	}
        	outfile.print("\n");
        	outfile.close();
        	
        	outfile = new PrintWriter(new FileWriter(act+".seqlabels.csv"));
        	outfile.print(act);
        	outfile.print("\n");
        	outfile.close();
        }
        

	}

	static boolean constraintMet(String[] c, ArrayList<String> out) {
		
		// check if there is at least one c[1] that meets the constraint
		for(int o=out.indexOf(c[0]);o<out.size();o++) {
			if(out.get(o).equals(c[1]))
				return true;
		}

		return false;
	}
}


