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


class SLL(AbstractLearner):
    '''
        sample-based log-likelihood
    '''
    
    def __init__(self, mrf, **params):
        AbstractLearner.__init__(self, mrf, **params)
        self.mcsatSteps = self.params.get("mcsatSteps", 2000)        
        self.samplerParams = dict(given="", softEvidence={}, maxSteps=self.mcsatSteps, 
                                  doProbabilityFitting=False,
                                  verbose=False, details=False, infoInterval=100, resultsInterval=100)
        self.samplerConstructionParams = dict(discardDuplicateWorlds=False, keepTopWorldCounts=False)
        
    def _sample(self, wt, caller):
        self.normSampler.sample(wt)
        
        #self.uniDiff = self.totalFormulaCountsUni - self.normSampler.topWorldFormulaCounts * self.numUniformSamples
        
        #sys.stderr.write("%s\n" % caller)
        #sys.stderr.write("wt: %s\n" % str(wt))
        #sys.stderr.write("DB: %s\n" % str(self.formulaCountsTrainingDB))
        #sys.stderr.write("sub: %s\n" % str(self.normSampler.globalFormulaCounts / self.normSampler.numSamples))
        #sys.stderr.write("grad: %s\n" % str(self.formulaCountsTrainingDB - self.normSampler.globalFormulaCounts / self.normSampler.numSamples))
        #sys.stderr.write("f: %s\n\n" % str(numpy.sum(self.formulaCountsTrainingDB * wt) - numpy.sum(self.normSampler.globalFormulaCounts * wt) / self.normSampler.numSamples))        
        
    def _f(self, wt):
        # although this function corresponds to the gradient, it cannot soundly be applied to
        # the problem, because the set of samples is drawn only from the set of worlds that
        # have probability mass only
        # i.e. it would optimize the world's probability relative to the worlds that have
        # non-zero probability rather than all worlds, which is problematic in the presence of
        # hard constraints that need to be learned as being hard
        
        self._sample(wt, "f")        
        ll = numpy.sum(self.formulaCountsTrainingDB * wt) - numpy.sum(self.normSampler.globalFormulaCounts * wt) / self.normSampler.numSamples
        
        # correction for shrinkage
        #corr = numpy.sum(self.uniDiff * wt) / self.numUniformSamples
        #ll -= corr
        
        return ll
    
    def _grad(self, wt):
        self._sample(wt, "grad")

        grad = self.formulaCountsTrainingDB - self.normSampler.globalFormulaCounts / self.normSampler.numSamples
        
        # correction for shrinkage
        #grad -= self.uniDiff / self.numUniformSamples
       
        # HACK: gradient gets too large, reduce it
        #if numpy.any(numpy.abs(grad) > 1):
        #    print "gradient values too large:", numpy.max(numpy.abs(grad))
        #    grad = grad / (numpy.max(numpy.abs(grad)) / 1)
        #    print "scaling down to:", numpy.max(numpy.abs(grad))        
        
        print "grad:", grad
        return grad
    
    def _initSampler(self):
        self.normSampler = MCMCSampler(self.mrf,
                                       self.samplerParams,
                                       **self.samplerConstructionParams)
    
    def _prepareOpt(self):
        # compute counts
        print "computing counts for training database..."
        self.formulaCountsTrainingDB = self.mrf.countTrueGroundingsInWorld(self.mrf.evidence)
        
        # initialise sampler
        self._initSampler()        
        
        # collect some uniform sample data for shrinkage correction
        #self.numUniformSamples = 5000
        #self.totalFormulaCountsUni = numpy.zeros(len(self.mrf.formulas))        
        #for i in xrange(self.numUniformSamples):
        #    world = self.mrf.getRandomWorld()
        #    self.totalFormulaCountsUni += self.mrf.countTrueGroundingsInWorld(world)
            

class SLL_DN(SLL):
    '''
        sample-based log-likelihood via diagonal Newton
    '''
    
    def __init__(self, mrf, **params):
        SLL.__init__(self, mrf, **params)
        self.samplerConstructionParams["computeHessian"] = True
    
    def _hessian(self, wt):
        self._sample(wt, "hessian")
        return self.normSampler.getHessian()
    
    def getAssociatedOptimizerName(self):
        return "diagonalNewton"
        

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
    



class SLL_ISE(LL_ISE):
    '''
        Uses soft features to compute counts for a fictitious soft world (assuming independent soft evidence)
        Uses MCMC sampling to approximate the normalisation constant
    '''    
    
    def __init__(self, mrf, **params):
        LL_ISE.__init__(self, mrf, **params)
    
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
        grad = numpy.zeros(len(self.mrf.formulas), numpy.float64)
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
        self.mrf.worlds = []
        self.mrf.worlds.append({"values": self.mrf.evidence}) # HACK
        self.idxTrainingDB = 0 
        # compute counts
        print "computing counts..."
        self._computeCounts()
        print "  %d counts recorded." % len(self.counts)
    
        # init sampler
        self.mcsatSteps = self.params.get("mcsatSteps", 2000)
        self.normSampler = MCMCSampler(self.mrf,
                                       dict(given="", softEvidence={}, maxSteps=self.mcsatSteps, 
                                            doProbabilityFitting=False,
                                            verbose=True, details =True, infoInterval=100, resultsInterval=100),
                                       discardDuplicateWorlds=True)


class MCMCSampler(object):
    def __init__(self, mrf, mcsatParams, discardDuplicateWorlds = False, keepTopWorldCounts = False, computeHessian = False):
        self.mrf = mrf
        self.N = len(self.mrf.mln.formulas) 
        self.wtsLast = None
        self.mcsatParams = mcsatParams
        self.keepTopWorldCounts = keepTopWorldCounts
        if keepTopWorldCounts:
            self.topWorldValue = 0.0
        self.computeHessian = computeHessian
        
        self.discardDuplicateWorlds = discardDuplicateWorlds        

    def sample(self, wtFull):
        if (self.wtsLast is None) or numpy.any(self.wtsLast != wtFull): # weights have changed => calculate new values
            self.wtsLast = wtFull.copy()
            
            # reset data
            N = self.N
            self.sampledWorlds = {}
            self.numSamples = 0
            self.Z = 0            
            self.globalFormulaCounts = numpy.zeros(N, numpy.float64)            
            self.scaledGlobalFormulaCounts = numpy.zeros(N, numpy.float64)
            #self.worldValues = []
            #self.formulaCounts = []
            self.currentWeights = wtFull
            if self.computeHessian:
                self.hessian = None
                self.hessianProd = numpy.zeros((N,N), numpy.float64)
            
            self.mrf.mln.setWeights(wtFull)
            print "calling MCSAT with weights:", wtFull
            
            #evidenceString = evidence2conjunction(self.mrf.getEvidenceDatabase())
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
        
        formulaCounts = self.mrf.countTrueGroundingsInWorld(world)               
        exp_sum = exp(numpy.sum(formulaCounts * self.currentWeights))
        #self.formulaCounts.append(formulaCounts)
        #self.worldValues.append(exp_sum)
        self.globalFormulaCounts += formulaCounts
        self.scaledGlobalFormulaCounts += formulaCounts * exp_sum
        self.Z += exp_sum
        self.numSamples += 1
        
        if self.keepTopWorldCounts and exp_sum > self.topWorldValue:
            self.topWorldFormulaCounts = formulaCounts
        
        if self.computeHessian:
            for i in xrange(self.N):
                self.hessianProd[i][i] += formulaCounts[i]**2
                for j in xrange(i+1, self.N):
                    v = formulaCounts[i] * formulaCounts[j]
                    self.hessianProd[i][j] += v
                    self.hessianProd[j][i] += v
        
        if self.numSamples % 1000 == 0:
            print "  MCSAT sample #%d" % self.numSamples
    
    def getHessian(self):
        if not self.computeHessian: raise Exception("The Hessian matrix was not computed for this learning method")
        if not self.hessian is None: return self.hessian
        self.hessian = numpy.zeros((self.N,self.N), numpy.float64)
        eCounts = self.globalFormulaCounts / self.numSamples
        for i in xrange(self.N):
            for j in xrange(self.N):
                self.hessian[i][j] = eCounts[i] * eCounts[j]
        self.hessian -= self.hessianProd / self.numSamples
        return -self.hessian

class SLL_SE(AbstractLearner):
    '''
        sampling-based maximum likelihood with soft evidence (SMLSE):
        uses MC-SAT-PC to sample soft evidence worlds
        uses MC-SAT to sample worlds in order to approximate Z
    '''
    
    def __init__(self, mrf, **params):
        AbstractLearner.__init__(self, mrf, **params)
    
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
        self.normSampler = MCMCSampler(self.mrf,
                                       dict(given="", softEvidence={}, maxSteps=self.mcsatSteps, 
                                            doProbabilityFitting=False,
                                            verbose=True, details =True, infoInterval=100, resultsInterval=100))
        evidenceString = evidence2conjunction(self.mrf.getEvidenceDatabase())
        self.seSampler = MCMCSampler(self.mrf,
                                     dict(given=evidenceString, softEvidence=self.mrf.softEvidence, maxSteps=self.mcsatStepsEvidence, 
                                          doProbabilityFitting=False,
                                          verbose=True, details =True, infoInterval=1000, resultsInterval=1000,
                                          maxSoftEvidenceDeviation=0.05))


class CD_SE(SLL_ISE):
    '''
        contrastive divergence with soft evidence
    '''
    
    def __init__(self, mrf, **params):
        SLL_ISE.__init__(self, mrf, **params)

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
        evidenceString = evidence2conjunction(self.mrf.getEvidenceDatabase())
        self.normSampler = MCMCSampler(self.mrf,
                                       dict(given="", softEvidence={}, maxSteps=self.mcsatSteps,
                                            doProbabilityFitting=False,
                                            verbose=True, details =True, infoInterval=100, resultsInterval=100))
        self.seSampler = MCMCSampler(self.mrf,
                                     dict(given=evidenceString, softEvidence=self.mrf.softEvidence, maxSteps=self.mcsatStepsEvidence, 
                                          doProbabilityFitting=False,
                                          verbose=True, details =True, infoInterval=1000, resultsInterval=1000,
                                          maxSoftEvidenceDeviation=0.05))
