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
        #ll = log(self.expsums[self.idxTrainingDB] / (self.partition_function / (len(self.mln.worlds))))
        ll = log(self.expsums[self.idxTrainingDB] / self.partition_function)
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
        for se in self.mln.softEvidence:
            self.mln._setEvidence(self.mln.gndAtoms[se["expr"]].idx, True)

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
    
    
class Abstract_ISEWW(SoftEvidenceLearner, LL):
    def __init__(self, mln):
        LL.__init__(self, mln)
        SoftEvidenceLearner.__init__(self, mln)     
        
    def _calculateWorldProbabilities(self):  
        #calculate only once as they do not change
        if False == hasattr(self, 'worldProbabilities'):
            self.worldProbabilities = {}
            #TODO: or (opimized) generate only world by flipping the soft evidences
            #discard all world where at least one non-soft evidence is different from the generated
            for idxWorld, world in enumerate(self.mln.worlds):
                worldProbability = 1
                discardWorld = False
                for gndAtom in self.mln.gndAtoms.values():
                    if world["values"][gndAtom.idx] != self.mln.worlds[self.idxTrainingDB]["values"][gndAtom.idx]:
                        
                        #check if it is soft:
                        isSoft = False
                        s = strFormula(gndAtom)
                        for se in self.mln.softEvidence:
                            if se["expr"] == s:
                                isSoft = True
                                break
                            
                        if False == isSoft:
                            discardWorld = True
                            break
                if discardWorld: 
                    print "discarded world", s, idxWorld#, world["values"][gndAtom.idx] , self.worlds[self.idxTrainingDB]["values"][gndAtom.idx]
                    continue
                
                for se in self.mln.softEvidence:
                    evidenceValue = self.mln._getSoftEvidence(self.mln.gndAtoms[se["expr"]], world["values"]) 
                    
                    worldProbability *= evidenceValue    
                    print "  ", "evidence, gndAtom", evidenceValue, se["expr"]#, self.evidence, world["values"]
                    
                if worldProbability > 0:
                    self.worldProbabilities[idxWorld] = worldProbability
                    
    def _grad(self, wt):
        raise Exception("Mode LL_ISEWW: gradient function is not implemented")
    
    def useGrad(self):
        return False   
    
class LL_ISEWW(Abstract_ISEWW):
    def __init__(self, mln):
        Abstract_ISEWW.__init__(self, mln)
    
    def _f(self, wt):
        self._calculateWorldValues(wt) #only to calculate partition function here:
        #print "worlds[idxTrainDB][\"sum\"] / Z", self.worlds[idxTrainDB]["sum"] , self.partition_function
        self._calculateWorldProbabilities()

        #old code, maximizes most probable world (see notes on paper)
        evidenceWorldSum = 0
        for idxWorld, world in enumerate(self.mln.worlds):
                
            if idxWorld in self.worldProbabilities:
                print "world:", idxWorld, "exp(worldWeights)", self.expsums[idxWorld], "worldProbability", self.worldProbabilities[idxWorld]
                evidenceWorldSum += self.expsums[idxWorld] * self.worldProbabilities[idxWorld]
                  
        print "wt =", wt
        print "evidenceWorldSum, self.partition_function", evidenceWorldSum, self.partition_function
        ll = log(evidenceWorldSum / self.partition_function)    
        
        print 
        return ll

    
class E_ISEWW(Abstract_ISEWW):    
    def __init__(self, mln):
        Abstract_ISEWW.__init__(self, mln)
        
    def _f(self, wt):
        self._calculateWorldValues(wt) #only to calculate partition function here:

        self._calculateWorldProbabilities()
        
        #new idea: minimize squared error of world prob. given by weights and world prob given by soft evidence
        error = 0
        for idxWorld, world in enumerate(self.mln.worlds):
            if idxWorld in self.worldProbabilities: #lambda_x
                worldProbability = self.worldProbabilities[idxWorld]
            else: 
                worldProbability = 0
                
            worldProbGivenWeights = self.expsums[idxWorld] / self.partition_function
            error += abs(worldProbGivenWeights - worldProbability)
            #print "(worldProbGivenWeights - worldProbability)**2",(worldProbGivenWeights - worldProbability)**2
            print "worldProbGivenWeights - worldProbability ", worldProbGivenWeights, "-", worldProbability

        print "wt =", wt
        print "error:", error
        ll = -error
        
        print 
        return ll
    



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
        
        #if evidence world does not occur in the samples, add it here:
        # in the soft case, it is never included as all normalization worlds are hard
        if tuple(self.mln.worlds[idxTrainDB]['values']) not in self.worldsSampled:
            #only here: add evidence world to partition function to guarantee that ll <= 0
            partition_function =  self.sampled_Z + self.expsums[idxTrainDB] #200000 + self.expsums[idxTrainDB]#
            partition_function /= self.mcsatSteps + 1
            print "_f: evidence world added to normalization"
        else:
            partition_function =  self.sampled_Z
            partition_function /= self.mcsatSteps
        
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
        grad = grad - self.formulaCount / self.mcsatSteps #self.sampled_Z
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
            
            self.formulaCount = numpy.zeros(len(self.mln.formulas), numpy.float64)
            self.worldsSampled = {} #hashmap with the worlds already sampled
            self.currentWeights = wtFull
            self.sampled_Z = 0
            what = [FOL.TrueFalse(True)]      
            self.mln.setWeights(wtFull)
            print "calling MCSAT with weights:", wtFull
            mcsat = self.mln.inferMCSAT(what, given="", softEvidence={}, sampleCallback=self._sampleCallback, maxSteps=self.mcsatSteps, 
                                        verbose=True, details =True, infoInterval=100, resultsInterval=100)
            print mcsat
            print "number of disctinct samples:", len(self.worldsSampled)
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
        
        #do not add duplicate worlds:
        if tuple(sampleWorld) not in self.worldsSampled:
            self.sampled_Z += exp_sum     
            self.worldsSampled[tuple(sampleWorld)] = True #add entry in hashmap
        #else:
            #print "discarded duplicate world:", hash(tuple(sampleWorld))
         
        self.formulaCount += sampleWorldFormulaCounts
        
        
        
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
        
        
class DSLL_ISEWW(SLL_ISE):
    def __init__(self, mln):
        SLL_ISE.__init__(self, mln)
        
    def _prepareOpt(self):
        LL._prepareOpt(self)
    
    def _computeCounts(self):
        LL._computeCounts(self)
    
    def _f(self, wt):
        
        wtFull = wt

        idxTrainDB = self.idxTrainingDB

        #sample expSum for evidence world
        self.sampledExpSum = 0
        self.currentWeights = wtFull
        what = [FOL.TrueFalse(True)]      
        self.mln.setWeights(wtFull)
        print "calling MCSAT with weights:", wtFull
        evidenceString = evidence2conjunction(self.mln.getEvidenceDatabase())
        #print "self.mln.getEvidenceDatabase()", self.mln.getEvidenceDatabase()
        print evidenceString
        print 
        print self.mln.softEvidence
        mcsat = self.mln.inferMCSAT(what, given=evidenceString, softEvidence=self.mln.softEvidence, sampleCallback=self._sampleEvidenceCallback, 
                                    maxSteps=self.mcsatStepsEvidenceWorld, verbose=True, details =True, infoInterval=1000, resultsInterval=1000,
                                    maxSoftEvidenceDeviation=0.05)
        print mcsat    
        #sample callback calculates self.sampledExpSum
        print "sampleEvidenceSteps:", self.sampleEvidenceSteps
        
        print 
        #sample worlds for Z
        print "DSLL_ISEWW: sample worlds for Z:"
        self._sampleWorlds(wtFull)
        
        self.sampledExpSum /= self.sampleEvidenceSteps
        
        #if evidence world does not occur in the samples, add it here:
        # in the soft case, it is never included as all normalization worlds are hard
        if tuple(self.mln.worlds[idxTrainDB]['values']) not in self.worldsSampled:
            #only here: add evidence world to partition function to guarantee that ll <= 0
            partition_function =  self.sampled_Z + self.sampledExpSum #200000 + self.expsums[idxTrainDB]#
            #partition_function /= self.mcsatStepsEvidenceWorld + 1
            print "_f: evidence world added to normalization"
        else:
            partition_function =  self.sampled_Z
            #partition_function /= self.mcsatStepsEvidenceWorld
        
        #print self.worlds
        print "sampledExpSum / Z", self.sampledExpSum, partition_function
        ll = log(self.sampledExpSum / partition_function)
        print "ll =", ll
        print 
        return ll
    
    #sampel is the chainGroup
    #step is step-number
    def _sampleEvidenceCallback(self, sample, step):
        #print "_sll_ise_sampleCallback:", sample, step
        
        #there is only one chain:
        sampleWorld = sample.chains[0].state
        weights = []
        for gndFormula in self.mln.gndFormulas:
            if self.mln._isTrue(gndFormula, sampleWorld):
                weights.append(self.currentWeights[gndFormula.idxFormula])
        exp_sum = exp(fsum(weights))

        self.sampledExpSum += exp_sum #/ self.mcsatStepsEvidenceWorld
        
        self.sampleEvidenceSteps = step
        
        if step % 1000 == 0:
            print "sampling evidence worlds (MCSAT), step: ", step, " sum(weights)", sum(weights)
    
    def _grad(self, wt):
        raise Exception("Mode DSLL_ISEWW: gradient function is not implemented")
    
    def useGrad(self):
        return False
    
    def _prepareOpt(self):
        self.mcsatStepsEvidenceWorld = self.params.get("mcsatStepsEvidenceWorld", 10000)
        SLL_ISE._prepareOpt(self)
        
        