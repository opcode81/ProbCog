from MLN import MLN, InferenceMethods
from math import exp, log
import random
import pickle

class StringBuffer:
	def __init__(self):
		self.buf = ""
	
	def write(self, s):
		self.buf += s
		
	def __str__(self):
		return self.buf

def createRandomMLN(numVars, numFeatures, maxCliqueSize, maxWeight, numProbConstraints):
	f = StringBuffer()
	# write declarations
	f.write("obj = {X}\n")
	vars = []
	for i in xrange(numVars):
		f.write("p%d(obj)\n" % i)
		vars.append("p%d(X)" % i)
	# write formulas
	for i in xrange(numFeatures):
		cliqueSize = random.randint(1, maxCliqueSize)
		clique = random.sample(vars, cliqueSize)
		lits = []
		for var in clique:
			lits.append("%s%s" % ({0: "!", 1: ""}[random.randint(0,1)], var))		
		#weight = log(random.uniform(1, exp(maxWeight)))
		weight = random.gauss(0, maxWeight/2)
		f.write("%f %s\n" % (weight, " v ".join(lits)))
	# write probability constraints
	pcVars = random.sample(vars, numProbConstraints)
	for v in pcVars:
		f.write("R(%s)=%f\n" % (v,random.uniform(0,1)))
		f.write("0 %s\n" % v)
	return (vars, str(f), pcVars)

def ipfpm_vs_mcsatpc(numExperiments, numVars, numFeatures, maxCliqueSize, maxWeight, numProbConstraints, ipfpmSubAlgorithm, ipfpmSubParams):
	ret = []
	# collect data
	for i in xrange(numExperiments):
		print "\nExperiment %d" % (i+1)
		
		# create MLN
		vars, mlnContent, pcVars = createRandomMLN(numVars, numFeatures, maxCliqueSize, maxWeight, numProbConstraints)
		#print mlnContent
		queryVars = list(set(vars).difference(pcVars))
		
		# run IPFP-M
		mln = MLN(mlnContent=mlnContent, verbose=False)
		mln.combine({})	
		ipfpmResults = mln.inferIPFPM(queryVars, details=True, inferenceMethod=ipfpmSubAlgorithm, inferenceParams=ipfpmSubParams)
		ipfpmData = mln.ipfpm.data
		print "  %s" % ipfpmResults
		
		# run MC-SAT-PC		
		mln = MLN(mlnContent=mlnContent, verbose=False)
		mln.combine({})	
		mcsatpcResults = mln.inferMCSAT(queryVars, maxSteps=10000, infoInterval=1000, resultsInterval=1000, verbose=False, details=False, keepResultsHistory=True, referenceResults=ipfpmResults, p=0.5)
		mcsatpcHistory = mln.mcsat.getResultsHistory()
		final = mcsatpcHistory[-1]
		print "  %s" % mcsatpcResults
		print " mean error: %f, max error: %f" % (final["reference_me"], final["reference_maxe"])		
		ret.append({"mlnContent": mlnContent, "mcsatpc": mcsatpcHistory, "ipfpm": {"results": ipfpmResults, "data": ipfpmData}})
	
	return ret

'''
How good is MC-SAT-PC in comparison to IPFP-M[exact] on small Ns?
How good is MC-SAT-PC in comparison to IPFP-M[MC-SAT] on larger Ns?
How does MC-SAT-PC compare to MC-SAT - do we need more steps to converge?
How good is MC-SAT-PC in our real-world example (entity res.) compared to IPFP-M[MC-SAT]?

Try implementing convergence of MC-SAT within IPFP-M[MC-SAT] (based computed MCMC probability of the formula that was last fitted)
'''

def collectData(numExperiments, numVars, useMCSAT):
	if useMCSAT:
		ipfpmSubAlgorithm = InferenceMethods.MCSAT
		ipfpmSubParams = dict(maxSteps=10000)
	else:
		ipfpmSubAlgorithm = InferenceMethods.exact
		ipfpmSubParams = None
	N = numVars
	data = ipfpm_vs_mcsatpc(1, N, N, min(N,10), log(50), N/2, ipfpmSubAlgorithm, ipfpmSubParams)
	filename = "ipfpm(%s)_vs_mcsatpc_%dvars.pickle" % (InferenceMethods._names[ipfpmSubAlgorithm], N)
	pickle.dump(data, file(filename, "wb"))

if __name__=='__main__':
	collectData(4, 4, False)