from jyimportlib import importjar, importbin, importdir
importbin()
importjar("srldb.jar")
importjar("tumutils.jar")
importjar("weka_fipm.jar")
#importdir("../WEKA/bin")

from java.util import Vector, HashMap
from java.lang import String, Double
import jarray
from weka.classifiers.trees import J48;
from weka.classifiers.functions import SMO;
from weka.classifiers.trees.j48 import Rule;
from weka.core import Attribute, FastVector, Instance, Instances

class WekaClassifier(object):
	def __init__(self, numericAttributes = None):		
		self.attName2Domain = {}
		self.numericAttributes = []
		if numericAttributes is not None:
			self.numericAttributes = list(numericAttributes)
		self.instances = []
	
	def setNumericAttribute(self, attName):
		self.numericAttributes.append(attName)
	
	def addInstance(self, i):
		for (attName, value) in i.iteritems():
			if attName not in self.numericAttributes:
				if attName not in self.attName2Domain:
					self.attName2Domain[attName] = set([value])
				else:
					self.attName2Domain[attName].add(value)
		self.instances.append(i)

	def _makeInstance(self, i, instances=None):
		inst = Instance(len(i))
		if instances is not None:
			inst.setDataset(instances)
		for (attName, value) in i.iteritems():
			if attName in self.numericAttributes: value = Double(value)
			else: value = String(value)
			attr = self.attName2Obj[attName]
			inst.setValue(attr, value)
		return inst

	def _getInstances(self, classAttr):
		# create attributes
		self.classAttr = classAttr
		attName2Obj = {}
		attVector = FastVector()
		for attName in self.numericAttributes:
			attr = Attribute(attName)
			attVector.addElement(attr)
			attName2Obj[attName] = attr
		for (attName, domain) in self.attName2Domain.iteritems():
			vDomain = FastVector(len(domain))
			for v in domain:
				vDomain.addElement(String(v))
			attr = Attribute(attName, vDomain)
			attVector.addElement(attr)
			attName2Obj[attName] = attr
		self.attName2Obj = attName2Obj
		
		# create Instances object
		instances = Instances("instances", attVector, len(self.instances))
		for i in self.instances:
			inst = self._makeInstance(i)
			instances.add(inst)
			
		instances.setClass(attName2Obj[classAttr])
		return instances

	def classify(self, instance):
		if type(instance) == dict: instance = self._makeInstance(instance, self.instances)		
		return self.attName2Obj[self.classAttr].value(int(self.classifier.classifyInstance(instance)))

class DecisionTree(WekaClassifier):
	def __init__(self, numericAttributes=None):
		WekaClassifier.__init__(self, numericAttributes)
		
	def learn(self, classAttr, unpruned=False, minNumObj=2):
		self.instances = self._getInstances(classAttr)		
		j48 = J48()
		j48.setUnpruned(unpruned)
		j48.setMinNumObj(minNumObj);
		#self.j48.setConfidenceFactor(1.0)
		j48.buildClassifier(self.instances)
		self.classifier = j48	
		print j48
		
class SVM(WekaClassifier):
	def __init__(self, numericAttributes=None):
		WekaClassifier.__init__(self, numericAttributes)

	def learn(self, classAttr):
		self.instances = self._getInstances(classAttr)		
		svm = SMO()
		svm.buildClassifier(self.instances)
		self.classifier = svm		
		

if __name__=='__main__':
	inst = [		
		{"sex":"m", "subject":"CS"},
		{"sex":"f", "subject":"Phil"},
		{"sex":"m", "subject":"CS"}
	]
	test = [
		{"sex":"f", "subject":"Phil"},
		{"sex":"m", "subject":"CS"}
	]
	numericAttributes=[]
	classAttr = "subject"
	
	tree = DecisionTree(numericAttributes)
	for i in inst: tree.addInstance(i)
	tree.learn(classAttr, unpruned=True, minNumObj=0)
	
	svm = SVM(numericAttributes)
	for i in inst: svm.addInstance(i)
	svm.learn(classAttr)
	
	for j,model in enumerate((tree, svm)):
		print "\nmodel", j
		for i in test:
			#del i[classAttr]
			print model.classify(i)
	