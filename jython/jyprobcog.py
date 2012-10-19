# -*- coding: iso-8859-1 -*-
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
importjar("yprolog.jar")

from probcog.service import ModelPool as _ModelPool, BLNModel as _BLNModel, MLNModel as _MLNModel
from java.util import Vector, HashMap
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

class Evidence(object):
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

class ModelPool(object):
    def __init__(self, filename):
        self.pool = _ModelPool(filename)
    
    def query(self, modelName, queries, evidence, **params):
        '''
            modelName: the name of a model
            queries: a list of queries
            evidence: an Evidence instance or a list of string literals
        '''
        model = self.pool.getModel(modelName)
        return Model(model).query(queries, evidence, **params)
    
    def getModel(self, modelName):
        self.pool.getModel(modelName)

class Model(object):
    def __init__(self, model):
        self.model = model
        
    def query(self, queries, evidence, **params):
        '''
            queries: a query (string) or list of queries
            evidence: an Evidence instance or a list of string literals
            params: additional parameters that are passed on to the inference procedure
        '''
        if type(queries) == str:
            queries = [queries]
        javaParams = HashMap()
        for key, value in params.iteritems():
            javaParams.put(key, String(str(value)))
        model = self.model
        model.beginSession(javaParams)
        if not isinstance(evidence, Evidence):
            evidence = Evidence(*evidence)
        model.setEvidence(evidence.getData())
        model.instantiate()
        return model.infer(queries)

class BLNModel(Model):
    def __init__(self, declsFile, networkFile, logicFile):
        name = "%s-%s-%s" % (declsFile, networkFile, logicFile)
        Model.__init__(self, _BLNModel(name, declsFile, networkFile, logicFile))

class MLNModel(Model):
    def __init__(self, networkFile):
        Model.__init__(self, _MLNModel(networkFile, networkFile))

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
        print "\n%s:" % modelName
        for result in pool.query(modelName, ["alarm(x)", "burglary(Stefan)"], evidence, verbose=False):
            print result

    # query meals BLN using time-limited inference
    queries = ["mealT", "usesAnyIn(x,Bowl,M)"]
    evidence = ["takesPartIn(P, M)", "takesPartIn(P2, M)", "consumesAnyIn(P, Cereals, M)"]
    params = {
        "verbose": True,
        "inferenceMethod": "BackwardSampling",
        "timeLimit": 5.0,
        "infoTime": 1.0,
    }
    pool.query("meals_bln", queries, evidence, **params)    

    # query smokers MLN (not in pool)
    mln = MLNModel("../examples/smokers/smoking.mln")
    for result in mln.query("Smokes(Anna)", ["Smokes(Bob)", "Friends(Anna,Bob)"], verbose=False):
        print result
