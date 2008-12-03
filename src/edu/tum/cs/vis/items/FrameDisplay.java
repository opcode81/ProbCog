package edu.tum.cs.vis.items;

import edu.tum.cs.vis.AnimatedCanvas;
import edu.tum.cs.vis.Canvas;
import edu.tum.cs.vis.Drawable;

public class FrameDisplay implements Drawable {

	protected AnimatedCanvas canvas;
	protected int x, y, textAlign;
	
	public FrameDisplay(AnimatedCanvas c, int x, int y, int textAlign) {
		this.canvas = c;
		this.x = x;
		this.y = y;
		this.textAlign = textAlign;
	}
	
	public void draw(Canvas c) {
		c.fill(0xffffffff);
		c.textMode(Canvas.SCREEN);
		c.textAlign(textAlign);
		c.text(String.format("%d/%d", canvas.getAnimationStep()+1, canvas.getMaxAnimationStep()+1), x, y);
	}
}
