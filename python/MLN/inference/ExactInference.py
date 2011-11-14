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

from Inference import *
import sys

class ExactInference(Inference):
    def __init__(self, mln):
        Inference.__init__(self, mln)
    
    # verbose: whether to print results (or anything at all, in fact)
    # details: (given that verbose is true) whether to output additional status information
    # debug: (given that verbose is true) if true, outputs debug information, in particular the distribution over possible worlds
    # debugLevel: level of detail for debug mode
    def _infer(self, verbose=True, details=False, shortOutput=False, debug=False, debugLevel=1, **args):
        worldValueKey = {"weights": "sum", "probs": "prod"}[self.mln.parameterType]
        # get set of possible worlds along with their probabilities
        if verbose and details: print "generating possible worlds..."
        self.mln._getWorlds() 
        if verbose and details: print "  %d worlds instantiated" % len(self.mln.worlds)
        # get the query formula(s)
        what = self.queries
        # if we have no explicit evidence, get the evidence that is set in the MLN as a conjunction
        given = self.given
        if given is None:
            given = evidence2conjunction(self.mln.getEvidenceDatabase())        
        # ground the evidence formula
        if given == "":
            given = None
        else:
            given = FOL.parseFormula(given)
            given = given.ground(self.mln, {})
        # start summing
        if verbose and details: print "summing..."
        numerators = [0.0 for i in range(len(what))]
        denominator = 0
        k = 1
        numerator_worlds = [[] for i in range(len(what))]
        denominator_worlds = []
        for world in self.mln.worlds:
            precond = (given == None)
            if not precond:
                precond = given.isTrue(world["values"])
            if precond:
                for i in range(len(what)):
                    if what[i].isTrue(world["values"]):
                        numerators[i] += world[worldValueKey]
                        numerator_worlds[i].append(k)
                denominator += world[worldValueKey]
                denominator_worlds.append(k)
            k += 1
        # normalize answers
        answers = []
        for i in range(len(what)):
            answers.append(numerators[i] / denominator)
        # some debug info
        if debug and verbose:  
            self.mln.printWorlds()
            for i in range(len(what)):
                self.additionalQueryInfo[i] = "\n   %s / %s " % (numerator_worlds[i], denominator_worlds)
        # return results
        return answers

class ExactInferenceLinear(Inference):
    '''
    variant of exact inference, where the possible worlds and associated values are generated dynamically (on demand) - linear space requirements;
    In particular, possible worlds are not kept in memory, such that memory consumption remains linear in the number of variables.    
    '''
    
    def __init__(self, mln):
        Inference.__init__(self, mln)
    
    # verbose: whether to print results (or anything at all, in fact)
    # details: (given that verbose is true) whether to output additional status information
    # debug: (given that verbose is true) if true, outputs debug information, in particular the distribution over possible worlds
    # debugLevel: level of detail for debug mode
    def _infer(self, verbose=True, details=False, shortOutput=False, debug=False, debugLevel=1, **args):
        # get the query formula(s)
        what = self.queries
        # if we have no explicit evidence, get the evidence that is set in the MLN as a conjunction
        given = self.given
        if given is None:
            given = evidence2conjunction(self.mln.getEvidenceDatabase())        
        # ground the evidence formula
        if given == "":
            given = None
        else:
            given = FOL.parseFormula(given)
            given = given.ground(self.mln, {})
        # start summing
        if verbose and details: print "summing..."
        wts = self.mln._weights()
        numerators = [0.0 for i in xrange(len(what))]
        denominator = 0
        k = 1
        for world in self._iterWorlds([], 0):
            precond = (given == None)
            if not precond:
                precond = given.isTrue(world)
            if precond:
                # compute world value
                worldValue = self.mln._calculateWorldExpSum(world, wts)
                # add to applicable numerators
                for i in xrange(len(what)):
                    if what[i].isTrue(world):
                        numerators[i] += worldValue
                # add to denominator
                denominator += worldValue
            k += 1
        # normalize answers
        answers = []
        for i in range(len(what)):
            answers.append(numerators[i] / denominator)
        return answers
    
    def _iterWorlds(self, values, idx):
        mln = self.mln
        if idx == len(mln.gndAtoms):
            yield values
        else:     
            # values that can be set for the truth value of the ground atom with index idx
            possible_settings = [True, False]
            # check for rigid predicates: for rigid predicates, we consider both values only if the evidence value is
            # unknown, otherwise we use the evidence value
            restricted = False
            gndAtom = mln.gndAtomsByIdx[idx]
            # check if setting the truth value for idx is critical for a block (which is the case when idx is the highest index in a block)
            if idx in mln.gndBlockLookup and POSSWORLDS_BLOCKING:
                block = mln.gndBlocks[mln.gndBlockLookup[idx]]
                if idx == max(block):
                    # count number of true values already set
                    nTrue, nFalse = 0, 0
                    for i in block:
                        if i < len(values): # i has already been set
                            if values[i]:
                                nTrue += 1
                    if nTrue >= 2: # violation, cannot continue
                        return
                    if nTrue == 1: # already have a true value, must set current value to false
                        possible_settings.remove(True)
                    if nTrue == 0: # no true value yet, must set current value to true
                        possible_settings.remove(False)
            # recursive descent
            for x in possible_settings:
                values.append(x)
                for w in self._iterWorlds(values, idx + 1):
                    yield w
                values.pop()

class EnumerationAsk(Inference):
    '''
        inference based on enumeration of (only) the worlds compatible with the evidence;
        supports soft evidence (assuming independence)
    '''
    
    def __init__(self, mln):
        Inference.__init__(self, mln)
        self.haveSoftEvidence = len(self.mln.softEvidence) > 0
    
    # verbose: whether to print results (or anything at all, in fact)
    # details: (given that verbose is true) whether to output additional status information
    # debug: (given that verbose is true) if true, outputs debug information, in particular the distribution over possible worlds
    # debugLevel: level of detail for debug mode
    def _infer(self, verbose=True, details=False, shortOutput=False, debug=False, debugLevel=1, **args):
        self.totalWorlds = 1.0
        self.doneCountingTotalWorlds = False
        # get blocks
        self.mln._getPllBlocks()
        self._getEvidenceBlockData(self.given)
        # start summing
        if verbose and details: print "summing..."
        numerators = [0.0 for i in range(len(self.queries))]
        denominator = 0
        k = 0
        for worldValues in self._enumerateWorlds():
            # compute exp. sum of weights for this world
            expsum = 0
            if self.haveSoftEvidence:
                for gf in self.mln.gndFormulas:                
                    expsum += self.mln.getTruthDegreeGivenSoftEvidence(gf, worldValues) * self.mln.formulas[gf.idxFormula].weight
            else:
                for gf in self.mln.gndFormulas:
                    if gf.isTrue(worldValues):
                        expsum +=  self.mln.formulas[gf.idxFormula].weight
            expsum = exp(expsum)
            # update numerators
            for i, query in enumerate(self.queries):
                if query.isTrue(worldValues):
                    numerators[i] += expsum
            denominator += expsum
            k += 1
            #print "%d %s\r" % (k, map(str, self.summedGndAtoms)),
            if verbose and k % 500 == 0:
                print "%d of %f worlds enumerated\r" % (k, self.totalWorlds),
                sys.stdout.flush()
        if verbose: print "%d worlds enumerated" % k
        # normalize answers
        return map(lambda x: x / denominator, numerators)
    
    def _enumerateWorlds(self):
        self.summedGndAtoms = set()
        for w in self.__enumerateWorlds(0, list(self.mln.evidence)):
            yield w
        
    def __enumerateWorlds(self, i, worldValues):
        numBlocks = len(self.mln.pllBlocks)
        if i == numBlocks:
            self.doneCountingTotalWorlds = True
            yield worldValues
            return
        idxGA, block = self.mln.pllBlocks[i]
        if block is not None: # block of mutex ground atoms
            haveEvidence = i in self.evidenceBlocks
            if self.haveSoftEvidence and not haveEvidence: # check for soft evidence                
                numSoft = 0
                for idxGA in block:
                    se = self.mln._getSoftEvidence(self.mln.gndAtomsByIdx[idxGA])
                    if se is not None:
                        numSoft += 1
                        worldValues[idxGA] = True
                if numSoft != len(block) and numSoft != 0:
                    raise Exception("Partial soft evidence for mutex block not allowed")
                haveEvidence = numSoft == len(block)
            if haveEvidence:
                for w in self.__enumerateWorlds(i+1, worldValues):
                    yield w
            else:
                if not self.doneCountingTotalWorlds: self.totalWorlds *= len(block)
                for idxGA in block:
                    for idxGA2 in block:
                        worldValues[idxGA2] = idxGA == idxGA2 # TODO it seems that this does not consider block exclusions
                    for w in self.__enumerateWorlds(i+1, worldValues):
                        yield w
        else: # it's a regular ground atom
            gndAtom = self.mln.gndAtomsByIdx[idxGA]
            if self.haveSoftEvidence:
                e = True if self.mln._getEvidenceDegree(gndAtom) > 0 else False
            else:
                e = self.mln._getEvidence(idxGA, False)
            if e is not None:
                worldValues[idxGA] = True if e > 0 else False
                for w in self.__enumerateWorlds(i+1, worldValues):
                    yield w
            else:
                if not self.doneCountingTotalWorlds: self.totalWorlds *= 2
                self.summedGndAtoms.add(gndAtom)
                for v in (True, False):
                    worldValues[gndAtom.idx] = v                
                    for w in self.__enumerateWorlds(i+1, worldValues):
                        yield w