# -*- coding: iso-8859-1 -*-
# Example Jython script that illustrates the unified query interface
# based on ProbCog model pools

from jyprobcog import *

# load pool of models
pool = ModelPool("examples.pool.xml")

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
print "\nsmokers:"
mln = MLNModel("smokers/wts.pypll.smoking-train-smoking.mln")
for result in mln.query("Smokes(Anna)", ["Smokes(Bob)", "Friends(Anna,Bob)"], verbose=False):
	print result
