# -*- coding: iso-8859-1 -*-
#
# Markov Logic Networks
#
# (C) 2006-2010 by Dominik Jain (jain@cs.tum.edu)
# 
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files (the
# "Software"), to deal in the Software without restriction, including
# without limitation the rights to use, copy, modify, merge, publish,
# distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to
# the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
# CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
# TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

from MLN import *

# --- The MLN Tool --- 
if __name__ == '__main__':
    #sys.argv = [sys.argv[0], "test", "graph"]
    args = sys.argv[1:]
    if len(args) == 0:
        print "\nMLNs in Python - helper tool\n\n  usage: MLN <action> <params>\n\n"
        print "  actions: print <mln file>"
        print "              print the MLN\n"
        print "           printGF <mln file> <db file>"
        print "              print the ground formulas we obtain when instantiating an MRF with the given database\n"
        print "           printGC <mln file> <db file>"
        print "              print the ground clauses we obtain when instantiating an MRF with the given database\n"
        print "           printGA <mln file> <db file>"
        print "              print the ground atoms we obtain when instantiating an MRF with the given database\n"
        print "           inferExact <mln file> <domain> <query> <evidence>"
        print "              domain: a dictionary mapping domain names to lists of constants, e.g."
        print "                      \"{'dom1':['const1', 'const2'], 'dom2':['const3']}\""
        print "                      To use just the constants declared in the MLN, use \"{}\""
        print "              query, evidence: ground formulas\n" 
        print "           inferGibbs <mln file> <domain> <query> <evidence>\n"
        print "           topWorlds <mln file> <domain>\n"
        print "           test <test name>"
        print "              run the test with the given name (dev only)\n"
        print "  NOTE: This script exposes but a tiny fraction of the functionality of the MLN class!\n"
        sys.exit(0)
    if args[0] == "print":
        mln = MLN(args[1])
        mln.write(sys.stdout)
    elif args[0] == 'printGF':
        mln = MLN(args[1])
        mln.combineDB(args[2])
        mln.printGroundFormulas()
    elif args[0] == 'printGC':
        mln = MLN(args[1])
        mln.combineDB(args[2])
        mln._toCNF()
        mln.printGroundFormulas()
    elif args[0] == 'printGA':
        mln = MLN(args[1])
        mln.combineDB(args[2], groundFormulas=False)
        mln.printGroundAtoms()
    elif args[0] == "inferExact":
        mln = MLN(args[1])
        mln.combine(eval(args[2]))
        mln.inferExact(args[3], args[4])
    elif args[0] == "topWorlds":
        mln = MLN(args[1])
        mln.combineDB(args[2])
        mln.printTopWorlds(10)
    elif args[0] == "inferGibbs":
        mln = MLN(args[1])
        mln.combine(eval(args[2]))
        mln.inferGibbs(args[3], args[4])
    elif args[0] == "printDomains":
        mln = MLN(args[1])
        print mln.domains
    elif args[0] == "test":
        test = args[1]
        #os.chdir(r"c:\dev\Java\AI\SRLDB\mln\drinking")
        #os.chdir(r"c:\dev\Java\AI\SRLDB\mln\kitchen")
        #os.chdir("/usr/wiss/jain/work/code/SRLDB/mln/kitchen")
        if test == 'gndFormulas':
            mln = MLN("in.actsit-tiny-conj-two.mln")
            mln.combine({"person": ["P"], "drink": ["D1", "D2"]})
            mln.printGroundFormulas()
        elif test == "predGndings":
            mln = MLN("wts.blog.meal_goods.mln", verbose=True)
            mln.combineDB("q7.db")
            mln._getPredGroundings("usedByForWithIn")
            pass
        elif test == 'infer':
            mln = MLN("wts.test.mln")
            mln.combine({"drink": ["D"], "person": ["P"]})
            mln.infer("hasRank(P,Student)")
            mln.infer("hasRank(P,Student)", "consumed(P, D) ^ drinkType(D, Tea)")
            #mln.printGroundFormulas()
            #mln.printWorlds()   
        elif test == 'count':
            #mln = MLN("wts.pyll.actsit-tinyk1-two.mln")
            #mln = MLN("in.actsit-tiny-conj-two.mln")
            #mln = MLN("in.actsit-tiny-conj-two-norel.mln")
            mln = MLN("in.actsit-tiny-conj-two-norel.mln")
            #mln.combineDB("tinyk1.db")
            mln.combineDB("tinyk1norel.db")
            idxFormula = 0
            idxFormulaHard = (idxFormula + 1) % 2
            idxWorld = mln._getEvidenceWorldIndex()
            print "idxWorld %d (%d worlds in total)" % (idxWorld, len(mln.worlds))
            print "true groundings of formula in training DB:", mln._countNumTrueGroundingsInWorld(idxFormula, mln.worlds[idxWorld])
            # count how many times the formula is true in possible worlds
            mln.countWorldsWhereFormulaIsTrue(idxFormula)
            # exclude worlds where the other formula isn't always true
            counts = mln.countTrueGroundingsForEachWorld()
            musthave = counts[idxWorld][idxFormulaHard]
            counts = filter(lambda x: x[idxFormulaHard] == musthave, counts)
            f = {}
            for c in counts:
                f[c[idxFormula]] = f.get(c[idxFormula], 0) + 1
            print f
            #mln.printWorlds()
            #mln.worlds.sort(key=lambda w:-w["sum"])
            #mln.printWorlds()
        elif test == "count2":
            #mln = MLN("wts.pyll.tinyk1norel-two-norel.mln")
            mln = MLN("wts.pyll.tinyk1symm-four.mln")
            #mln.setRigidPredicate("hasRank")
            #mln.setRigidPredicate("drinkType")
            mln.combineDB("tinyk1symm.db")
            counts = mln.countTrueGroundingsForEachWorld(True)
            mln.printWorlds(format=2)
        elif test == 'blockprob':
            mln = MLN("wts.pybpll.tinyk1-two.mln")
            mln.combineDB("tinyk1.db")
            mln.printBlockProbsMB()
        elif test == 'learnwts':
            def learn(infile, mode, dbfile, startpt=False, rigidPreds=[]):
                mln = MLN(infile)    
                #db = "tinyk%d%s" %  (k, db)
                mln.combineDB(dbfile)
                for predName in rigidPreds:
                    mln.setRigidPredicate(predName)
                mln.learnwts(mode, initialWts=startpt)
                prefix = 'wts'
                if mode == 'PLL':
                    tag = "pll"
                else:
                    tag = mode.lower()
                    if mode == 'LL' and not POSSWORLDS_BLOCKING:
                        tag += "-nobl"
                    if mode == 'LL_fac':
                        prefix = 'fac'
                fname = ("%s.py%s.%s" % (prefix, tag, infile[3:]))
                mln.write(file(fname, "w"))
                print "WROTE %s\n\n" % fname
            #POSSWORLDS_BLOCKING=True
            learn("in.tiny-paper.mln", "LL", "tinyk1b.db")
        elif test == 'pll':
            mln = MLN('wts.actsit-unaryallmink1-perfectwts.mln')
            mln.combineDB("unaryallmink1.db")
            mln.getAtomProbMB("goalkeeper(F)")
        elif test == 'expect':
            #mln = MLN("wts.actsit-tinyk1-doConnection-manual-perfect2.mln")
            mln = MLN("wts.pyll.tinyk1symm-process-norigid.mln")
            #mln = MLN("wts.pyll.tinyk1symm-process-hasRank.mln")
            #mln.setRigidPredicate("hasRank")
            mln.combineDB("tinyk1symm.db")
            print "expected # of gndings:"
            mln.printExpectedNumberOfGroundings()
            print "\nformula probs:"
            mln.printFormulaProbabilities()
            print "\ninference:"
            mln.infer("drinkType(D1,Tea)", "hasRank(Steve, Student) ^ consumed(Steve,D1)")
            mln.infer("drinkType(D1,Coffee)", "hasRank(Steve, Student) ^ consumed(Steve,D1)")
            mln.infer("drinkType(D1,Tea)", "hasRank(Steve, Professor) ^ consumed(Steve,D1)")
            mln.infer("drinkType(D1,Coffee)", "hasRank(Steve, Professor) ^ consumed(Steve,D1)")
            mln.infer("hasRank(Steve,Student)", "drinkType(D1, Tea) ^ consumed(Steve,D1)")
        elif test == "Gibbs":
            def infer(what, given, gs):
                gs.mln.infer(what, given)
                gs.infer(what, given)
            mln = MLN("wts.test.mln")
            mln.combine({"person": ["P"], "drink": ["D1"]})
            gs = GibbsSampler(mln)
            infer("hasRank(P,Student)", "consumed(P,D1) ^ drinkType(D1,Tea)", gs)
            infer("hasRank(P,Student)", None, gs)
        elif test == 'graph':
            mln = MLN("in.tiny-process2-noforcedrink.mln")
            mln.combine({"person": ["Steve", "Pete"], "drink": ["C1", "T1", "T2"]})
            mln.writeDotFile("test.dot")
        elif test == "MCSAT":
            os.chdir("/usr/wiss/jain/work/code/SRLDB/models/daimler")
            mln = MLN("daimler2.mln", verbose=True)
            query = ("personT", "q3.db", 30)
            query = ("utensilT", "q10.db", 5)
            query = ("req(A)", "empty.db", 10)
            evidence = evidence2conjunction(mln.combineDB(query[1], verbose=True))
            mcsat = MCSAT(mln, verbose=True)
            mcsat.infer(query[0], evidence, debug=False, randomSeed=0, verbose=True, details=True, maxSteps=query[2], shortOutput=True)
        elif test == 'profile_mcsat':
            mln = MLN("wts.blog.meal_goods.mln", verbose=True)
            query = ("personT", "q3.db")
            query = ("utensilT", "q10.db")
            evidence = evidence2conjunction(mln.combineDB(query[1], verbose=True))
            mcsat = MCSAT(mln, verbose=True)
            print "\nnow profiling..."
            import profile
            import pstats
            profile.run("mcsat.infer(query[0], evidence, debug=False, randomSeed=0, verbose=True, details=True, maxSteps=5, shortOutput=True)", "prof_mcsat")
            stats = pstats.Stats("prof_mcsat")
            stats.strip_dirs()
            stats.sort_stats('cumulative')
            stats.print_stats()
            stats.print_callees()
        elif test == 'profile_mcsat2':
            mln = MLN("meal_goods2/wts.meals1.mln", verbose=True)
            query = ("takesPartIn", "meal_goods2/new-1.db")
            #query = ("utensilT", "q10.db")
            evidence = evidence2conjunction(mln.combineDB(query[1], verbose=True))
            mcsat = MCSAT(mln, verbose=True)
            print "\nnow profiling..."
            import profile
            import pstats
            profile.run("mcsat.infer(query[0], evidence, debug=False, randomSeed=0, verbose=True, details=True, maxSteps=25, shortOutput=True)", "prof_mcsat")
            stats = pstats.Stats("prof_mcsat")
            stats.strip_dirs()
            stats.sort_stats('cumulative')
            stats.print_stats()
            stats.print_callees()
        elif test == 'count_constraint':
            mln = MLN('test.mln')
            mln.combine({})
            mln.printGroundFormulas()
            mln.inferMCSAT("directs", infoInterval=1, details=True, maxSteps=1000, verbose=True, resultsInterval=1, debug=False, debugLevel=3)
        elif test == "count_constraint2": # compare count constraint size to existential quantification
            mln = MLN('count_constraint_vs_exists.mln')
            mln.combine({})
            mln._toCNF()
            mln.printGroundFormulas()
            
