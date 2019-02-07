# -*- coding: iso-8859-1 -*-

# This is a very simple example script that illustrates how
# you can use scripting to automate your inference tasks.

from MLN import *

mln = MLN("wts.pybpll.smoking-train-smoking.mln")
mrf = mln.groundMRF("smoking-test-smaller.db")
queries = ["Smokes(Ann)", "Smokes(Bob)", "Smokes(Ann) ^ Smokes(Bob)"]
mrf.inferMCSAT(queries, verbose=False)
for query, prob in mrf.getResultsDict().iteritems():
    print "  %f  %s" % (prob, query)