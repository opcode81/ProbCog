package edu.tum.cs.vis;

import java.util.Vector;

public class AnimatedCanvas extends Canvas {

	private static final long serialVersionUID = 1L;
	
	protected int animationStep = 0, maxAnimationStep = 0;
	protected Vector<DrawableAnimated> animatedItems = new Vector<DrawableAnimated>();

	@Override
	public void keyPressed() {
		if(keyCode == RIGHT)
			animationStepForward();
		if(keyCode == LEFT)
			animationStepBackward();
	}
	
	public void animationStepForward() {
		if(animationStep < maxAnimationStep)
			animationStep++;
		redraw();
	}
	
	public void animationStepBackward() {
		if(animationStep > 0)
			animationStep--;
		redraw();
	}
	
	public void animationReset() {
		animationStep = 0;
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
