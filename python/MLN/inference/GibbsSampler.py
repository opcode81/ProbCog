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

from MCMCInference import MCMCInference

class GibbsSampler(MCMCInference):
    class Chain(MCMCInference.Chain):
        def __init__(self, gibbsSampler):
            self.gs = gibbsSampler
            MCMCInference.Chain.__init__(self, gibbsSampler, gibbsSampler.queries)
            # run walksat
            mws = SAMaxWalkSAT(self.state, self.gs.mln, self.gs.evidenceBlocks)
            mws.run()
        
        def step(self, debug=False):
            mln = self.gs.mln
            if debug: 
                print "step %d" % (self.numSteps + 1)
                #time.sleep(1)
                pass
            # reassign values by sampling from the conditional distributions given the Markov blanket
            wt = mln._weights()
            for idxBlock, (idxGA, block) in enumerate(mln.pllBlocks):
                if idxBlock in self.gs.evidenceBlocks: # do not sample if we have evidence 
                    continue
                if block != None:
                    expsums = mln._getBlockExpsums(block, wt, self.state, None, mln.blockRelevantGFs[idxBlock])
                    if idxBlock in self.gs.blockExclusions:
                        for i in self.gs.blockExclusions[idxBlock]:
                            expsums[i] = 0
                else:
                    expsums = mln._getAtomExpsums(idxGA, wt, self.state, mln.blockRelevantGFs[idxBlock])
                r = random.uniform(0, sum(expsums))
                idx = 0
                s = expsums[0]
                while r > s:
                    idx += 1
                    s += expsums[idx]
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
        Inference.__init__(self, mln)
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
    def _infer(self, verbose=True, numChains=3, maxSteps=5000, shortOutput=False, details=False, debug=False, debugLevel=1, infoInterval=10, resultsInterval=100):
        random.seed(time.time())
        # set evidence according to given conjunction (if any)
        self._readEvidence(self.given)
        # initialize chains
        if verbose and details:
            print "initializing %d chain(s)..." % numChains
        chainGroup = MCMCInference.ChainGroup(self)
        for i in range(numChains):
            chainGroup.addChain(GibbsSampler.Chain(self))
        # do gibbs sampling
        if verbose and details: print "sampling..."
        converged = 0
        numSteps = 0
        minSteps = 200
        while converged != numChains and numSteps < maxSteps:
            converged = 0
            numSteps += 1
            for chain in chainGroup.chains:
                chain.step(debug=debug)
                if chain.converged and numSteps >= minSteps:
                    #pass
                    converged += 1
            if verbose and details:
                if numSteps % infoInterval == 0:
                    print "step %d (fraction converged: %.2f)" % (numSteps, float(converged) / numChains)
                if numSteps % resultsInterval == 0:
                    chainGroup.getResults()
                    chainGroup.printResults(shortOutput=True)
        # get the results
        return chainGroup.getResults()
