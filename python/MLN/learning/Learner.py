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



# !!!!!!!!!!! MOST OF THIS FILE IS OBSOLETE
# For the most part, the code contained herein has been moved elsewhere (as indicated by comments "moved to X")
# This file contains legacy learning methods only



import sys
import math

try:
    import numpy
    from scipy.optimize import fmin_bfgs, fmin_cg, fmin_ncg, fmin_tnc, fmin_l_bfgs_b, fsolve, fmin_slsqp
    #from minfx import bfgs
except:
    sys.stderr.write("Warning: Failed to import SciPy/NumPy (http://www.scipy.org)! Parameter learning with the MLN module is disabled.\n")

from MLN.methods import *
from MLN.util import *
from AbstractLearner import *

PMB_METHOD = 'old' # 'excl' or 'old'
# concerns the calculation of the probability of a ground atom assignment given the ground atom's Markov blanket
# If set to 'old', consider only the two assignments of the ground atom x (i.e. add the weights of any ground
# formulas within which x appears for both cases and then use the appriopriate fraction).
# If set to 'excl', consider mutual exclusiveness and exhaustiveness by looking at all the assignments of the
# block that x is in (and all the formulas that are affected by any of the atoms in the block). We obtain an exp. sum of
# weights for each block assignment and consider the fraction of those block assignments where x has a given value.

DIFF_METHOD = 'blocking' # 'blocking' or 'simple'
# This applies to parameter learning with pseudo-likelihood, where, for each ground atom x, the difference in the number
# of true groundings of a formula is computed for the case where x's truth value is flipped and where x's truth value
# remains the same (as indicated by the training db).
# If set to 'blocking', then we not only consider the effects of flipping x itself but also flips of any
# ground atoms with which x appears together in a block, because flipping them may (or may not) affect the truth
# value of x and thus the truth of ground formulas within which x appears.


class Learner(AbstractLearner):
    
    def __init__(self, mln):
        self.mln = mln
    
    def __getattr__(self, attr):
        return self.mln.__getattribute__(attr) # HACK

    # determines the probability of the given ground atom (string) given its Markov blanket
    # (the MLN must have been provided with evidence using combineDB)
    def getAtomProbMB(self, atom): # moved to PLL
        idxGndAtom = self.gndAtoms[atom].idx
        weights = self._weights()
        return self._getAtomProbMB(idxGndAtom, weights)

    # get the probability of the assignment for the block the given atom is in
    def getBlockProbMB(self, atom): # moved to BPLL
        idxGndAtom = self.gndAtoms[atom].idx       
        self._getBlockProbMB(idxBlock, self._weights())

    # gets the probability of the ground atom with index idxGndAtom when given its Markov blanket (evidence set)
    # using the specified weight vector
    def _getAtomProbMB(self, idxGndAtom, wt, relevantGroundFormulas=None):  # moved to PLL          
        #old_tv = self._getEvidence(idxGndAtom)
        # check if the ground atom is in a block
        block = None
        if idxGndAtom in self.gndBlockLookup and PMB_METHOD != 'old':
            blockname = self.gndBlockLookup[idxGndAtom]
            block = self.gndBlocks[blockname] # list of gnd atom indices that are in the block
            sums = [0 for i in range(len(block))] # init sum of weights for each possible assignment of block
                                                  # sums[i] = sum of weights for assignment where the block[i] is set to true
            idxBlockMainGA = block.index(idxGndAtom)
            # find out which one of the ground atoms in the block is true
            idxGATrueone = -1
            for i in block:
                if self._getEvidence(i):
                    if idxGATrueone != -1: raise Exception("More than one true ground atom in block %s!" % blockname)
                    idxGATrueone = i                    
            if idxGATrueone == -1: raise Exception("No true gnd atom in block!" % blockname)
            mainAtomIsTrueone = idxGATrueone == idxGndAtom
        else: # not in block
            wts_inverted = 0
            wts_regular = 0
            wr, wi = [], []
        # determine the set of ground formulas to consider
        checkRelevance = False
        if relevantGroundFormulas == None:
            try:
                relevantGroundFormulas = self.atomRelevantGFs[idxGndAtom]
            except:
                relevantGroundFormulas = self.gndFormulas
                checkRelevance = True
        # check the ground formulas
        #print self.gndAtomsByIdx[idxGndAtom]
        if PMB_METHOD == 'old' or block == None: # old method (only consider formulas that contain the ground atom)
            for gf in relevantGroundFormulas:
                if checkRelevance:
                    if not gf.containsGndAtom(idxGndAtom):
                        continue
                # gnd atom maintains regular truth value
                prob1 = self._getTruthDegreeGivenEvidence(gf)
                #print "gf: ", str(gf), " index: ", gf.idxFormula, ", wt size:", len(wt), " formula size:", len(self.formulas)
                if prob1 > 0:
                    wts_regular += wt[gf.idxFormula] * prob1
                    wr.append(wt[gf.idxFormula] * prob1)
                # flipped truth value
                #self._setTemporaryEvidence(idxGndAtom, not old_tv)
                self._setInvertedEvidence(idxGndAtom)
                #if self._isTrueGndFormulaGivenEvidence(gf):
                #    wts_inverted += wt[gf.idxFormula]
                #    wi.append(wt[gf.idxFormula])
                prob2 = self._getTruthDegreeGivenEvidence(gf)
                if prob2 > 0:
                    wts_inverted += wt[gf.idxFormula] * prob2
                    wi.append(wt[gf.idxFormula] * prob2)
                #self._removeTemporaryEvidence()
                #print "  F%d %f %s %f -> %f" % (gf.idxFormula, wt[gf.idxFormula], str(gf), prob1, prob2)
                self._setInvertedEvidence(idxGndAtom)
            #print "  %s %s" % (wts_regular, wts_inverted)
            return math.exp(wts_regular) / (math.exp(wts_regular) + math.exp(wts_inverted))
        elif PMB_METHOD == 'excl' or PMB_METHOD == 'excl2': # new method (consider all the formulas that contain one of the ground atoms in the same block as the ground atom)
            for gf in relevantGroundFormulas: # !!! here the relevant ground formulas may not be sufficient!!!! they are different than in the other case
                # check if one of the ground atoms in the block appears in the ground formula
                if checkRelevance:
                    gfRelevant = False
                    for i in block:
                        if gf.containsGndAtom(i):
                            gfRelevant = True
                            break
                    if not gfRelevant: continue
                # make each one of the ground atoms in the block true once
                idxSum = 0
                for i in block:
                    # set the i-th variable in the block to true
                    if i != idxGATrueone:
                        self._setTemporaryEvidence(i, True)
                        self._setTemporaryEvidence(idxGATrueone, False)
                    # is the formula true?
                    if self._isTrueGndFormulaGivenEvidence(gf):
                        sums[idxSum] += wt[gf.idxFormula]
                    # restore truth values
                    self._removeTemporaryEvidence()
                    idxSum += 1
            expsums = map(math.exp, sums)
            if PMB_METHOD == 'excl':
                if mainAtomIsTrueone:
                    return expsums[idxBlockMainGA] / sum(expsums)
                else:
                    s = sum(expsums)
                    return (s - expsums[idxBlockMainGA]) / s
            elif PMB_METHOD == 'excl2':
                if mainAtomIsTrueone:
                    return expsums[idxBlockMainGA] / sum(expsums)
                else:
                    idxBlockTrueone = block.index(idxGATrueone)
                    return expsums[idxBlockTrueone] / (expsums[idxBlockTrueone] + expsums[idxBlockMainGA])
        else:
            raise Exception("Unknown PMB_METHOD")
    
    def _setInvertedEvidence(self, idxGndAtom): # moved to PLL
        old_tv = self._getEvidence(idxGndAtom)
        self._setEvidence(idxGndAtom, not old_tv)

    def _setInvertedEvidenceSoft(self, idxGndAtom): # moved to PLL_ISE
        s = strFormula(self.gndAtomsByIdx[idxGndAtom])
        isSoftEvidence = False
        for se in self.softEvidence:
            if se["expr"] == s:
                isSoftEvidence = True
                old_tv = se["p"]
                break
        if isSoftEvidence:
            self._setSoftEvidence(self.gndAtomsByIdx[idxGndAtom], 1 - old_tv)
        else:
            old_tv = self._getEvidence(idxGndAtom)
            self._setEvidence(idxGndAtom, not old_tv)

    # prints the probability of each ground atom truth assignment given its Markov blanket
    def printAtomProbsMB(self): # moved to PLL
        gndAtoms = self.gndAtoms.keys()
        gndAtoms.sort()
        values = []
        for gndAtom in gndAtoms:
            v = self.getAtomProbMB(gndAtom)
            print "%s=%s  %f" % (gndAtom, str(self._getEvidence(self.gndAtoms[gndAtom].idx)), v)
            values.append(v)
        pll = sum(map(math.log, values))
        print "PLL = %f" % pll

    # prints the probability of each block assignment given the Markov blanket
    def printBlockProbsMB(self): # moved to PLL
        self._getPllBlocks()
        values = []
        wt = self._weights()
        for idxBlock, (idxGA, block) in enumerate(self.pllBlocks):
            v = self._getBlockProbMB(idxBlock, wt)
            if idxGA != None:
                print "%s=%s  %f" % (str(self.gndAtomsByIdx[idxGA]), str(self._getEvidence(idxGA)), v)
            else:
                trueone = -1
                for i in block:
                    if self._getEvidence(i):
                        trueone = i
                        break
                print "{%s}=%s  %f" % (self._strBlock(block), str(self.gndAtomsByIdx[trueone]), v)
            values.append(v)
        print "BPLL =", sum(map(math.log, values))

    def _calculateAtomProbsMB(self, wt): # moved to PLL
        if ('wtsLastAtomProbMBComputation' not in dir(self)) or self.wtsLastAtomProbMBComputation != list(wt):
            print "recomputing atom probabilities...",
            self.atomProbsMB = [self._getAtomProbMB(i, wt) for i in range(len(self.gndAtoms))]
            self.wtsLastAtomProbMBComputation = list(wt)
            print "done."

    def _pll(self, wt): # moved to PLL
        self._calculateAtomProbsMB(self._reconstructFullWeightVectorWithFixedWeights(wt))
        #print self.atomProbsMB
        probs = map(lambda x: x if x > 0 else 1e-10, self.atomProbsMB) # prevent 0 probs
        pll = sum(map(math.log, probs))
        print "pseudo-log-likelihood:", pll
        return pll


    def _addToDiff(self, idxFormula, idxGndAtom, diff): # moved to PLL
        key = (idxFormula, idxGndAtom)
        cur = self.diffs.get(key, 0)
        self.diffs[key] = cur + diff        

    def _computeDiffs(self): # moved to PLL
        self.diffs = {}
        for gndFormula in self.gndFormulas:
            for idxGndAtom in gndFormula.idxGroundAtoms():
                cnt1, cnt2 = 0, 0
                # check if formula is true if gnd atom maintains its truth value
                if self._isTrueGndFormulaGivenEvidence(gndFormula): cnt1 = 1
                # check if formula is true if gnd atom's truth value is inversed
                cnt2 = 0
                old_tv = self._getEvidence(idxGndAtom)
                self._setTemporaryEvidence(idxGndAtom, not old_tv)
                if self._isTrueGndFormulaGivenEvidence(gndFormula): cnt2 = 1
                self._removeTemporaryEvidence()
                # save difference
                diff = cnt2 - cnt1
                if diff != 0:
                    self._addToDiff(gndFormula.idxFormula, idxGndAtom, diff) 
                    # if the gnd atom is in a block with other variables, then these other variables can also
                    # cause a change in the number of true groundings of this formula:
                    # let's say there are k gnd atoms in the block and one of the k items appears in gndFormula, say item x.
                    # if gnd atom x is true, then a change to any of the other k-1 items will flip x.
                    # if gnd atom x is false, then there is a chance of 1/(k-1) that x is flipped
                    if DIFF_METHOD == 'blocking':
                        if idxGndAtom in self.gndBlockLookup:
                            blockname = self.gndBlockLookup[idxGndAtom]
                            block = self.gndBlocks[blockname] # list of gnd atom indices that are in the block
                            for i in block:
                                if i not in gndFormula.idxGroundAtoms(): # for each ground atom in the block besides the one we are just processing (which occurs in the ground formula)
                                    if old_tv:
                                        self._addToDiff(gndFormula.idxFormula, i, diff)
                                    else:
                                        self._addToDiff(gndFormula.idxFormula, i, diff / (len(block) - 1))


    def _computeDiffsSoft(self): # moved to PLL_ISE
        self.diffs = {}
        for gndFormula in self.gndFormulas:
            cnt1 = self._getTruthDegreeGivenEvidenceSoft(gndFormula)
            for idxGndAtom in gndFormula.idxGroundAtoms():
                # check if formula is true if gnd atom's truth value is inversed
                cnt2 = 0
                # check if it really is a soft evidence:
                s = strFormula(self.gndAtomsByIdx[idxGndAtom])
                isSoftEvidence = False
                 
                for se in self.softEvidence:
                    if se["expr"] == s:
                        isSoftEvidence = True
                        old_tv = se["p"]
                        break
                if isSoftEvidence:
                    self._setSoftEvidence(self.gndAtomsByIdx[idxGndAtom], 1 - old_tv)
                    cnt2 = self._getTruthDegreeGivenEvidenceSoft(gndFormula)
                    self._setSoftEvidence(self.gndAtomsByIdx[idxGndAtom], old_tv)
                else:
                    old_tv = self._getEvidence(idxGndAtom)
                    self._setTemporaryEvidence(idxGndAtom, not old_tv)
                    if self._isTrueGndFormulaGivenEvidence(gndFormula): cnt2 = 1
                    self._removeTemporaryEvidence()
                    
                # save difference
                diff = cnt2 - cnt1
                if diff != 0:
                    self._addToDiff(gndFormula.idxFormula, idxGndAtom, diff) 


    def _grad_pll(self, wt): # moved to PLL
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        fullWt = self._reconstructFullWeightVectorWithFixedWeights(wt)
        self._calculateAtomProbsMB(fullWt)
        for (idxFormula, idxGndAtom), diff in self.diffs.iteritems():
            v = diff * (self.atomProbsMB[idxGndAtom] - 1)
            grad[idxFormula] += v            
        print "wts =", fullWt
        print "grad =", grad
        return self._projectVectorToNonFixedWeightIndices(grad)
    
    def _negated_pll_with_fixation(self, wt, *args):
        #wtD = wtD = numpy.zeros(len(self.formulas))
        #wtIndex = 0
        #for i,formula in enumerate(self.formulas):
        #    if (formula in self._fixedWeightFormulas):
        #        wtD[i] = self._fixedWeightFormulas[formula]
        #    else:
        #        wtD.append(wt[wtIndex])
        #        wtIndex = wtIndex + 1

        npll = self._negated_pll(wt, *args)
        #print npll
        return npll
    
    
    def _eqConstraints_with_fixation(self, wt, *args):
        result = numpy.zeros(len(self._fixedWeightFormulas), numpy.float64)
        wtIndex = 0
        for i, formula in enumerate(self.formulas):
            if (i in self._fixedWeightFormulas):
                result[wtIndex] = result[wtIndex] + wt[i] - self._fixedWeightFormulas[i]
                wtIndex = wtIndex + 1
        return result

    def _eqConstraints_with_fixation_prime(self, wt, *args):
        result = numpy.zeros((len(self._fixedWeightFormulas), len(wt)), numpy.float64)
        wtIndex = 0
        for i, formula in enumerate(self.formulas):
            if (i in self._fixedWeightFormulas):
                result[(wtIndex, i)] = 1
                wtIndex = wtIndex + 1
            
        return result

    # we don't need inequality constraints:
    def _ieqConstraints_with_fixation(self, wt, *args):
        result = numpy.ones(1, numpy.float64)
        return - result
    
    def _ieqConstraints_with_fixation_prime(self, wt, *args):
        result = numpy.zeros(len(wt), numpy.float64)
        return result

    def _pl(self, wt):
        self._calculateAtomProbsMB(wt)
        prob = float(1)
        for x in self.atomProbsMB:
            prob = prob * x
        #print - prob
        return prob

    def _negated_grad_pll(self, wt, *args):
        return - self._grad_pll(wt)

    def _negated_grad_pll_scaled(self, wt):
        wt_scaled = numpy.zeros(len(wt), numpy.float64)
        for i in range(len(wt)):
            wt_scaled[i] = wt[i] * self.formulaScales[i]
        return - self._grad_pll(wt_scaled)

    def _negated_pll(self, wt, *args):
        pll = self._pll(wt)
        #print "pll = %f" % pll
        return - pll   

    def _negated_pll_scaled(self, wt):
        wt_scaled = numpy.zeros(len(wt), numpy.float64)
        for i in range(len(wt)):
            wt_scaled[i] = wt[i] * self.formulaScales[i]
        return - self._pll(wt_scaled)

    def _negated_pl_with_fixation(self, wt, *args):
        return - self._pl(self._reconstructFullWeightVectorWithFixedWeights(wt), *args)
    
    def _negated_grad_pll_with_fixation(self, wt, *args):
        #wtD = numpy.zeros(len(self.formulas), numpy.float64)
        #wtIndex = 0
        #for i,formula in enumerate(self.formulas):
        #    if (formula in self._fixedWeightFormulas):
        #        wtD[i] = self._fixedWeightFormulas[formula]
        #    else:
        #        wtD[i] = wt[wtIndex]
        #        wtIndex = wtIndex + 1
                
        grad_pll_fixed = self._negated_grad_pll(wt, *args)
        
        #grad_pll_fixed = numpy.zeros(len(self.formulas)-len(self._fixedWeightFormulas.items()), numpy.float64)
        #wtIndex = 0
        #for i,formula in enumerate(self.formulas):
        #    if (formula in self._fixedWeightFormulas):
        #        continue
        #    grad_pll_fixed[wtIndex] = grad_pll_without_fixation[i]
        #    wtIndex = wtIndex + 1

        #print "wts =", wt 
        #print "grad =", grad_pll_fixed
        self.grad_opt_norm = sqrt(sum(map(lambda x: x * x, grad_pll_fixed)))
        return grad_pll_fixed
    
    def _computeCounts(self): # moved to LL
        ''' computes the number of true groundings of each formula in each possible world '''
        self.counts = {}
        # for each possible world
        for i in range(len(self.worlds)):
            world = self.worlds[i]
            # count how many true groundings there are for each formula
            for gf in self.gndFormulas:                
                if self._isTrue(gf, world["values"]):
                    key = (i, gf.idxFormula)
                    cnt = self.counts.get(key, 0)
                    cnt += 1
                    self.counts[key] = cnt

    def _computeCounts2(self):
        ''' computes the number of true and false groundings of each formula in each possible world '''
        self.counts = {}
        # for each possible world
        for i in range(len(self.worlds)):
            world = self.worlds[i]
            # count how many true and false groundings there are for each formula
            for gf in self.gndFormulas:                
                key = (i, gf.idxFormula)
                cnt = self.counts.get(key, [0, 0])
                if self._isTrue(gf, world["values"]):
                    cnt[0] += 1
                else:
                    cnt[1] += 1
                self.counts[key] = cnt

    def _computeCountsSoft(self, allSoft): # moved to LL_ISE
        ''' computes  '''
        print "number of worlds:", len(self.worlds)
        if not hasattr(self, "worldCode2Index"):
            self.idxTrainingDB = 0 #there is only the training world
        else:
            self.idxTrainingDB = self._getEvidenceWorldIndex()
        if allSoft == False:
            # compute regular counts for all posible worlds
            self._computeCounts()
            # add another world for soft beliefs            
            baseWorld = self.worlds[self.idxTrainingDB]
            self.worlds.append({"values": baseWorld["values"]})
            self.idxTrainingDB = len(self.worlds) - 1
            # and compute soft counts only for that world
            softCountWorldIndices = [self.idxTrainingDB]
        else:
            # compute soft counts for all possible worlds
            self.counts = {}
            softCountWorldIndices = xrange(len(self.worlds))
        # compute soft counts      
        
        
          
        for i in softCountWorldIndices:
            world = self.worlds[i]     
            if i == self.idxTrainingDB:
                print "TraningDB: prod, groundformula"       
            for gf in self.gndFormulas:
                prod = self._getTruthDegreeGivenEvidenceSoft(gf, world["values"])
                key = (i, gf.idxFormula)
                cnt = self.counts.get(key, 0)
                cnt += prod
                self.counts[key] = cnt
                if i == self.idxTrainingDB:
                    print "%f gf: %s" % (prod, str(gf))
            
            if i == self.idxTrainingDB:
                print "TrainingDB: softCounts, formula"
                for j, f in enumerate(self.formulas):
                    print "  %f %s" % (self.counts[(i, j)], strFormula(f))
#            else:
#                for j,f in enumerate(self.formulas):
#                    normalizationWorldsMeanCounts[j] += self.counts[(i,j)]
#                    print "xx", self.counts[(i,j)]
            
        normalizationWorldsMeanCounts = numpy.zeros(len(self.formulas)) 
        normalizationWorldCounter = 0   
        for i in xrange(len(self.worlds)):
            if allSoft == True or i != self.idxTrainingDB:
                normalizationWorldCounter += 1
                print "world", i
                for j, f in enumerate(self.formulas):
                    if (i, j) in self.counts:
                        normalizationWorldsMeanCounts[j] += self.counts[(i, j)]
                        print "  count", self.counts[(i, j)], strFormula(f)
            
        print "normalizationWorldsMeanCounts:"
        normalizationWorldsMeanCounts /= normalizationWorldCounter
        for j, f in enumerate(self.formulas):
            print " %f %s" % (normalizationWorldsMeanCounts[j], strFormula(self.formulas[j]))
              
              
    def _getTruthDegreeGivenEvidenceSoft(self, gf, worldValues=None): # moved to softeval
        if worldValues is None: worldValues = self.evidence
        cnf = gf.toCNF()
        prod = 1.0
        if isinstance(cnf, FOL.Conjunction):
            for disj in cnf.children:
                prod *= self._noisyor(worldValues, disj) 
        else:
            prod *= self._noisyor(worldValues, cnf)  
        return prod  

    def _noisyor(self, worldValues, disj): # moved to softeval
        if isinstance(disj, FOL.GroundLit):
            lits = [disj]
        elif isinstance(disj, FOL.TrueFalse):
            return 1.0 if disj.isTrue(worldValues) else 0.0
        else:
            lits = disj.children
        prod = 1.0
        for lit in lits:
            p = self._getEvidenceTruthDegreeCW(lit.gndAtom, worldValues)     
            factor = p if not lit.negated else 1.0 - p
            prod *= 1.0 - factor
        return 1.0 - prod                

    # computes the gradient of the log-likelihood given the weight vector wt
    def _grad_ll(self, wt, *args): # moved to LL
        idxTrainDB = args[0]
        self._calculateWorldValues(wt)        
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        #ctraining = [0 for i in range(len(self.formulas))]
        #cothers = [0 for i in range(len(self.formulas))]
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            #print ((idxWorld, idxFormula), count)
            if idxTrainDB == idxWorld:                
                grad[idxFormula] += count
                #ctraining[idxFormula] += count
            if self.learnWtsMode != 'LL_ISE' or self.allSoft == True or idxTrainDB != idxWorld:
                n = count * self.worlds[idxWorld]["sum"] / self.partition_function
                grad[idxFormula] -= n
            #cothers[idxFormula] += n
        #print "grad_ll"
        print "wts =", wt
        print "grad =", grad
        print
        return grad

    def _ll(self, wt, *args): # moved to LL
        idxTrainDB = args[0]
        self._calculateWorldValues(wt)
        print "worlds[idxTrainDB][\"sum\"] / Z", self.worlds[idxTrainDB]["sum"] , self.partition_function
        ll = math.log(self.worlds[idxTrainDB]["sum"] / self.partition_function)
        print "ll =", ll
        return ll
    
            
    def _ll_iseww(self, wts, *args):

        idxTrainDB = args[0]
        self._calculateWorldValues(wts) #only to calculate partition function here:
        #print "worlds[idxTrainDB][\"sum\"] / Z", self.worlds[idxTrainDB]["sum"] , self.partition_function
        
        #calculate only once as they do not change
        if False == hasattr(self, 'worldProbabilities'):
            self.worldProbabilities = {}
            #TODO: or (opimized) generate only world by flipping the soft evidences
            #discard all world where at least one non-soft evidence is different from the generated
            for idxWorld, world in enumerate(self.worlds):
                worldProbability = 1
                discardWorld = False
                for gndAtom in self.gndAtoms.values():
                    if world["values"][gndAtom.idx] != self.worlds[self.idxTrainingDB]["values"][gndAtom.idx]:
                        
                        #check if it is soft:
                        isSoft = False
                        s = strFormula(gndAtom)
                        for se in self.softEvidence:
                            if se["expr"] == s:
                                isSoft = True
                                break
                            
                        if False == isSoft:
                            discardWorld = True
                            break
                if discardWorld: 
                    print "discarded world", s, idxWorld#, world["values"][gndAtom.idx] , self.worlds[self.idxTrainingDB]["values"][gndAtom.idx]
                    continue
                
                for se in self.softEvidence:
                    evidenceValue = self._getEvidenceTruthDegreeCW(self.gndAtoms[se["expr"]], world["values"]) 
                    worldProbability *= evidenceValue    
                    print "  ", "evidence, gndAtom", evidenceValue, se["expr"]#, self.evidence, world["values"]
                    
                if worldProbability > 0:
                    self.worldProbabilities[idxWorld] = worldProbability
        
        evidenceWorldSum = 0
        for idxWorld, world in enumerate(self.worlds):
                
            if idxWorld in self.worldProbabilities:
                print "world:", idxWorld, "exp(worldWeights)", world["sum"], "worldProbability", self.worldProbabilities[idxWorld]
                evidenceWorldSum += world["sum"] * self.worldProbabilities[idxWorld]
                  
        print "wts =", wts
        print "evidenceWorldSum, self.partition_function", evidenceWorldSum, self.partition_function
        ll = math.log(evidenceWorldSum / self.partition_function)
        print "ll_iseww =", ll
        print 
        return ll

    def _grad_ll_fixed(self, wt, *args): # deprecated
        fixedWeights = args[1]
        grad = self._grad_ll(wt, *args)
        for idxFormula in fixedWeights:
            grad[idxFormula] = 0
        return grad

    def _grad_ll_fac(self, fac, *args): 
        idxTrainDB = args[0]
        self._calculateWorldValues2(fac)        
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            if idxTrainDB == idxWorld:                
                grad[idxFormula] += count[0] / fac[idxFormula] - count[1] / (1.0 - fac[idxFormula])
            n = (count[0] / fac[idxFormula] - count[1] / (1.0 - fac[idxFormula])) * self.worlds[idxWorld]["prod"] / self.partition_function
            grad[idxFormula] -= n
        print "facs =", fac
        print "grad =", grad
        print
        return grad

    def _ll_fac(self, wt, *args):
        idxTrainDB = args[0]
        self._calculateWorldValues2(wt)
        ll = math.log(self.worlds[idxTrainDB]["prod"] / self.partition_function)
        print "ll =", ll
        return ll

    def _grad_ll_prob(self, probs, *args):
        idxTrainDB = args[0]
        self._calculateWorldValues_prob(probs)        
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            if idxTrainDB == idxWorld:                
                grad[idxFormula] += count[0] / probs[idxFormula]
            n = (count[0] / probs[idxFormula]) * self.worlds[idxWorld]["prod"] / self.partition_function
            grad[idxFormula] -= n
        print "probs =", probs
        print "grad =", grad
        print
        return grad

    def _ll_prob(self, wt, *args):
        idxTrainDB = args[0]
        self._calculateWorldValues_prob(wt)
        ll = math.log(self.worlds[idxTrainDB]["prod"] / self.partition_function)
        print "ll =", ll
        return ll

    def _grad_ll_prob(self, probs, *args):
        idxTrainDB = args[0][0]
        self._calculateWorldValues_prob(probs)        
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            if idxTrainDB == idxWorld:                
                grad[idxFormula] += count[0] / probs[idxFormula]
            n = (count[0] / probs[idxFormula]) * self.worlds[idxWorld]["prod"] / self.partition_function
            grad[idxFormula] -= n
        print "probs =", probs
        print "grad =", grad
        print
        return grad

    def _ll_prob(self, wt, *args):
        idxTrainDB = args[0][0]
        self._calculateWorldValues_prob(wt)
        ll = math.log(self.worlds[idxTrainDB]["prod"] / self.partition_function)
        print "ll =", ll
        return ll

    def _addToBlockDiff(self, idxFormula, idxBlock, diff): # moved to BPLL
        key = (idxFormula, idxBlock)
        cur = self.blockdiffs.get(key, 0)
        self.blockdiffs[key] = cur + diff        

    def _computeBlockDiffs(self): # moved to BPLL
        self.blockdiffs = {}
        for idxPllBlock, (idxGA, block) in enumerate(self.pllBlocks):
            for gndFormula in self.blockRelevantGFs[idxPllBlock]:
                if idxGA != None: # ground atom is the variable as it's not in a block (block is None)
                    cnt1, cnt2 = 0, 0
                    #if not (idxGA in gndFormula.idxGroundAtoms()): continue
                    # check if formula is true if gnd atom maintains its truth value
                    if self._isTrueGndFormulaGivenEvidence(gndFormula): cnt1 = 1
                    # check if formula is true if gnd atom's truth value is inversed
                    old_tv = self._getEvidence(idxGA)
                    self._setTemporaryEvidence(idxGA, not old_tv)
                    if self._isTrueGndFormulaGivenEvidence(gndFormula): cnt2 = 1
                    self._removeTemporaryEvidence()
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
                        if self._getEvidence(i):
                            if idxGATrueone != -1: raise Exception("More than one true ground atom in block %s!" % blockname)
                            idxGATrueone = i                    
                    if idxGATrueone == -1: raise Exception("No true gnd atom in block!" % blockname)
                    idxInBlockTrueone = block.index(idxGATrueone)
                    # check true groundings for each block assigment
                    for i in block:
                        if i != idxGATrueone:
                            self._setTemporaryEvidence(i, True)
                            self._setTemporaryEvidence(idxGATrueone, False)
                        if self._isTrueGndFormulaGivenEvidence(gndFormula):
                            if i == idxGATrueone:
                                cnt1 += 1
                            else:
                                cnt2 += 1
                        self._removeTemporaryEvidence()
                    # save difference
                    diff = cnt2 - cnt1
                    if diff != 0:
                        self._addToBlockDiff(gndFormula.idxFormula, idxPllBlock, diff)

    def _getBlockProbMB(self, idxPllBlock, wt, relevantGroundFormulas=None): # moved to BPLL
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
            expsums = self._getBlockExpsums(block, wt, self.evidence, idxGATrueone, relevantGroundFormulas)
            #mpexpsums = map(lambda x: mpmath.mpf(x), expsums)
            #sums = self._getBlockSums(block, wt, self.evidence, idxGATrueone, relevantGroundFormulas)
            #print sums
            #avgsum = sum(map(lambda x: x/len(sums),sums))                        
            #sums = map(lambda x: x-avgsum, sums)
            #print sums
            #expsums = map(math.exp, sums)
            return float(expsums[idxInBlockTrueone] / fsum(expsums))

    def _getBlockTrueone(self, block): # moved to MLN
        idxGATrueone = -1
        for i in block:
            if self._getEvidence(i):
                if idxGATrueone != -1: raise Exception("More than one true ground atom in block %s!" % blockname)
                idxGATrueone = i
                break
        if idxGATrueone == -1: raise Exception("No true gnd atom in block %s!" % self._strBlock(block))
        return idxGATrueone

    def _getBlockExpsums(self, block, wt, world_values, idxGATrueone=None, relevantGroundFormulas=None): # moved to MLN
        # if the true gnd atom in the block is not known (or there isn't one perhaps), set the first one to true by default and restore values later
        mustRestoreValues = False
        if idxGATrueone == None:
            mustRestoreValues = True
            backupValues = [world_values[block[0]]]
            world_values[block[0]] = True
            for idxGA in block[1:]:
                backupValues.append(world_values[idxGA])
                world_values[idxGA] = False
            idxGATrueone = block[0]
        # init the sums
        sums = [0 for i in range(len(block))] # init sum of weights for each possible assignment of block
                                              # sums[i] = sum of weights for assignment where the block[i] is set to true
        # process all (relevant) ground formulas
        checkRelevance = False
        if relevantGroundFormulas == None:
            relevantGroundFormulas = self.gndFormulas
            checkRelevance = True
        for gf in relevantGroundFormulas:
            # check if one of the ground atoms in the block appears in the ground formula
            if checkRelevance:
                isRelevant = False
                for i in block:
                    if i in gf.idxGroundAtoms():
                        isRelevant = True
                        break
                if not isRelevant: continue
            # make each one of the ground atoms in the block true once
            idxSum = 0
            for i in block:
                # set the current variable in the block to true
                world_values[idxGATrueone] = False
                world_values[i] = True
                # is the formula true?
                if gf.isTrue(world_values):
                    sums[idxSum] += wt[gf.idxFormula]
                # restore truth values
                world_values[i] = False
                world_values[idxGATrueone] = True
                idxSum += 1
                
        # if initialization values were used, reset them
        if mustRestoreValues:
            for i, value in enumerate(backupValues):
                world_values[block[i]] = value
                
        # return the list of exponentiated sums
        return map(exp, sums)

    def _getAtomExpsums(self, idxGndAtom, wt, world_values, relevantGroundFormulas=None): # moved to MarkovLogicNetwork
        sums = [0, 0]
        # process all (relevant) ground formulas
        checkRelevance = False
        if relevantGroundFormulas == None:
            relevantGroundFormulas = self.gndFormulas
            checkRelevance = True
        old_tv = world_values[idxGndAtom]
        for gf in relevantGroundFormulas:
            if checkRelevance: 
                if not gf.containsGndAtom(idxGndAtom):
                    continue
            for i, tv in enumerate([False, True]):
                world_values[idxGndAtom] = tv
                if gf.isTrue(world_values):
                    sums[i] += wt[gf.idxFormula]
                world_values[idxGndAtom] = old_tv
        return map(math.exp, sums)

    def _grad_blockpll(self, wt): # moved to BPLL
        #grad = [mpmath.mpf(0) for i in xrange(len(self.formulas))]        
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        self._calculateBlockProbsMB(wt)
        for (idxFormula, idxBlock), diff in self.blockdiffs.iteritems():
            v = diff * (self.blockProbsMB[idxBlock] - 1)
            grad[idxFormula] += v
        #print "wts =", wt
        #print "grad =", grad
        self.grad_opt_norm = float(math.sqrt(sum(map(lambda x: x * x, grad))))
        
        #print "norm = %f" % norm
        return numpy.array(grad)

    def _calculateBlockProbsMB(self, wt): # moved to BPLL
        if ('wtsLastBlockProbMBComputation' not in dir(self)) or self.wtsLastBlockProbMBComputation != list(wt):
            #print "recomputing block probabilities...",
            self.blockProbsMB = [self._getBlockProbMB(i, wt, self.blockRelevantGFs[i]) for i in range(len(self.pllBlocks))]
            self.wtsLastBlockProbMBComputation = list(wt)
            #print "done."

    def _blockpll(self, wt): # moved to BPLL
        self._calculateBlockProbsMB(wt)
        probs = map(lambda x: 1e-10 if x == 0 else x, self.blockProbsMB) # prevent 0 probs
        return sum(map(math.log, probs))

    def getBPLL(self): # moved to BPLL
        return self._blockpll(self._weights())
        
    def _negated_blockpll_with_fixation(self, wt, *args):
        wtD = numpy.zeros(len(self.formulas), numpy.float64)
        #wtD = numpy.array([mpmath.mpf(0) for i in xrange(len(self.formulas))])
        wtIndex = 0
        for i, formula in enumerate(self.formulas):
            if (i in self._fixedWeightFormulas):
                wtD[i] = self._fixedWeightFormulas[i]
            else:
                wtD[i] = wt[wtIndex]
                wtIndex = wtIndex + 1

        return - self._blockpll(wtD, *args)
    
    def _negated_grad_blockpll_with_fixation(self, wt, *args):
        wtD = numpy.zeros(len(self.formulas), numpy.float64)
        #wtD = numpy.array([mpmath.mpf(0) for i in xrange(len(self.formulas))])
        wtIndex = 0
        for i, formula in enumerate(self.formulas):
            if (i in self._fixedWeightFormulas):
                wtD[i] = self._fixedWeightFormulas[i]
            else:
                wtD[i] = wt[wtIndex]
                wtIndex = wtIndex + 1

        grad_pll_without_fixation = -self._grad_blockpll(wtD, *args)
        
        grad_pll_fixed = numpy.zeros(len(self.formulas) - len(self._fixedWeightFormulas.items()), numpy.float64)
        #grad_pll_fixed = numpy.array([mpmath.mpf(0) for i in xrange( len(self.formulas) - len(self._fixedWeightFormulas.items()) )])
        wtIndex = 0
        for i, formula in enumerate(self.formulas):
            if (i in self._fixedWeightFormulas):
                continue
            grad_pll_fixed[wtIndex] = grad_pll_without_fixation[i]
            wtIndex = wtIndex + 1
        self.grad_opt_norm = math.sqrt(sum(map(lambda x: x * x, grad_pll_fixed)))
        print "||grad(f)||", self.grad_opt_norm
        return grad_pll_fixed

    def _getAtomRelevantGroundFormulas(self): # moved to PLL
        if PMB_METHOD == 'old':
            self.atomRelevantGFs = self.gndAtomOccurrencesInGFs
        else:
            raise Exception("Not implemented")
    
    def _collectFixationCandidates(self):
        '''
        determines formulas that are candidates for fixed weights
        '''
        candidates = {}
        for i in range(len(self.formulas)):
            # candidates are positive literals
            if type(self.formulas[i]) == FOL.Lit and self.formulas[i].negated == False: # We only want to collect positive Literals
                sVarIndex = self.formulas[i].getSingleVariableIndex(self)
                if sVarIndex != -1:
                    if (self.formulas[i].predName, sVarIndex) in candidates:
                        candidates[(self.formulas[i].predName, sVarIndex)].append(self.formulas[i])
                    else:
                        candidates[(self.formulas[i].predName, sVarIndex)] = [self.formulas[i]]

        return candidates

    def _isUnitaryClauseFixableDFS(self, predName, varIndex, formulaList, depth=0, assignment=None):
        ''' depth-first search for fixable unitary clauses '''
        
        domainList = self.predicates[predName]
        
        if depth == varIndex: depth = depth + 1
        
        if assignment == None:
            assignment = []
            for i in xrange(0, len(domainList)):
                assignment.append(0)

        if depth == len(domainList):
            anyFormulaMatches = False
            for fIdx, formula in enumerate(formulaList):
                formulaMatch = True
                for idParam, param in enumerate(formula.params):
                    if idParam != varIndex:
                        if param != self.domains[domainList[idParam]][assignment[idParam]]:
                            formulaMatch = False
                            break
                            
                if formulaMatch:
                    anyFormulaMatches = True
                    self._formulaByAssignment[tuple(assignment)] = formula
                    del formulaList[fIdx]
                    break
    
            return anyFormulaMatches
        
        for i in xrange(0, len(self.domains[domainList[depth]])):
            assignment[depth] = i
            if self._isUnitaryClauseFixableDFS(predName, varIndex, formulaList, depth + 1, list(assignment)) == False:
                return False
        
        if (depth == 0 or (depth == 1 and varIndex == 0)) and len(formulaList) > 1:
            return False
        
        return True
            
    def _fixUnitaryClauses(self):
        self._fixedWeightFormulas = {}            
        self._formulaByAssignment = {}
        
        # determine candidates for fixation
        fixationCandidates = self._collectFixationCandidates()
        print "candidates: ", fixationCandidates, "fixationSet:", self.fixationSet
        # check all candidates
        for (predName, varIndex), candList in fixationCandidates.iteritems():
            # candidates whose predicate is not defined in the pre-specified fixation set can be omitted
            if hasattr(self, 'fixationSet') and predName not in self.fixationSet:
                continue
            # if candidates are indeed fixable, compute the statistics
            if self._isUnitaryClauseFixableDFS(predName, varIndex, list(candList)):
                self._computeUnitClauseStatistics(predName, varIndex, list(candList))
            # clean up
            self._formulaByAssignment = {}

    def _computeUnitClauseStatistics(self, predName, varIndex, candList):
        totalRelevantGndAtoms = 0        
        uclStatistics = {}
        for idxGA, value in enumerate(self.evidence):
            if self.gndAtomsByIdx[idxGA].predName == predName and value == True:
                totalRelevantGndAtoms = totalRelevantGndAtoms + 1
                assignment = []
                for idParam, param in enumerate(self.gndAtomsByIdx[idxGA].params):
                    if idParam != varIndex: 
                        assignment.append(self.domains[self.predicates[predName][idParam]].index(param))
                    else:
                        assignment.append(0)
                assignment = tuple(assignment)
                if assignment in uclStatistics:
                    uclStatistics[assignment] = uclStatistics[assignment] + 1
                else:
                    uclStatistics[assignment] = 1

        if totalRelevantGndAtoms > 0:
            for assignment, formula in self._formulaByAssignment.iteritems():
                if assignment in uclStatistics:
                    if uclStatistics[assignment] > 0:
                        self._fixedWeightFormulas[formula.idxFormula] = log(float(uclStatistics[assignment]) / totalRelevantGndAtoms)
                    else:
                        self._fixedWeightFormulas[formula.idxFormula] = -10
                else:
                    self._fixedWeightFormulas[formula.idxFormula] = -10
                    
    def _getEvidenceWorldIndex(self): # moved to LL
        code = 0
        bit = 1
        for i in range(len(self.mln.gndAtoms)):
            if self.mln._getEvidence(i):
                code += bit
            bit *= 2
        return self.mln.worldCode2Index[code]
        
    # learn the weights of the mln given the training data previously loaded with combineDB
    #   mode: the measure that is used
    #           'PLL'    pseudo-log-likelihood based on ground atom probabilities
    #           'LL'     log-likelihood (warning: *very* slow and resource-heavy in all but the smallest domains)
    #           'BPLL'   pseudo-log-likelihood based on block probabilities
    #           'LL_fac' log-likelihood; factors between 0 and 1 are learned instead of regular weights
    #   initialWts: whether to use the MLN's current weights as the starting point for the optimization
    def run(self, mode=ParameterLearningMeasures.BPLL, initialWts=False, **params):
        
        if type(mode) == int:
            mode = ParameterLearningMeasures._shortnames[mode]
        if not 'scipy' in sys.modules:
            raise Exception("Scipy was not imported! Install numpy and scipy if you want to use weight learning.")
        
        self.learnWtsMode = mode
        # intial parameter vector: all zeros or weights from formulas
        wt = numpy.zeros(len(self.formulas), numpy.float64)
        if initialWts:
            for i in range(len(self.formulas)):
                wt[i] = self.formulas[i].weight
                
        print mode, "learning %s" % str(params)        
        # optimization
        if mode == 'NPL_fixed': # this mode actually removes the respective formulas from the weight vector, whereas other fixed methods just use bounds on the weight
            self._fixUnitaryClauses()
            print "computing differences..."
            self._computeDiffs()
            print "  %d differences recorded" % len(self.diffs)
            print "determining relevant formulas for each ground atom..."
            self._getAtomRelevantGroundFormulas()            
            wtD = numpy.zeros(len(self.formulas) - len(self._fixedWeightFormulas), numpy.float64)
            wtIndex = 0
            for i, formula in enumerate(self.formulas):
                if (i in self._fixedWeightFormulas):
                    continue
                wtD[wtIndex] = wt[i]
                wtIndex = wtIndex + 1   

            wt = fsolve(self._negated_grad_pll_with_fixation, wtD)
            #wt, pll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags  = fmin_bfgs(self._negated_pl_with_fixation, wtD, fprime=self._negated_grad_pll_with_fixation, full_output=1)
            wtD = numpy.zeros(len(self.formulas), numpy.float64)
            wtIndex = 0
            for i, formula in enumerate(self.formulas):
                if (i in self._fixedWeightFormulas):
                    wtD[i] = self._fixedWeightFormulas[formula]
                else:
                    wtD[i] = wt[wtIndex]
                    wtIndex = wtIndex + 1
                        
            wt = wtD            
        elif mode == 'PLL' or mode == 'PLL_ISE' or mode == 'PLL_fixed':
            
            if mode == 'PLL_ISE':
                if PMB_METHOD != 'old': raise Exception("Only PMB (probability given Markov blanket) method 'old' supported by PLL_ISE")
                # set all soft evidence values to true
                for se in self.softEvidence:
                    self._setEvidence(self.gndAtoms[se["expr"]].idx, True)
                # overwrite methods
                self._computeDiffs = self._computeDiffsSoft
                self._setInvertedEvidence = self._setInvertedEvidenceSoft
                self._getTruthDegreeGivenEvidence = self._getTruthDegreeGivenEvidenceSoft            
            
            if mode == 'PLL_fixed':
                self._fixUnitaryClauses()
            else:
                self._fixFormulaWeights()


            print "computing differences..."
            self._computeDiffs()
            print "  %d differences recorded" % len(self.diffs)
            print "determining relevant formulas for each ground atom..."
            self._getAtomRelevantGroundFormulas()
            pllfunc = lambda * args:-self._pll(*args)
            gradfunc = lambda * args:-self._grad_pll(*args)
            gtol = 1.0000000000000001e-005#020
            print "starting optimization..." #, fprime=gradfunc
            #wt, pll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags  = fmin_bfgs(pllfunc, wt, fprime=gradfunc, gtol=gtol, full_output=1)
                
            if mode == 'PLL_fixed':
                print "formulas with fixed weights: %d" % len(self._fixedWeightFormulas)
                #wtD = numpy.zeros(len(self.formulas)-len(self._fixedWeightFormulas.items()), numpy.float64)
                b = [] 
                for i, formula in enumerate(self.formulas):
                    if i in self._fixedWeightFormulas:
                        wt[i] = self._fixedWeightFormulas[i]
                        b.append((wt[i], wt[i]))
                    else:
                        b.append((-1.0E12, 1.0E12))
                self.slsqp_result = fmin_slsqp(self._negated_pll_with_fixation, wt, bounds=b, f_eqcons=self._eqConstraints_with_fixation, fprime=self._negated_grad_pll_with_fixation, fprime_eqcons=self._eqConstraints_with_fixation_prime, args=(), iter=100, acc=1.0E8, iprint=1, full_output=1)
                
                wt = self.slsqp_result[0]
                pll_opt = self.slsqp_result[1]
            else:
                wtD = self._projectVectorToNonFixedWeightIndices(wt)
                #,
                wt, pll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags = fmin_bfgs(pllfunc, wtD, fprime=gradfunc , full_output=1)
                wt = self._reconstructFullWeightVectorWithFixedWeights(wt)
                
                
                #wt, pll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags = fmin_bfgs(pllfunc, wt, fprime=gradfunc, full_output=1)
                #wt, pll_opt, func_calls, grad_calls, warn_flags  = fmin_cg(pllfunc, wt, fprime=gradfunc, full_output=1)
                #self.learnwts_message = "pseudo-log-likelihood: %.16f\ngradient: %s\nfunction evaluations: %d\nwarning flags: %d\n" % (-pll_opt, str(-grad_opt), func_calls, warn_flags)
        elif mode in ['LL', 'LL_neg', 'LL_fixed', 'LL_fixed_neg']: # moved to LL
            # create possible worlds if neccessary
            if not 'worlds' in dir(self):
                print "creating possible worlds (%d ground atoms)..." % len(self.gndAtoms)
                self._createPossibleWorlds()
                print "  %d worlds created." % len(self.worlds)
            # compute counts
            print "computing counts..."
            self._computeCounts()
            print "  %d counts recorded." % len(self.counts)
            # get the possible world index of the training database
            self.idxTrainingDB = self._getEvidenceWorldIndex()
            # mode-specific stuff
            gradfunc = self._grad_ll
            llfunc = self._ll
            args = [self.idxTrainingDB]
            if mode == 'LL_fixed' or mode == 'LL_fixed_neg':
                args.append(params['fixedWeights'])
                gradfunc = self._grad_ll_fixed
                for idxFormula, weight in params["fixedWeights"].iteritems():
                    wt[idxFormula] = weight
            # opt
            print "starting optimization..."
            gtol = 1.0000000000000001e-005
            neg_llfunc = lambda params, *args:-llfunc(params, *args)
            neg_gradfunc = lambda params, *args:-gradfunc(params, *args)
            if mode != 'LL_neg' and mode != 'LL_fixed_neg':                
                wt, ll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags = fmin_bfgs(neg_llfunc, wt, gtol=gtol, fprime=neg_gradfunc, args=args, full_output=True)
            else:
                bounds = [(-100, 0.0) for i in range(len(wt))]
                wt, ll_opt, d = fmin_l_bfgs_b(neg_llfunc, wt, fprime=neg_gradfunc, args=args, bounds=bounds)
                warn_flags = d['warnflag']
                func_calls = d['funcalls']
                grad_opt = d['grad']
            # add final log likelihood to learnwts status for output
            self.learnwts_message = "log-likelihood: %.16f\ngradient: %s\nfunction evaluations: %d\nwarning flags: %d\n" % (-ll_opt, str(-grad_opt), func_calls, warn_flags)
        elif mode == 'BPLL' or mode == 'BPLL_fixed':
            if mode == 'BPLL_fixed':
                try:
                    self._fixUnitaryClauses()
                except:
                    print "BPLL_fixed: Unable to fix unitary clauses!"                    
                    self._fixedWeightFormulas = {}
            # get blocks
            print "constructing blocks..."
            self._getPllBlocks()
            self._getBlockRelevantGroundFormulas()
            # counts
            print "computing differences..."
            self._computeBlockDiffs()
            print "  %d differences recorded" % len(self.blockdiffs)
            # optimize
            print "starting opimization..."
            grad_opt, pll_opt = None, None

            if False: # sample some points (just for testing)
                mm = [ -30, 30]
                step = 3
                w1 = mm[0]
                while w1 <= mm[1]:
                    w2 = mm[0]
                    while w2 <= mm[1]:
                        print "%s," % [w1, w2, self._blockpll([w1, w2])],
                        w2 += 3
                    w1 += 3
                return
            
            if mode == 'BPLL_fixed':
                wtD = numpy.zeros(len(self.formulas) - len(self._fixedWeightFormulas), numpy.float64)
                #wtD = numpy.array([mpmath.mpf(0) for i in xrange(len(self.formulas) - len(self._fixedWeightFormulas.items()))])
                wtIndex = 0
                for i, formula in enumerate(self.formulas):
                    if (i in self._fixedWeightFormulas):
                        continue
                    wtD[wtIndex] = wt[i] #mpmath.mpf(wt[i])
                    wtIndex = wtIndex + 1   
                                             
                wt, pll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags = fmin_bfgs(self._negated_blockpll_with_fixation, wtD, fprime=self._negated_grad_blockpll_with_fixation, full_output=1)
                #result = bfgs.bfgs(func=self._negated_blockpll_with_fixation, dfunc=self._negated_grad_blockpll_with_fixation, args=(), x0=wtD, min_options=("Nocedal Wright Int",), func_tol=1e-20, grad_tol=1e-05, maxiter=1e3, a0=1.0, mu=0.0001, eta=0.9, full_output=1, print_flag=3, print_prefix="> ")
                #print result
                #wt = result[0]
                #pll_opt = result[1]
                
                wtD = numpy.zeros(len(self.formulas), numpy.float64)
                wtIndex = 0
                for i, formula in enumerate(self.formulas):
                    if (i in self._fixedWeightFormulas):
                        wtD[i] = self._fixedWeightFormulas[i]
                    else:
                        wtD[i] = wt[wtIndex]
                        wtIndex = wtIndex + 1

                wt = wtD
            else:
                neg_llfunc = lambda * args:-self._blockpll(*args)
                neg_grad = lambda wt:-self._grad_blockpll(wt)
                wt, pll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags = fmin_bfgs(neg_llfunc, wt, fprime=neg_grad, full_output=1)              
            if grad_opt != None:
                grad_str = "\n%s" % str(-grad_opt)
            else:
                grad_str = ""
            if pll_opt == None:
                pll_opt = self._blockpll(wt)
            #self.learnwts_message = "pseudo-log-likelihood: %.16f%s\nfunction evaluations: %d\nwarning flags: %d\n" % (- pll_opt, grad_str, func_calls, warn_flags)
        elif mode == 'LL_fac' or mode == 'LL_prob':
            if not 'worlds' in dir(self):
                print "creating possible worlds..."
                self._createPossibleWorlds()
                print "  %d worlds created." % len(self.worlds)
            # compute counts
            print "computing counts..."
            self._computeCounts2()
            print "  %d counts recorded." % len(self.counts)
            # get the possible world index of the training database
            idxTrainingDB = self._getEvidenceWorldIndex()
            # start opt
            for i in range(len(self.formulas)):
                wt[i] = 0.5
            bounds = [(1e-10, 1.0 - 1e-10) for i in range(len(wt))]
            if mode == 'LL_fac':
                gradfunc = self._grad_ll_fac
                llfunc = self._ll_fac
            elif mode == 'LL_prob':
                gradfunc = self._grad_ll_prob
                llfunc = self._ll_prob
            neg_llfunc = lambda params, *args:-llfunc(params, *args)
            neg_gradfunc = lambda params, *args:-gradfunc(params, *args)
            wt, ll_opt, d = fmin_l_bfgs_b(neg_llfunc, wt, fprime=neg_gradfunc, args=[idxTrainingDB], bounds=bounds)
            warn_flags = d['warnflag']
            func_calls = d['funcalls']
            grad_opt = d['grad']
            self.learnwts_message = "log-likelihood: %f\ngradient: %s\nfunction evaluations: %d\nwarning flags: %d\n" % (-ll_opt, str(-grad_opt), func_calls, warn_flags)
        elif mode == 'LL_ISE': # log-likelihood with independent soft evidence and weighting of formulas (soft counts) # moved to LL_ISE
            # create possible worlds if neccessary
            if not 'worlds' in dir(self):
                print "creating possible worlds (%d ground atoms)..." % len(self.gndAtoms)
                self._createPossibleWorlds()
                print "  %d worlds created." % len(self.worlds)
            # set soft evidence variables to true in evidence
            for se in self.softEvidence:
                self._setEvidence(self.gndAtoms[se["expr"]].idx, True)
            # compute counts
            print "computing (soft) counts..."
            self.allSoft = params.get("allSoft", False) #also used in _calculateWorldValues in _ll
            self._computeCountsSoft(self.allSoft)
            print "  %d counts recorded." % len(self.counts)
            # mode-specific stuff
            gradfunc = self._grad_ll
            llfunc = self._ll
            args = [self.idxTrainingDB] # computed in computeSoftCounts
            # opt
            print "starting optimization..."
            gtol = 1.0000000000000001e-005
            neg_llfunc = lambda params, *args:-llfunc(params, *args)
            neg_gradfunc = lambda params, *args:-gradfunc(params, *args)
            #optimizer = fmin_cg
            wt, ll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags = fmin_bfgs(neg_llfunc, wt, gtol=gtol, fprime=neg_gradfunc, args=args, full_output=True)
            #wt, ll_opt, func_calls, grad_calls, warn_flags  = fmin_cg(neg_llfunc, wt, args=args, fprime=neg_gradfunc, full_output=1)
            print wt
            
            # add final log likelihood to learnwts status for output
            self.learnwts_message = "log-likelihood: %.16f\ngradient: %s\nfunction evaluations: %d\nwarning flags: %d\n" % (-ll_opt, str(-grad_opt), func_calls, warn_flags)
        
        elif mode == 'LL_ISEWW': # log-likelihood with independent soft evidence and weighting of worlds
            # create possible worlds if neccessary
            if not 'worlds' in dir(self):
                print "creating possible worlds (%d ground atoms)..." % len(self.gndAtoms)
                self._createPossibleWorlds()
                print "  %d worlds created." % len(self.worlds)
            # compute counts
            print "computing counts..."
            self._computeCounts()
            print "  %d counts recorded." % len(self.counts)
            # mode-specific stuff
            #gradfunc = self._grad_ll_iseww
            llfunc = self._ll_iseww
            self.idxTrainingDB = self._getEvidenceWorldIndex()
            args = [self.idxTrainingDB]
            # opt
            print "starting optimization..."
            gtol = 1.0000000000000001e-005
            neg_llfunc = lambda params, *args:-llfunc(params, *args)
            neg_gradfunc = lambda params, *args:-gradfunc(params, *args)
            #, fprime=neg_gradfunc
            wt, ll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags = fmin_bfgs(neg_llfunc, wt, gtol=gtol, args=args, full_output=True)
            #wt, ll_opt, func_calls, grad_calls, warn_flags  = fmin_cg(neg_llfunc, wt, args=args, fprime=neg_gradfunc, full_output=1)
            
            
            print wt
            
            # add final log likelihood to learnwts status for output
            self.learnwts_message = "log-likelihood: %.16f\ngradient: %s\nfunction evaluations: %d\nwarning flags: %d\n" % (-ll_opt, str(-grad_opt), func_calls, warn_flags)
        else:
            raise Exception("Unknown mode '%s'" % mode)
        # use obtained vector to reset formula weights
            
        return wt
