package edu.tum.cs.tools;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class FileUtil {

	public static String readTextFile(String filename) throws FileNotFoundException, IOException {
		File inputFile = new File(filename);
		FileReader fr = new FileReader(inputFile);
		char[] cbuf = new char[(int)inputFile.length()];
		fr.read(cbuf);
		String content = new String(cbuf);
		fr.close();
		return content;
	}

}
