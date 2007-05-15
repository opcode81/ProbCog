package edu.tum.cs.tools;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

public class FileUtil {

	public static String readTextFile(String filename) { 
        try { 
            File f = new File(filename); 
            byte[] buffer = new byte[(int) f.length()]; 
            BufferedInputStream fis = new BufferedInputStream(new FileInputStream(f)); 
            int i = 0; 
            int b = fis.read(); 
            while (b != -1) { 
                buffer[i++] = (byte) b; 
                b = fis.read(); 
            } 
            fis.close(); 
            return new String(buffer); 
        } 
        catch (Exception e1) { 
            e1.printStackTrace(); 
        } 
        return null;            
    } 

}
