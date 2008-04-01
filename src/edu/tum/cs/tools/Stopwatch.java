package edu.tum.cs.tools;

public class Stopwatch {
    
    private long startTime = 0;
    private long stopTime = 0;
    private boolean running = false;
    
    public void start() {
        this.startTime = System.currentTimeMillis();
        this.running = true;
    }
    
    public void stop() {
        this.stopTime = System.currentTimeMillis();
        this.running = false;
    }
    
    /**
     * gets elapsed time in msecs
     * @return
     */
    public long getElapsedTime() {
        if(running)
        	return System.currentTimeMillis() - startTime;
        else
            return stopTime - startTime;
    }    
    
    /**
     * gets elapsed time in seconds
     * @return
     */
    public double getElapsedTimeSecs() {
    	return (double)getElapsedTime() / 1000;
    }
}