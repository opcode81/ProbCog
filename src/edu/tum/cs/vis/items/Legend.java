package edu.tum.cs.vis.items;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import processing.core.PFont;

import edu.tum.cs.vis.Canvas;
import edu.tum.cs.vis.Drawable;

public class Legend implements Drawable {
	
	public Vector<String> stringLeg = new Vector<String>();
	public int[] colors = new int[] {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffff00, 0xffff00ff, 0xff00ffff, 0xffff8800, 0xffff0088, 0xff88ff00, 0xff00ff88, 0xff8800ff, 0xff0088ff};
	//public PFont locFont, globFont;
	
	public void draw(Canvas c) {
		// TODO Auto-generated method stub
		//c.textFont(locFont, 11); 
		/*int legPosX = 0,legPosY = 0;
		int i = 0;
		for (String str : stringLeg){
			c.fill(colors[i]);
			c.text(str,legPosX + 10,legPosY + 13*(i+3));
			i += 1;
		}*/
		c.noStroke();
		c.fill (255, 255, 255);
		
		
		 c.textMode(c.SCREEN);
		 c.textAlign(c.LEFT);
		 
		c.rect(35, 10, 10, 10);
		String a = " - cup";
		c.text(a, 55, 10, 100, 100);
		
		c.ellipse(40, 35, 10, 10);
		String p = " - plate";
		c.text(p, 55 , 30, 100, 100);
		
		c.fill(255, 0, 0);
		c.ellipse(40, 55, 7, 4);
		c.fill (255, 255, 255);
		String g = " - the object is gripped";
		c.text(g, 55, 50, 1000, 100);
		
		c.fill (0, 255, 0);
		c.ellipse(40, 75, 7, 4);
		c.fill(255, 255, 255);
		String d = " - the object is dropped";
		c.text(d, 55, 70, 1000, 100);
		
		//c.textFont(globFont,11);
	}
	
/*	public void loadFonts(Canvas c){
		locFont = c.loadFont("serif"); 
		globFont = c.loadFont("Verdana");
	}*/
	
	public void loadLabels(java.io.File ascFile) throws NumberFormatException, IOException{
		BufferedReader r = new BufferedReader(new FileReader(ascFile));
		String line;
		int iLine = 0;
		while((line = r.readLine()) != null) {			
			this.stringLeg.add(line);
			iLine++;
		}
	}

}
