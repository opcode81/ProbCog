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

try:
    import numpy
    from scipy.optimize import fmin_bfgs, fmin_cg, fmin_ncg, fmin_tnc, fmin_l_bfgs_b, fsolve, fmin_slsqp, fmin, fmin_powell
except:
    sys.stderr.write("Warning: Failed to import SciPy/NumPy (http://www.scipy.org)! Parameter learning with the MLN module is disabled.\n")

from MLN.util import *

class AbstractLearner(object):
    
    def __init__(self, mln, gaussianPriorSigma=None, **params):
        self.mln = mln
        self.params = params
        self.gaussianPriorSigma = gaussianPriorSigma
    
    def _reconstructFullWeightVectorWithFixedWeights(self, wt):        
        if len(self._fixedWeightFormulas) == 0:
            return wt
        
        wtD = numpy.zeros(len(self.mln.formulas), numpy.float64)
        wtIndex = 0
        for i, formula in enumerate(self.mln.formulas):
            if (i in self._fixedWeightFormulas):
                wtD[i] = self._fixedWeightFormulas[i]
                #print "self._fixedWeightFormulas[i]", self._fixedWeightFormulas[i]
            else:
                wtD[i] = wt[wtIndex]
                #print "wt[wtIndex]", wt[wtIndex]
                wtIndex = wtIndex + 1
        return wtD
    
    def _projectVectorToNonFixedWeightIndices(self, wt):
        if len(self._fixedWeightFormulas) == 0:
            return wt

        wtD = numpy.zeros(len(self.mln.formulas) - len(self._fixedWeightFormulas), numpy.float64)
        #wtD = numpy.array([mpmath.mpf(0) for i in xrange(len(self.formulas) - len(self._fixedWeightFormulas.items()))])
        wtIndex = 0
        for i, formula in enumerate(self.mln.formulas):
            if (i in self._fixedWeightFormulas):
                continue
            wtD[wtIndex] = wt[i] #mpmath.mpf(wt[i])
            wtIndex = wtIndex + 1   
        return wtD

    def _getTruthDegreeGivenEvidence(self, gndFormula):
        return 1.0 if self.mln._isTrueGndFormulaGivenEvidence(gndFormula) else 0.0
    
    def _fixFormulaWeights(self):
        self._fixedWeightFormulas = {}
        for formula in self.mln.fixedWeightFormulas:
            c = 0.0
            Z = 0.0
            for gf, referencedAtoms in formula.iterGroundings(self.mln):
                Z += 1
                print "self._getTruthDegreeGivenEvidence(gf), gf", self._getTruthDegreeGivenEvidence(gf), gf
                c += self._getTruthDegreeGivenEvidence(gf)
            w = logx(c / Z)
            self._fixedWeightFormulas[formula.idxFormula] = w
            print "fixed weight: %f=log(%f) F#%d %s" % (w, (c / Z), formula.idxFormula, str(formula))
            
    def __f(self, wt):
        # compute prior
        prior = 0
        if self.gaussianPriorSigma is not None:
            for weight in wt:
                prior += gaussianZeroMean(weight, self.gaussianPriorSigma)
        
        # reconstruct full weight vector
        wt = self._reconstructFullWeightVectorWithFixedWeights(wt)
        wt = self._convertToFloatVector(wt)
        print "_f: wt = ", wt
        sys.stdout.flush()
        
        # compute likelihood
        likelihood = self._f(wt)
        
        return likelihood + prior
        
    
    def __fDummy(self, wt):
        if not hasattr(self, 'dummyFValue'):
            self.dummyFCount = 0
        self.dummyFCount += 1
        if self.dummyFCount > 150:
            return 0
        print "self.dummyFCount", self.dummyFCount
        
        if not hasattr(self, 'dummyFValue'):
            self.dummyFValue = 0
        if not hasattr(self, 'lastFullGradient'):
            self.dummyFValue = 0
        else:
            self.dummyFValue += sum(abs(self.lastFullGradient))
        print "_f: self.dummyFValue = ", self.dummyFValue
#        if not hasattr(self, 'lastFullGradient'):
#            return 0
#        if not hasattr(self, 'dummyFValue'):
#            self.dummyFValue = 1
#        else:
#            if numpy.any(self.secondlastFullGradient != self.lastFullGradient):
#                self.dummyFValue += 1
#            
#        self.secondlastFullGradient = self.lastFullGradient     
            
        
        return self.dummyFValue
        
    def __grad(self, wt):
        wt = self._reconstructFullWeightVectorWithFixedWeights(wt)
        wt = self._convertToFloatVector(wt)
        
        grad = self._grad(wt)
        print "_grad: wt = %s\ngrad = %s" % (wt, grad)
        sys.stdout.flush()

        self.lastFullGradient = grad
        
        # add gaussian prior
        if self.gaussianPriorSigma is not None:
            for i, weight in enumerate(wt):
                grad[i] += gradGaussianZeroMean(weight, self.gaussianPriorSigma)
        
        return self._projectVectorToNonFixedWeightIndices(grad)
    
    #make sure mpmath datatypes aren't propagated in here as they can be very slow compared to native floats
    def _convertToFloatVector(self, wts):
        for wt in wts:
            wt = float(wt)
        return wts

    # learn the weights of the mln given the training data previously loaded with combineDB
    #   initialWts: whether to use the MLN's current weights as the starting point for the optimization
    def run(self, initialWts=False, **params):
        
        if not 'scipy' in sys.modules:
            raise Exception("Scipy was not imported! Install numpy and scipy if you want to use weight learning.")
        
        # initial parameter vector: all zeros or weights from formulas

        wt = numpy.zeros(len(self.mln.formulas), numpy.float64)# + numpy.random.ranf(len(self.mln.formulas)) * 100
        if initialWts:
            for i in range(len(self.mln.formulas)):
                wt[i] = self.mln.formulas[i].weight
        
        # precompute fixed formula weights
        self._fixFormulaWeights()
        self.wt = self._projectVectorToNonFixedWeightIndices(wt)
        
        self.params.update(params)
        self._prepareOpt()
        self._optimize(**params)
            
        return self.wt
    
    def _prepareOpt(self):
        pass
    
    def _optimize(self, gtol = 1.0000000000000001e-005, optimizer = "bfgs", useGrad = True, useF = True, **params):
        print "starting optimization with %s, useGrad = %s, useF = %s" % (optimizer, useGrad, useF)
        #print "initial wts = ", self._reconstructFullWeightVectorWithFixedWeights(self.wt)        
        neg_f = lambda wt: -self.__f(wt)
        neg_grad = lambda wt: -self.__grad(wt)
        if not useGrad or not self.useGrad(): neg_grad = None
        if not useF or not self.useF(): neg_f = lambda wt: -self.__fDummy(wt)
        
        if optimizer == "bfgs":
            #epsilon=0.05 seems to be a good value for simpleConditional example and DSLL_ISEWW with 1000, 10000 steps
            wt, f_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags = fmin_bfgs(neg_f, self.wt, gtol=gtol, fprime=neg_grad, args=(), full_output=True)
            print "optimization done with %s..." % optimizer
            print "f-opt: %.16f\ngradient: %s\nfunction evaluations: %d\nwarning flags: %d\n" % (-f_opt, str(-grad_opt), func_calls, warn_flags)
        elif optimizer == "cg":
            wt, f_opt, func_calls, grad_calls, warn_flags = fmin_cg(neg_f, self.wt, gtol=gtol, fprime=neg_grad, args=(), full_output=True)
            print "optimization done with %s..." % optimizer
            print "f-opt: %.16f\nfunction evaluations: %d\nwarning flags: %d\n" % (-f_opt, func_calls, warn_flags)
        elif optimizer == "ncg":
            wt, f_opt, func_calls, grad_calls, warn_flags = fmin_cg(neg_f, self.wt, gtol=gtol, fprime=neg_grad, args=(), full_output=True)
            print "optimization done with %s..." % optimizer
            print "f-opt: %.16f\nfunction evaluations: %d\nwarning flags: %d\n" % (-f_opt, func_calls, warn_flags)
        elif optimizer == "fmin":
            wt = fmin(neg_f, self.wt, args=(), full_output=True)
            print "optimization done with %s..." % optimizer
        elif optimizer == "powell":
            wt = fmin_powell(neg_f, self.wt,   args=(), full_output=True)
            print "optimization done with %s..." % optimizer
        self.wt = self._reconstructFullWeightVectorWithFixedWeights(wt)
        #print "allvecs", allvecs
        
        
        
    def useGrad(self):
        return True
    
    def useF(self):
        return True    


from softeval import truthDegreeGivenSoftEvidence

class SoftEvidenceLearner(AbstractLearner):

    def __init__(self, mln, **params):
        AbstractLearner.__init__(self,mln, **params)
        

    def _getTruthDegreeGivenEvidence(self, gf, worldValues=None):
        if worldValues is None: worldValues = self.mln.evidence
        return truthDegreeGivenSoftEvidence(gf, worldValues, self.mln)
