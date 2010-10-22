import sys
import os
import re
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

def parseLiteral(line):    
    '''
        parses a literal such as !p(A,B) or p(A,B)=False and returns a tuple where the first item is the function/predicate, then follow the arguments,
        and finally the value, e.g. (False, "p", ["A", "B"])
    '''
    # try regular MLN syntax
    m = re.match(r'(!?)(\w+)\((.*?)\)$', line)
    if m is not None:
        return [m.group(2)] + map(str.strip, m.group(3).split(",")) + [str(m.group(1) != "!")]
    # try BLOG syntax where instead of p(A,B) we have p(A)=B or instead of !q(A) we have q(A)=False
    m = re.match(r'(\w+)\((.*?)\)\s*=\s*(\w+)$', line)
    if m is not None:
        params = map(str.strip, m.group(2).split(","))
        value = m.group(3).strip()
        isTrue = True
        if value == 'True':
            pass
        elif value == 'False':
            isTrue = False
        else:
            params.append(value)
        return [m.group(1)] + params + [str(isTrue)]
    raise Exception("Could not parse evidence entry '%s'" % line)

class Evidence:
	def __init__(self, *literals):
		self.vector = Vector()
		self.addAll(*literals)
	
	def add(self, *strings):
		'''
			adds a single piece of evidence. You may pass
				* a list of strings that directly use the probcog evidence format, i.e. a list (function, arg1, arg2, value)
				* a single string with an MLN-style literal definition, e.g. "function(arg1,arg2,value)" or "!pred(arg1)"
				* a single string with a single BLOG-style assignment, e.g. "function(arg1,arg2)=value"
		'''
		if len(strings) == 1:
			strings = parseLiteral(strings[0].strip())
		jarr = jarray.array(strings, String)
		self.vector.add(jarr)
	
	def addAll(self, *literals):
		for lit in literals:
			self.add(lit)
	
	def getData(self):
		return self.vector
	
	def clear(self):
		self.vector.clear()

def query(pool, modelName, queries, evidence):
	'''
		pool: a ModelPool instance
		modelName: the name of a model
		queries: a list of queries
		evidence: an Evidence instance or a list of string literals
	'''
	model = pool.getModel(modelName)
	if type(evidence) != Evidence:
		evidence = Evidence(*evidence)
	model.setEvidence(evidence.getData())
	model.instantiate()
	return model.infer(queries)

# example usage
if __name__=='__main__':
	# load pool of models
	pool = ModelPool("../examples/examples.pool.xml")
	
	# query alarm BLN and MLN models
	evidence = [
		"livesIn(James, Yorkshire)",
		"livesIn(Stefan, Freiburg)",
		"burglary(James)",
		"tornado(Freiburg)",
		"neighborhood(James, Average)",
		"neighborhood(Stefan, Bad)"]	
	for modelName in ("alarm_mln", "alarm_bln"):
		print "\n\n%s:" % modelName
		for result in query(pool, modelName, ["alarm(x)", "burglary(Stefan)"], evidence):
			print result
	
	# query meals BLN by using the pool directly
	evidence = Evidence()
	evidence.add("takesPartIn", "P", "M")
	evidence.add("takesPartIn", "P2", "M")
	evidence.add("consumesAnyIn", "P", "Cereals", "M")	
	model = pool.getModel("meals_bln")
	model.setEvidence(evidence.getData())
	model.instantiate()
	for result in model.infer(["mealT", "usesAnyIn(x,Bowl,M)"]):
		print result
