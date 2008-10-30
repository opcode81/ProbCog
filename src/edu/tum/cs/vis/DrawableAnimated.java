package edu.tum.cs.vis;

public interface DrawableAnimated {
	public void draw(Canvas c, int step);
	public int getMaxStep();
}
