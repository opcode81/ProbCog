import sys
import os
from jyimportlib import importjar, importbin
importbin()
importjar("srldb.jar")
importjar("tumutils.jar")
importjar("bnj.jar")
importjar("bnj_res.jar")
importjar("log4j-1.2.9.jar")
importjar("ssj.jar")
importjar("optimization.jar")

from edu.tum.cs.probcog import ModelPool
from java.util import Vector
from java.lang import String
import jarray

class Evidence:
	def __init__(self):
		self.vector = Vector()
	
	def add(self, *strings):
		jarr = jarray.array(strings, String)
		self.vector.add(jarr)
	
	def getData(self):
		return self.vector
	
	def clear(self):
		self.vector.clear()

# example usage
if __name__=='__main__':
	# load pool of models
	pool = ModelPool("../examples/examples.pool.xml")
	# construct evidence
	evidence = Evidence()
	evidence.add("takesPartIn", "P", "M")
	evidence.add("takesPartIn", "P2", "M")
	evidence.add("consumesAnyIn", "P", "Cereals", "M")
	# query BLN
	model = pool.getModel("meals_bln")
	model.setEvidence(evidence.getData())
	model.instantiate()
	for result in model.infer(["mealT", "usesAnyIn(x,Bowl,M)"]):
		print result
	# query MLN
	model = pool.getModel("meals_mln")
	model.setEvidence(evidence.getData())
	model.instantiate()
	for result in model.infer(["mealT", "usesAnyIn(x,Bowl,M)"]):
		print result
