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

import random

from Inference import *

# abstract super class for Markov chain Monte Carlo-based inference
class MCMCInference(Inference):
    # set a random state, taking the evidence blocks and block exclusions into account
    # blockInfo [out]
    def setRandomState(self, state, blockInfo=None):
        mln = self.mln
        for idxBlock, (idxGA, block) in enumerate(mln.pllBlocks):
            if idxBlock not in self.evidenceBlocks:
                if block != None: # block of mutually exclusive atoms
                    blockExcl = self.blockExclusions.get(idxBlock)
                    if blockExcl == None:
                        chosen = block[random.randint(0, len(block) - 1)]
                        for idxGA in block:
                            state[idxGA] = (idxGA == chosen)
                        if blockInfo != None:
                            falseOnes = filter(lambda x: x != chosen, block)
                            blockInfo[idxBlock] = [chosen, falseOnes]
                    else:
                        choosable = []
                        for i, idxGA in enumerate(block):
                            if i not in blockExcl:
                                choosable.append(idxGA)
                        maxidx = len(choosable) - 1
                        if maxidx == 0:
                            raise Exception("Evidence forces all ground atoms in block %s to be false" % mln._strBlock(block))
                        chosen = choosable[random.randint(0, maxidx)]
                        for idxGA in choosable:
                            state[idxGA] = (idxGA == chosen)
                        if blockInfo != None:
                            choosable.remove(chosen)
                            blockInfo[idxBlock] = [chosen, choosable]
                else: # regular ground atom, which can either be true or false
                    chosen = random.randint(0, 1)
                    state[idxGA] = bool(chosen)

    def _readEvidence(self, conjunction):
        # set evidence
        self._setEvidence(conjunction)
        # build up data structures
        self.evidence = conjunction
        self.evidenceBlocks = [] # list of pll block indices where we know the true one (and thus the setting for all of the block's atoms)
        self.blockExclusions = {} # dict: pll block index -> list (of indices into the block) of atoms that mustn't be set to true
        for idxBlock, (idxGA, block) in enumerate(self.mln.pllBlocks): # fill the list of blocks that we have evidence for
            if block != None:
                haveTrueone = False
                falseones = []
                for i, idxGA in enumerate(block):
                    ev = self.mln._getEvidence(idxGA, False)
                    if ev == True:
                        haveTrueone = True
                        break
                    elif ev == False:
                        falseones.append(i)
                if haveTrueone:
                    self.evidenceBlocks.append(idxBlock)
                elif len(falseones) > 0:
                    self.blockExclusions[idxBlock] = falseones
            else:
                if self.mln._getEvidence(idxGA, False) != None:
                    self.evidenceBlocks.append(idxBlock)

    class Chain:
        def __init__(self, inferenceObject, queries):
            self.queries = queries
            self.numSteps = 0
            self.numTrue = [0 for i in range(len(self.queries))]
            self.converged = False
            self.lastResult = 10
            # copy the current mln evidence as this chain's state
            self.state = list(inferenceObject.mln.evidence)
            # initialize remaining variables randomly (but consistently)
            inferenceObject.setRandomState(self.state)
        
        def update(self):
            debug = False
            self.numSteps += 1
            # keep track of counts for queries
            for i in range(len(self.queries)):
                if self.queries[i].isTrue(self.state):
                    self.numTrue[i] += 1
            # check if converged !!! TODO check for all queries
            if self.numSteps % 50 == 0:
                currentResult = self.getResults()[0]
                diff = abs(currentResult - self.lastResult)
                #print diff
                if diff < 0.001:
                    self.converged = True
                self.lastResult = currentResult
            # debug output
            if self.numSteps % 50 == 0 and debug:
                #print "  --> %s" % str(self.state),
                #print "after %d steps: P(%s | e) = %f" % (self.numSteps, str(self.query), float(self.numTrue) / self.numSteps)
                pass
        
        def getResults(self):
            results = []
            for i in range(len(self.queries)):
                results.append(float(self.numTrue[i]) / self.numSteps)
            return results
    
        def currentlyTrue(self, formula):
            ''' returns 1 if the given formula is true in the current state, 0 otherwise '''
            if formula.isTrue(self.state):
                return 1
            return 0
        
            
    class ChainGroup:
        def __init__(self, inferObject):
            self.chains = []
            self.inferObject = inferObject
    
        def addChain(self, chain):
            self.chains.append(chain)
    
        def getResults(self):
            numChains = len(self.chains)
            queries = self.chains[0].queries
            # compute average
            results = [0.0 for i in range(len(queries))]
            for chain in self.chains:
                cr = chain.getResults()
                for i in range(len(queries)):
                    results[i] += cr[i] / numChains
            # compute variance
            var = [0.0 for i in range(len(queries))]
            for chain in self.chains:
                cr = chain.getResults()
                for i in range(len(self.chains[0].queries)):
                    var[i] += (cr[i] - results[i]) ** 2 / numChains
            self.var = var
            self.results = results
            return results
        
        def currentlyTrue(self, formula):
            ''' returns the fraction of chains in which the given formula is currently true '''
            t = 0.0 
            for c in self.chains:
                t += c.currentlyTrue(formula)
            return t / len(self.chains)
        
        def printResults(self, shortOutput=False):
            if len(self.chains) > 1:
                for i in range(len(self.inferObject.queries)):
                    self.inferObject.additionalQueryInfo[i] = "[%dx%d steps, sd=%.3f]" % (len(self.chains), self.chains[0].numSteps, sqrt(self.var[i]))
            self.inferObject._writeResults(sys.stdout, self.results, shortOutput)
