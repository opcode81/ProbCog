package edu.tum.cs.vis;

import java.util.Vector;

public class AnimatedCanvas extends Canvas {

	private static final long serialVersionUID = 1L;
	
	protected int animationStep = 0, maxAnimationStep = 0;
	protected Vector<DrawableAnimated> animatedItems = new Vector<DrawableAnimated>();

	@Override
	public void keyPressed() {
		if (keyCode == RIGHT) {
			// step forward
			if(animationStep < maxAnimationStep)
				animationStep++;
			else
				return;
		}

		if(keyCode == LEFT) {
			// check if at the beginning of the sequence
			if(animationStep == 0)
				return;

			// step back
			animationStep--;
		}

		redraw();
	}
	
	@Override
	public void drawItems() {
		super.drawItems();
		
		for(DrawableAnimated d : animatedItems) {
			d.draw(this, animationStep);
		}
	}
	
	public void addAnimated(DrawableAnimated item) {
		maxAnimationStep = Math.max(maxAnimationStep, item.getMaxStep());
		animatedItems.add(item);
	}
}
