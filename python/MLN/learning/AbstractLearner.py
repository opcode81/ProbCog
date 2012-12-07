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

from MLN.util import *
import MLN
import optimize
try:
    import numpy
except:
    pass

class AbstractLearner(object):
    
    def __init__(self, mrf, gaussianPriorSigma=None, **params):
        self.mln = mrf # only for backward compatibility of implementations
        self.mrf = mrf
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
            
    def f(self, wt):
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
        print "  likelihood = %f" % likelihood
        
        return likelihood + prior
        
    
    def __fDummy(self, wt):
        ''' a dummy target function that is used when f is disabled '''
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
        
    def grad(self, wt):
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
    
    def _optimize(self, optimizer = None, **params):
        imposedOptimizer = self.getAssociatedOptimizerName()
        if imposedOptimizer is not None:
            if optimizer is not None: raise Exception("Cannot override the optimizer for this method with '%s'" % optimizer)
            optimizer = imposedOptimizer
        else:
            if optimizer is None: optimizer = "bfgs"
        
        if optimizer == "directDescent":
            opt = optimize.DirectDescent(self.wt, self, **params)        
        elif optimizer == "diagonalNewton":
            opt = optimize.DiagonalNewton(self.wt, self, **params)        
        else:
            opt = optimize.SciPyOpt(optimizer, self.wt, self, **params)        
        
        wt = opt.run()        
        self.wt = self._reconstructFullWeightVectorWithFixedWeights(wt)
        
        
    def useGrad(self):
        return True
    
    def useF(self):
        return True

    def getAssociatedOptimizerName(self):
        return None

    def hessian(self, wt):
        wt = self._reconstructFullWeightVectorWithFixedWeights(wt)
        wt = self._convertToFloatVector(wt)
        fullHessian = self._hessian(wt)
        return self._projectMatrixToNonFixedWeightIndices(fullHessian)
    
    def _projectMatrixToNonFixedWeightIndices(self, matrix):
        if len(self._fixedWeightFormulas) == 0:
            return matrix

        dim = len(self.mln.formulas) - len(self._fixedWeightFormulas)
        proj = numpy.zeros((dim, dim), numpy.float64)
        i2 = 0
        for i in xrange(len(self.mln.formulas)):
            if (i in self._fixedWeightFormulas):
                continue
            j2 = 0
            for j in xrange(len(self.mln.formulas)):
                if (j in self._fixedWeightFormulas):
                    continue
                proj[i2][j2] = matrix[i][j]
                j2 += 1
            i2 += 1            
        return proj

    def _hessian(self, wt):
        raise Exception("The learner '%s' does not provide a Hessian computation; use another optimizer!" % str(type(self)))
    
    def _f(self, wt):
        raise Exception("The learner '%s' does not provide an objective function computation; use another optimizer!" % str(type(self)))

    def getName(self):
        return "%s[%s]" % (self.__class__.__name__, "sigma=%f" % self.gaussianPriorSigma if self.gaussianPriorSigma is not None else "no prior")

from softeval import truthDegreeGivenSoftEvidence

class SoftEvidenceLearner(AbstractLearner):

    def __init__(self, mln, **params):
        AbstractLearner.__init__(self,mln, **params)
        

    def _getTruthDegreeGivenEvidence(self, gf, worldValues=None):
        if worldValues is None: worldValues = self.mln.evidence
        return truthDegreeGivenSoftEvidence(gf, worldValues, self.mln)


class MultipleDatabaseLearner(AbstractLearner):
    '''
    learns from multiple databases using an arbitrary sub-learning method for each database, assuming independence between individual databases
    '''
    
    def __init__(self, mln, method, dbs, verbose=True, **params):
        '''
        dbs: list of tuples (domain, evidence) as returned by the database reading method
        '''        
        AbstractLearner.__init__(self, mln, **params)        
        self.dbs = dbs
        self.constructor = MLN.ParameterLearningMeasures.byShortName(method)
        self.params = params
        
        self.learners = []
        for i, db in enumerate(self.dbs):
            print "grounding MRF for database %d/%d..." % (i+1, len(self.dbs))
            mrf = self.mln.groundMRF(db)
            learner = eval("MLN.learning.%s(mrf, **self.params)" % self.constructor)
            self.learners.append(learner)
            learner._prepareOpt()
    
    def getName(self):
        return "MultipleDatabaseLearner[%d*%s]" % (len(self.learners), self.learners[0].getName())
    
    def _f(self, wt):
        likelihood = 0
        for learner in self.learners:
            likelihood += learner._f(wt)
        return likelihood
    
    def _grad(self, wt):
        grad = numpy.zeros(len(self.mln.formulas), numpy.float64)
        for i, learner in enumerate(self.learners):
            grad_i = learner._grad(wt)
            #print "  grad %d: %s" % (i, str(grad_i))
            grad += grad_i
        return grad

    def _hessian(self, wt):
        N = len(self.mln.formulas)
        hessian = numpy.matrix(numpy.zeros((N,N)))
        for learner in self.learners:
            hessian += learner._hessian(wt)
        return hessian

    def _prepareOpt(self):
        pass # _prepareOpt is called for individual learners during construction
    
    def _fixFormulaWeights(self):
        self._fixedWeightFormulas = {}
        for learner in self.learners:
            learner._fixFormulaWeights()
            for i, w in learner._fixedWeightFormulas.iteritems():
                if i not in self._fixedWeightFormulas:
                    self._fixedWeightFormulas[i] = 0.0
                self._fixedWeightFormulas[i] += w / len(self.learners)

    