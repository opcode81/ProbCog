import weka.classifiers.trees.J48;

import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.srldb.Object;
import edu.tum.cs.srldb.datadict.DDAttribute;
import edu.tum.cs.srldb.datadict.domain.Domain;
import edu.tum.cs.srldb.datadict.DDException;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map.Entry;

public class J48Reader {
	
	public static void main(String[] args){
		try{
			String path = "/usr/stud/waldhers/models/pointcloud/";
			//String dbdir = path + "zoli4uli4";
			String dbdir = path + args[1];
			J48 j48 = readJ48(dbdir);
			Instances instances = readDB(args[0]);
			//System.out.println(j48.toString());
			for (int i = 0; i < instances.numInstances(); i++){
				Instance inst = instances.instance(i);
				//System.out.println(instances.toString());
				/*
				double d = j48.classifyInstance(inst);
				System.out.println(inst.attribute(inst.classIndex()).toString());
				inst.setValue(inst.attribute(inst.classIndex()),d);
				System.out.println("Object is classified as "+ inst.toString(inst.attribute(inst.classIndex())));
				*/
				double dist[] = j48.distributionForInstance(inst);
				int j = 0;
				for (double d : dist){
					Attribute att = instances.attribute(instances.classIndex());
					String classification = att.value(j);
					System.out.println(d + "  "+ classification);
					j++;
				}
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public static J48 readJ48(String dbdir) throws IOException, ClassNotFoundException{
		ObjectInputStream objstream = new ObjectInputStream(new FileInputStream(dbdir + "/pcc.j48"));
		J48 j48 = (J48) objstream.readObject();
		objstream.close();
		return j48;
	}
	
	public static Instances readDB(String dbname) throws IOException, ClassNotFoundException, DDException, FileNotFoundException, Exception{
		Database db = Database.fromFile(new FileInputStream(dbname));
		edu.tum.cs.srldb.datadict.DataDictionary dd = db.getDataDictionary();
		//the vector of attributes
		FastVector fvAttribs = new FastVector();
		HashMap<String,Attribute> mapAttrs = new HashMap<String,Attribute>();
		for(DDAttribute attribute : dd.getObject("object").getAttributes().values()){
			if(attribute.isDiscarded() && !attribute.getName().equals("objectT")){
				continue;
			}
			FastVector attValues = new FastVector();
			Domain dom = attribute.getDomain();
			for(String s : dom.getValues())
				attValues.addElement(s);
			Attribute attr = new Attribute(attribute.getName(), attValues);				
			fvAttribs.addElement(attr);
			mapAttrs.put(attribute.getName(), attr);
		}
		
		Instances instances = new Instances("name",fvAttribs,10000);
		instances.setClass(mapAttrs.get("objectT"));
		//for each object add an instance
		for(Object o : db.getObjects()){
			if (o.hasAttribute("objectT")){
				Instance instance = new Instance(fvAttribs.size());
				for(Entry<String,String> e : o.getAttributes().entrySet()) {
					if (!dd.getAttribute(e.getKey()).isDiscarded()){
						instance.setValue(mapAttrs.get(e.getKey()), e.getValue());
					}		
				}
				instances.add(instance);
			}			
		}
		return instances;
	}
}
