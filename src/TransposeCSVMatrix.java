
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.io.*;


public class TransposeCSVMatrix {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {

		if(args.length<1) {return;}

		
		ArrayList<ArrayList<String>> matrix = new ArrayList<ArrayList<String>>();
		BufferedReader input =  new BufferedReader(new FileReader(args[0]));
		String line="";
		
		
		// fill the matrix
        while (( line = input.readLine()) != null){
        	ArrayList<String> row= new ArrayList<String>();
        	for(String f : line.split(","))
        		row.add(f);
        	matrix.add(row);
        }
		
        String outfile = "transposed.csv";
        if(args.length>1) {outfile=args[1];}
        
        // read the matrix
        PrintWriter out = new PrintWriter(new FileWriter(outfile));

        for(int col=0;col<matrix.get(0).size(); col++) {

        	for(int row=0;row<matrix.size();row++) {
        		ArrayList<String> r = matrix.get(row);
        		out.print(r.get(col)+",");
        	}
        	out.print("\n");
        }
        out.close();
	}

}
