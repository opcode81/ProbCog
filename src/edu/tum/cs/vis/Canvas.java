package edu.tum.cs.vis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Label;
import java.awt.SystemColor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Vector;

import processing.core.PApplet;
import processing.core.PFont;
import edu.tum.cs.tools.Vector3f;


public class Canvas extends PApplet implements MouseListener,
		MouseMotionListener {

	static final long serialVersionUID = 0;

	/**
	 * dimensions of the display
	 */
	protected int width = 800, height = 600; 
	
	// init values for the display and mouse interaction
	protected float leftMouseX = -1.0f, leftMouseY = -1.0f, rightMouseX = -1.0f, rightMouseY = -1.0f, centerMouseY = -1.0f;
	protected float xRotDisplay = -106.25027f, yRotDisplay = 0.020062504f;
	protected float xShiftDisplay = 103f, zShiftDisplay = 162f;
	protected float zoomDisplay = 0.13f;
	
	/*
	float leftMouseX = -1.0f, leftMouseY = -1.0f, rightMouseX = -1.0f,
	rightMouseY = -1.0f, centerMouseY = -1.0f;
	float xRotDisplay = 24.4f, yRotDisplay = -14.8f, xShiftDisplay = 96.0f,
	zShiftDisplay = -315.5f, zoomDisplay = 2.12f;
	*/
	
	protected float sceneSize = 4000;	
	protected Vector3f eye, eyeTarget, eyeUp;

	public static final boolean useCamera = true;

	Vector<Drawable> items = new Vector<Drawable>();

	public void setWidth(int width) {
		this.width = width;
	}
	
	public void setHeight(int height) {
		this.height = height;
	}
	
	public void setSceneSize(float size) {
		this.sceneSize = size;
		this.zoomDisplay = 400 / sceneSize;
	}
	
	public Canvas() {
		eye = new Vector3f(0.0f,-50f,0f);
		eyeUp = new Vector3f(0,0,1);
		eyeTarget = new Vector3f(0,0,0);
	}
	
	public void setup() {

		size(width, height, P3D);
		lights();
		
		//xShiftDisplay = sceneSize/2;
		//zShiftDisplay = sceneSize/2;

		PFont font = createFont("Verdana", 11);
		textFont(font);

		ellipseMode(RADIUS);
		frameRate(20);

		background(255, 255, 255);

		noLoop();
		draw();
	}

	public void draw() {

		background(60, 60, 60);
		cursor(CROSS);
		
		if(!useCamera) {
			pushMatrix();
			translate(width / 4.0f, height / 1.5f, -400.0f);
	
			lights();
			pushMatrix();	
			
			rotateZ(PI / 2);
			rotateY(-PI / 2);
			translate(0.0f, zShiftDisplay, xShiftDisplay);
	
			rotateZ(radians(xRotDisplay));
			rotateX(radians(yRotDisplay));
			
			scale(zoomDisplay);
			
			//System.out.println("zoom: " + zoomDisplay + " zShift: " + zShiftDisplay + " xShift: " + xShiftDisplay + " xRot: " + xRotDisplay + " yRot: " + yRotDisplay);
		}
		else
			setCamera();

		//scale(1000);
		drawItems();

		if(useCamera)
			;//setCamera();
		else {
			popMatrix();
	
			popMatrix();
		}
	}
	
	protected void setCamera() {
		//beginCamera();
		camera(eye.x, eye.y, eye.z, eyeTarget.x, eyeTarget.y, eyeTarget.z, eyeUp.x, eyeUp.y, eyeUp.z);
		//camera(eye.x, eye.y, eye.z, eyeTarget.x, eyeTarget.y, eyeTarget.z, eye.x+eyeUp.x, eye.y+eyeUp.y, eye.z+eyeUp.z);
		//endCamera();
		
		System.out.println("eye: " + eye + " -> " + eyeTarget + "  up: " + eyeUp);
	}
	
	public void drawItems() {
		for(Drawable d : items)
			d.draw(this);		
	}
	
	public void drawLine(Vector3f p1, Vector3f p2, int color) {
		drawLine(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, color);
	}
	
	public void drawLine(float x1, float y1, float z1, float x2, float y2, float z2, int color) {
		stroke(color);
		line(x1,y1,z1,x2,y2,z2);
	}
	
	public void add(Drawable d) {
		this.items.add(d);
	}


	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////
	// 
	// EVENT HANDLER
	// 

	public void mousePressed(MouseEvent e) {

		// general: save mouse positions for calculating rotation and translation
		if (e.getButton() == MouseEvent.BUTTON1) {
			leftMouseX = e.getX();
			leftMouseY = e.getY();
		}
		else if (e.getButton() == MouseEvent.BUTTON3) {
			rightMouseX = e.getX();
			rightMouseY = e.getY();
		}
		else if (e.getButton() == MouseEvent.BUTTON2) {
			centerMouseY = e.getY();
		}

	}

	public void mouseReleased(MouseEvent e) {

		if (e.getButton() == MouseEvent.BUTTON1) { // reset the buffers
			leftMouseX = -1.0f;
			leftMouseY = -1.0f;
		}
		else if (e.getButton() == MouseEvent.BUTTON3) { // reset the buffers
			rightMouseX = -1.0f;
			rightMouseY = -1.0f;
		}
		else if (e.getButton() == MouseEvent.BUTTON2) {
			centerMouseY = -1.0f;
		}

	}

	public void mouseDragged(MouseEvent e) {

		if (leftMouseX != -1.0f) { // change rotation
			float dx = (e.getX() - leftMouseX) * 0.02f;
			float dy = (e.getY() - leftMouseY) * 0.02f;
			
			yRotDisplay += (e.getY() - leftMouseY) * 0.03;
			xRotDisplay += (e.getX() - leftMouseX) * 0.05;
			leftMouseX = e.getX();
			leftMouseY = e.getY();
			
			// translate eye, so that the origin is its target
			Vector3f negTarget = new Vector3f(eyeTarget);
			negTarget.negate();
			eye.add(negTarget);
			
			// rotation around vertical axis
			eye.rotate(dx, eyeUp);

			// rotation around horizontal axis
			Vector3f dir = new Vector3f(eyeTarget);
			dir.subtract(eye);
			Vector3f horDir = new Vector3f();
			horDir.cross(eyeUp, dir);			
			eye.rotate(dy, horDir);
			eyeUp.rotate(dy, horDir);
			eyeUp.normalize();
			
			// reverse translation
			eye.add(eyeTarget);
		}
		else if (rightMouseX != -1.0f) { // change translation
			float dx = (e.getX() - rightMouseX) * sceneSize / 1000;
			float dy = (e.getY() - rightMouseY) * sceneSize / 1000;
			
			xShiftDisplay += -(e.getY() - rightMouseY) * 0.5;
			zShiftDisplay += -(e.getX() - rightMouseX) * 0.5;
			rightMouseX = e.getX();
			rightMouseY = e.getY();
			
			// horizontal translation
			Vector3f dir = new Vector3f(eyeTarget);
			dir.subtract(eye);
			Vector3f horDir = new Vector3f();
			horDir.cross(eyeUp, dir);
			horDir.normalize();
			horDir.scale(dx);
			
			// vertical translation
			Vector3f vertDir = new Vector3f(eyeUp);
			vertDir.normalize();
			vertDir.scale(dy);
			vertDir.negate();
			//System.out.println("hor move: " + horDir);
			//System.out.println("vert mode: " + vertDir);
			
			eye.add(horDir);
			eye.add(vertDir);
			eyeTarget.add(horDir);
			eyeTarget.add(vertDir);
		}
		else if (centerMouseY != -1.0f) { // zoom
			float dy = (e.getY() - centerMouseY) * sceneSize / 1000;			
			
			zoomDisplay += -(e.getY() - centerMouseY) * sceneSize;
			if (zoomDisplay < 0.01) {
				zoomDisplay = 0.01f;
			}
			centerMouseY = e.getY();
			
			Vector3f dir = new Vector3f(eyeTarget);
			dir.subtract(eye);
			dir.normalize();
			dir.scale(dy);			
			eye.add(dir);
		}

		redraw();
	}


	/**
	 * runs this applet as a main application (copied from PApplet.main)
	 * @param args
	 */
	public void runMain() {
		// Disable abyssmally slow Sun renderer on OS X 10.5.
		if(platform == MACOSX) {
			// Only run this on OS X otherwise it can cause a permissions error.
			// http://dev.processing.org/bugs/show_bug.cgi?id=976
			System.setProperty("apple.awt.graphics.UseQuartz", "true");
		}

		/*if(args.length < 1) {
			System.err.println("Usage: PApplet <appletname>");
			System.err.println("For additional options, "
					+ "see the javadoc for PApplet");
			System.exit(1);
		}*/

		try {
			boolean external = false;
			int location[] = null;
			int editorLocation[] = null;

			String name = null;
			boolean present = false;
			Color backgroundColor = Color.black; //BLACK;
			Color stopColor = Color.gray; //GRAY;
			GraphicsDevice displayDevice = null;
			boolean hideStop = false;

			String param = null, value = null;

			// try to get the user folder. if running under java web start,
			// this may cause a security exception if the code is not signed.
			// http://processing.org/discourse/yabb_beta/YaBB.cgi?board=Integrate;action=display;num=1159386274
			String folder = null;
			try {
				folder = System.getProperty("user.dir");
			}
			catch (Exception e) {
			}

			/*
			int argIndex = 0;
			while(argIndex < args.length) {
				int equals = args[argIndex].indexOf('=');
				if(equals != -1) {
					param = args[argIndex].substring(0, equals);
					value = args[argIndex].substring(equals + 1);

					if(param.equals(ARGS_EDITOR_LOCATION)) {
						external = true;
						editorLocation = parseInt(split(value, ','));

					}
					else if(param.equals(ARGS_DISPLAY)) {
						int deviceIndex = Integer.parseInt(value) - 1;

						//DisplayMode dm = device.getDisplayMode();
						//if ((dm.getWidth() == 1024) && (dm.getHeight() == 768)) {

						GraphicsEnvironment environment = GraphicsEnvironment
								.getLocalGraphicsEnvironment();
						GraphicsDevice devices[] = environment
								.getScreenDevices();
						if((deviceIndex >= 0) && (deviceIndex < devices.length)) {
							displayDevice = devices[deviceIndex];
						}
						else {
							System.err.println("Display " + value
									+ " does not exist, "
									+ "using the default display instead.");
						}

					}
					else if(param.equals(ARGS_BGCOLOR)) {
						if(value.charAt(0) == '#')
							value = value.substring(1);
						backgroundColor = new Color(Integer.parseInt(value, 16));

					}
					else if(param.equals(ARGS_STOP_COLOR)) {
						if(value.charAt(0) == '#')
							value = value.substring(1);
						stopColor = new Color(Integer.parseInt(value, 16));

					}
					else if(param.equals(ARGS_SKETCH_FOLDER)) {
						folder = value;

					}
					else if(param.equals(ARGS_LOCATION)) {
						location = parseInt(split(value, ','));
					}

				}
				else {
					if(args[argIndex].equals(ARGS_PRESENT)) {
						present = true;

					}
					else if(args[argIndex].equals(ARGS_HIDE_STOP)) {
						hideStop = true;

					}
					else if(args[argIndex].equals(ARGS_EXTERNAL)) {
						external = true;

					}
					else {
						name = args[argIndex];
						break;
					}
				}
				argIndex++;
			}*/

			// Set this property before getting into any GUI init code
			//System.setProperty("com.apple.mrj.application.apple.menu.about.name", name);
			// This )*)(*@#$ Apple crap don't work no matter where you put it
			// (static method of the class, at the top of main, wherever)

			if(displayDevice == null) {
				GraphicsEnvironment environment = GraphicsEnvironment
						.getLocalGraphicsEnvironment();
				displayDevice = environment.getDefaultScreenDevice();
			}

			Frame frame = new Frame(displayDevice.getDefaultConfiguration());
			/*
			Frame frame = null;
			if (displayDevice != null) {
			  frame = new Frame(displayDevice.getDefaultConfiguration());
			} else {
			  frame = new Frame();
			}
			 */
			//Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			// remove the grow box by default
			// users who want it back can call frame.setResizable(true)
			frame.setResizable(false);

			// Set the trimmings around the image
			//!!!Image image = Toolkit.getDefaultToolkit().createImage(ICON_IMAGE);
			//frame.setIconImage(image);
			frame.setTitle(name);

			//	    Class c = Class.forName(name);
			//Class<?> c = Thread.currentThread().getContextClassLoader().loadClass(name);
			//PApplet applet = (PApplet) c.newInstance();
			PApplet applet = this;

			// these are needed before init/start
			applet.frame = frame;
			applet.sketchPath = folder;
			//applet.args = PApplet.subset(args, 1);
			//!!!applet.external = external;

			// For 0149, moving this code (up to the pack() method) before init().
			// For OpenGL (and perhaps other renderers in the future), a peer is
			// needed before a GLDrawable can be created. So pack() needs to be
			// called on the Frame before applet.init(), which itself calls size(),
			// and launches the Thread that will kick off setup().
			// http://dev.processing.org/bugs/show_bug.cgi?id=891
			// http://dev.processing.org/bugs/show_bug.cgi?id=908
			if(present) {
				frame.setUndecorated(true);
				frame.setBackground(backgroundColor);
				displayDevice.setFullScreenWindow(frame);
			}
			frame.setLayout(null);
			frame.add(applet);
			frame.pack();

			applet.init();

			// Wait until the applet has figured out its width.
			// In a static mode app, this will be after setup() has completed,
			// and the empty draw() has set "finished" to true.
			// TODO make sure this won't hang if the applet has an exception.
			while(applet.defaultSize && !applet.finished) {
				//System.out.println("default size");
				try {
					Thread.sleep(5);

				}
				catch (InterruptedException e) {
					//System.out.println("interrupt");
				}
			}
			//println("not default size " + applet.width + " " + applet.height);
			//println("  (g width/height is " + applet.g.width + "x" + applet.g.height + ")");

			if(present) {
				//	        frame.setUndecorated(true);
				//	        frame.setBackground(backgroundColor);
				//	        displayDevice.setFullScreenWindow(frame);

				//	        frame.add(applet);
				Dimension fullscreen = frame.getSize();
				applet.setBounds((fullscreen.width - applet.width) / 2,
						(fullscreen.height - applet.height) / 2, applet.width,
						applet.height);

				if(!hideStop) {
					Label label = new Label("stop");
					label.setForeground(stopColor);
					label.addMouseListener(new MouseAdapter() {
						public void mousePressed(MouseEvent e) {
							System.exit(0);
						}
					});
					frame.add(label);

					Dimension labelSize = label.getPreferredSize();
					// sometimes shows up truncated on mac
					//System.out.println("label width is " + labelSize.width);
					labelSize = new Dimension(100, labelSize.height);
					label.setSize(labelSize);
					label.setLocation(20, fullscreen.height - labelSize.height
							- 20);
				}

				// not always running externally when in present mode
				if(external) {
					applet.setupExternalMessages();
				}

			}
			else { // if not presenting
				// can't do pack earlier cuz present mode don't like it
				// (can't go full screen with a frame after calling pack)
				//	        frame.pack();  // get insets. get more.
				Insets insets = frame.getInsets();

				int windowW = Math.max(applet.width, MIN_WINDOW_WIDTH)
						+ insets.left + insets.right;
				int windowH = Math.max(applet.height, MIN_WINDOW_HEIGHT)
						+ insets.top + insets.bottom;

				frame.setSize(windowW, windowH);

				if(location != null) {
					// a specific location was received from PdeRuntime
					// (applet has been run more than once, user placed window)
					frame.setLocation(location[0], location[1]);

				}
				else if(external) {
					int locationX = editorLocation[0] - 20;
					int locationY = editorLocation[1];

					if(locationX - windowW > 10) {
						// if it fits to the left of the window
						frame.setLocation(locationX - windowW, locationY);

					}
					else { // doesn't fit
						// if it fits inside the editor window,
						// offset slightly from upper lefthand corner
						// so that it's plunked inside the text area
						locationX = editorLocation[0] + 66;
						locationY = editorLocation[1] + 66;

						if((locationX + windowW > applet.screen.width - 33)
								|| (locationY + windowH > applet.screen.height - 33)) {
							// otherwise center on screen
							locationX = (applet.screen.width - windowW) / 2;
							locationY = (applet.screen.height - windowH) / 2;
						}
						frame.setLocation(locationX, locationY);
					}
				}
				else { // just center on screen
					frame.setLocation((applet.screen.width - applet.width) / 2,
							(applet.screen.height - applet.height) / 2);
				}

				//	        frame.setLayout(null);
				//	        frame.add(applet);

				if(backgroundColor == Color.black) { //BLACK) {
					// this means no bg color unless specified
					backgroundColor = SystemColor.control;
				}
				frame.setBackground(backgroundColor);

				int usableWindowH = windowH - insets.top - insets.bottom;
				applet.setBounds((windowW - applet.width) / 2, insets.top
						+ (usableWindowH - applet.height) / 2, applet.width,
						applet.height);

				if(external) {
					applet.setupExternalMessages();

				}
				else { // !external
					frame.addWindowListener(new WindowAdapter() {
						public void windowClosing(WindowEvent e) {
							System.exit(0);
						}
					});
				}

				// handle frame resizing events
				applet.setupFrameResizeListener();

				// all set for rockin
				if(applet.displayable()) {
					frame.setVisible(true);
				}
			}

			//System.out.println("showing frame");
			//System.out.println("applet requesting focus");
			applet.requestFocus(); // ask for keydowns
			//System.out.println("exiting main()");

		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
