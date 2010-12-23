import sys
import math
import os
import fnmatch
import MLN
import FOL
import numpy
from math import exp, log, floor, ceil, e, sqrt
from numpy import *

from scipy import linalg

import psyco # Don't use Psyco when debugging!
psyco.full()
  
plotStyle = 'LaTeX'

if __name__ == '__main__':
    from matplotlib import rc
    rc('text', usetex=True)
    rc('font',**{'family':'serif'})
    if plotStyle == 'LaTeX':
        import pylab
        fig_width_pt = 234.8775 
        inches_per_pt = 1.0/72.27 
        golden_mean = float(3)/4#(sqrt(5)-1.0)/2.0   
        fig_width = fig_width_pt*inches_per_pt  
        fig_height = fig_width*golden_mean
        fig_size =  [fig_width,fig_height]
        pylab.rcParams.update({'backend': 'pdf',
                               'axes.titlesize': 11,
                               'axes.labelsize': 10,
                               'text.fontsize': 10,
                               'legend.fontsize': 8,
                               'xtick.labelsize': 8,
                               'ytick.labelsize': 8,
                               'text.latex.preamble' : ["\usepackage[cmex10]{amsmath}","\usepackage{amsfonts}","\usepackage{amssymb}","\\renewcommand{\\rmdefault}{cmr}"],
                               'text.usetex': True,
                               'figure.figsize': fig_size})

    import matplotlib.pyplot as plot


class DynamicWeightLearner:
    def __init__(self, mln_file):
        self.mln_file = mln_file
        
    def _getDomainsInvolvedinFormula(self, f, mln):
        domsInvolved = set([])
        if type(f) == FOL.Lit:
            return set(f.getVariables(mln).values())
        
        if type(f) == FOL.CountConstraint:
            return set(f.literal.getVariables(mln).values())
            
        for child in f.children:
            domsInvolved |= self._getDomainsInvolvedinFormula(child,mln)
        
        return domsInvolved
    
    def _registerGradNorm(self, domSizes, gradNorm):
        self.gradNormOpt.append((domSizes, gradNorm))
        
    def learnWeights(self, db_files):
        self.db_files = db_files
        self.mln_dict = {}
        self.wtAndDomSizes = {}
        self.domainRange = {}
        self.gradNormOpt = []
        self.exceptionDBs = []
        for db_file in self.db_files:
            self.tmpMLN = MLN.MLN(self.mln_file)
            self.tmpMLN.combineDB(db_file)
            print "Learning ", db_file
            try:
                self.tmpMLN.learnwts(MLN.ParameterLearningMeasures.BPLL_fixed)
            except:
                self.exceptionDBs.append(db_file)
                continue
                
            if float(self.tmpMLN.grad_opt_norm) > 1:
                print " "
                print " !!!! - Norm of gradient too big: ", float(self.tmpMLN.grad_opt_norm)
                print " "
            #    continue
            #numpy.float.__s
            for formula in self.tmpMLN.formulas:
                domainsInvolved = self._getDomainsInvolvedinFormula(formula,self.tmpMLN)
                if type(formula) == FOL.Lit and hasattr(self.tmpMLN,'AdaptiveDependencyMap') and formula.predName in self.tmpMLN.AdaptiveDependencyMap:
                    domainsInvolved.update(self.tmpMLN.AdaptiveDependencyMap[formula.predName])
                    
                domSizes = {}
                for domain in domainsInvolved:
                    domSizes[domain] = len(self.tmpMLN.domains[domain])
                    if domain in self.domainRange:
                        self.domainRange[domain][0] = min(self.domainRange[domain][0], domSizes[domain])
                        self.domainRange[domain][1] = max(self.domainRange[domain][1], domSizes[domain])
                    else:
                        self.domainRange[domain] = [domSizes[domain], domSizes[domain]]
                if str(formula) in self.wtAndDomSizes:
                    self.wtAndDomSizes[str(formula)].append([formula.weight, domSizes])
                else:
                    self.wtAndDomSizes[str(formula)] = [[formula.weight, domSizes]]
                self._registerGradNorm(domSizes,self.tmpMLN.grad_opt_norm)            
            
            self.mln_dict[db_file] = self.tmpMLN
        
        self.constantWeightFormulas = set([])
        
        for (domain, range) in self.domainRange.iteritems():
            if range[0] == range[1]:
                for formula,dynweight in self.wtAndDomSizes.iteritems():
                    for l in dynweight:
                        if domain in l[1]:
                            if len(l[1]) > 1:
                                del l[1][domain]
                            else:
                                self.constantWeightFormulas.add(formula)

        self.smartWeight = {}
        self.A = {}
        self.wts = {}
        for (formula, dynweight) in self.wtAndDomSizes.iteritems():
            if formula in self.constantWeightFormulas:
                self.A[formula] = ones((len(dynweight),1))
                self.wts[formula] = zeros((len(dynweight),1))
                i = 0
                for weight in dynweight:
                    self.wts[formula][i] = weight[0]
                    i = i + 1
                self.smartWeight[formula] = linalg.lstsq(self.A[formula],self.wts[formula])
                
                continue
            
            self.A[formula] = ones((len(dynweight),1+2*len(dynweight[0][1].values())))
            self.wts[formula] = zeros((len(dynweight),1))
            i = 0
            for weight in dynweight:
                self.wts[formula][i] = weight[0]
                j = 0
                for (domain, size) in weight[1].iteritems():
                    self.A[formula][i,j+1] = size
                    self.A[formula][i,j+2] = emath.log(size)
                    j = j + 2
                i = i + 1            
            self.smartWeight[formula] = linalg.lstsq(self.A[formula],self.wts[formula])
            
        result = MLN.MLN(self.mln_file)
        for formula in result.formulas:
            smartWeight = self.smartWeight[str(formula)][0]
            formula.weight = str(float(smartWeight[0]))
            if str(formula) in self.constantWeightFormulas:
                continue
            
            j = 1
            for domain in self.wtAndDomSizes[str(formula)][0][1].keys():
                formula.weight += "+("+str(float(smartWeight[j]))+"*domSize("+domain+"))"
                formula.weight += "+("+str(float(smartWeight[j+1]))+"*logx(domSize("+domain+")))"
                j = j + 2  
        
        self.learnedMLN = result
        return result          
    
    def loadWeights(self, mln_files):
        raise Exception("Not implemented yet.")    
    
    def analyzeError(self, db_test_files):
        self.db_test_files = db_test_files
        self.mln_test_dict = {}
        self.testWTAndDomSizes = {}
        self.domainDBase = {}
        self.inferMLNs = {}
        for db_test_file in self.db_test_files:
            self.tmpMLN = MLN.MLN(self.mln_file)
            self.tmpMLN.combineDB(db_test_file)
            print "Learning ", db_test_file     
            try:
                self.tmpMLN.learnwts(MLN.ParameterLearningMeasures.BPLL_fixed)
            except:
                self.exceptionDBs.append(db_test_file)
                continue
                   
            if float(self.tmpMLN.grad_opt_norm) > 1:
                print " "
                print " !!!! - Norm of gradient too big: ", float(self.tmpMLN.grad_opt_norm)
                print " "
            #    
            #    continue
            
            for formula in self.tmpMLN.formulas:
                domainsInvolved = self._getDomainsInvolvedinFormula(formula,self.tmpMLN)
                if type(formula) == FOL.Lit and hasattr(self.tmpMLN,'AdaptiveDependencyMap') and formula.predName in self.tmpMLN.AdaptiveDependencyMap:
                    domainsInvolved.update(self.tmpMLN.AdaptiveDependencyMap[formula.predName])
                domSizes = {}
                for domain in domainsInvolved:
                    domSizes[domain] = len(self.tmpMLN.domains[domain])
                    if domain in self.domainRange:
                        self.domainRange[domain][0] = min(int(self.domainRange[domain][0]), int(domSizes[domain]))
                        self.domainRange[domain][1] = max(int(self.domainRange[domain][1]), int(domSizes[domain]))
                    else:
                        self.domainRange[domain] = [domSizes[domain], domSizes[domain]]
                    if domain in self.domainDBase: self.domainDBase[domain].append(domSizes[domain])
                    else: self.domainDBase[domain] = [domSizes[domain]]                   
                if str(formula) in self.testWTAndDomSizes:
                    self.testWTAndDomSizes[str(formula)].append([formula.weight, domSizes])
                else:
                    self.testWTAndDomSizes[str(formula)] = [[formula.weight, domSizes]]
                self._registerGradNorm(domSizes,self.tmpMLN.grad_opt_norm)

            self.mln_test_dict[db_test_file] = self.tmpMLN

        self.plotDomain = None
        self.domainBasis = {}
        for (domain, range) in self.domainRange.iteritems():
            if range[0] < range[1]:
                if self.plotDomain is None: self.plotDomain = domain
                else: raise Exception("Error: At least two domains are varying in test samples. Unable to do 3D Plot with Matplotlib.")

            if range[0] == range[1]:
                for dynweight in self.testWTAndDomSizes.values():
                    for l in dynweight:
                        if domain in l[1]:
                            del l[1][domain]
                continue
            
            self.domainBasis[domain] = linspace(range[0],range[1],300)

        if self.plotDomain is None: raise Exception("Error: Unable to plot error over varying domain size, since there is no variation.")
        
        self.plotsByTitle = {}
        
        self.test_wts = {}
        self.plotsByTitle['gradNorm'] = plot.figure()
        for (domSizes, gradNorm) in self.gradNormOpt:
            if self.plotDomain in domSizes:
                print domSizes[self.plotDomain]," ",float(gradNorm)
                plot.plot(array([domSizes[self.plotDomain]]),array([float(gradNorm)]),'x')

        if plotStyle != 'LaTeX':
            plot.title("Optimization Quality")
        plot.xlabel("\# of elements in ``"+self.plotDomain+"'' domain")
        plot.ylabel("Gradient Norm")
        
        for (formula, dynweight) in self.testWTAndDomSizes.iteritems():
            if formula in self.constantWeightFormulas:
                self.plotsByTitle[formula] = plot.figure()
                if plotStyle == 'LaTeX':
                    plot.clf()
                    plot.axes([0.175,0.175,0.95-0.175,0.95-0.175])
                y = self.smartWeight[formula][0][0]*ones(300)
                self.plotDomainIndex = 0
            
                self.test_wts[formula] = zeros((len(dynweight),1))
                self.test_Basis = zeros((len(dynweight),1))
                i = 0
                for weight in dynweight:
                    self.test_wts[formula][i] = weight[0]
                    self.test_Basis[i] = 1#weight[1]["student"]
                    i = i + 1
                
            #plot.plot(emath.power(math.e, self.A[formula][:,self.plotDomainIndex]),self.wts[formula],'x',self.test_Basis,self.test_wts[formula],'o',self.domainBasis[self.plotDomain],y)
                plot.plot(self.test_Basis,self.test_wts[formula],'bx',self.A[formula][:,0],self.wts[formula],'g+',self.domainBasis[self.plotDomain],y)
                plot.title(formula)
                plot.xlabel("\# of elements in ``"+self.plotDomain+"'' domain")
                plot.ylabel("Weight")
                
                continue
            
            if self.plotDomain not in dynweight[0][1]:
                continue

            self.plotsByTitle[formula] = plot.figure()
            if plotStyle == 'LaTeX':
                plot.clf()
                plot.axes([0.175,0.175,0.95-0.175,0.95-0.175])
            
            y = self.smartWeight[formula][0][0]*ones(300)
            self.plotDomainIndex = 0
            j = 1
            for (domain, size) in dynweight[0][1].iteritems():
                if domain == self.plotDomain: self.plotDomainIndex = j#+1
                y = y + self.smartWeight[formula][0][j] * self.domainBasis[domain]
                y = y + self.smartWeight[formula][0][j+1] * emath.log(self.domainBasis[domain])
                j = j + 2
            
            self.test_wts[formula] = zeros((len(dynweight),1))
            self.test_Basis = zeros((len(dynweight),1))
            i = 0
            for weight in dynweight:
                self.test_wts[formula][i] = weight[0]
                self.test_Basis[i] = weight[1][self.plotDomain]
                i = i + 1
                
            #plot.plot(emath.power(math.e, self.A[formula][:,self.plotDomainIndex]),self.wts[formula],'x',self.test_Basis,self.test_wts[formula],'o',self.domainBasis[self.plotDomain],y)
            plot.plot(self.test_Basis,self.test_wts[formula],'wo',self.A[formula][:,self.plotDomainIndex],self.wts[formula],'bx',self.domainBasis[self.plotDomain],y)
            if plotStyle != 'LaTeX':
                plot.title(formula)
            plot.xlabel("\# of elements in ``"+self.plotDomain+"'' domain")
            plot.ylabel("weight")
            if formula.startswith("(!takes"):
                plot.legend(('test data','training data','weight curve'),loc=4)
            else:
                plot.legend(('test data','training data','weight curve'))
            
            
        if len(self.exceptionDBs) > 0:
            print "Exceptions occured while learning:"
            print self.exceptionDBs
        else:
            print "No exceptions occured while learning."
            
        if plotStyle != 'LaTeX':
            plot.show()
        else:
            for title,figure in self.plotsByTitle.iteritems():
                figure.savefig(title+'.pdf')
        return
        
        # construct MLNs for evaluation of dynamic parameters:
        domSizesDone = set([])
        plot.figure()
        plot.title("Expected number of courses taken by one student")
        plot.xlabel("# of elements in \""+self.plotDomain+"\" domain")
        plot.ylabel("Expected number of courses")
        
        mln = self.learnedMLN
        for db_test_file in self.db_test_files:
        #self.tmpMLN = MLN.MLN(self.mln_file)
        #self.tmpMLN.combineDBOverwriteDomains(db_test_file)
            mln.combineDBOverwriteDomains(db_test_file)
            ievidence = {}
            for idx,gndAtom in mln.gndAtomsByIdx.iteritems():                
                if gndAtom.predName != "takes":
                    ievidence[str(gndAtom)] = mln.evidence[idx]
            
            if len(mln.domains[self.plotDomain]) in domSizesDone:
                continue
            """Test how good the dynamic string weight of the formula really is:
            for (formula, dynweight) in self.testWTAndDomSizes.iteritems():
                y = self.smartWeight[formula][0][0]
                #self.plotDomainIndex = 0
                j = 1
                for (domain, size) in dynweight[0][1].iteritems():
                    #if domain == self.plotDomain: self.plotDomainIndex = j#+1
                    y = y + self.smartWeight[formula][0][j] * len(mln.domains[domain])
                    y = y + self.smartWeight[formula][0][j+1] * math.log(len(mln.domains[domain]))
                    j = j + 2
                
                for mf in mln.formulas:
                    if str(mf) == formula:
                        print float(mf.weight - y)     # Answer: 10e-12                   
                        break
           """
            result = mln.inferMCSAT("takes(x,y)", given=MLN.evidence2conjunction(ievidence), debug=True, details=False)
            print result
            #calculate take-count:
            plot.plot(numpy.array([len(mln.domains[self.plotDomain])]),numpy.array([sum(result)/len(mln.domains["student"])]),'x')
            plot.ylim(0,5)
            xmin,xmax = plot.xlim()
            plot.xlim(xmin-0.5,xmax+0.5)
            domSizesDone.add(len(mln.domains[self.plotDomain]))
        print ""
        plot.show()

if __name__ == '__main__':
    args = sys.argv[1:]
    if len(args) < 3:
        print "\nMLNs for variable domain sizes in Python - helper tool\n\n  usage: MLNWeightCurveFitting.py <mln-file-to-learn> <dir-with-db-files-to-learn> <dir-with-db-files-to-test>\n\n"
        sys.exit(0)

    dwl = DynamicWeightLearner(args[0])

    db_files = []
    for filename in os.listdir(args[1]):
        if fnmatch.fnmatch(filename, "*.db"):
            db_files.append(args[1] + "/" + filename)
    
    db_test_files = []
    for filename in os.listdir(args[2]):
        if fnmatch.fnmatch(filename, "*.db"):
            db_test_files.append(args[2] + "/" + filename)
            
    outFile = file(args[0][:-3]+"a.mln","w")
    dwl.learnWeights(db_files).write(outFile)
    outFile.close()
    dwl.analyzeError(db_test_files)
    print "Done."
