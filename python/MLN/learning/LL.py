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
    
    def __init__(self, mrf, **params):
        AbstractLearner.__init__(self, mrf, **params)
    
    def _computeCounts(self):
        ''' computes the number of true groundings of each formula in each possible world (sufficient statistics) '''
        self.counts = {}
        # for each possible world, count how many true groundings there are for each formula
        for i, world in enumerate(self.mrf.worlds):            
            for gf in self.mrf.gndFormulas:                
                if self.mrf._isTrue(gf, world["values"]):
                    key = (i, gf.idxFormula)
                    cnt = self.counts.get(key, 0)
                    cnt += 1
                    self.counts[key] = cnt
    
    def _calculateWorldValues(self, wts):
        if hasattr(self, 'wtsLastWorldValueComputation') and self.wtsLastWorldValueComputation == list(wts): # avoid computing the values we already have
            return

        self.expsums = [0 for i in range(len(self.mrf.worlds))]

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
        grad = numpy.zeros(len(self.mrf.formulas), numpy.float64)
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
        #ll = log(self.expsums[self.idxTrainingDB] / (self.partition_function / (len(self.mrf.worlds))))
        ll = log(self.expsums[self.idxTrainingDB] / self.partition_function)
        print "ll =", ll
        return ll
    
    def _getEvidenceWorldIndex(self):
        code = 0
        bit = 1
        for i in range(len(self.mrf.gndAtoms)):
            if self.mrf._getEvidence(i):
                code += bit
            bit *= 2
        return self.mrf.worldCode2Index[code]
        
    def _prepareOpt(self):
        # create possible worlds if neccessary
        if not 'worlds' in dir(self.mrf):
            print "creating possible worlds (%d ground atoms)..." % len(self.mrf.gndAtoms)
            self.mrf._createPossibleWorlds()
            print "  %d worlds created." % len(self.mrf.worlds)
        # get the possible world index of the training database
        self.idxTrainingDB = self._getEvidenceWorldIndex()
        # compute counts
        print "computing counts..."
        self._computeCounts()
        print "  %d counts recorded." % len(self.counts)


from softeval import truthDegreeGivenSoftEvidence


class LL_ISE(SoftEvidenceLearner, LL):
    def __init__(self, mrf, **params):
        LL.__init__(self, mrf, **params)
        SoftEvidenceLearner.__init__(self, mrf, **params)

    def _prepareOpt(self):
        # HACK set soft evidence variables to true in evidence
        # TODO allsoft currently unsupported
        for se in self.mrf.softEvidence:
            self.mrf._setEvidence(self.mrf.gndAtoms[se["expr"]].idx, True)

        LL._prepareOpt(self)
        
    def _computeCounts(self):
        ''' compute soft counts (assuming independence) '''
        allSoft = self.params.get("allSoft", False)
        if allSoft == False:
            # compute regular counts for all "normal" possible worlds
            LL._computeCounts(self)
            # add another world for soft beliefs            
            baseWorld = self.mrf.worlds[self.idxTrainingDB]
            self.mrf.worlds.append({"values": baseWorld["values"]})
            self.idxTrainingDB = len(self.mrf.worlds) - 1
            # and compute soft counts only for that world
            softCountWorldIndices = [self.idxTrainingDB]
        else:
            # compute soft counts for all possible worlds
            self.counts = {}
            softCountWorldIndices = xrange(len(self.mrf.worlds))
            
        # compute soft counts      
        for i in softCountWorldIndices:
            world = self.mrf.worlds[i]     
            if i == self.idxTrainingDB:
                print "TrainingDB: prod, groundformula"       
            for gf in self.mrf.gndFormulas:
                prod = truthDegreeGivenSoftEvidence(gf, world["values"], self.mrf)
                key = (i, gf.idxFormula)
                cnt = self.counts.get(key, 0)
                cnt += prod
                self.counts[key] = cnt
                if i == self.idxTrainingDB:
                    print "%f gf: %s" % (prod, str(gf))
            
            
            
        print "worlds len: ", len(self.mrf.worlds)    
        #    if i == self.idxTrainingDB:
        #        print "TrainingDB: softCounts, formula"
        #        for j, f in enumerate(self.mrf.formulas):
        #            print "  %f %s" % (self.counts[(i, j)], strFormula(f))
        #    else:
        #        for j,f in enumerate(self.formulas):
        #            normalizationWorldsMeanCounts[j] += self.counts[(i,j)]
        #            print "xx", self.counts[(i,j)]
        #    
        #normalizationWorldsMeanCounts = numpy.zeros(len(self.mrf.formulas)) 
        #normalizationWorldCounter = 0   
        #for i in xrange(len(self.mrf.worlds)):
        #    if allSoft == True or i != self.idxTrainingDB:
        #        normalizationWorldCounter += 1
        #        print "world", i
        #        for j, f in enumerate(self.mrf.formulas):
        #            if (i, j) in self.counts:
        #                normalizationWorldsMeanCounts[j] += self.counts[(i, j)]
        #                print "  count", self.counts[(i, j)], strFormula(f)
        #    
        #print "normalizationWorldsMeanCounts:"
        #normalizationWorldsMeanCounts /= normalizationWorldCounter
        #for j, f in enumerate(self.mrf.formulas):
        #    print " %f %s" % (normalizationWorldsMeanCounts[j], strFormula(self.mrf.formulas[j]))        
    
    
class Abstract_ISEWW(SoftEvidenceLearner, LL):
    def __init__(self, mrf, **params):
        LL.__init__(self, mrf, **params)
        SoftEvidenceLearner.__init__(self, mrf, **params)     
        
    def _calculateWorldProbabilities(self):  
        #calculate only once as they do not change
        if False == hasattr(self, 'worldProbabilities'):
            self.worldProbabilities = {}
            #TODO: or (opimized) generate only world by flipping the soft evidences
            #discard all world where at least one non-soft evidence is different from the generated
            for idxWorld, world in enumerate(self.mrf.worlds):
                worldProbability = 1
                discardWorld = False
                for gndAtom in self.mrf.gndAtoms.values():
                    if world["values"][gndAtom.idx] != self.mrf.worlds[self.idxTrainingDB]["values"][gndAtom.idx]:
                        
                        #check if it is soft:
                        isSoft = False
                        s = strFormula(gndAtom)
                        for se in self.mrf.softEvidence:
                            if se["expr"] == s:
                                isSoft = True
                                break
                            
                        if False == isSoft:
                            discardWorld = True
                            break
                if discardWorld: 
                    print "discarded world", s, idxWorld#, world["values"][gndAtom.idx] , self.worlds[self.idxTrainingDB]["values"][gndAtom.idx]
                    continue
                
                for se in self.mrf.softEvidence:
                    evidenceValue = self.mrf._getEvidenceTruthDegreeCW(self.mrf.gndAtoms[se["expr"]], world["values"]) 
                    
                    worldProbability *= evidenceValue    
                    print "  ", "evidence, gndAtom", evidenceValue, se["expr"]#, self.evidence, world["values"]
                    
                if worldProbability > 0:
                    self.worldProbabilities[idxWorld] = worldProbability
                    
    def _grad(self, wt):
        raise Exception("Mode LL_ISEWW: gradient function is not implemented")
    
    def useGrad(self):
        return False   


class LL_ISEWW(Abstract_ISEWW):
    def __init__(self, mrf, **params):
        Abstract_ISEWW.__init__(self, mrf, **params)
    
    def _f(self, wt):
        self._calculateWorldValues(wt) #only to calculate partition function here:
        #print "worlds[idxTrainDB][\"sum\"] / Z", self.worlds[idxTrainDB]["sum"] , self.partition_function
        self._calculateWorldProbabilities()

        #old code, maximizes most probable world (see notes on paper)
        evidenceWorldSum = 0
        for idxWorld, world in enumerate(self.mrf.worlds):
                
            if idxWorld in self.worldProbabilities:
                print "world:", idxWorld, "exp(worldWeights)", self.expsums[idxWorld], "worldProbability", self.worldProbabilities[idxWorld]
                evidenceWorldSum += self.expsums[idxWorld] * self.worldProbabilities[idxWorld]
                  
        print "wt =", wt
        print "evidenceWorldSum, self.partition_function", evidenceWorldSum, self.partition_function
        ll = log(evidenceWorldSum / self.partition_function)    
        
        print 
        return ll

    
class E_ISEWW(Abstract_ISEWW):    
    def __init__(self, mrf, **params):
        Abstract_ISEWW.__init__(self, mrf, **params)
        self.countsByWorld = {}
        self.softCountsEvidenceWorld = {}
        
    def _f(self, wt):
        self._calculateWorldValues(wt) #only to calculate partition function here:

        #self._calculateWorldProbabilities()
        
        #new idea: minimize squared error of world prob. given by weights and world prob given by soft evidence
        error = 0

        #old method (does not work with mixed hard and soft evidence)
        if True:
            for idxWorld, world in enumerate(self.mrf.worlds):
                if idxWorld in self.worldProbabilities: #lambda_x
                    worldProbability = self.worldProbabilities[idxWorld]
                else: 
                    worldProbability = 0
                    
                worldProbGivenWeights = self.expsums[idxWorld] / self.partition_function
                error += abs(worldProbGivenWeights - worldProbability)
                #print "worldProbGivenWeights - worldProbability ", worldProbGivenWeights, "-", worldProbability
    
    #        for idxWorld, worldProbability  in self.worldProbabilities.iteritems(): #lambda_x
    #            worldProbGivenWeights = self.expsums[idxWorld] / self.partition_function
    #            error += abs(worldProbGivenWeights - worldProbability)
    #            #print "world:", self.mrf.worlds[idxWorld]
    #            print "worldProbGivenWeights - worldProbability ", worldProbGivenWeights, "-", worldProbability
      
        if False:#new try, doesn't work...
            for idxWorld, world in enumerate(self.mrf.worlds):
                worldProbGivenWeights = self.expsums[idxWorld] / self.partition_function
                
                #compute countDiffSum:
                #for i, world in enumerate(self.mrf.worlds):  
                if idxWorld not in self.countsByWorld:
                    print "computing counts for:", idxWorld
                    counts = {} #n         
                    for gf in self.mrf.gndFormulas:                
                        if self.mrf._isTrue(gf, self.mrf.worlds[idxWorld]["values"]):
                            key = gf.idxFormula
                            cnt = counts.get(key, 0)
                            cnt += 1
                            counts[key] = cnt
                    self.countsByWorld[idxWorld] = counts
                
                #� (soft counts for evidence)
                if len(self.softCountsEvidenceWorld) == 0:
                    print "computing evidence soft counts"
                    self.softCountsEvidenceWorld = {}
                    for gf in self.mrf.gndFormulas: 
                        prod = truthDegreeGivenSoftEvidence(gf, self.mrf.evidence, self.mrf)
                        key = gf.idxFormula
                        cnt = self.softCountsEvidenceWorld.get(key, 0)
                        cnt += prod
                        self.softCountsEvidenceWorld[key] = cnt
                            #if i == self.idxTrainingDB:
                            #    print "%f gf: %s" % (prod, str(gf))
                    
                countDiffSum = 0
                for idxFormula, count in self.countsByWorld[idxWorld].iteritems():
                    countDiffSum += abs(count - self.softCountsEvidenceWorld[idxFormula])
                
                #print "countDiffSum", countDiffSum, "worldProbability", worldProbGivenWeights
                error += worldProbGivenWeights * ((countDiffSum)**2)
      
        print "wt =", wt
        print "error:", error
        ll = -error 
        
        print 
        return ll

