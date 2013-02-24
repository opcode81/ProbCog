# -*- coding: iso-8859-1 -*-

from mlnQueryTool import MLNInfer

inf = MLNInfer()
mlnFiles = ["wts.pybpll.smoking-train-smoking.mln"]
db = "smoking-test-smaller.db"
queries = "Smokes"
output_filename = "results.txt"
allResults = {}
for method, engine in (("MC-SAT", "PyMLNs"), ("MC-SAT", "J-MLNs"), ("MC-SAT", "Alchemy - August 2010 (AMD64)")):
	allResults[(method,engine)] = inf.run(mlnFiles, db, method, queries, engine, output_filename, saveResults=True, maxSteps=5000)

for (method, engine), results in allResults.iteritems():
	print "Results obtained using %s and %s" % (engine, method)
	for atom, p in results.iteritems():
		print  "  %.6f  %s" % (p, atom)

