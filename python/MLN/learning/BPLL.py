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
    
    def __init__(self, mln, **params):
        PLL.__init__(self, mln, **params)

    # get the probability of the assignment for the block the given atom is in
    def getBlockProbMB(self, atom): 
        idxGndAtom = self.gndAtoms[atom].idx       
        self._getBlockProbMB(idxBlock, self._weights())

    def _addToBlockDiff(self, idxFormula, idxBlock, diff):
        key = (idxFormula, idxBlock)
        cur = self.blockdiffs.get(key, 0)
        self.blockdiffs[key] = cur + diff        

    def _computeStatistics(self):
        '''
        computes the differences for the gradient computation
        '''
        print "computing differences..."
        self.blockdiffs = {}
        for idxPllBlock, (idxGA, block) in enumerate(self.mln.pllBlocks):
            for gndFormula in self.mln.blockRelevantGFs[idxPllBlock]:
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
                            if idxGATrueone != -1: raise Exception("More than one true ground atom in block '%s'!" % self.mln._strBlock(block))
                            idxGATrueone = i                    
                    if idxGATrueone == -1: raise Exception("No true ground atom in block '%s'!" % self.mln._strBlock(block))
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
        print "  %d differences recorded" % len(self.blockdiffs)

    def _getBlockProbMB(self, idxPllBlock, wt):
        if self.mln.blockRelevantGFs is not None:
            relevantGroundFormulas = self.mln.blockRelevantGFs[idxPllBlock]
        else:
            relevantGroundFormulas = None
        idxGA, block = self.mln.pllBlocks[idxPllBlock]
        if idxGA != None:
            return self._getAtomProbMB(idxGA, wt, relevantGroundFormulas)
        else:
            # find out which one of the ground atoms in the block is true
            idxGATrueone = self.mln._getBlockTrueone(block)
            idxInBlockTrueone = block.index(idxGATrueone)
            # get the exponentiated sum of weights for each possible assignment
            if relevantGroundFormulas == None:
                try:
                    relevantGroundFormulas = self.mln.blockRelevantGFs[idxPllBlock]
                except:
                    relevantGroundFormulas = None
            # TODO: (potentially) numerically instable, therefore using mpmath
            expsums = self.mln._getBlockExpsums(block, wt, self.mln.evidence, idxGATrueone, relevantGroundFormulas)
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
            self.blockProbsMB = [self._getBlockProbMB(i, wt) for i in range(len(self.mln.pllBlocks))]
            self.wtsLastBlockProbMBComputation = list(wt)
            #print "done."

    def _f(self, wt):
        self._calculateBlockProbsMB(wt)
        probs = map(lambda x: 1e-10 if x == 0 else x, self.blockProbsMB) # prevent 0 probs
        return fsum(map(log, probs))

    def getBPLL(self):
        return self._blockpll(self._weights())
        
    def _prepareOpt(self):
        print "constructing blocks..."
        self.mln._getPllBlocks()
        self.mln._getBlockRelevantGroundFormulas()
        # counts        
        self._computeStatistics()        


class BPLLMemoryEfficient(BPLL):
    '''
    memory-efficient version of BPLL that computes f based only on a sufficient statistic (especially great for multiple databases)
    '''    
    
    def __init__(self, mln, **params):
        BPLL.__init__(self, mln, **params)
        self.mbcounts = {}

    def _prepareOpt(self):
        #BPLL._prepareOpt(self, computeRelevantGFs=True)
        #self._computeBlockStats() # compute the sufficient statistic for the computation of f
        #self.blockRelevantG.Fs=None
        print "constructing blocks..."
        self.mln._getPllBlocks()
        self.mln._getAtom2BlockIdx()
        self._computeStatistics()
        # remove data that is now obsolete
        self.mln.removeGroundFormulaData()
        self.mln.atom2BlockIdx = None
    
    def _addMBCount(self, idxVar, size, idxValue, idxWeight):
        if idxVar not in self.mbcounts:
            d = self.mbcounts[idxVar] = [[] for i in xrange(size)]
        self.mbcounts[idxVar][idxValue].append(idxWeight)

    def _getBlockProbMB(self, idxPllBlock, wt):
        idxGA, block = self.mln.pllBlocks[idxPllBlock]
        if idxGA != None:
            return self._getMBValue(idxPllBlock, 0, wt)
        else:
            idxGATrueone = self.mln._getBlockTrueone(block)
            idxInBlockTrueone = block.index(idxGATrueone) # TODO: cache this from block computation?
            return self._getMBValue(idxPllBlock, idxInBlockTrueone, wt)
    
    def _getMBValue(self, idxVar, idxValue, wt):
        l = self.mbcounts[idxVar]
        sums = []
        for i, l2 in enumerate(l):
            v = 0.0
            for idxWeight in l2:
                v += wt[idxWeight]
            sums.append(v)
        expsums = map(exp, sums)
        return float(expsums[idxValue] / fsum(expsums))
    
    def _computeStatistics(self):
        '''
        computes the statistics upon which the optimization is based:
        differences for the gradient computation, counts for probability given Markov blanket
        '''
        debug = True
        print "computing statistics..."
        self.blockdiffs = {}
        for idxGndFormula, gndFormula in enumerate(self.mln.gndFormulas):
            print "  ground formula %d/%d\r" % (idxGndFormula, len(self.mln.gndFormulas)),
            print
            
            idxBlocks = set()
            for idxGA in gndFormula.idxGroundAtoms():
                print self.mln.gndAtomsByIdx[idxGA]
                idxBlocks.add(self.mln.atom2BlockIdx[idxGA])
                        
            for idxVar in idxBlocks:
                
                (idxGA, block) = self.mln.pllBlocks[idxVar]
            
                if idxGA is not None: # ground atom is the variable as it's not in a block
                    
                    cnt1, cnt2 = 0, 0
                    #if not (idxGA in gndFormula.idxGroundAtoms()): continue
                    
                    # check if formula is true if gnd atom maintains its truth value
                    if self.mln._isTrueGndFormulaGivenEvidence(gndFormula):
                        cnt1 = 1
                        self._addMBCount(idxVar, 2, 0, gndFormula.idxFormula)
                    
                    # check if formula is true if gnd atom's truth value is inverted
                    old_tv = self.mln._getEvidence(idxGA)
                    self.mln._setTemporaryEvidence(idxGA, not old_tv)
                    if self.mln._isTrueGndFormulaGivenEvidence(gndFormula):
                        cnt2 = 1
                        self._addMBCount(idxVar, 2, 1, gndFormula.idxFormula)
                    self.mln._removeTemporaryEvidence()
                    
                    # save difference
                    diff = cnt2 - cnt1
                    if diff != 0:
                        self._addToBlockDiff(gndFormula.idxFormula, idxVar, diff)
                        
                else: # the block is the variable (idxGA is None)

                    cnt1, cnt2 = 0, 0
                    size = len(block)
                    
                    # find out which ga is true in the block
                    idxGATrueone = -1
                    for i in block:
                        if self.mln._getEvidence(i):
                            if idxGATrueone != -1: raise Exception("More than one true ground atom in block '%s'!" % self.mln._strBlock(block))
                            idxGATrueone = i                    
                    if idxGATrueone == -1: raise Exception("No true ground atom in block '%s'!" % self.mln._strBlock(block))
                    idxInBlockTrueone = block.index(idxGATrueone)
                    
                    # check true groundings for each block assigment
                    for idxValue, i in enumerate(block):
                        if i != idxGATrueone:
                            self.mln._setTemporaryEvidence(i, True)
                            self.mln._setTemporaryEvidence(idxGATrueone, False)
                        if self.mln._isTrueGndFormulaGivenEvidence(gndFormula):
                            if i == idxGATrueone:
                                cnt1 += 1
                            else:
                                cnt2 += 1
                            self._addMBCount(idxVar, size, idxValue, gndFormula.idxFormula)
                        self.mln._removeTemporaryEvidence()
                        
                    # save difference
                    diff = cnt2 - cnt1
                    if diff != 0:
                        self._addToBlockDiff(gndFormula.idxFormula, idxVar, diff)