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
		int legPosX = 0,legPosY = 0;
		int i = 0;
		for (String str : stringLeg){
			c.fill(colors[i]);
			c.rect(legPosX + 2, legPosY + 13*(i+2) + 6 , 5,5);
			c.text(str,legPosX + 10,legPosY + 13*(i+3));
			i += 1;
		}
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
