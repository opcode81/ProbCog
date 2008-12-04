package edu.tum.cs.vis.items;

import processing.core.PFont;
import edu.tum.cs.vis.AnimatedCanvas;
import edu.tum.cs.vis.Canvas;
import edu.tum.cs.vis.Drawable;

public class FrameDisplay implements Drawable {

	protected AnimatedCanvas canvas;
	protected int x, y, textAlign;
	protected PFont font;
	
	/**
	 * 
	 * @param c
	 * @param x
	 * @param y
	 * @param textAlign
	 * @param font  may be null if it is not to be explicitly set
	 */
	public FrameDisplay(AnimatedCanvas c, int x, int y, int textAlign, PFont font) {
		this.canvas = c;
		this.x = x;
		this.y = y;
		this.textAlign = textAlign;
		this.font = font;
	}
	
	public void draw(Canvas c) {
		if(font != null)
			c.textFont(font);
		c.fill(0xffffffff);
		c.textMode(Canvas.SCREEN);
		c.textAlign(textAlign);
		c.text(String.format("%d/%d", canvas.getAnimationStep()+1, canvas.getMaxAnimationStep()+1), x, y);
	}
}
