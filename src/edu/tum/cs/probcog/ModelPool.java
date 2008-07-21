package edu.tum.cs.probcog;

import java.io.File;
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
	protected File poolPath;
	
	public ModelPool(String poolFilename) throws IOException, ParseException, Exception {
		pool = new HashMap<String, Model>();
		
		File poolFile = new File(poolFilename);
		poolPath = poolFile.getParentFile();
		
		XMLReader xr = XMLReaderFactory.createXMLReader();
		PoolReader handler = new PoolReader();
		xr.setContentHandler(handler);
		xr.setErrorHandler(handler);
	    FileReader r = new FileReader(poolFile);
		xr.parse(new InputSource(r));			 
	}
	
	public Model getModel(String name) {
		return pool.get(name);
	}
	
	protected class PoolReader extends DefaultHandler {
		protected class ModelData {
			String name, type, path;
			HashMap<String, String> files;
			HashMap<String, String> params;
			
			public ModelData() {
				files = new HashMap<String,String>();
			}
			
			private void checkFileTypes(String[] requiredTypes) throws Exception {
				for(String t : requiredTypes) {
					if(!files.containsKey(t))
						throw new Exception(String.format("Missing file of type '%s' for model '%s'", t, name));
				}
			}
			
			public Model instantiate() throws Exception {
				Model m; 
				if(name == null)
					throw new Exception("Model has no 'name' attribute.");
				System.out.println("Loading model " + name + "...");
				// get model path
				File fPath;
				if(path == null)
					fPath = poolPath;
				else
					fPath = new File(path);
				if(!fPath.isAbsolute())
					fPath = new File(poolPath, path);
				// instantiate
				if(type.equals("BLN")) {
					checkFileTypes(new String[]{"network", "decls", "logic"});
					m = new BLNModel(name, new File(fPath, files.get("decls")).getPath(), new File(fPath, files.get("network")).getPath(), new File(fPath, files.get("logic")).getPath());
				}
				else if(type.equals("MLN")) {
					throw new RuntimeException("MLNs not yet supported");
				}
				else
					throw new Exception(String.format("Unknown model type '%s'", type));
				return m;
			}
		}

		ModelData currentModel;
		
		public void startElement(String uri, String name, String qName, Attributes attrs) {
			if(qName.equals("model")) {
				currentModel = new ModelData();
				for(int i = 0; i < attrs.getLength(); i++) {
					String attrName = attrs.getQName(i);
					if(attrName.equals("name")) 
						currentModel.name = attrs.getValue(i);
					else if(attrName.equals("type"))
						currentModel.type = attrs.getValue(i);
					else if(attrName.equals("path"))
						currentModel.path = attrs.getValue(i); 
					else
						throw new RuntimeException(String.format("Unhandled attribute '%s' of model.", attrName));
				}
			}
			else if(qName.equals("file")) {
				String type = attrs.getValue("type");
				String filename = attrs.getValue("name");
				if(type == null || filename == null)
					throw new RuntimeException("The 'filename' tag must have 'type' and 'name' attributes.");
				currentModel.files.put(type, filename);
			}
		}
	
		public void endElement(String uri, String name, String qName) {
			try {
				if(qName.equals("model"))
					pool.put(currentModel.name, currentModel.instantiate());				
			}
			catch (Exception e) {
				throw new RuntimeException(e.getMessage());					
			}
		}
	}
}
