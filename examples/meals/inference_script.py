from jyblns import infer

network = "meals_any_for_functional.xml"
decls = "meals_any_for.blnd"
logic = "meals_any_for_functional.blnl"
inferenceMethod = "LikelihoodWeighting"
evidenceDB = "query2.blogdb"
queries = "name,usesAnyIn(x,Plate,M)"
inf = infer(network, decls, logic, inferenceMethod, evidenceDB, queries, args=["--confidenceLevel=0.95"])
for result in inf.getResults():
    print result
