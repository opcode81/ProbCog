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

from edu.tum.cs.srl.bayesnets.inference import BLNinfer

def infer(network, decls, logic, algorithm, evidence, queries, cwPreds = None, maxSteps = None, args=None):
	'''
		network: the model's fragment network (filename)
		decls: the model's declarations (filename)
		logic: the model's logical constraints (filename)
		algorithm: an algorithm name (use printInferenceAlgorithmList())
		evidence: an evidence database (filename)
		queries: a comma-separated list of queries (predicate names or fully or partially grounded atoms)
		cwPreds: a comma-separated list of closed-world predicates
		maxSteps: maximum number of steps to take		
		args: list of additional arguments to pass (see BLNinfer help)
		returns the BLNinfer object, which can be used to immediately obtain the results via .getResults() and other information
	'''
	a = ['-ia', algorithm, '-x', network, '-b', decls, '-l', logic, '-e', evidence, '-q', queries]
	if cwPreds is not None:
		a.extend(["-cw", cwPreds])
	if maxSteps is not None:
		a.extend(["-maxSteps", str(maxSteps)])
	a.extend(args)
	i = BLNinfer()
	i.readArgs(a)
	i.run()
	return i
	
# example usage
if __name__=='__main__':
	p = "../models/kitchen/tableSetting/"
	inf = infer(p+"meals_any_for_functional.adUt30w.learnt.xml", p+"meals_any_for.adUt.learnt.blog", p+"meals_any_for_functional.bln", 'LikelihoodWeighting', p+"rssdejan_scene4.blogdb", "usesAnyIn,consumesAnyIn,name", args=["--confidenceLevel=0.95"])
	for r in inf.getResults():
		print "%s" % r.varName
		for i in range(r.getDomainSize()):
			print "  %f  %s" % (r.probabilities[i], r.domainElements[i]),
			if r.additionalInfo is not None:
				interval = r.additionalInfo[i]
				print " [%f;%f]" % (interval.lowerEnd, interval.upperEnd),
			print
	print "time taken: %fs" % inf.getSamplingTime()
	print "steps taken: %d" % inf.getNumSteps()