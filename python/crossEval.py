
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
	
	def __init__(self, dbs, learning_params, infer_params, transformTestingPreds = None):
		self.dbs = dbs
		self.learning_params = learning_params
		self.infer_params = infer_params
		self.transformTestingPreds = transformTestingPreds
	
	def run(self, n):
		
		# determine folds
		self.folds = []
		dbsPerFold = len(dbs) / n
		i = 0
		while i < len(dbs):
			self.folds.append(dbs[i*dbsPerFold:(i+1)*dbsPerFold])
			i = i+1
			
		for i in xrange(len(self.folds)):
			self._learn(i)			
			self._test(i)
	
	def _learn(self, i):
		alchemy_path = os.path.join(os.getenv("ALCHEMY_HOME"), "bin")
		trainingSet = reduce(lambda x,y : x+y,self.folds[:i] + self.folds[i+1:])
		learnwts = os.path.join(alchemy_path, "learnwts")
		output_file = "wts.crosseval%d.mln" % i
		cmd = "%s %s -t %s -o %s" % (learnwts, self.learning_params, ",".join(trainingSet), output_file)
		print cmd
		os.system(cmd)

	def _test(self, i):
		alchemy_path = os.path.join(os.getenv("ALCHEMY_HOME"), "bin")
		testSet = self.folds[i]
		for test_db in testSet:
			infer = os.path.join(alchemy_path, "infer")
			results_file = "crosseval-%d-%s.results" % (i, test_db)
			tempDB = self.transformForTesting(test_db)
			cmd = "%s %s -i %s -e %s -r %s" % (infer, self.infer_params, "wts.crosseval%d.mln" % i,tempDB,results_file)
			print cmd
			os.system(cmd)
			results = readAlchemyResults(results_file)
			self.evalResults(results, test_db)
			sys.exit(0)
	
	def transformForTesting(self, db):
		if self.transformTestingPreds is None:
			raise Exception("Don't know how to transform the database for testing")
		with file(db, "r") as f:
			content = f.read()
			for pred in self.transformTestingPreds:
				content = re.sub(r"%s\(.*?\)" % pred, "", content)
		filename = "crosseval-%s" % db
		with file(filename, "w") as f:
			f.write(content)
		return filename
	
	def evalResults(self, results, testDB):
		raise Exception("must be overridden")
	

if __name__ == '__main__':
	class MyEval(MLNCrossEval):
		def __init__(self, dbs, learning_params, infer_params, transformTestingPreds = None):
			MLNCrossEval.__init__(self, dbs, learning_params, infer_params, transformTestingPreds=transformTestingPreds)
		
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
	
	dbs = "db_0.db,db_1.db,db_2.db,db_3.db,db_4.db,db_5.db,db_6.db,db_7.db,db_8.db,db_9.db,db_10.db,db_11.db,db_12.db,db_13.db,db_14.db,db_15.db,db_16.db,db_17.db,db_18.db,db_19.db,db_20.db,db_21.db,db_22.db,db_23.db,db_24.db,db_25.db,db_26.db,db_27.db,db_28.db,db_29.db,db_30.db,db_31.db,db_32.db,db_33.db,db_34.db,db_35.db,db_36.db,db_37.db,db_38.db,db_39.db,db_40.db,db_41.db,db_42.db,db_43.db,db_44.db,db_45.db,db_46.db,db_47.db,db_48.db,db_49.db,db_50.db,db_51.db,db_52.db,db_53.db,db_54.db,db_55.db,db_56.db,db_57.db,db_58.db,db_59.db,db_60.db,db_61.db,db_62.db,db_63.db,db_64.db,db_65.db,db_66.db,db_67.db,db_68.db,db_69.db,db_70.db,db_71.db,db_72.db,db_73.db,db_74.db,db_75.db,db_76.db,db_77.db,db_78.db,db_79.db,db_80.db,db_81.db,db_82.db,db_83.db,db_84.db,db_85.db,db_86.db,db_87.db,db_88.db,db_89.db,db_90.db,db_91.db,db_92.db,db_93.db,db_94.db,db_95.db,db_96.db,db_97.db,db_98.db,db_99.db,db_100.db,db_101.db,db_102.db,db_103.db,db_104.db,db_105.db,db_106.db,db_107.db,db_108.db,db_109.db,db_110.db,db_111.db,db_112.db,db_113.db".split(",")
	ce = MyEval(dbs[:40], "-g -multipleDatabases -i wsd.mln", "-q sense", ["sense"])
	ce.run(2)