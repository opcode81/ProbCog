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

from MCMCInference import * 
from SAMaxWalkSAT import *
import pickle

class MCSAT(MCMCInference):
    ''' MC-SAT/MC-SAT-PC '''
    
    def __init__(self, mln, verbose=False):
        Inference.__init__(self, mln)
        # minimize the formulas' weights by exploiting group information (in order to speed up convergence)
        if verbose: print "normalizing weights..."
        #mln.minimizeGroupWeights() # TODO!!! disabled because weights are not yet evaluated
        # get the pll blocks
        if verbose: print "getting blocks..."
        mln._getPllBlocks()
        # get the block index for each ground atom
        mln._getAtom2BlockIdx()
 
    # initialize the knowledge base to the required format and collect structural information for optimization purposes
    def _initKB(self, verbose=False):
        # convert the MLN ground formulas to CNF
        if verbose: print "converting formulas to CNF..."
        #self.mln._toCNF(allPositive=True)
        self.gndFormulas, self.formulas = toCNF(self.mln.gndFormulas, self.mln.formulas, allPositive=True)

        # get clause data
        if verbose: print "gathering clause data..."
        self.gndFormula2ClauseIdx = {} # ground formula index -> tuple (idxFirstClause, idxLastClause+1) for use with range
        self.clauses = [] # list of clauses, where each entry is a list of ground literals
        #self.GAoccurrences = {} # ground atom index -> list of clause indices (into self.clauses)
        idxClause = 0
        # process all ground formulas
        for idxGndFormula, f in enumerate(self.gndFormulas):
            # get the list of clauses
            if type(f) == FOL.Conjunction:
                lc = f.children
            else:
                lc = [f]
            self.gndFormula2ClauseIdx[idxGndFormula] = (idxClause, idxClause + len(lc))
            # process each clause
            for c in lc:
                if hasattr(c, "children"):
                    lits = c.children
                else: # unit clause
                    lits = [c]
                # add clause to list
                self.clauses.append(lits)
                # next clause index
                idxClause += 1
        # add clauses for soft evidence atoms
        for se in self.softEvidence:
            se["numTrue"] = 0.0
            formula = FOL.parseFormula(se["expr"])
            se["formula"] = formula.ground(self.mln, {})
            cnf = formula.toCNF().ground(self.mln, {}) 
            idxFirst = idxClause
            for clause in self._formulaClauses(cnf):                
                self.clauses.append(clause)
                #print clause
                idxClause += 1
            se["idxClausePositive"] = (idxFirst, idxClause)
            cnf = FOL.Negation([formula]).toCNF().ground(self.mln, {})
            idxFirst = idxClause
            for clause in self._formulaClauses(cnf):                
                self.clauses.append(clause)
                #print clause
                idxClause += 1
            se["idxClauseNegative"] = (idxFirst, idxClause)
            
    def _formulaClauses(self, f):
        # get the list of clauses
        if type(f) == FOL.Conjunction:
            lc = f.children
        else:
            lc = [f]
        # process each clause
        for c in lc:
            if hasattr(c, "children"):
                yield c.children
            else: # unit clause
                yield [c]
    
    def _infer(self, numChains=1, maxSteps=5000, verbose=True, shortOutput=False, details=True, debug=False, debugLevel=1, initAlgo="SampleSAT", randomSeed=None, infoInterval=None, resultsInterval=None, p=0.5, keepResultsHistory=False, referenceResults=None, saveHistoryFile=None, sampleCallback=None, softEvidence=None, maxSoftEvidenceDeviation=None, handleSoftEvidence=True, **args):
        '''
        p: probability of a greedy (WalkSAT) move
        initAlgo: algorithm to use in order to find an initial state that satisfies all hard constraints ("SampleSAT" or "SAMaxWalkSat")
        verbose: whether to display results upon completion
        details: whether to display information while the algorithm is running            
        infoInterval: [if details==True] interval (no. of steps) in which to display the current step number and some additional info
        resultsInterval: [if details==True] interval (no. of steps) in which to display intermediate results; [if keepResultsHistory==True] interval in which to store intermediate results in the history
        debug: whether to display debug information (e.g. internal data structures) while the algorithm is running
            debugLevel: controls degree to which debug information is presented
        keepResultsHistory: whether to store the history of results (at each resultsInterval)
        referenceResults: reference results to compare obtained results to
        saveHistoryFile: if not None, save history to given filename
        sampleCallback: function that is called for every sample with the sample and step number as parameters
        softEvidence: if None, use soft evidence from MLN, otherwise use given dictionary of soft evidence
        handleSoftEvidence: if False, ignore all soft evidence in the MCMC sampling (but still compute softe evidence statistics is soft evidence is there)
        '''
        
        if verbose: print "starting MC-SAT with maxSteps=%d" % maxSteps
        if softEvidence is None:
            self.softEvidence = self.mln.softEvidence
        else:
            self.softEvidence = softEvidence
        self.handleSoftEvidence = handleSoftEvidence

        # initialize the KB and gather required info
        self._initKB(verbose)
        # get the list of relevant ground atoms for each block (!!! only needed for SAMaxWalkSAT actually)
        self.mln._getBlockRelevantGroundFormulas()
        
        self.p = p
        self.debug = debug
        self.debugLevel = debugLevel
        self.resultsHistory = []
        if saveHistoryFile is not None:
            keepResultsHistory = True
        self.referenceResults = referenceResults
        t_start = time.time()
        details = verbose and details
        # print CNF KB
        if self.debug:
            print "\nCNF KB:"
            for gf in self.gndFormulas:
                print "%7.3f  %s" % (self.formulas[gf.idxFormula].weight, strFormula(gf))
            print
        # set the random seed if it was given
        if randomSeed != None:
            random.seed(randomSeed)
        # read evidence
        if details: print "reading evidence..."
        self._readEvidence(self.given)
        #print "evidence", self.evidence
        if details:
            print "evidence blocks: %d" % len(self.evidenceBlocks)
            print "block exclusions: %d" % len(self.blockExclusions)
            print "initializing %d chain(s)..." % numChains
            
            
#        self.phistory = {} # HACK for debugging (see all references to self.phistory)
#        maxSteps = 1000
#        maxSoftEvidenceDeviation = None

        # create chains
        chainGroup = MCMCInference.ChainGroup(self)
        self.chainGroup = chainGroup
        mln = self.mln
        self.wt = [f.weight for f in self.formulas]
        for i in range(numChains):
            chain = MCMCInference.Chain(self, self.queries)
            chainGroup.addChain(chain)
            # satisfy hard constraints using initialization algorithm
            if initAlgo == "SAMaxWalkSAT":
                ##mws = SAMaxWalkSAT(chain.state, self.mln, self.evidenceBlocks)
                #mws.run()
                raise Exception("SAMaxWalkSAT currently unsupported") # no longer making use of self.mln's (gnd)formulas 
            elif initAlgo == "SampleSAT":
                M = []
                NLC = []
                for idxGF, gf in enumerate(self.gndFormulas):
                    if self.wt[gf.idxFormula] >= 20:
                        if gf.isLogical():
                            clauseRange = self.gndFormula2ClauseIdx[idxGF]
                            M.extend(range(*clauseRange))
                        else:
                            NLC.append(gf)
                ss = SampleSAT(mln, chain.state, M, NLC, self, p=0.8, debug=debug and debugLevel >= 2) # Note: can't use p=1.0 because there is a chance of getting into an oscillating state
                ss.run()
            else:
                raise Exception("MC-SAT Error: Unknown initialization algorithm specified")
            # some debug info
            if debug:
                print "\ninitial state:"
                mln.printState(chain.state)
        # do MCSAT sampling
        if details: print "sampling (p=%f)... time elapsed: %s" % (self.p, self._getElapsedTime()[1])
        if debug: print
        if infoInterval is None: infoInterval = {True:1, False:10}[debug]
        if resultsInterval is None: resultsInterval = {True:1, False:50}[debug]
        
        
        if len(self.softEvidence) == 0 and maxSoftEvidenceDeviation is not None:
            maxSoftEvidenceDeviation = None
            print "no soft evidence -> setting maxSoftEvidenceDeviation to None, terminate after maxSteps=", maxSteps
        if maxSoftEvidenceDeviation is not None:
            print "iterate until maxSoftEvidenceDeviation <", maxSoftEvidenceDeviation        
        
        self.step = 1        
        while maxSoftEvidenceDeviation is not None or self.step <= maxSteps:
            # take one step in each chain
            for chain in chainGroup.chains:
                if debug: 
                    print "step %d..." % self.step
                # choose a subset of the satisfied formulas and sample a state that satisfies them
                numSatisfied = self._satisfySubset(chain)
                # update chain counts
                chain.update()
                # print progress
                
                if details and self.step % infoInterval == 0:
                    print "step %d (%d constraints were to be satisfied), time elapsed: %s" % (self.step, numSatisfied, self._getElapsedTime()[1]),
                    if referenceResults is not None:
                        ref = self._compareResults(referenceResults)
                        print "  REF me=%f, maxe=%f" % (ref["reference_me"], ref["reference_maxe"]),
                    if len(self.softEvidence) > 0:
                        dev = self._getProbConstraintsDeviation()
                        print "  PC dev. mean=%f, max=%f (%s)" % (dev["pc_dev_mean"], dev["pc_dev_max"], dev["pc_dev_max_item"]),
                    print
                    if debug:
                        self.mln.printState(chain.state)
                        print
            # update soft evidence counts
            for se in self.softEvidence:
                se["numTrue"] += self.chainGroup.currentlyTrue(se["formula"])
                
            if sampleCallback is not None:
                sampleCallback(chainGroup, self.step)
                
            # intermediate results
            if (details or keepResultsHistory) and self.step % resultsInterval == 0 and self.step != maxSteps:
                results = chainGroup.getResults()
                if details:
                    chainGroup.printResults(shortOutput=True)
                    if debug: print
                if keepResultsHistory: self._extendResultsHistory(results)
            self.step += 1
            
            
            #termination condition
            #TODO:
            minStep = 1000
            if maxSoftEvidenceDeviation is not None and self.step > minStep:
                dev = self._getProbConstraintsDeviation()
                if dev["pc_dev_max"] < maxSoftEvidenceDeviation:
                    break
        
#        if(len(self.softEvidence) != 0):
#            pickle.dump(self.phistory, file("debug.txt", "w"))
#            sys.exit(1)
        
        # get results
        self.step -= 1
        results = chainGroup.getResults()
        if keepResultsHistory: self._extendResultsHistory(results)
        if saveHistoryFile is not None:            
            pickle.dump(self.getResultsHistory(), file(saveHistoryFile, "w"))
        return results
    
    def _satisfySubset(self, chain):
        # choose a set of logical formulas M to be satisfied (more specifically, M is a set of clause indices)
        # and also choose a set of non-logical constraints NLC to satisfy
        t0 = time.time()
        M = []
        NLC = []
        for idxGF, gf in enumerate(self.gndFormulas):
            if gf.isTrue(chain.state):                
                    u = random.uniform(0, math.exp(self.wt[gf.idxFormula]))
                    if u > 1:
                        if gf.isLogical():
                            clauseRange = self.gndFormula2ClauseIdx[idxGF]
                            M.extend(range(*clauseRange))
                        else:
                            NLC.append(gf)
                        if self.debug and self.debugLevel >= 3:
                            print "  to satisfy:", strFormula(gf)
        # add soft evidence constraints
        if self.handleSoftEvidence:
            for se in self.softEvidence:
                p = se["numTrue"] / self.step
                
                #l = self.phistory.get(strFormula(se["formula"]), [])
                #l.append(p)
                #self.phistory[strFormula(se["formula"])] = l
                
                if se["formula"].isTrue(chain.state):
                    #print "true case"
                    add = False
                    if p < se["p"]:
                        add = True
                    if add:
                        M.extend(range(*se["idxClausePositive"]))
                    #print "positive case: add=%s, %s, %f should become %f" % (add, map(str, [map(str, self.clauses[i]) for i in range(*se["idxClausePositive"])]), p, se["p"])
                else:
                    #print "false case"
                    add = False
                    if p > se["p"]:
                        add = True
                    if add:
                        M.extend(range(*se["idxClauseNegative"]))
                    #print "negative case: add=%s, %s, %f should become %f" % (add, map(str, [map(str, self.clauses[i]) for i in range(*se["idxClauseNegative"])]), p, se["p"])
        # (uniformly) sample a state that satisfies them
        t1 = time.time()
        ss = SampleSAT(self.mln, chain.state, M, NLC, self, debug=self.debug and self.debugLevel >= 2, p=self.p)
        t2 = time.time()
        ss.run()
        t3 = time.time()
        #print "select formulas: %f, SS init: %f, SS run: %f" % (t1-t0, t2-t1, t3-t2)
        return len(M) + len(NLC)
    
    def _getProbConstraintsDeviation(self):
        if len(self.softEvidence) == 0:
            return {}
        se_mean, se_max, se_max_item = 0.0, -1, None
        for se in self.softEvidence:
            dev = abs((se["numTrue"] / self.step) - se["p"])
            se_mean += dev
            if dev > se_max:
                se_max = max(se_max, dev)
                se_max_item = se
        se_mean /= len(self.softEvidence)
        return {"pc_dev_mean": se_mean, "pc_dev_max": se_max, "pc_dev_max_item": se_max_item["expr"]}
    
    def _extendResultsHistory(self, results):
        currentResults = {"step": self.step, "results": list(results), "time": self._getElapsedTime()[0]}
        currentResults.update(self._getProbConstraintsDeviation())
        if self.referenceResults is not None:
            currentResults.update(self._compareResults(results, self.referenceResults))
        self.resultsHistory.append(currentResults)
        
    def getResultsHistory(self):
        return self.resultsHistory


class SampleSAT:
    # clauseIdxs: list of indices of clauses to satisfy
    # p: probability of performing a greedy WalkSAT move
    # state: the state (array of booleans) to work with (is reinitialized randomly by this constructor)
    # NLConstraints: list of grounded non-logical constraints
    def __init__(self, mln, state, clauseIdxs, NLConstraints, inferObject, p=0.5, debug=False):
        t_start = time.time()
        self.debug = debug
        self.inferObject = inferObject
        self.state = state
        self.mln = mln        
        self.p = p
        # initialize the state randomly (considering the evidence) and obtain block info
        t1 = time.time()
        self.blockInfo = {}
        self.inferObject.setRandomState(self.state, blockInfo=self.blockInfo)
        t2 = time.time()
        if debug: mln.printState(self.state)
        # list of unsatisfied constraints
        self.unsatisfiedConstraints = []
        # keep a map of bottlenecks: index of the ground atom -> list of constraints where the corresponding lit is a bottleneck
        self.bottlenecks = {}
        # ground atom occurrences in constraints: ground atom index -> list of constraints
        self.GAoccurrences = {}
        # instantiate clauses        
        for idxClause in clauseIdxs:            
            SampleSAT._Clause(self, idxClause)
        # instantiate non-logical constraints
        for nlc in NLConstraints:
            if isinstance(nlc, FOL.GroundCountConstraint): # count constraint
                SampleSAT._CountConstraint(self, nlc)
            else:
                raise Exception("SampleSAT cannot handle constraints of type '%s'" % str(type(nlc)))
        t3 = time.time()
        #print "init time: %f" % (time.time()-t_start)
        #print "random state: %f, init constraints: %f" % (t3-t2,t2-t1)
    
    def _addGAOccurrence(self, idxGA, constraint):
        '''add ground atom occurrence in constraint'''
        occ = self.GAoccurrences.get(idxGA)
        if occ == None:
            occ = []
            self.GAoccurrences[idxGA] = occ
        occ.append(constraint)
    
    class _Clause:
        def __init__(self, sampleSAT, idxClause):
            self.ss = sampleSAT
            self.idxClause = idxClause
            self.lits = sampleSAT.inferObject.clauses[idxClause]
            # check all the literals
            numTrue = 0
            idxTrueGndAtoms = {}
            for gndLit in self.lits:
                idxGA = gndLit.gndAtom.idx
                if gndLit.isTrue(self.ss.state):
                    numTrue += 1
                    idxTrueGndAtoms[idxGA] = True
                # save ground atom occurrence
                self.ss._addGAOccurrence(idxGA, self)
            # save clause data
            self.trueGndLits = idxTrueGndAtoms
            if numTrue == 1:
                self.ss._addBottleneck(idxTrueGndAtoms.keys()[0], self)
            elif numTrue == 0:
                self.ss.unsatisfiedConstraints.append(self)
        
        def greedySatisfy(self):
            self.ss._pickAndFlipLiteral(map(lambda x: x.gndAtom.idx, self.lits), self)
        
        def handleFlip(self, idxGA):
            '''handle all effects of the flip except bottlenecks of the flipped gnd atom and clauses that became unsatisfied as a result of a bottleneck flip'''
            trueLits = self.trueGndLits
            numTrueLits = len(trueLits)
            if idxGA in trueLits: # the lit was true and is now false, remove it from the clause's list of true lits
                del trueLits[idxGA]
                numTrueLits -= 1
                # if no more true lits are left, the clause is now unsatisfied; this is handled in flipGndAtom
            else: # the lit was false and is now true, add it to the clause's list of true lits
                if numTrueLits == 0: # the clause was previously unsatisfied, it is now satisfied
                    self.ss.unsatisfiedConstraints.remove(self)
                elif numTrueLits == 1: # we are adding a second true lit, so the first one is no longer a bottleneck of this clause
                    self.ss.bottlenecks[trueLits.keys()[0]].remove(self)
                trueLits[idxGA] = True
                numTrueLits += 1
            if numTrueLits == 1:
                self.ss._addBottleneck(trueLits.keys()[0], self)
        
        def flipSatisfies(self, idxGA):
            '''returns true iff the constraint is currently unsatisfied and flipping the given ground atom would satisfy it'''
            return len(self.trueGndLits) == 0
        
        def __str__(self):
            return " v ".join(map(lambda x: strFormula(x), self.lits))
    
        def getFormula(self):
            ''' gets the original formula that the clause with the given index is part of
                (this is slow and should only be used for informational purposes, e.g. error reporting)'''
            i = 0
            for f in self.ss.mln.gndFormulas:
                if not f.isLogical():
                    continue
                if type(f) == FOL.Conjunction:
                    n = len(f.children)
                else:
                    n = 1
                if self.idxClause < i + n:
                    return f
                i += n
                
    class _CountConstraint:
        def __init__(self, sampleSAT, groundCountConstraint):
            self.ss = sampleSAT
            self.cc = groundCountConstraint
            self.trueOnes = []
            self.falseOnes = []
            # determine true and false ones
            for ga in groundCountConstraint.gndAtoms:
                idxGA = ga.idx
                if self.ss.state[idxGA]:
                    self.trueOnes.append(idxGA)
                else:
                    self.falseOnes.append(idxGA)
                self.ss._addGAOccurrence(idxGA, self)
            # determine bottlenecks
            self._addBottlenecks()
            # if the formula is unsatisfied, add it to the list
            if not self._isSatisfied():
                self.ss.unsatisfiedConstraints.append(self)
        
        def _isSatisfied(self):
            return eval("len(self.trueOnes) %s self.cc.count" % self.cc.op)
        
        def _addBottlenecks(self):
            # there are only bottlenecks if we are at the border of the interval
            numTrue = len(self.trueOnes)
            if self.cc.op == "!=":
                trueNecks = numTrue == self.cc.count + 1
                falseNecks = numTrue == self.cc.count - 1
            else:
                border = numTrue == self.cc.count
                trueNecks = border and self.cc.op in ["==", ">="]
                falseNecks = border and self.cc.op in ["==", "<="]
            if trueNecks:
                for idxGA in self.trueOnes:
                    self.ss._addBottleneck(idxGA, self)
            if falseNecks:
                for idxGA in self.falseOnes:
                    self.ss._addBottleneck(idxGA, self)
        
        def greedySatisfy(self):
            c = len(self.trueOnes)
            satisfied = self._isSatisfied()
            assert not satisfied
            if c < self.cc.count and not satisfied:
                self.ss._pickAndFlipLiteral(self.falseOnes, self)
            elif c > self.cc.count and not satisfied:
                self.ss._pickAndFlipLiteral(self.trueOnes, self)
            else: # count must be equal and op must be !=
                self.ss._pickAndFlipLiteral(self.trueOnes + self.falseOnes, self)
        
        def flipSatisfies(self, idxGA):
            if self._isSatisfied():
                return False
            c = len(self.trueOnes)            
            if idxGA in self.trueOnes:
                c2 = c - 1
            else:
                assert idxGA in self.falseOnes
                c2 = c + 1
            return eval("c2 %s self.cc.count" % self.cc.op)
        
        def handleFlip(self, idxGA):
            '''handle all effects of the flip except bottlenecks of the flipped gnd atom and clauses that became unsatisfied as a result of a bottleneck flip'''
            wasSatisfied = self._isSatisfied()
            # update true and false ones
            if idxGA in self.trueOnes:
                self.trueOnes.remove(idxGA)
                self.falseOnes.append(idxGA)
            else:
                self.trueOnes.append(idxGA)
                self.falseOnes.remove(idxGA)
            isSatisfied = self._isSatisfied()
            # if the constraint was previously satisfied and is now unsatisfied or
            # if the constraint was previously satisfied and is still satisfied (i.e. we are pushed further into the satisfying interval, away from the border),
            # remove all the bottlenecks (if any)
            if wasSatisfied:
                for idxGndAtom in self.trueOnes + self.falseOnes: 
                    if idxGndAtom in self.ss.bottlenecks and self in self.ss.bottlenecks[idxGndAtom]: # TODO perhaps have a smarter method to know which ones actually were bottlenecks (or even info about whether we had bottlenecks)
                        if idxGA != idxGndAtom:
                            self.ss.bottlenecks[idxGndAtom].remove(self)
                # the constraint was added to the list of unsatisfied ones in SampleSAT._flipGndAtom (bottleneck flip)
            # if the constraint is newly satisfied, remove it from the list of unsatisfied ones
            elif not wasSatisfied and isSatisfied:
                self.ss.unsatisfiedConstraints.remove(self)
            # bottlenecks must be added if, because of the flip, we are now at the border of the satisfying interval
            self._addBottlenecks()
            
        def __str__(self):
            return str(self.cc)
    
        def getFormula(self):
            return self.cc
    
    def _addBottleneck(self, idxGndAtom, constraint):
        bn = self.bottlenecks.get(idxGndAtom)
        if bn == None:
            bn = []
            self.bottlenecks[idxGndAtom] = bn
        bn.append(constraint)
    
    def _printUnsatisfiedConstraints(self):
        for constraint in self.unsatisfiedConstraints:
            print "    %s" % str(constraint)
    
    def run(self):
        t_start = time.time()
        p = self.p # probability of performing a WalkSat move
        iter = 1
        steps = [0, 0]
        times = [0.0, 0.0]
        while len(self.unsatisfiedConstraints) > 0:
            
            # in debug mode, check if really exactly the unsatisfied clauses are in the corresponding list
            if False and self.debug:
                for idxClause, clause in enumerate(self.realClauses):
                    isTrue = clause.isTrue(self.state)
                    if not isTrue:
                        if idxClause not in self.unsatisfiedClauseIdx:
                            print "    %s is unsatisfied but not in the list" % strFormula(clause)
                    else:
                        if idxClause in self.unsatisfiedClauseIdx:
                            print "    %s is satisfied but in the list" % strFormula(clause)
            
            if self.debug and False:
                self.mln.printState(self.state, True)
                print "bottlenecks:", self.bottlenecks
            
            # make a WalkSat move or a simulated annealing move
            if random.uniform(0, 1) <= p:
                if self.debug:
                    print "%d random walk (%d left)" % (iter, len(self.unsatisfiedConstraints))
                    self._printUnsatisfiedConstraints()
                #t = time.time()
                self._walkSatMove()
                #steps[0] += 1
                #times[0] += time.time()-t
            else:
                if self.debug:
                    print "%d SA (%d left)" % (iter, len(self.unsatisfiedConstraints))
                    self._printUnsatisfiedConstraints()
                #t = time.time()
                self._SAMove()
                #steps[1] += 1
                #times[1] += time.time()-t
            iter += 1
        #print "run time: %f" % (time.time()-t_start)
        #print "avg rw time: %f" % (times[0]/steps[0])
        #print "avg sa time: %f" % (times[1]/steps[1])
    
    def _walkSatMove(self):
        '''randomly pick one of the unsatisfied constraints and satisfy it (or at least make one step towards satisfying it'''
        constraint = self.unsatisfiedConstraints[random.randint(0, len(self.unsatisfiedConstraints) - 1)]
        constraint.greedySatisfy()
    
    def _pickAndFlipLiteral(self, candidates, constraint):
        '''chooses from the list of given literals (as ground atom indices: candidates) the best one to flip, i.e. the one that causes the fewest constraints to become unsatisified'''
        # get the literal that makes the fewest other formulas false
        bestNum = len(self.inferObject.clauses) * 3
        bestGA = None
        bestGAsecond = None
        mln = self.mln
        inferObject = self.inferObject
        for idxGA in candidates:
            #strGA = str(self.mln.gndAtomsByIdx[idxGA])
            idxGAsecond = None
            # ignore ground atoms for which we have evidence
            idxBlock = mln.atom2BlockIdx[idxGA]
            if idxBlock in inferObject.evidenceBlocks:
                #print "%s is in evidence" % strGA
                continue
            blockExcl = inferObject.blockExclusions.get(idxBlock, [])
            if idxGA in blockExcl:
                #print "%s is excluded" % strGA
                continue
            # get the number of unsatisfied clauses the flip would cause
            num = 0
            block = mln.pllBlocks[idxBlock][1]
            if block is not None: # if the atom is in a real block, select a second atom to flip to get a consistent state
                trueOne, falseOnes = self.blockInfo[idxBlock]
                if trueOne in falseOnes: 
                    print "Error: The true one is part of the false ones!"
                if len(falseOnes) == 0: # there are no false atoms that we could set to true, so skip this ground atom
                    #print "no false ones for %s" % strGA
                    continue
                if idxGA == trueOne: # if the current GA is the true one, then randomly choose one of the false ones to flip
                    idxGAsecond = falseOnes[random.randint(0, len(falseOnes) - 1)]
                elif idxGA in falseOnes: # if the current GA is false, the second literal to flip is the true one
                    idxGAsecond = trueOne
                else: # otherwise, this literal must be excluded and must not be flipped
                    continue
                num += len(self.bottlenecks.get(idxGAsecond, [])) # !!!!!! additivity ignores the possibility that the first and the second GA could occur together in the same formula (should perhaps perform the first flip temporarily)
            num += len(self.bottlenecks.get(idxGA, []))
            # check if it's better than the previous best (or equally good)
            newBest = False
            if num < bestNum:
                newBest = True
            elif num == bestNum: # in case of equality, decide randomly
                newBest = random.randint(0, 1) == 1
            if newBest:
                bestGA = idxGA
                bestGAsecond = idxGAsecond
                bestNum = num
            #else:
            #   print "%s is not good enough (%d)" % (strGA, num)
        if bestGA == None:
            #c = self.realClauses[clauseIdx]
            #print "UNSATISFIABLE: %s" % str(c)
            #print self.mln.printState(self.state)
            gf = constraint.getFormula()
            raise Exception("SampleSAT error: unsatisfiable constraint '%s' given the evidence! It is an instance of '%s'." % (strFormula(gf), strFormula(self.mln.formulas[gf.idxFormula])))
        # flip the best one and, in case of a blocked ground atom, a second one
        self._flipGndAtom(bestGA)
        if bestGAsecond != None:
            self._flipGndAtom(bestGAsecond)
            self._updateBlockInfo(bestGA, bestGAsecond)

    # update the true one and the false ones for a flip of both the given ground atoms (which are in the same block)
    def _updateBlockInfo(self, idxGA, idxGA2):
        # update the block information
        idxBlock = self.mln.atom2BlockIdx[idxGA]
        bi = self.blockInfo[idxBlock]
        if bi[0] == idxGA: # idxGA is the true one, so add it to the false ones and make idxGA2 the true one
            bi[1].append(idxGA)
            bi[1].remove(idxGA2)
            bi[0] = idxGA2
        else: # idxGA2 is the true one
            try:
                bi[1].append(idxGA2)
                bi[1].remove(idxGA)
                bi[0] = idxGA
            except:
                raise Exception("Could not change true one in block from %s to %s" % (self.mln.strGroundAtom(idxGA2), self.mln.strGroundAtom(idxGA)))
                
    # flips the truth value of a literal (referred to by the ground atom index). This is the only place where changes to the state are made!
    # If an atom in a block is flipped, be sure to also call _updateBlockInfo
    def _flipGndAtom(self, idxGA):
        # flip the ground atom
        if self.debug: print "  flipping %s" % str(self.mln.gndAtomsByIdx[idxGA])
        self.state[idxGA] = not self.state[idxGA]
        # the constraints where the literal was a bottleneck are now unsatisfied
        bn = self.bottlenecks.get(idxGA)
        if bn is not None:
            self.unsatisfiedConstraints.extend(bn)
            #print "  %s now unsatisfied" % str(bn)
            self.bottlenecks[idxGA] = []
        # update: 
        #  - list of true literals for each clause containing idxGA
        #  - bottlenecks: clauses where this results in one true literal now have a bottleneck
        #  - unsatisfied clauses: clauses where there are no true lits before the flip are now satisfied
        affectedConstraints = self.GAoccurrences.get(idxGA, [])
        #print "  %d constraints affected" % len(affectedConstraints)
        for constraint in affectedConstraints:
            #print "  %s" % str(constraint)
            constraint.handleFlip(idxGA)      
               
    def _SAMove(self):
        # TODO are block exclusions handled correctly here? check it!
        # pick one of the blocks at random until we get one where we can flip (true one not known)
        idxPllBlock = 0
        while True:
            idxPllBlock = random.randint(0, len(self.mln.pllBlocks) - 1)
            if idxPllBlock in self.inferObject.evidenceBlocks: # skip evidence block
                #print "skipping evidence block"
                #pass
                return
            else:
                break
        # randomly pick one of the block's ground atoms to flip
        delta = 0
        idxGA, block = self.mln.pllBlocks[idxPllBlock]
        trueOne = None
        if block is not None: # if it's a proper block, we need to look at the truth values to make a consistent setting
            trueOne, falseOnes = self.blockInfo[idxPllBlock]
            if len(falseOnes) == 0: # no false atom can be flipped
                print "no false ones in block"
                return
            delta += self._delta(trueOne) # consider the delta cost of making the true one false
            # already perform the flip of the true one - and reverse it later if the new state is ultimately rejected
            # Note: need to perform it here, so that the delta calculation for the other gnd Atom that is flipped is correct
            self._flipGndAtom(trueOne)
            idxGA = falseOnes[random.randint(0, len(falseOnes) - 1)]
        # consider the delta cost of flipping the selected gnd atom 
        delta += self._delta(idxGA)
        # if the delta is non-negative, we always use the resulting state
        if delta >= 0:
            p = 1.0
        else:
            # !!! the temperature has a great effect on the uniformity of the sampled states! it's a "magic" number that needs to be chosen with care. if it's too low, then probabilities will be way off; if it's too high, it will take longer to find solutions
            temp = 14.0 # the higher the temperature, the greater the probability of deciding for a flip
            p = exp(-float(delta) / temp)
            p = 1.0 #!!!
        # decide and flip
        if random.uniform(0, 1) <= p:
            self._flipGndAtom(idxGA)
            if trueOne is not None:
                self._updateBlockInfo(idxGA, trueOne)
        else: # not flipping idxGA, so reverse the flip of the true one if it was previously performed
            if trueOne is not None: 
                self._flipGndAtom(trueOne)
    
    # get the delta cost of flipping the given ground atom (newly satisfied constraints - now unsatisfied constraints)    
    def _delta(self, idxGA):
        bn = self.bottlenecks.get(idxGA, [])
        # minus now unsatisfied clauses (as indicated by the bottlenecks)
        delta = -len(bn)
        # plus newly satisfied clauses
        for constraint in self.GAoccurrences.get(idxGA, []):
            if constraint.flipSatisfies(idxGA):                
                delta += 1
        return delta
