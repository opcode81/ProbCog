
# requires ALCHEMY_HOME to be set

import os
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
	
	def __init__(self, dbs, learning_params, infer_params, transformTestingPreds = None):
		self.dbs = dbs
		self.learning_params = learning_params
		self.infer_params = infer_params
		self.transformTestingPreds = transformTestingPreds
	
	def run(self, n):
		
		alchemy_path = os.path.join(os.getenv("ALCHEMY_HOME"), "bin")
		
		# determine folds
		self.folds = []
		dbsPerFold = len(dbs) / n
		i = 0
		while i < len(dbs):
			self.folds.append(dbs[i*dbsPerFold:(i+1)*dbsPerFold])
		
		for i in xrange(len(folds)):
			self._learn(i)			
			self._test(i)
	
	def _learn(self, i):
		trainingSet = self.folds[:i] + self.folds[i+1:]
		learnwts = os.path.join(alchemy_path, "learnwts")
		output_file = "wts.crosseval%d.mln" % i
		cmd = "%s %s -t %s -o %s" % (learnwts, self.learning_params, ",".join(trainingSet), output_file)
		os.system(cmd)

	def _test(self, i):
		testSet = self.folds[i]
		for test_db in testSet:
			infer = os.path.join(alchemy_path, "infer")
			results_file = "crosseval-%d-%s.results" % (i, test_db)
			tempDB = self.transformForTesting(test_db)
			cmd = "%s %s -i %s -e %s -r %s" % (infer, self.infer_params, output_file, tempDB)
			results = readAlchemyResults(results_file)
			self.evalResults(results, test_db)
	
	def transformForTesting(self, db):
		if self.transformTestingPreds is None:
			raise Exception("Don't know how to transform the database for testing")
		with file(db, "r") as f:
			content = f.read()
			for pred in self.transformForTestingPreds:
				content = re.sub(r"^%s\(.*?$" % pred, "", content, 0, re.MULTILINE)
		filename = "crosseval-%s" % db
		with file(filename, "w") as f:
			f.write(content)
		return filename
	
	def evalResults(self, results, testDB):
		raise Exception("must be overridden")
	

if __name__ == '__main__':
	class MyEval(MLNCrossEval):
		def __init__(self, dbs, learning_params, infer_params, transformTestingPreds = None):
			MLNCrossEval.__init__(self, dbs, learning_params, inferparams, transformTestingPreds=transformTestingPreds)
		
		def evalResults(self, results, testDB):
			evidence = readDBFile(testDB)
			print
			print testDB
			for r in results:
				isTrue, predName, constants = parseLiteral(r[0])
				if predName in self.transformTestingPreds:
					print " ", r,				
					if tuple(constants) in evidence[predName]:
						print " is ground truth"
					else:
						print
	
	dbs = ["db_1.db", "db_2.db"]
	ce = MyEval(dbs, "-g", "", ["sense"])
	ce.run(2)