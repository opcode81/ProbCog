
from jyimportlib import importjar, importbin, importdir
importjar("jahmm.jar")
from be.ac.ulg.montefiore.run.jahmm import OpdfGaussian, ObservationReal
from java.util import Vector
from java.lang import Double


class DiscreteDist(object):
	def __init__(self, varName, values):
		self.values = values
		self.varName = varName
		self.dist = [0.0 for i in xrange(len(values))]
	
	def normalize(self):
		Z = sum(self.dist)
		self.dist = map(lambda x: x / Z, self.dist)
		
	def addExample(self, d):
		idx = self.values.index(d[self.varName])
		self.dist[idx] += 1.0
		
	def finish(self):
		self.normalize()
		
	def density(self, v):
		return self.dist[self.values.index(v)]
		
class ContDist(object):
	def __init__(self, varName):
		self.varName = varName
		self.v = Vector()
		self.pdf = OpdfGaussian()
	
	def addExample(self, d):
		value = d[self.varName]
		self.v.add(ObservationReal(value))
		
	def finish(self):
		self.pdf.fit(self.v)
	
	def density(self, v):
		return self.pdf.probability(ObservationReal(v))

class NaiveBayesCont(object):
	def __init__(self, classAttr, classValues, discreteAttrs, contAttrs):
		self.classAttr = classAttr
		self.contAttrs = contAttrs
		self.discreteAttrs = discreteAttrs
		self.classValues = classValues
		
	def learn(self, data):
		self.pdfs = {}
		for attrName, domain in self.discreteAttrs.iteritems():
			self.pdfs[attrName] = [DiscreteDist(attrName, domain) for i in xrange(len(self.classValues))]
		for attrName in self.contAttrs:
			self.pdfs[attrName] = [ContDist(attrName) for i in xrange(len(self.classValues))]
		self.classPrior = DiscreteDist(self.classAttr, self.classValues)
		for d in data:
			self.classPrior.addExample(d)
			classValue = d[self.classAttr]
			idx = self.classValues.index(classValue)
			for attrName, pdfs in self.pdfs.iteritems():
				pdfs[idx].addExample(d)
		for pdfs in self.pdfs.values():
			for pdf in pdfs: pdf.finish()
	
	def classify(self, d):
		results = []
		best = 0.0
		bestIdx = None
		Z = 0.0
		for i, cls in enumerate(self.classValues):
			p = self.classPrior.density(self.classValues[i])
			for attr, pdfs in self.pdfs.iteritems():
				p *= pdfs[i].density(d[attr])
			results.append(p)
			if p > best:
				best = p
				bestIdx = i
			Z += p
		results = map(lambda x: x/Z, results)
		for i, cls in enumerate(self.classValues):
			print "%.10f %s" % (results[i], cls)
		return (bestIdx, self.classValues, results)
		
if __name__ == '__main__':
	nb = NaiveBayesCont("graduates", ["T", "F"], {"clever": ["T", "F"]}, ["age"])
	d = [
		{"graduates": "T", "clever": "T", "age": 30},
		{"graduates": "T", "clever": "T", "age": 27},
		{"graduates": "T", "clever": "T", "age": 31},
		{"graduates": "T", "clever": "T", "age": 28},
		{"graduates": "F", "clever": "F", "age": 30},
		{"graduates": "F", "clever": "F", "age": 30},
		{"graduates": "F", "clever": "T", "age": 25}
	]
	nb.learn(d)
	nb.classify({"age":40, "clever": "T"})
	print
	nb.classify({"age":30, "clever": "T"})
	print
	nb.classify({"age":26, "clever": "T"})
	print
	nb.classify({"age":20, "clever": "T"})
	print
	nb.classify({"age":29, "clever": "F"})