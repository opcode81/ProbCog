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

import sys

from AbstractLearner import *


class LL(AbstractLearner):
    
    def __init__(self, mln):
        AbstractLearner.__init__(self, mln)
    
    def _computeCounts(self):
        ''' computes the number of true groundings of each formula in each possible world (sufficient statistics) '''
        self.counts = {}
        # for each possible world, count how many true groundings there are for each formula
        for i, world in enumerate(self.mln.worlds):            
            for gf in self.mln.gndFormulas:                
                if self.mln._isTrue(gf, world["values"]):
                    key = (i, gf.idxFormula)
                    cnt = self.counts.get(key, 0)
                    cnt += 1
                    self.counts[key] = cnt
    
    def _calculateWorldValues(self, wts):
        if hasattr(self, 'wtsLastWorldValueComputation') and self.wtsLastWorldValueComputation == list(wts): # avoid computing the values we already have
            return

        self.expsums = [0 for i in range(len(self.mln.worlds))]
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            self.expsums[idxWorld] += wts[idxFormula] * count
        
        #for worldIndex, world in enumerate(self.worlds):
            #world["sum"] = exp(world["sum"])
            #if allSoft == False, we added a new world for training (duplicated, but with soft evidence)  
            #  exclude this new one from the normalization (partition_function)
            #if self.learnWtsMode != 'LL_ISE' or self.allSoft == True or worldIndex != self.idxTrainingDB:
            #   total += world["sum"]
        
        self.expsums = map(exp, self.expsums)
        self.partition_function = fsum(self.expsums)
        
        self.wtsLastWorldValueComputation = list(wts)

    def _grad(self, wt):
        ''' computes the gradient of the log-likelihood given the weight vector wt '''
        idxTrainDB = self.idxTrainingDB
        self._calculateWorldValues(wt) # TODO move the calculation based on counts to this class
        grad = numpy.zeros(len(self.mln.formulas), numpy.float64)
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            if idxTrainDB == idxWorld:                
                grad[idxFormula] += count
            #TODO if self.learnWtsMode != 'LL_ISE' or self.allSoft == True or idxTrainDB != idxWorld:
            n = count * self.expsums[idxWorld] / self.partition_function
            grad[idxFormula] -= n
        print "wts =", wt
        print "grad =", grad
        print
        return grad

    def _f(self, wt):
        self._calculateWorldValues(wt)
        ll = log(self.expsums[self.idxTrainingDB] / (self.partition_function / (len(self.mln.worlds))))
        print "ll =", ll
        return ll
    
    def _getEvidenceWorldIndex(self):
        code = 0
        bit = 1
        for i in range(len(self.mln.gndAtoms)):
            if self.mln._getEvidence(i):
                code += bit
            bit *= 2
        return self.mln.worldCode2Index[code]
        
    def _prepareOpt(self):
        # create possible worlds if neccessary
        if not 'worlds' in dir(self.mln):
            print "creating possible worlds (%d ground atoms)..." % len(self.mln.gndAtoms)
            self.mln._createPossibleWorlds()
            print "  %d worlds created." % len(self.mln.worlds)
        # get the possible world index of the training database
        self.idxTrainingDB = self._getEvidenceWorldIndex()
        # compute counts
        print "computing counts..."
        self._computeCounts()
        print "  %d counts recorded." % len(self.counts)


from softeval import truthDegreeGivenSoftEvidence

class LL_ISE(SoftEvidenceLearner, LL):
    def __init__(self, mln):
        LL.__init__(self, mln)
        SoftEvidenceLearner.__init__(self, mln)

    def _prepareOpt(self):
        # HACK set soft evidence variables to true in evidence
        # TODO allsoft currently unsupported
        #for se in self.mln.softEvidence:
        #    self.mln._setEvidence(self.mln.gndAtoms[se["expr"]].idx, True)

        LL._prepareOpt(self)
        
    def _computeCounts(self):
        ''' compute soft counts (assuming independence) '''
        allSoft = self.params.get("allSoft", False)
        if allSoft == False:
            # compute regular counts for all "normal" possible worlds
            LL._computeCounts(self)
            # add another world for soft beliefs            
            baseWorld = self.mln.worlds[self.idxTrainingDB]
            self.mln.worlds.append({"values": baseWorld["values"]})
            self.idxTrainingDB = len(self.mln.worlds) - 1
            # and compute soft counts only for that world
            softCountWorldIndices = [self.idxTrainingDB]
        else:
            # compute soft counts for all possible worlds
            self.counts = {}
            softCountWorldIndices = xrange(len(self.mln.worlds))
            
        # compute soft counts      
        for i in softCountWorldIndices:
            world = self.mln.worlds[i]     
            if i == self.idxTrainingDB:
                print "TrainingDB: prod, groundformula"       
            for gf in self.mln.gndFormulas:
                prod = truthDegreeGivenSoftEvidence(gf, world["values"], self.mln)
                key = (i, gf.idxFormula)
                cnt = self.counts.get(key, 0)
                cnt += prod
                self.counts[key] = cnt
                if i == self.idxTrainingDB:
                    print "%f gf: %s" % (prod, str(gf))
            
            
            
        print "worlds len: ", len(self.mln.worlds)    
        #    if i == self.idxTrainingDB:
        #        print "TrainingDB: softCounts, formula"
        #        for j, f in enumerate(self.mln.formulas):
        #            print "  %f %s" % (self.counts[(i, j)], strFormula(f))
        #    else:
        #        for j,f in enumerate(self.formulas):
        #            normalizationWorldsMeanCounts[j] += self.counts[(i,j)]
        #            print "xx", self.counts[(i,j)]
        #    
        #normalizationWorldsMeanCounts = numpy.zeros(len(self.mln.formulas)) 
        #normalizationWorldCounter = 0   
        #for i in xrange(len(self.mln.worlds)):
        #    if allSoft == True or i != self.idxTrainingDB:
        #        normalizationWorldCounter += 1
        #        print "world", i
        #        for j, f in enumerate(self.mln.formulas):
        #            if (i, j) in self.counts:
        #                normalizationWorldsMeanCounts[j] += self.counts[(i, j)]
        #                print "  count", self.counts[(i, j)], strFormula(f)
        #    
        #print "normalizationWorldsMeanCounts:"
        #normalizationWorldsMeanCounts /= normalizationWorldCounter
        #for j, f in enumerate(self.mln.formulas):
        #    print " %f %s" % (normalizationWorldsMeanCounts[j], strFormula(self.mln.formulas[j]))        
    

class SLL_ISE(LL_ISE):
    def __init__(self, mln):
        LL_ISE.__init__(self, mln)
    
    def _f(self, wt):
        
        wtFull = wt

        idxTrainDB = self.idxTrainingDB
        self._calculateWorldValues(wtFull) #calculate sum for evidence world only
        print 
        #sample worlds for Z
        print "SLL_ISE: sample worlds:"
        self._sampleWorlds(wtFull)
        
        #only here: add evidence world to partition function to guarantee that ll <= 0
        partition_function = self.sampled_Z + self.expsums[idxTrainDB]
        
        partition_function /= self.mcsatSteps + 1
        
        #print self.worlds
        print "worlds[idxTrainDB][\"sum\"] / Z", self.expsums[idxTrainDB], partition_function
        ll = log(self.expsums[idxTrainDB] / partition_function)
        print "ll =", ll
        print 
        return ll
    
    def _grad(self, wt):
        idxTrainDB = self.idxTrainingDB
        
        wtFull = wt
        print 
        # sample other worlds:
        print "GRAD_SLL_ISE: sample worlds:"
        self._sampleWorlds(wtFull)
        
        #calculate gradient
        grad = numpy.zeros(len(self.mln.formulas), numpy.float64)
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            if idxTrainDB == idxWorld:                
                grad[idxFormula] += count        

        #print "before: ", grad
        grad = grad - self.weightedFormulaCount / self.mcsatSteps #self.sampled_Z
        #print "after: ", grad

        #TODO: figure out why the cache-reset is necessary to get non-0 weights
        #self.wtsLastSLLWorldSampling = []

        return grad
    
    #calculates self.partition_function and self.weightedFormulaCount in _sll_ise_sampleCallback()
    def _sampleWorlds(self, wtFull):
        if  ('wtsLastSLLWorldSampling' in dir(self)):
            print self.wtsLastSLLWorldSampling, "self.wtsLastSLLWorldSampling"
            print wtFull, "wtFull" 
            print
        #weights have changed => calculate new values
        if  ('wtsLastSLLWorldSampling' not in dir(self)) or numpy.any(self.wtsLastSLLWorldSampling != wtFull):
            self.wtsLastSLLWorldSampling = wtFull.copy()
            
            self.weightedFormulaCount = numpy.zeros(len(self.mln.formulas), numpy.float64)
            self.currentWeights = wtFull
            self.sampled_Z = 0
            what = [FOL.TrueFalse(True)]      
            self.mln.setWeights(wtFull)
            print "calling MCSAT with weights:", wtFull
            mcsat = self.mln.inferMCSAT(what, given="", softEvidence={}, sampleCallback=self._sampleCallback, maxSteps=self.mcsatSteps, verbose=False)
            print mcsat
        else:
            print "use cached values, do not sample (weights did not change)"
            #use cached values for self.weightedFormulaCount and self.partition_function, do nothing here
            
    #sampel is the chainGroup
    #step is step-number
    def _sampleCallback(self, sample, step):
        #print "_sll_ise_sampleCallback:", sample, step
        
        sampleWorldFormulaCounts = numpy.zeros(len(self.mln.formulas), numpy.float64)
        #there is only one chain:
        sampleWorld = sample.chains[0].state
        weights = []
        for gndFormula in self.mln.gndFormulas:
            if self.mln._isTrue(gndFormula, sampleWorld):
                sampleWorldFormulaCounts[gndFormula.idxFormula] += 1
                weights.append(self.currentWeights[gndFormula.idxFormula])
        exp_sum = exp(fsum(weights))
        
        self.sampled_Z += 1# exp_sum      
        self.weightedFormulaCount += sampleWorldFormulaCounts #* exp_sum
        
        if step % 100 == 0:
            print "sampling worlds (MCSAT), step: ", step, " sum(weights)", sum(weights)

    def _prepareOpt(self):
        self.mcsatSteps = self.params.get("mcsatSteps", 1000)
        
        # create just one possible worlds (for our training database)
        self.mln.worlds = []
        self.mln.worlds.append({"values": self.mln.evidence}) # HACK
        self.idxTrainingDB = 0 
        # compute counts
        print "computing counts..."
        self._computeCounts()
        print "  %d counts recorded." % len(self.counts)