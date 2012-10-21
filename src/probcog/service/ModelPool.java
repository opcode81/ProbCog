/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import probcog.logic.parser.ParseException;

/**
 * Represents a pool of models.
 * @author Dominik Jain
 */
public class ModelPool {
	protected HashMap<String, Model> pool;
	protected File poolPath;
	
	public ModelPool(String poolFilename) throws IOException, ParseException, Exception {
		pool = new HashMap<String, Model>();
		
		File poolFile = new File(poolFilename);
		poolPath = poolFile.getParentFile();
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(true);
		factory.newSAXParser().parse(poolFile, new PoolReader());
	}
	
	public Model getModel(String name) {
		return pool.get(name);
	}
	
	/**
	 * Reader for XML-based format for pools of models 
	 */
	protected class PoolReader extends DefaultHandler implements ErrorHandler {
		protected class ModelData {
			String name, type, path;
			HashMap<String, String> files;
			HashMap<String, String> params;
			HashMap<String, String> constantMap;
			
			public ModelData() {
				files = new HashMap<String,String>();
				params = new HashMap<String,String>();
				constantMap = new HashMap<String,String>();
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
					checkFileTypes(new String[]{"network"});
					m = new MLNModel(name, new File(fPath, files.get("network")).getPath());
				}
				else
					throw new Exception(String.format("Unknown model type '%s'", type));
				m.setConstantMap(constantMap);
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
				currentModel.files.put(type, filename);
			}
			else if(qName.equals("constantMap")) {
				String from = attrs.getValue("from");
				String to = attrs.getValue("to");
				currentModel.constantMap.put(from, to);
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
		
		public void warning(SAXParseException e) throws SAXException {
			throw e;
		}

		public void error(SAXParseException e) throws SAXException {
			throw e;
		}

		public void fatalError(SAXParseException e) throws SAXException {
			throw e;
		}
	}
}
