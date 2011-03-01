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

from PLL import *

class BPLL(PLL):
    
    def __init__(self, mln):
        PLL.__init__(self, mln)

    # get the probability of the assignment for the block the given atom is in
    def getBlockProbMB(self, atom): 
        idxGndAtom = self.gndAtoms[atom].idx       
        self._getBlockProbMB(idxBlock, self._weights())

    def _addToBlockDiff(self, idxFormula, idxBlock, diff):
        key = (idxFormula, idxBlock)
        cur = self.blockdiffs.get(key, 0)
        self.blockdiffs[key] = cur + diff        

    def _computeBlockDiffs(self):
        self.blockdiffs = {}
        for idxPllBlock, (idxGA, block) in enumerate(self.pllBlocks):
            for gndFormula in self.blockRelevantGFs[idxPllBlock]:
                if idxGA != None: # ground atom is the variable as it's not in a block (block is None)
                    cnt1, cnt2 = 0, 0
                    #if not (idxGA in gndFormula.idxGroundAtoms()): continue
                    # check if formula is true if gnd atom maintains its truth value
                    if self.mln._isTrueGndFormulaGivenEvidence(gndFormula): cnt1 = 1
                    # check if formula is true if gnd atom's truth value is inversed
                    old_tv = self.mln._getEvidence(idxGA)
                    self.mln._setTemporaryEvidence(idxGA, not old_tv)
                    if self.mln._isTrueGndFormulaGivenEvidence(gndFormula): cnt2 = 1
                    self.mln._removeTemporaryEvidence()
                    # save difference
                    diff = cnt2 - cnt1
                    if diff != 0:
                        self._addToBlockDiff(gndFormula.idxFormula, idxPllBlock, diff)
                else: # the block is the variable (idxGA is None)
                    #isRelevant = False
                    #for i in block:
                    #    if i in gndFormula.idxGroundAtoms():
                    #        isRelevant = True
                    #        break
                    #if not isRelevant: continue
                    cnt1, cnt2 = 0, 0
                    # find out which ga is true in the block
                    idxGATrueone = -1
                    for i in block:
                        if self.mln._getEvidence(i):
                            if idxGATrueone != -1: raise Exception("More than one true ground atom in block %s!" % blockname)
                            idxGATrueone = i                    
                    if idxGATrueone == -1: raise Exception("No true gnd atom in block!" % blockname)
                    idxInBlockTrueone = block.index(idxGATrueone)
                    # check true groundings for each block assigment
                    for i in block:
                        if i != idxGATrueone:
                            self.mln._setTemporaryEvidence(i, True)
                            self.mln._setTemporaryEvidence(idxGATrueone, False)
                        if self.mln._isTrueGndFormulaGivenEvidence(gndFormula):
                            if i == idxGATrueone:
                                cnt1 += 1
                            else:
                                cnt2 += 1
                        self.mln._removeTemporaryEvidence()
                    # save difference
                    diff = cnt2 - cnt1
                    if diff != 0:
                        self._addToBlockDiff(gndFormula.idxFormula, idxPllBlock, diff)

    def _getBlockProbMB(self, idxPllBlock, wt, relevantGroundFormulas=None):
        idxGA, block = self.pllBlocks[idxPllBlock]
        if idxGA != None:
            return self._getAtomProbMB(idxGA, wt, relevantGroundFormulas)
        else:
            # find out which one of the ground atoms in the block is true
            idxGATrueone = self._getBlockTrueone(block)
            idxInBlockTrueone = block.index(idxGATrueone)
            # get the exponentiated sum of weights for each possible assignment
            if relevantGroundFormulas == None:
                try:
                    relevantGroundFormulas = self.blockRelevantGFs[idxPllBlock]
                except:
                    relevantGroundFormulas = None
            # TODO: (potentially) numerically instable, therefore using mpmath
            expsums = self.mln._getBlockExpsums(block, wt, self.evidence, idxGATrueone, relevantGroundFormulas)
            #mpexpsums = map(lambda x: mpmath.mpf(x), expsums)
            #sums = self._getBlockSums(block, wt, self.evidence, idxGATrueone, relevantGroundFormulas)
            #print sums
            #avgsum = sum(map(lambda x: x/len(sums),sums))                        
            #sums = map(lambda x: x-avgsum, sums)
            #print sums
            #expsums = map(math.exp, sums)
            return float(expsums[idxInBlockTrueone] / fsum(expsums))

    def _grad(self, wt):
        #grad = [mpmath.mpf(0) for i in xrange(len(self.formulas))]        
        grad = numpy.zeros(len(self.mln.formulas), numpy.float64)
        self._calculateBlockProbsMB(wt)
        for (idxFormula, idxBlock), diff in self.blockdiffs.iteritems():
            v = diff * (self.blockProbsMB[idxBlock] - 1)
            grad[idxFormula] += v
        #print "wts =", wt
        #print "grad =", grad
        self.grad_opt_norm = float(sqrt(fsum(map(lambda x: x * x, grad))))
        
        #print "norm = %f" % norm
        return numpy.array(grad)

    def _calculateBlockProbsMB(self, wt):
        if ('wtsLastBlockProbMBComputation' not in dir(self)) or self.wtsLastBlockProbMBComputation != list(wt):
            #print "recomputing block probabilities...",
            self.blockProbsMB = [self._getBlockProbMB(i, wt, self.blockRelevantGFs[i]) for i in range(len(self.pllBlocks))]
            self.wtsLastBlockProbMBComputation = list(wt)
            #print "done."

    def _f(self, wt):
        self._calculateBlockProbsMB(wt)
        probs = map(lambda x: 1e-10 if x == 0 else x, self.blockProbsMB) # prevent 0 probs
        return fsum(map(log, probs))

    def getBPLL(self):
        return self._blockpll(self._weights())
        
    def _prepareOpt(self):
        # get blocks
        print "constructing blocks..."
        self.mln._getPllBlocks()
        self.pllBlocks = self.mln.pllBlocks        
        self.mln._getBlockRelevantGroundFormulas()
        self.blockRelevantGFs = self.mln.blockRelevantGFs
        # counts
        print "computing differences..."
        self._computeBlockDiffs()
        print "  %d differences recorded" % len(self.blockdiffs)
