# -*- coding: iso-8859-1 -*-
from jyblns import infer

network = "meals_any_for_functional.xml"
decls = "meals_any_for.blnd"
logic = "meals_any_for_functional.blnl"
inferenceMethod = "LikelihoodWeighting"
evidenceDB = "query2.blogdb"
queries = "name,usesAnyIn(x,Plate,M)"
inf = infer(network, decls, logic, inferenceMethod, evidenceDB, queries, args=["--confidenceLevel=0.95"])
for r in inf.getResults():
    print "%s" % r.varName
    for i in range(r.getDomainSize()):
        print "  %f  %s" % (r.probabilities[i], r.domainElements[i]),
        if r.additionalInfo is not None:
            interval = r.additionalInfo[i]
            print " [%f;%f]" % (interval.lowerEnd, interval.upperEnd),
        print
print "time taken: %fs" % inf.getInferenceTime()
print "steps taken: %d" % inf.getNumSteps()