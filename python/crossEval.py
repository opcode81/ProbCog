
# requires ALCHEMY_HOME to be set

import os
import sys
import re
from mlnQueryTool import readAlchemyResults
from MLN.util import stripComments, parseLiteral

def readDBFile(dbfile):
	# read file
	f = file(dbfile, "r")
	db = f.read()
	f.close()
	db = stripComments(db)
	# expand domains with db constants and save evidence
	evidence = {}
	for l in db.split("\n"):
		l = l.strip()
		if l == "":
			continue
		# literal
		else:              
			isTrue, predName, constants = parseLiteral(l)
			# save evidence
			if predName not in evidence: evidence[predName] = {}
			evidence[predName][tuple(constants)] = isTrue		
	return evidence

class MLNCrossEval(object):
	'''
		n-fold cross-validation for learning from multiple databases with Alchemy
	'''
	
	def __init__(self, dbs, learning_params, infer_params, location, transformTestingPreds = None):
		self.dbs = dbs
		self.learning_params = learning_params
		self.infer_params = infer_params
		self.transformTestingPreds = transformTestingPreds
		self.location = location
	
	def run(self, n):
		
		if not os.path.exists(self.location+"crossmlns"):
			os.mkdir(self.location+"crossmlns")
		
		if not os.path.exists(self.location+"crossdb"):
			os.mkdir(self.location+"crossdb")
			
		if not os.path.exists(self.location+"crossresults"):
			os.mkdir(self.location+"crossresults")
		
		# determine folds
		self.folds = []
		dbsPerFold = len(self.dbs) / n
		
		i = 0
		while i < n:
			self.folds.append(self.dbs[i*dbsPerFold:(i+1)*dbsPerFold])
			i = i+1
			
		self.folds[-1]+=self.dbs[i*dbsPerFold:]	
			
		for i in xrange(len(self.folds)):
			self._learn(i)
			self._test(i)
	
	def _learn(self, i):
		alchemy_path = os.path.join(os.getenv("ALCHEMY_HOME"), "bin")
		trainingSet = reduce(lambda x,y : x+y,self.folds[:i] + self.folds[i+1:])
		dbs = ",".join(map(lambda x: self.location+"truthdb/" + x, trainingSet))
		
		learnwts = os.path.join(alchemy_path, "learnwts")
		output_file = self.location+"crossmlns/wts.crosseval%d.mln" % i
		cmd = "%s %s -t %s -o %s" % (learnwts, self.learning_params, dbs, output_file)
		print cmd
		os.system(cmd)

	def _test(self, i):
		alchemy_path = os.path.join(os.getenv("ALCHEMY_HOME"), "bin")
		testSet = self.folds[i]
		for test_db in testSet:
			infer = os.path.join(alchemy_path, "infer") # TODO could use MLNInfer class for this
			results_file = self.location+"crossresults/crosseval-%d-%s.results" % (i, test_db)
			tempDB = self.transformForTesting(test_db)
			cmd = "%s %s -i %s -e %s -r %s" % (infer, self.infer_params, self.location+"crossmlns/wts.crosseval%d.mln" % i,tempDB,results_file)
			print cmd
			os.system(cmd)
			results = readAlchemyResults(results_file)
			self.evalResults(results, test_db)
			#sys.exit(0)
	
	def transformForTesting(self, db):
		print db
		if self.transformTestingPreds is None:
			raise Exception("Don't know how to transform the database for testing")
		with file(self.location+"truthdb/"+db, "r") as f:
			content = f.read()
			for pred in self.transformTestingPreds:
				content = re.sub(r"%s\(.*?\)" % pred, "", content)
		filename = self.location+"crossdb/crosseval-%s" % db
		with file(filename, "w") as f:
			f.write(content)
		return filename
	
	def evalResults(self, results, testDB):
		raise Exception("must be overridden")