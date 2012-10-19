# -*- coding: iso-8859-1 -*-
#
# Markov Logic Networks
#
# (C) 2006-2011 by Dominik Jain (jain@cs.tum.edu)
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

class GibbsSampler(MCMCInference):
    class Chain(MCMCInference.Chain):
        def __init__(self, gibbsSampler):
            self.gs = gibbsSampler
            MCMCInference.Chain.__init__(self, gibbsSampler, gibbsSampler.queries)            
            # run walksat
            mws = SAMaxWalkSAT(self.state, self.gs.mln, self.gs.evidenceBlocks)
            mws.run()
            
        def _getAtomExpsums(self, idxGndAtom, wt, world_values, relevantGroundFormulas=None):
            sums = [0, 0]
            # process all (relevant) ground formulas
            checkRelevance = False
            if relevantGroundFormulas == None:
                relevantGroundFormulas = self.gndFormulas
                checkRelevance = True
            old_tv = world_values[idxGndAtom]
            for gf in relevantGroundFormulas:
                if checkRelevance: 
                    if not gf.containsGndAtom(idxGndAtom):
                        continue
                for i, tv in enumerate([False, True]):
                    world_values[idxGndAtom] = tv
                    if gf.isTrue(world_values):
                        sums[i] += wt[gf.idxFormula]
                    world_values[idxGndAtom] = old_tv
            return map(math.exp, sums)
        
        def step(self, debug=False):
            mln = self.gs.mln
            if debug: 
                print "step %d" % (self.numSteps + 1)
                #time.sleep(1)
                pass
            # reassign values by sampling from the conditional distributions given the Markov blanket
            wt = mln._weights()
            for idxBlock, (idxGA, block) in enumerate(mln.pllBlocks):
                # compute distribution to sample from
                if idxBlock in self.gs.evidenceBlocks: # do not sample if we have evidence 
                    continue
                if block != None:
                    expsums = self.gs.mln._getBlockExpsums(block, wt, self.state, None, mln.blockRelevantGFs[idxBlock])
                    if idxBlock in self.gs.blockExclusions:
                        for i in self.gs.blockExclusions[idxBlock]:
                            expsums[i] = 0
                else:
                    expsums = mln._getAtomExpsums(idxGA, wt, self.state, mln.blockRelevantGFs[idxBlock])
                Z = sum(expsums)           
                # check for soft evidence and greedily satisfy it if possible                
                idx = None
                if block == None:
                    formula = self.gs.mln.gndAtomsByIdx[idxGA]
                    p = self.gs.mln._getSoftEvidence(formula)
                    if p is not None:
                        currentBelief = self.getSoftEvidenceFrequency(formula)
                        if p > currentBelief and expsums[1] > 0:
                            idx = 1
                        elif p < currentBelief and expsums[0] > 0:
                            idx = 0
                # sample value
                if idx is None:
                    r = random.uniform(0, Z)                    
                    idx = 0
                    s = expsums[0]
                    while r > s:
                        idx += 1
                        s += expsums[idx]                
                # make assignment
                if block != None:
                    for i, idxGA in enumerate(block):
                        tv = (i == idx)
                        self.state[idxGA] = tv
                    if debug: print "  setting block %s to %s, odds = %s" % (str(map(lambda x: str(mln.gndAtomsByIdx[x]), block)), str(mln.gndAtomsByIdx[block[idx]]), str(expsums))
                else:
                    self.state[idxGA] = bool(idx)
                    if debug: print "  setting atom %s to %s" % (str(mln.gndAtomsByIdx[idxGA]), bool(idx))
            # update results
            self.update()
    
    def __init__(self, mln):
        print "initializing Gibbs sampler...",
        self.useConvergenceTest = False
        MCMCInference.__init__(self, mln)
        # check compatibility with MLN
        for f in mln.formulas:
            if not f.isLogical():
                raise Exception("GibbsSampler does not support non-logical constraints such as '%s'!" % strFormula(f))
        # get the pll blocks
        mln._getPllBlocks()
        # get the list of relevant ground atoms for each block
        mln._getBlockRelevantGroundFormulas()
        print "done."

    # infer one or more probabilities P(F1 | F2)
    #   what: a ground formula (string) or a list of ground formulas (list of strings) (F1)
    #   given: a formula as a string (F2)
    def _infer(self, verbose=True, numChains=3, maxSteps=5000, shortOutput=False, details=False, debug=False, debugLevel=1, infoInterval=10, resultsInterval=100, softEvidence=None, **args):
        random.seed(time.time())
        # set evidence according to given conjunction (if any)
        self._readEvidence(self.given)
        if softEvidence is None:
            self.softEvidence = self.mln.softEvidence
        else:
            self.softEvidence = softEvidence
        # initialize chains
        if verbose and details:
            print "initializing %d chain(s)..." % numChains
        chainGroup = MCMCInference.ChainGroup(self)
        for i in range(numChains):
            chain = GibbsSampler.Chain(self)
            chainGroup.addChain(chain)
            if self.softEvidence is not None:
                chain.setSoftEvidence(self.softEvidence)
        # do Gibbs sampling
        if verbose and details: print "sampling..."
        converged = 0
        numSteps = 0
        minSteps = 200
        while converged != numChains and numSteps < maxSteps:
            converged = 0
            numSteps += 1
            for chain in chainGroup.chains:
                chain.step(debug=debug)
                if self.useConvergenceTest:
                    if chain.converged and numSteps >= minSteps:
                        converged += 1
            if verbose and details:
                if numSteps % infoInterval == 0:
                    print "step %d (fraction converged: %.2f)" % (numSteps, float(converged) / numChains)
                if numSteps % resultsInterval == 0:
                    chainGroup.getResults()
                    chainGroup.printResults(shortOutput=True)
        # get the results
        return chainGroup.getResults()
