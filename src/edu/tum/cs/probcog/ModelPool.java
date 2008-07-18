package edu.tum.cs.probcog;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import edu.tum.cs.logic.parser.ParseException;

public class ModelPool {
	protected HashMap<String, Model> pool;
	
	public ModelPool(String poolFilename) throws IOException, ParseException, Exception {
		pool = new HashMap<String, Model>();
		
		/*
		XMLReader xr = XMLReaderFactory.createXMLReader();
		PoolReader handler = new PoolReader();
		xr.setContentHandler(handler);
		xr.setErrorHandler(handler);
	    FileReader r = new FileReader(poolFilename);
		xr.parse(new InputSource(r));
		*/		 
		
		Model m = new BLNModel("tableSetting", "/usr/stud/waldhers/kitchen/new/meals_any_for.blog", "/usr/stud/waldhers/kitchen/new/meals_any_for.learnt.xml", "/usr/stud/waldhers/kitchen/new/meals_any_for.bln");
		pool.put(m.getName(), m);
	}
	
	public Model getModel(String name) {
		return pool.get(name);
	}
	
	public class PoolReader extends DefaultHandler {
		public class Model {
			
		}
		//enum ModelType {BLN, MLN}; 
		
		Model currentModel;
		
		public void startElement(String uri, String name, String qName, Attributes atts) {
			if(qName.equals("model")) {
				
			}
		}
	
		public void endElement(String uri, String name, String qName) {
			if(qName.equals("model")) {
				
			}
		}
	}
}
