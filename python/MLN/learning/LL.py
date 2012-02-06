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
    
    def __init__(self, mln, **params):
        AbstractLearner.__init__(self, mln, **params)
    
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


class CD(AbstractLearner):
    '''
        contrastive divergence-style learner
        maximises the omega-value of the training database world relative to the
        geometric mean of sampled worlds obtained via MC-SAT
    '''
    
    def __init__(self, mln, **params):
        AbstractLearner.__init__(self, mln, **params)
        
    def _f(self, wt):
        self.normSampler.sample(wt)
        
        ll = numpy.sum(self.formulaCountsTrainingDB * wt)
        ll -= numpy.sum(self.normSampler.globalFormulaCounts * wt) / self.normSampler.numSamples
        
        return ll
    
    def _grad(self, wt):
        self.normSampler.sample(wt)
        
        #calculate gradient
        sub = self.normSampler.globalFormulaCounts / self.normSampler.numSamples
        sys.stderr.write("wt: %s\n" % wt)
        sys.stderr.write("DB: %s\n" % self.formulaCountsTrainingDB)
        sys.stderr.write("sub: %s\n\n" % sub)
        grad = self.formulaCountsTrainingDB - self.normSampler.globalFormulaCounts / self.normSampler.numSamples
        sys.stderr.write("grad: %s\n\n" % grad)
       
        # HACK: gradient gets too large, reduce it
        #if numpy.any(numpy.abs(grad) > 1):
        #    print "gradient values too large:", numpy.max(numpy.abs(grad))
        #    grad = grad / (numpy.max(numpy.abs(grad)) / 1)
        #    print "scaling down to:", numpy.max(numpy.abs(grad))        
        
        print "CD_SE: _grad:", grad
        return grad        
    
    def _prepareOpt(self):
        # create just one possible worlds (for our training database)
        self.mln.worlds = []
        self.mln.worlds.append({"values": self.mln.evidence}) # HACK
        self.idxTrainingDB = 0
        
        # compute counts
        print "computing counts for training database..."
        self.formulaCountsTrainingDB = numpy.zeros(len(self.mln.formulas), numpy.float64)
        for i, world in enumerate(self.mln.worlds):            
            for gf in self.mln.gndFormulas:                
                if self.mln._isTrue(gf, world["values"]):
                    self.formulaCountsTrainingDB[gf.idxFormula] += 1.0
        
        # initialise sampler
        self.mcsatStepsEvidence = self.params.get("mcsatStepsEvidenceWorld", 1000)
        print self.params
        self.mcsatSteps = self.params.get("mcsatSteps", 2000)
        evidenceString = evidence2conjunction(self.mln.getEvidenceDatabase())
        self.normSampler = MCMCSampler(self.mln,
                                       dict(given="", softEvidence={}, maxSteps=self.mcsatSteps, 
                                            doProbabilityFitting=False,
                                            verbose=False, details=False, infoInterval=100, resultsInterval=100),
                                       discardDuplicateWorlds=False)

from softeval import truthDegreeGivenSoftEvidence


class LL_ISE(SoftEvidenceLearner, LL):
    def __init__(self, mln, **params):
        LL.__init__(self, mln, **params)
        SoftEvidenceLearner.__init__(self, mln, **params)

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
    def __init__(self, mln, **params):
        LL.__init__(self, mln, **params)
        SoftEvidenceLearner.__init__(self, mln, **params)     
        
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
                    evidenceValue = self.mln._getEvidenceTruthDegreeCW(self.mln.gndAtoms[se["expr"]], world["values"]) 
                    
                    worldProbability *= evidenceValue    
                    print "  ", "evidence, gndAtom", evidenceValue, se["expr"]#, self.evidence, world["values"]
                    
                if worldProbability > 0:
                    self.worldProbabilities[idxWorld] = worldProbability
                    
    def _grad(self, wt):
        raise Exception("Mode LL_ISEWW: gradient function is not implemented")
    
    def useGrad(self):
        return False   


class LL_ISEWW(Abstract_ISEWW):
    def __init__(self, mln, **params):
        Abstract_ISEWW.__init__(self, mln, **params)
    
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
    def __init__(self, mln, **params):
        Abstract_ISEWW.__init__(self, mln, **params)
        self.countsByWorld = {}
        self.softCountsEvidenceWorld = {}
        
    def _f(self, wt):
        self._calculateWorldValues(wt) #only to calculate partition function here:

        #self._calculateWorldProbabilities()
        
        #new idea: minimize squared error of world prob. given by weights and world prob given by soft evidence
        error = 0

        #old method (does not work with mixed hard and soft evidence)
        if True:
            for idxWorld, world in enumerate(self.mln.worlds):
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
    #            #print "world:", self.mln.worlds[idxWorld]
    #            print "worldProbGivenWeights - worldProbability ", worldProbGivenWeights, "-", worldProbability
      
        if False:#new try, doesn't work...
            for idxWorld, world in enumerate(self.mln.worlds):
                worldProbGivenWeights = self.expsums[idxWorld] / self.partition_function
                
                #compute countDiffSum:
                #for i, world in enumerate(self.mln.worlds):  
                if idxWorld not in self.countsByWorld:
                    print "computing counts for:", idxWorld
                    counts = {} #n         
                    for gf in self.mln.gndFormulas:                
                        if self.mln._isTrue(gf, self.mln.worlds[idxWorld]["values"]):
                            key = gf.idxFormula
                            cnt = counts.get(key, 0)
                            cnt += 1
                            counts[key] = cnt
                    self.countsByWorld[idxWorld] = counts
                
                #� (soft counts for evidence)
                if len(self.softCountsEvidenceWorld) == 0:
                    print "computing evidence soft counts"
                    self.softCountsEvidenceWorld = {}
                    for gf in self.mln.gndFormulas: 
                        prod = truthDegreeGivenSoftEvidence(gf, self.mln.evidence, self.mln)
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
    



class SLL_ISE(LL_ISE):
    '''
        Uses soft features to compute counts for a fictitious soft world (assuming independent soft evidence)
        Uses MCMC sampling to approximate the normalisation constant
    '''    
    
    def __init__(self, mln, **params):
        LL_ISE.__init__(self, mln, **params)
    
    def _f(self, wt):
        idxTrainDB = self.idxTrainingDB
        self._calculateWorldValues(wt) # (calculates sum for evidence world only)
        self.normSampler.sample(wt)

        partition_function = self.normSampler.Z / self.normSampler.numSamples
            
        #print self.worlds
        print "worlds[idxTrainDB][\"sum\"] / Z", self.expsums[idxTrainDB], partition_function
        ll = log(self.expsums[idxTrainDB]) - log(partition_function)
        print "ll =", ll
        print 
        return ll
    
    def _grad(self, wt):
        idxTrainDB = self.idxTrainingDB

        self.normSampler.sample(wt)
        
        #calculate gradient
        grad = numpy.zeros(len(self.mln.formulas), numpy.float64)
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            if idxTrainDB == idxWorld:                
                grad[idxFormula] += count
        grad = grad - self.normSampler.globalFormulaCounts / self.normSampler.numSamples

        # HACK: gradient gets too large, reduce it
        if numpy.any(numpy.abs(grad) > 1):
            print "gradient values too large:", numpy.max(numpy.abs(grad))
            grad = grad / (numpy.max(numpy.abs(grad)) / 1)
            print "scaling down to:", numpy.max(numpy.abs(grad))
        
        return grad
    
    def _prepareOpt(self):
        # create just one possible worlds (for our training database)
        self.mln.worlds = []
        self.mln.worlds.append({"values": self.mln.evidence}) # HACK
        self.idxTrainingDB = 0 
        # compute counts
        print "computing counts..."
        self._computeCounts()
        print "  %d counts recorded." % len(self.counts)
    
        # init sampler
        self.mcsatSteps = self.params.get("mcsatSteps", 2000)
        self.normSampler = MCMCSampler(self.mln,
                                       dict(given="", softEvidence={}, maxSteps=self.mcsatSteps, 
                                            doProbabilityFitting=False,
                                            verbose=True, details =True, infoInterval=100, resultsInterval=100),
                                       discardDuplicateWorlds=True)


class MCMCSampler(object):
    def __init__(self, mrf, mcsatParams, discardDuplicateWorlds = False):
        self.mrf = mrf
        self.wtsLast = None
        self.mcsatParams = mcsatParams
        
        self.discardDuplicateWorlds = discardDuplicateWorlds        

    def sample(self, wtFull):
        if (self.wtsLast is None) or numpy.any(self.wtsLast != wtFull): # weights have changed => calculate new values
            self.wtsLast = wtFull.copy()
            
            # reset data
            self.sampledWorlds = {}
            self.numSamples = 0
            self.Z = 0            
            self.globalFormulaCounts = numpy.zeros(len(self.mrf.mln.formulas), numpy.float64)            
            self.scaledGlobalFormulaCounts = numpy.zeros(len(self.mrf.mln.formulas), numpy.float64)
            #self.worldValues = []
            #self.formulaCounts = []
            self.currentWeights = wtFull
            
            self.mrf.mln.setWeights(wtFull)
            print "calling MCSAT with weights:", wtFull
            
            #evidenceString = evidence2conjunction(self.mln.getEvidenceDatabase())
            what = [FOL.TrueFalse(True)]      
            mcsat = self.mrf.inferMCSAT(what, sampleCallback=self._sampleCallback, **self.mcsatParams)
            #print mcsat
            print "sampled %d worlds" % self.numSamples
        else:
            print "using cached values, no sampling (weights did not change)"
            
    def _sampleCallback(self, sample, step):
        world = sample.chains[0].state
        
        if self.discardDuplicateWorlds:
            t = tuple(world)
            if t in self.sampledWorlds:
                return
            self.sampledWorlds[t] = True
        
        formulaCounts = numpy.zeros(len(self.mrf.mln.formulas), numpy.float64)                
        weights = []
        for gndFormula in self.mrf.mln.gndFormulas:
            if self.mrf._isTrue(gndFormula, world):
                formulaCounts[gndFormula.idxFormula] += 1
                weights.append(self.currentWeights[gndFormula.idxFormula])
                
        exp_sum = exp(fsum(weights))        
        #self.formulaCounts.append(formulaCounts)
        #self.worldValues.append(exp_sum)
        self.globalFormulaCounts += formulaCounts
        self.scaledGlobalFormulaCounts += formulaCounts * exp_sum
        self.Z += exp_sum
        self.numSamples += 1
        
        if self.numSamples % 1000 == 0:
            print "  MCSAT sample #%d" % self.numSamples


class SLL_SE(AbstractLearner):
    '''
        sampling-based maximum likelihood with soft evidence (SMLSE):
        uses MC-SAT-PC to sample soft evidence worlds
        uses MC-SAT to sample worlds in order to approximate Z
    '''
    
    def __init__(self, mln, **params):
        AbstractLearner.__init__(self, mln, **params)
    
    def _grad(self, wt):        
        self.normSampler.sample(wt)
        self.seSampler.sample(wt)
        
        grad = (self.seSampler.scaledGlobalFormulaCounts / self.seSampler.Z) - (self.normSampler.scaledGlobalFormulaCounts / self.normSampler.Z)

        #HACK: gradient gets too large, reduce it
        if numpy.any(numpy.abs(grad) > 1):
            print "gradient values too large:", numpy.max(numpy.abs(grad))
            grad = grad / (numpy.max(numpy.abs(grad)) / 1)
            print "scaling down to:", numpy.max(numpy.abs(grad))        
        
        print "SLL_SE: _grad:", grad
        return grad    
   
    def _f(self, wt):        
        self.normSampler.sample(wt)
        self.seSampler.sample(wt)
        
        numerator = self.seSampler.Z / self.seSampler.numSamples
                
        partition_function = self.normSampler.Z / self.normSampler.numSamples 
        
        ll = log(numerator) - log(partition_function)
        print "ll =", ll
        print 
        return ll
    
    def _prepareOpt(self):
        self.mcsatStepsEvidence = self.params.get("mcsatStepsEvidenceWorld", 1000)
        self.mcsatSteps = self.params.get("mcsatSteps", 2000)
        evidenceString = evidence2conjunction(self.mln.getEvidenceDatabase())
        self.normSampler = MCMCSampler(self.mln,
                                       dict(given="", softEvidence={}, maxSteps=self.mcsatSteps, 
                                            doProbabilityFitting=False,
                                            verbose=True, details =True, infoInterval=100, resultsInterval=100))
        self.seSampler = MCMCSampler(self.mln,
                                     dict(given=evidenceString, softEvidence=self.mln.softEvidence, maxSteps=self.mcsatStepsEvidence, 
                                          doProbabilityFitting=False,
                                          verbose=True, details =True, infoInterval=1000, resultsInterval=1000,
                                          maxSoftEvidenceDeviation=0.05))


class CD_SE(SLL_ISE):
    '''
        contrastive divergence with soft evidence
    '''
    
    def __init__(self, mln, **params):
        SLL_ISE.__init__(self, mln, **params)

    def _f(self, wt):
        pass # TODO
    
    def _grad(self, wt):        
        self.normSampler.sample(wt)
        self.seSampler.sample(wt)
        
        grad = (self.seSampler.globalFormulaCounts / self.seSampler.numSamples) - (self.normSampler.globalFormulaCounts / self.normSampler.numSamples)
       
        #HACK: gradient gets too large, reduce it
        if numpy.any(numpy.abs(grad) > 1):
            print "gradient values too large:", numpy.max(numpy.abs(grad))
            grad = grad / (numpy.max(numpy.abs(grad)) / 1)
            print "scaling down to:", numpy.max(numpy.abs(grad))        
        
        print "CD_SE: _grad:", grad
        return grad        
    
    def _prepareOpt(self):
        self.mcsatStepsEvidence = self.params.get("mcsatStepsEvidenceWorld", 1000)
        self.mcsatSteps = self.params.get("mcsatSteps", 2000)
        evidenceString = evidence2conjunction(self.mln.getEvidenceDatabase())
        self.normSampler = MCMCSampler(self.mln,
                                       dict(given="", softEvidence={}, maxSteps=self.mcsatSteps, 
                                            doProbabilityFitting=False,
                                            verbose=True, details =True, infoInterval=100, resultsInterval=100))
        self.seSampler = MCMCSampler(self.mln,
                                     dict(given=evidenceString, softEvidence=self.mln.softEvidence, maxSteps=self.mcsatStepsEvidence, 
                                          doProbabilityFitting=False,
                                          verbose=True, details =True, infoInterval=1000, resultsInterval=1000,
                                          maxSoftEvidenceDeviation=0.05))
