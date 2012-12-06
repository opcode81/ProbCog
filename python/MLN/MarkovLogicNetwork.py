# -*- coding: iso-8859-1 -*-
#
# Markov Logic Networks
#
# (C) 2006-2011 by Dominik Jain (jain@cs.tum.edu)
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

'''
Your MLN files may contain:
    - domain declarations, e.g.
            domainName = {value1, value2, value3}
    - predicate declarations, e.g.
            pred1(domainName1, domainName2)
        mutual exclusiveness and exhaustiveness may be declared simultaneously, e.g.
        pred1(a,b!) to state that for every constant value that variable a can take on, there is exactly one value that b can take on
    - formulas, e.g.
            12   cloudy(d)
    - C++-style comments (i.e. // and /* */) anywhere

The syntax of .mln files is mostly compatible to the Alchemy system (see manual of the Alchemy system)
with the following limitations:
    - one line per definition, no line breaks allowed
    - all formulas must be preceded by a weight, which can be expressed as an arithmetic expression such as "1.0/3.0" or "log(0.5)"
      note that formulas are not decomposed into clauses but processed as is.
      operators (in order of precedence; use parentheses to change precendence):
              !   negation
              v   disjunction
              ^   conjunction
              =>  implication
              <=> biimplication
      Like Alchemy, we support the prefix operators * on literals and + on variables to specify templates for formulas.
    - no support for functions

As a special construct of our implementation, an MLN file may contain constraints of the sort
    P(foo(x, Bar)) = 0.5
i.e. constraints on formula probabilities that cause weights to be automatically adjusted to conform to the constraint.
Note that the MLN must contain the corresponding formula.

deprecated features:

We support an alternate representation where the parameters are factors between 0 and 1 instead of regular weights.
To use this representation, make use of infer2 rather than infer to compute probabilities.
To learn factors rather than regular weights, use "LL_fac" rather than "LL" as the method you supply to learnwts.
(pseudolikelihood is currently unsupported for learning such representations)
'''

DEBUG = False

import math
import re
import sys
import os
import random
import time
import traceback
import pickle

sys.setrecursionlimit(10000)

import platform
if platform.architecture()[0] == '32bit':
    try:
        if not DEBUG:
            import psyco # Don't use Psyco when debugging!
            psyco.full()
    except:
        sys.stderr.write("Note: Psyco (http://psyco.sourceforge.net) was not loaded. On 32bit systems, it is recommended to install it for improved performance.\n")

from _pyparsing import ParseException
import FOL
from inference import *
from util import *
from methods import *
import learning


POSSWORLDS_BLOCKING = True

# -- Markov logic network

class MLN(object):
    '''
    represents a Markov logic network and/or a ground Markov network

    members:
        blocks:
            dict: predicate name -> list of booleans that indicate which arguments of the pred are functionally determined by the others
            (one boolean per argument, True = is functionally determined)
        closedWorldPreds:
            list of predicates that are assumed to be closed-world (for inference)
        formulas:
            list of formula objects
        predicates:
            dict: predicate name -> list of domain names that apply to the predicate's parameters
        worldCode2Index:
            dict that maps a world's identification code to its index in self.worlds
        worlds
            list of possible worlds, each entry is a dict with
                'values' -> list of booleans - one for each gnd atom
        allSoft:
            for soft evidence learning: flag,
              if true: compute counts for normalization worlds using soft counts
              if false: compute counts for normalization worlds using hard counts (boolean worlds)
        learnWtsMode
    '''

    def __init__(self, filename_or_list=None, defaultInferenceMethod=InferenceMethods.MCSAT, parameterType='weights', verbose=False, mlnContent=None):
        '''
            constructs an MLN object
            at least one of the arguments
                filename_or_list: either a single filename or a list of filenames (.mln files)
                mlnContent: string containing the MLN declarations
            must be given (both possible)
        '''
        t_start = time.time()
        self.domains = {}
        self.predicates = {}
        self.rigidPredicates = []
        self.formulas = []
        self.blocks = {}
        self.domDecls = []
        self.probreqs = []
        self.posteriorProbReqs = []
        self.defaultInferenceMethod = defaultInferenceMethod
        self.probabilityFittingInferenceMethod = InferenceMethods.Exact
        self.probabilityFittingThreshold = 0.002 # maximum difference between desired and computed probability
        self.probabilityFittingMaxSteps = 20 # maximum number of steps to run iterative proportional fitting
        self.parameterType = parameterType
        self.formulaGroups = []
        self.closedWorldPreds = []
        self.learnWtsMode = None
        formulatemplates = []
        self.vars = {}
        self.allSoft = False
        self.fixedWeightFormulas = []

        # read MLN file
        text = ""
        if filename_or_list is not None:
            if not type(filename_or_list) == list:
                filename_or_list = [filename_or_list]
            for filename in filename_or_list:
                #print filename
                f = file(filename)
                text += f.read()
                f.close()
        if mlnContent is not None:
            text += "\n"
            text += mlnContent
        if text == "": return #raise Exception("No MLN content to construct model from was given; must specify either file/list of files or content string!")
        # replace some meta-directives in comments
        text = re.compile(r'//\s*<group>\s*$', re.MULTILINE).sub("#group", text)
        text = re.compile(r'//\s*</group>\s*$', re.MULTILINE).sub("#group.", text)
        # remove comments
        text = stripComments(text)

        # read lines
        self.hard_formulas = []
        if verbose: print "reading MLN..."
        templateIdx2GroupIdx = {}
        inGroup = False
        idxGroup = -1
        fixWeightOfNextFormula = False
        fixedWeightTemplateIndices = []
        lines = text.split("\n")
        iLine = 0
        while iLine < len(lines):
            line = lines[iLine]
            iLine += 1
            line = line.strip()
            try:
                if len(line) == 0: continue
                # meta directives
                if line == "#group":
                    idxGroup += 1
                    inGroup = True
                    continue
                elif line == "#group.":
                    inGroup = False
                    continue
                elif line.startswith("#fixWeightFreq"):
                    fixWeightOfNextFormula = True
                    continue
                elif line.startswith("#include"):
                    filename = line[len("#include "):].strip()
                    content = stripComments(file(filename, "r").read())
                    lines = content.split("\n") + lines[iLine:]
                    iLine = 0
                    continue
                elif line.startswith("#fixUnitary:"): # deprecated (use instead #fixedWeightFreq)
                    predName = line[12:len(line)]
                    if hasattr(self, 'fixationSet'):
                        self.fixationSet.add(predName)
                    else:
                        self.fixationSet = set([predName])
                    continue
                elif line.startswith("#AdaptiveMLNDependency"): # declared as "#AdaptiveMLNDependency:pred:domain"; seems to be deprecated
                    depPredicate, domain = line.split(":")[1:3]
                    if hasattr(self, 'AdaptiveDependencyMap'):
                        if depPredicate in self.AdaptiveDependencyMap:
                            self.AdaptiveDependencyMap[depPredicate].add(domain)
                        else:
                            self.AdaptiveDependencyMap[depPredicate] = set([domain])
                    else:
                        self.AdaptiveDependencyMap = {depPredicate:set([domain])}
                    continue
                # domain decl
                if '{' in line:
                    domName, constants = parseDomDecl(line)
                    if domName in self.domains: raise Exception("Domain redefinition: '%s' already defined" % domName)
                    self.domains[domName] = constants
                    self.domDecls.append(line)
                    continue
                # prior probability requirement
                if line.startswith("P("):
                    m = re.match(r"P\((.*?)\)\s*=\s*([\.\de]+)", line)
                    if m is None:
                        raise Exception("Prior probability constraint formatted incorrectly: %s" % line)
                    self.probreqs.append({"expr": strFormula(FOL.parseFormula(m.group(1))).replace(" ", ""), "p": float(m.group(2))})
                    continue
                # posterior probability requirement/soft evidence
                if line.startswith("R(") or line.startswith("SE("):
                    m = re.match(r"(?:R|SE)\((.*?)\)\s*=\s*([\.\de]+)", line)
                    if m is None:
                        raise Exception("Posterior probability constraint formatted incorrectly: %s" % line)
                    self.posteriorProbReqs.append({"expr": strFormula(FOL.parseFormula(m.group(1))).replace(" ", ""), "p": float(m.group(2))})
                    continue
                # variable definition
                if line.startswith("$"):
                    m = re.match(r'(\$\w+)\s*=(.+)', line)
                    if m is None:
                        raise Exception("Variable assigment malformed: %s" % line)
                    self.vars[m.group(1)] = "(%s)" % m.group(2).strip()
                    continue
                # mutex constraint
                if re.search(r"[a-z_][-_'a-zA-Z0-9]*\!", line) != None:
                    pred = parsePredicate(line)
                    mutex = []
                    for param in pred[1]:
                        if param[ -1] == '!':
                            mutex.append(True)
                        else:
                            mutex.append(False)
                    self.blocks[pred[0]] = mutex
                    # if the corresponding predicate is not yet declared, take this to be the declaration
                    if not pred[0] in self.predicates:
                        argTypes = map(lambda x: x.strip("!"), pred[1])
                        self.predicates[pred[0]] = argTypes
                    continue
                # predicate decl or formula with weight
                else:
                    # try predicate declaration
                    isPredDecl = True
                    try:
                        pred = FOL.predDecl.parseString(line)[0]
                    except:
                        isPredDecl = False
                    if isPredDecl:
                        predName = pred[0]
                        if predName in self.predicates:
                            raise Exception("Predicate redefinition: '%s' already defined" % predName)
                        self.predicates[predName] = pred[1]
                        continue
                    # formula (template) with weight or terminated by '.'
                    else:
                        isHard = False
                        if line[ -1] == '.': # hard (without explicit weight -> determine later)
                            isHard = True
                            formula = line[:-1]
                        else: # with weight
                            spacepos = line.find(' ')
                            weight = line[:spacepos]
                            formula = line[spacepos:].strip()
                        try:
                            formula = FOL.parseFormula(formula)
                            if not isHard:
                                formula.weight = weight
                            else:
                                formula.weight = None # not set until instantiation when other weights are known
                            formula.isHard = isHard
                            idxTemplate = len(formulatemplates)
                            formulatemplates.append(formula)
                            if inGroup:
                                templateIdx2GroupIdx[idxTemplate] = idxGroup
                            if fixWeightOfNextFormula == True:
                                fixWeightOfNextFormula = False
                                fixedWeightTemplateIndices.append(idxTemplate)
                        except ParseException, e:
                            raise Exception("Error parsing formula '%s'\n" % formula)
            except:
                sys.stderr.write("Error processing line '%s'\n" % line)
                cls, e, tb = sys.exc_info()
                traceback.print_tb(tb)
                raise e

        # augment domains with constants appearing in formula templates
        for f in formulatemplates:
            constants = {}
            f.getVariables(self, None, constants)
            for domain, constants in constants.iteritems():
                for c in constants: self.addConstant(domain, c)

        # save data on formula templates for materialization
        self.materializedTemplates = False
        self.formulas = formulatemplates
        self.templateIdx2GroupIdx = templateIdx2GroupIdx
        self.fixedWeightTemplateIndices = fixedWeightTemplateIndices

    def __getattr__(self, attr):
        # forward inference calls to the MRF (for backward compatibility)
        if attr[:5] == "infer":
            return self.mrf.__getattribute__(attr)

    def materializeFormulaTemplates(self, dbs):
        '''
            dbs: list of Database objects
        '''
        
        # obtain full domain with all objects 
        fullDomain = mergeDomains(self.domains, *[db.domains for db in dbs])
        
        # expand formula templates
        oldDomains = self.domains
        self.domains = fullDomain
        self._materializeFormulaTemplates()
        self.domains = oldDomains
        
        # permanently transfer domains of variables that were expanded from templates
        for ft in self.formulaTemplates:
            domNames = ft._getTemplateVariables(self).values()
            for domName in domNames:
                self.domains[domName] = fullDomain[domName]
                #print "permanent domain %s: %s" % (domName, fullDomain[domName])

    def _materializeFormulaTemplates(self, verbose=False):
        if self.materializedTemplates:
            raise Exception("This MLN's formula templates were previously materialized")

        self.formulaTemplates = self.formulas
        templateIdx2GroupIdx = self.templateIdx2GroupIdx
        fixedWeightTemplateIndices = self.fixedWeightTemplateIndices
        self.materializedTemplates = True
        self.formulas = []

        # materialize formula templates
        if verbose: print "materializing formula templates..."
        idxGroup = None
        prevIdxGroup = None
        group = []
        for idxTemplate, tf in enumerate(self.formulaTemplates):
            idxGroup = templateIdx2GroupIdx.get(idxTemplate)
            if idxGroup != None:
                if idxGroup != prevIdxGroup: # starting new group
                    self.formulaGroups.append(group)
                    group = []
                prevIdxGroup = idxGroup
            # get template variants
            fl = tf.getTemplateVariants(self)
            # add them to the list of formulas and set index
            for f in fl:
                f.weight = tf.weight
                f.isHard = tf.isHard
                if f.weight is None:
                    self.hard_formulas.append(f)
                idxFormula = len(self.formulas)
                self.formulas.append(f)
                f.idxFormula = idxFormula
                # add the formula indices to the group if any
                if idxGroup != None:
                    group.append(idxFormula)
                # fix weight of formulas
                if idxTemplate in fixedWeightTemplateIndices:
                    self.fixedWeightFormulas.append(f)
        if group != []: # add the last group (if any)
            self.formulaGroups.append(group)
        #print "time taken: %fs" % (time.time()-t_start)

    def addConstant(self, domainName, constant):
        if domainName not in self.domains: self.domains[domainName] = []
        dom = self.domains[domainName]
        if constant not in dom: dom.append(constant)

    def _groundFormula(self, formula, variables, assignment, idxFormula):
        # DEPRECATED function
        # if all variables have been grounded...
        if variables == {}:
            #print atoms
            referencedGndAtoms = []
            gndFormula = formula.ground(self, assignment, referencedGndAtoms)
            for idxGA in referencedGndAtoms:
                self.gndAtomOccurrencesInGFs[idxGA].append(gndFormula)
            gndFormula.idxFormula = idxFormula
            self.gndFormulas.append(gndFormula)
            return
        # ground the first variable...
        varname, domName = variables.popitem()
        for value in self.domains[domName]: # replacing it with one of the constants
            assignment[varname] = value
            # recursive descent to ground further variables
            self._groundFormula(formula, dict(variables), assignment, idxFormula)


    def _substVar(self, matchobj):
        varName = matchobj.group(0)
        if varName not in self.vars:
            raise Exception("Unknown variable '%s'" % varName)
        return self.vars[varName]

    def combine(self, domain, verbose=False, groundFormulas=True, evidence=None):
        '''
            combines the existing domain (if any) with the given one
                domain: a dictionary with domainName->list of string constants to add
        '''
        if groundFormulas:
            if evidence is None: evidence = {}
            self.groundMRF((domain, evidence))
        else:
            raise Exception("This usage is deprecated")
        '''
        # combine domains
        domNames = set(self.domains.keys() + domain.keys())
        for domName in domNames:
            a = self.domains.get(domName, [])
            b = domain.get(domName, [])
            self.domains[domName] = list(set(a + b))
        # collect data
        if groundFormulas:
            self.ground(verbose=verbose)
        # set evidence if given
        if evidence is not None:
            self.setEvidence(evidence)
        '''

    def ground(self, verbose=False):
        '''
            DEPRECATED, use groundMRF instead
        '''
        self._generateGroundAtoms()
        if verbose: print "ground atoms: %d" % len(self.gndAtoms)
        self._materializeFormulaTemplates(verbose)
        self._createFormulaGroundings(verbose)
        if verbose: print "ground formulas: %d" % len(self.gndFormulas)

    def groundMRF(self, db, verbose=False, simplify=False):
        '''
            creates and returns a ground Markov random field for the given database
                db: database filename (string) or Database object
        '''
        self.mrf = MRF(self, db, verbose=verbose, simplify=simplify)
        return self.mrf

    def combineOverwrite(self, domain, verbose=False, groundFormulas=True):
        '''
            combines the existing domain (if any) with the given one
                domain: a dictionary with domainName->list of string constants to add
        '''
        domNames = set(self.domains.keys() + domain.keys())
        for domName in domNames:
            a = self.domains.get(domName, [])
            b = domain.get(domName, [])
            if b == [] and a != []:
                self.domains[domName] = list(a)
            else:
                self.domains[domName] = list(b)

        # collect data
        self._generateGroundAtoms()
        if groundFormulas: self._createFormulaGroundings()
        if verbose:
            print "ground atoms: %d" % len(self.gndAtoms)
            print "ground formulas: %d" % len(self.gndFormulas)

        self._fitProbabilityConstraints(self.probreqs, self.probabilityFittingInferenceMethod, self.probabilityFittingThreshold, self.probabilityFittingMaxSteps, verbose=True)

    def minimizeGroupWeights(self):
        '''
            minimize the weights of formulas in groups by subtracting from each formula weight the minimum weight in the group
            this results in weights relative to 0, therefore this equivalence transformation can be thought of as a normalization
        '''
        wt = self._weights()
        for group in self.formulaGroups:
            if len(group) == 0:
                continue
            # find minimum absolute weight
            minWeight = wt[group[0]]
            for idxFormula in group:
                if abs(wt[idxFormula]) < abs(minWeight):
                    minWeight = wt[idxFormula]
            # shift all weights in the group
            for idxFormula in group:
                self.formulas[idxFormula].weight -= minWeight

    def combineDB(self, dbfile, verbose=False, groundFormulas=True):
        '''
          DEPRECATED method, use groundMRF instead
          This method serves two purposes:
            a) extend the domain - analogous to method 'combine' only that the constants are taken from a database base file rather than a dictionary
            b) stores the literals in the given db as evidence for future use (e.g. as a training database for weight learning)
          dbfile: name of a database file
          returns the evidence defined in the database (dictionary mapping ground atom strings to truth values)
        '''
        db = Database(self, dbfile)
        domain, evidence = db.domains, db.evidence
        # combine domains
        self.combine(domain, verbose=verbose, groundFormulas=groundFormulas, evidence=evidence)
        return evidence

    def combineDBOverwriteDomains(self, dbfile, verbose=False, groundFormulas=True):
        db = Database(self, dbfile)
        domain, evidence = db.domains, db.evidence
        # combine domains
        self.combineOverwrite(domain, verbose=verbose, groundFormulas=groundFormulas)

        if hasattr(self, 'gibbsSampler'):
            del self.gibbsSampler
        if hasattr(self, 'mcsat'):
            del self.mcsat

        # update domDecls:
        #self.domDecls = []
        #for domain in self.domains.keys():
        #    domDecl = domain + " = {"
        #    firstToken = True
        #    for constant in self.domains[domain]:
        #        if firstToken: firstToken = False
        #        else: domDecl = domDecl + ", "
        #        domDecl = domDecl + constant
        #    domDecl = domDecl + "}"
        #    self.domDecls.append(domDecl)
        # keep evidence in list (ground atom indices)
        self._clearEvidence()
        for gndAtom in evidence:
            idx = self.gndAtoms[gndAtom].idx
            value = evidence[gndAtom]
            self._setEvidence(idx, value)
            # set evidence for other vars in block (if any)
            if idx in self.gndBlockLookup and evidence[gndAtom]:
                block = self.gndBlocks[self.gndBlockLookup[idx]]
                for i in block:
                    if i != idx:
                        self._setEvidence(i, False)
        return evidence

    def combineDBDomain(self, dbfile, requestDomain):
        '''
            combines the MLN with the request domain (given as a dictionary), adding additional objects from the database file
            if the request domain is smaller (so that precisely the number of objects in the database file is reached -- which is assumed to
            contain the (number of) objects the MLN was trained with; Since the MLN is guaranteed to be sound for this domain size,
            it makes sense to instantiate it with this size only). Obviously, a request domain that is larger than the domain
            in the database file is not allowed.
        '''
        # extend domain
        db = Database(self, dbfile)
        domain, evidence = db.domains, db.evidence
        for domName, dbd in domain.iteritems():
            if domName not in self.domains:
                self.domains[domName] = []
            d = self.domains[domName]
            rd = requestDomain.get(domName)
            if rd != None:
                if len(rd) > len(dbd):
                    raise Exception("Domain in database is too small for requested domain '%s'." % domName)
                dbd = dbd[:-len(rd)]
                dbd.extend(rd)
            for constant in dbd:
                if constant not in d:
                    d.append(constant)
        # ground
        self.ground(verbose=False)


    # sets the given predicate as closed-world (for inference)
    # a predicate that is closed-world is assumed to be false for any parameters not explicitly specified otherwise in the evidence
    def setClosedWorldPred(self, predicateName):
        if predicateName not in self.predicates:
            raise Exception("Unknown predicate '%s'" % predicateName)
        self.closedWorldPreds.append(predicateName)

    def _weights(self):
        '''
            returns the weight vector of the MLN as a list
        '''
        return [f.weight for f in self.formulas]

    # evaluate this mln with regard to the given DB
    # useful to determine how good the weights of this mln are, assuming that they were learned with the given db
    def evaluate(self, db_filename):
        raise Exception("This method is no longer supported")
        '''
        global PMB_METHOD
        self.combineDB(db_filename)
        old_pmb_method = PMB_METHOD
        PMB_METHOD = 'excl'
        pll = self.getPLL()
        print "PLL: %f (PL: %f)" % (pll, math.exp(pll))
        PMB_METHOD = 'old'
        pll = self.getPLL()
        print "PLL (old): %f (PL: %f)" % (pll, math.exp(pll))
        PMB_METHOD = old_pmb_method
        if False:
            self._getPllBlocks()
            self._getBlockRelevantGroundFormulas()
            bpll = self.getBPLL()
            print "BPLL: %f (BPL: %f)" % (bpll, math.exp(bpll))
        print "Building worlds..."
        self._getWorlds()
        idxWorld = self._getEvidenceWorldIndex()
        #self.printWorld(self.worlds[idxWorld])
        got_prob = self.worlds[idxWorld]["sum"] / self.partition_function
        print "LL: %.16f" % math.log(got_prob)
        print "Probability of database (idx=%d): %f" % (idxWorld, got_prob)
        worlds = list(self.worlds)
        worlds.sort(key=lambda w:-w["sum"])
        top_prob = worlds[0]["sum"] / self.partition_function
        print "Top observed probability of a possible world:", top_prob
        print "Number of worlds:", len(self.worlds)
        '''

    def learnwts(self, mode=ParameterLearningMeasures.BPLL, initialWts=False, **params):
        '''
        This function is DEPRECATED (superseded by learnWeights)
        learn the weights of the mln given the training data previously loaded with combineDB
          mode: the measure that is used
                   'PLL'    pseudo-log-likelihood based on ground atom probabilities
                   'LL'     log-likelihood (warning: *very* slow and resource-heavy in all but the smallest domains)
                   'BPLL'   pseudo-log-likelihood based on block probabilities
                   'LL_fac' log-likelihood; factors between 0 and 1 are learned instead of regular weights
           initialWts: whether to use the MLN's current weights as the starting point for the optimization
        '''

        modeName = ParameterLearningMeasures.byShortName(mode)
        if modeName in dir(learning) and True: # disable this case to use the old code
            learner = eval("learning.%s(self, **params)" % modeName)
            wt = learner.run(initialWts, **params)
        else:
            learner = learning.Learner(self) # the Learner class contains the old code and the functions marked with "moved to <somewhere>" are subject to removal
            wt = learner.run(mode, initialWts, **params)

        self.setWeights(wt)

        #delete worlds from learning
        if hasattr(self.mrf, "worlds"):
            del self.mrf.worlds

        # fit prior prob. constraints if any available
        if len(self.probreqs) > 0:
            fittingParams = {
                "fittingMethod": self.probabilityFittingInferenceMethod,
                "fittingSteps": self.probabilityFittingMaxSteps,
                "fittingThreshold": self.probabilityFittingThreshold
            }
            fittingParams.update(params)
            print "fitting with params ", fittingParams
            self._fitProbabilityConstraints(self.probreqs, **fittingParams)

        print "\n// formulas"
        for formula in self.formulas:
            print "%f  %s" % (float(eval(str(formula.weight))), strFormula(formula))

    def learnWeights(self, databases, method=ParameterLearningMeasures.BPLL, initialWts=False, **params):
        '''
            databases: list of Database objects or filenames
        '''
        
        # get a list of database objects
        dbs = []        
        for db in databases:
            if type(db) == str:
                db = Database(self, db)
            elif not isinstance(db, Database):
                raise Exception("Got database of unknown type '%s'" % (str(type(db))))
            dbs.append(db)

        if not self.materializedTemplates:
            self.materializeFormulaTemplates(dbs)
        
        # run learner
        if len(dbs) == 1:
            print "grounding MRF..." 
            mrf = self.groundMRF(dbs[0])
            learner = eval("learning.%s(mrf, **params)" % method)
        else:
            learner = learning.MultipleDatabaseLearner(self, method, dbs, **params)
        print "learner: %s" % learner.getName()
        wt = learner.run(initialWts, **params)

        # set new weights
        self.setWeights(wt)

        # delete worlds from learning
        if hasattr(self.mrf, "worlds"):
            del self.mrf.worlds

        # fit prior prob. constraints if any available
        if len(self.probreqs) > 0:
            fittingParams = {
                "fittingMethod": self.probabilityFittingInferenceMethod,
                "fittingSteps": self.probabilityFittingMaxSteps,
                "fittingThreshold": self.probabilityFittingThreshold
            }
            fittingParams.update(params)
            print "fitting with params ", fittingParams
            self._fitProbabilityConstraints(self.probreqs, **fittingParams)

        print "\n// formulas"
        for formula in self.formulas:
            print "%f  %s" % (float(eval(str(formula.weight))), strFormula(formula))

    def setWeights(self, wt):
        if len(wt) != len(self.formulas):
            raise Exception("length of weight vector != number of formulas")
        for i, f in enumerate(self.formulas):
            f.weight = wt[i]

    def write(self, f, mutexInDecls=True):
        '''
            writes the MLN to the given file object
                mutexInDecls: whether to write the definitions for mutual exclusiveness directly to the predicate declaration (instead of extra constraints)
        '''
        if 'learnwts_message' in dir(self):
            f.write("/*\n%s*/\n\n" % self.learnwts_message)
        f.write("// domain declarations\n")
        for d in self.domDecls: f.write("%s\n" % d)
        f.write("\n// predicate declarations\n")
        for predname, args in self.predicates.iteritems():
            excl = self.blocks.get(predname)
            if not mutexInDecls or excl is None:
                f.write("%s(%s)\n" % (predname, ", ".join(args)))
            else:
                f.write("%s(%s)\n" % (predname, ", ".join(map(lambda x: "%s%s" % (x[0], {True:"!", False:""}[x[1]]), zip(args, excl)))))
        if not mutexInDecls:
            f.write("\n// mutual exclusiveness and exhaustiveness\n")
            for predname, excl in self.blocks.iteritems():
                f.write("%s(" % (predname))
                for i in range(len(excl)):
                    if i > 0: f.write(",")
                    f.write("a%d" % i)
                    if excl[i]: f.write("!")
                f.write(")\n")
        f.write("\n// formulas\n")
        for formula in self.formulas:
            if formula.isHard:
                f.write("%s.\n" % strFormula(formula))
            else:
                try:
                    weight = "%-10.6f" % float(eval(str(formula.weight)))
                except:
                    weight = str(formula.weight)
                f.write("%s  %s\n" % (weight, strFormula(formula)))

    def printFormulas(self):
        for f in self.formulas:
            print "%7.3f  %s" % (f.weight, strFormula(f))

    def setRigidPredicate(self, predName):
        self.rigidPredicates.append(predName)


class Database(object):
    def __init__(self, mln, dbfile=None):
        self.mln = mln
        self.domains = {}
        self.evidence = {}
        self.softEvidence = []
        self.includeNonExplicitDomains = True
        if dbfile is not None:
            self.readFile(dbfile)
    
    def readFile(self, dbfile):
        '''
            reads a database file containing literals and/or domains
            returns (domains, evidence) where domains is dictionary mapping domain names to lists of constants defined in the database
            and evidence is a dictionary mapping ground atom strings to truth values
        '''
        domains = self.domains
        # read file
        f = file(dbfile, "r")
        db = f.read()
        f.close()
        db = stripComments(db)
        # expand domains with db constants and save evidence
        evidence = self.evidence
        for l in db.split("\n"):
            l = l.strip()
            if l == "":
                continue
            # soft evidence
            if l[0] in "0123456789":
                s = l.find(" ")
                gndAtom = l[s + 1:].replace(" ", "")
                d = {"expr": gndAtom, "p": float(l[:s])}
                if self.getSoftEvidence(gndAtom) == None:
                    self.softEvidence.append(d)
                else:
                    raise Exception("Duplicate soft evidence for '%s'" % gndAtom)
                predName, constants = parsePredicate(gndAtom) # TODO Should we allow soft evidence on non-atoms here? (This assumes atoms)
                domNames = self.mln.predicates[predName]
            # domain declaration
            elif "{" in l:
                domName, constants = parseDomDecl(l)
                domNames = [domName for c in constants]
            # literal
            else:
                if l[0] == "?":
                    raise Exception("Unknown literals not supported (%s)" % l) # this is an Alchemy feature
                isTrue, predName, constants = parseLiteral(l)
                domNames = self.mln.predicates[predName]
                # save evidence
                evidence["%s(%s)" % (predName, ",".join(constants))] = isTrue

            # expand domains
            if len(domNames) != len(constants):
                raise Exception("Ground atom %s in database %s has wrong number of parameters" % (l, dbfile))

            if "{" in l or self.includeNonExplicitDomains:
                for i in range(len(constants)):
                    if domNames[i] not in domains:
                        domains[domNames[i]] = []
                    d = domains[domNames[i]]
                    if constants[i] not in d:
                        d.append(constants[i])
                        
    def getSoftEvidence(self, gndAtom):
        '''
            gets the soft evidence value (probability) for a given ground atom (or complex formula)
            returns None if there is no such value
        '''
        s = strFormula(gndAtom)
        for se in self.softEvidence: # TODO optimize
            if se["expr"] == s:
                return se["p"]
        return None
    
    def getPseudoMRF(self):
        '''
            gets a pseudo-MRF object that can be used to generate formula groundings
            or count true groundings based on the domain in this database
        '''       
        return Database.PseudoMRF(self)

    class PseudoMRF(object):
        '''
            can be used in order to use only a Database object to ground formulas
            (without instantiating an MRF) and determine the truth of these ground
            formulas by partly replicating the interface of an MRF object
        '''
        
        def __init__(self, db):
            self.mln = db.mln
            self.domains = mergeDomains(self.mln.domains, db.domains)
            self.gndAtoms = Database.PseudoMRF.GroundAtomGen()
            self.evidence = Database.PseudoMRF.WorldValues(db)

        class GroundAtomGen(object):
            def __getitem__(self, gndAtomName):
                return Database.PseudoMRF.TextGroundAtom(gndAtomName)
        
        class TextGroundAtom(object):
            def __init__(self, name):
                self.name = self.idx = name
        
            def isTrue(self, world_values):
                return world_values[self.name]
        
            def __str__(self):
                return self.name
            
            def simplify(self, mrf):
                return self
            
        class WorldValues(object):
            def __init__(self, db):
                self.db = db
            
            def __getitem__(self, gndAtomString):
                return self.db.evidence.get(gndAtomString, False)
            
        def iterGroundings(self, formula):
            for t in formula.iterGroundings(self):
                yield t
        
        def isTrue(self, gndFormula):
            return gndFormula.isTrue(self.evidence)
        
        def countTrueGroundings(self, formula):
            numTotal = 0
            numTrue = 0
            for gf, refGndAtoms in self.iterGroundings(formula):
                numTotal += 1
                if gf.isTrue(self.evidence):
                    numTrue += 1
            return (numTrue, numTotal)

class MRF(object):
    '''
    represents a ground Markov random field

    members:
        gndAtoms:
            maps a string representation of a ground atom to a FOL.GroundAtom object
        gndAtomsByIdx:
            dict: ground atom index -> FOL.GroundAtom object
        gndBlocks:
            dict: block name -> list of ground atom indices
        gndBlockLookup:
            dict: ground atom index -> block name
        gndAtomOccurrencesInGFs
            dict: ground atom index -> ground formula
        gndFormulas:
            list of grounded formula objects
        pllBlocks:
            list of *all* the ground blocks, including trivial blocks consisting of a single ground atom
            each element is a tuple (ground atom index, list of ground atom indices) where one element is always None

    '''

    def __init__(self, mln, db, verbose=False, simplify=False):
        '''
        db: database filename (.db) or a Database object
        '''
        self.mln = mln
        self.evidence = {}
        self.evidenceBackup = {}
        self.softEvidence = list(mln.posteriorProbReqs) # constraints on posterior probabilities are nothing but soft evidence and can be handled in exactly the same way
        self.simplify = simplify
        
        if type(db) == str:
            db = Database(self.mln, db)
        elif isinstance(db, Database):
            pass
        else:
            raise Exception("Not a valid database argument (type %s)" % (str(type(db))))

        # get combined domain
        self.domains = mergeDomains(mln.domains, db.domains)
        #print "MLN domains: ", self.mln.domains
        #print "MRF domains: ", self.domains

        # materialize MLN formulas
        if not self.mln.materializedTemplates:
            self.mln._materializeFormulaTemplates(verbose)
        self.formulas = list(mln.formulas) # copy the list of formulas, because we may change or extend it

        # ground atoms
        self._generateGroundAtoms()

        # set evidence
        self.setEvidence(db.evidence)
        self.softEvidence = db.softEvidence
        
        # ground formulas after setting the evidence to apply formula simplification
        self._createFormulaGroundings(verbose=verbose)


    def __getattr__(self, attr):
        # forward attribute access to the MLN (yes, it's a hack)
        return self.mln.__getattribute__(attr)

    def _generateGroundAtoms(self):
        self.gndAtoms = {}
        self.gndBlockLookup = {}
        self.gndBlocks = {}

        # create ground atoms
        atoms = []
        for predName, domNames in self.mln.predicates.iteritems():
            self._groundAtoms([], predName, domNames)

        # create reverse lookup
        self.gndAtomsByIdx = dict([(g.idx, g) for g in self.gndAtoms.values()])

        # for backward compatibility with older code, transfer the members to the MLN
        self.mln.gndAtoms = self.gndAtoms
        self.mln.gndBlockLookup = self.gndBlockLookup
        self.mln.gndBlocks = self.gndBlocks

    def _groundAtoms(self, cur, predName, domNames):
        # if there are no more parameters to ground, we're done
        if domNames == []:
            idxAtom = len(self.gndAtoms)

            # add atom
            gndAtom = "%s(%s)" % (predName, ",".join(cur))
            #print gndAtom
            self.gndAtoms[gndAtom] = FOL.GroundAtom(predName, cur, idxAtom)

            # check if atom is in block
            mutex = self.mln.blocks.get(predName)
            if mutex != None:
                blockName = "%s_" % predName
                for i, v in enumerate(mutex):
                    if v == False:
                        blockName += cur[i]
                if not blockName in self.gndBlocks:
                    self.gndBlocks[blockName] = []
                self.gndBlocks[blockName].append(idxAtom)
                self.gndBlockLookup[idxAtom] = blockName

            return

        # create ground atoms for each way of grounding the first of the remaining variables whose domains are given in domNames
        dom = self.domains.get(domNames[0])
        if dom is None or len(dom) == 0:
            raise Exception("Domain '%s' is empty!" % domNames[0])
        for value in dom:
            self._groundAtoms(cur + [value], predName, domNames[1:])

    def _getPredGroundings(self, predName):
        '''
            gets the names of all ground atoms of the given predicate
        '''
        # get the string represenation of the first grounding of the predicate
        domNames = self.predicates[predName]
        params = []
        for domName in domNames:
            params.append(self.domains[domName][0])
        gndAtom = "%s(%s)" % (predName, ",".join(params))
        # get all subsequent groundings (by index) until the predicate name changes
        groundings = []
        idx = self.gndAtoms[gndAtom].idx
        while True:
            groundings.append(gndAtom)
            idx += 1
            if idx >= len(self.gndAtoms):
                break
            gndAtom = str(self.gndAtomsByIdx[idx])
            if parsePredicate(gndAtom)[0] != predName:
                break
        return groundings

    def _getPredGroundingsAsIndices(self, predName):
        '''
            get a list of all the indices of all groundings of the given predicate
        '''
        # get the index of the first grounding of the predicate and the number of groundings
        domNames = self.predicates[predName]
        params = []
        numGroundings = 1
        for domName in domNames:
            params.append(self.domains[domName][0])
            numGroundings *= len(self.domains[domName])
        gndAtom = "%s(%s)" % (predName, ",".join(params))
        idxFirst = self.gndAtoms[gndAtom].idx
        return range(idxFirst, idxFirst + numGroundings)

    def _createFormulaGroundings(self, verbose=False):
        self.gndFormulas = []
        self.gndAtomOccurrencesInGFs = [[] for i in range(len(self.gndAtoms))]
        
        # generate all groundings
        if verbose: print "grounding formulas..."
        for idxFormula, formula in enumerate(self.formulas):
            if verbose: print "  %s" % strFormula(formula)
            for gndFormula, referencedGndAtoms in formula.iterGroundings(self, self.simplify):
                gndFormula.isHard = formula.isHard
                gndFormula.weight = formula.weight
                if isinstance(gndFormula, FOL.TrueFalse):
                    continue
                self._addGroundFormula(gndFormula, idxFormula, referencedGndAtoms)

        # materialize all formula weights
        max_weight = 0
        for f in self.formulas:
            if f.weight is not None:
                if hasattr(f, "complexWeight"): # TODO check if complexWeight is ever used anywhere (old AMLN learning?)
                    f.weight = f.complexWeight
                w = str(f.weight)
                f.complexWeight = w
                while "$" in w:
                    try:
                        w, numReplacements = re.subn(r'\$\w+', self._substVar, w)
                    except:
                        sys.stderr.write("Error substituting variable references in '%s'\n" % w)
                        raise
                    if numReplacements == 0:
                        raise Exception("Undefined variable(s) referenced in '%s'" % w)
                w = re.sub(r'domSize\((.*?)\)', r'self.domSize("\1")', w)
                try:
                    f.weight = eval(w)
                except:
                    sys.stderr.write("Evaluation error while trying to compute '%s'\n" % w)
                    raise
                max_weight = max(abs(f.weight), max_weight)

        # set weights of hard formulas
        hard_weight = 20 + max_weight
        if verbose: print "setting %d hard weights to %f" % (len(self.hard_formulas), hard_weight)
        for f in self.hard_formulas:
            if verbose: print "  ", strFormula(f)
            f.weight = hard_weight
        if verbose:
            pass
            #self.printGroundFormulas()
            #self.mln.printFormulas()

        # for backward compatibility with older code, transfer the members to the MLN
        self.mln.gndFormulas = self.gndFormulas
        self.mln.gndAtomOccurrencesInGFs = self.gndAtomOccurrencesInGFs

    def _addGroundFormula(self, gndFormula, idxFormula, idxGndAtoms = None):
        '''
            adds a ground formula to the model

            idxGndAtoms: indices of the ground atoms that are referenced by the formula (precomputed); If not given (None), will be determined automatically
        '''
        gndFormula.idxFormula = idxFormula
        self.gndFormulas.append(gndFormula)
        # update ground atom references
        if idxGndAtoms is None:
            idxGndAtoms = gndFormula.idxGroundAtoms()
        for idxGA in idxGndAtoms:
            self.gndAtomOccurrencesInGFs[idxGA].append(gndFormula)

    def removeGroundFormulaData(self):
        '''
        remove data on ground formulas to save space (e.g. because the necessary statistics were already collected and the actual formulas
        are no longer needed)
        '''
        del self.gndFormulas
        del self.gndAtomOccurrencesInGFs
        del self.mln.gndFormulas
        del self.mln.gndAtomOccurrencesInGFs
        if hasattr(self, "blockRelevantGFs"):
            del self.blockRelevantGFs

    def _addFormula(self, formula, weight):
        idxFormula = len(self.formulas)
        formula.weight = weight
        self.formulas.append(formula)
        return idxFormula

    def _setEvidence(self, idxGndAtom, value):
        self.evidence[idxGndAtom] = value

    def _setTemporaryEvidence(self, idxGndAtom, value):
        self.evidenceBackup[idxGndAtom] = self._getEvidence(idxGndAtom, closedWorld=False)
        self._setEvidence(idxGndAtom, value)

    def _getEvidence(self, idxGndAtom, closedWorld=True):
        '''
            gets the evidence truth value for the given ground atom or None if no evidence was given
            if closedWorld is True, False instead of None is returned
        '''
        v = self.evidence[idxGndAtom]
        if closedWorld and v == None:
            return False
        return v

    def _clearEvidence(self):
        self.evidence = [None for i in range(len(self.gndAtoms))]

    def getEvidenceDatabase(self):
        '''
            returns, from the current evidence list, a dictionary that maps ground atom names to truth values
        '''
        d = {}
        for idxGA, tv in enumerate(self.evidence):
            if tv != None:
                d[str(self.gndAtomsByIdx[idxGA])] = tv
        return d

    def printEvidence(self):
        for idxGA, value in enumerate(self.evidence):
            print "%s = %s" % (str(self.gndAtomsByIdx[idxGA]), str(value))

    def _getEvidenceTruthDegreeCW(self, gndAtom, worldValues):
        '''
            gets (soft or hard) evidence as a degree of belief from 0 to 1, making the closed world assumption,
            soft evidence has precedence over hard evidence
        '''
        se = self._getSoftEvidence(gndAtom)
        if se is not None:
            return se if (True == worldValues[gndAtom.idx] or None == worldValues[gndAtom.idx]) else 1.0 - se # TODO allSoft currently unsupported
        return 1.0 if worldValues[gndAtom.idx] else 0.0

    def _getEvidenceDegree(self, gndAtom):
        '''
            gets (soft or hard) evidence as a degree of belief from 0 to 1 or None if no evidence is given,
            soft evidence takes precedence over hard evidence
        '''
        se = self._getSoftEvidence(gndAtom)
        if se is not None:
            return se
        he = self._getEvidence(gndAtom.idx, False)
        if he is None:
            return None
        return 1.0 if he == True else 0.0


    def _getSoftEvidence(self, gndAtom):
        '''
            gets the soft evidence value (probability) for a given ground atom (or complex formula)
            returns None if there is no such value
        '''
        s = strFormula(gndAtom)
        for se in self.softEvidence: # TODO optimize
            if se["expr"] == s:
                #print "worldValues[gndAtom.idx]", worldValues[gndAtom.idx]
                return se["p"]
        return None

    def _setSoftEvidence(self, gndAtom, value):
        s = strFormula(gndAtom)
        for se in self.softEvidence:
            if se["expr"] == s:
                se["p"] = value
                return

    def getTruthDegreeGivenSoftEvidence(self, gf, worldValues):
        cnf = gf.toCNF()
        prod = 1.0
        if isinstance(cnf, FOL.Conjunction):
            for disj in cnf.children:
                prod *= self._noisyOr(worldValues, disj)
        else:
            prod *= self._noisyOr(worldValues, cnf)
        return prod

    def _noisyOr(mln, worldValues, disj):
        if isinstance(disj, FOL.GroundLit):
            lits = [disj]
        elif isinstance(disj, FOL.TrueFalse):
            return 1.0 if disj.isTrue(worldValues) else 0.0
        else:
            lits = disj.children
        prod = 1.0
        for lit in lits:
            p = mln._getEvidenceTruthDegreeCW(lit.gndAtom, worldValues)
            factor = p if not lit.negated else 1.0 - p
            prod *= 1.0 - factor
        return 1.0 - prod

    def _removeTemporaryEvidence(self):
        for idx, value in self.evidenceBackup.iteritems():
            self._setEvidence(idx, value)
        self.evidenceBackup.clear()

    def _isTrueGndFormulaGivenEvidence(self, gf):
        return gf.isTrue(self.evidence)

    def setEvidence(self, evidence, clear=True):
        '''
          sets the evidence, which is to be given as a dictionary that maps ground atom strings to their truth values.
          Any previous evidence is cleared.
          The closed-world assumption is applied to any predicates for which it was declared.
        '''
        if clear is True:
            self._clearEvidence()
        for gndAtom, value in evidence.iteritems():
            idx = self.gndAtoms[gndAtom].idx
            self._setEvidence(idx, value)
            # If the value is true, set evidence for other vars in block (if any)
            if value == True and idx in self.gndBlockLookup:
                block = self.gndBlocks[self.gndBlockLookup[idx]]
                for i in block:
                    if i != idx:
                        self._setEvidence(i, False)
        # handle closed-world predicates: Set all their instances that aren't yet known to false
        for pred in self.closedWorldPreds:
            for idxGA in self._getPredGroundingsAsIndices(pred):
                if self._getEvidence(idxGA, False) == None:
                    self._setEvidence(idxGA, False)

    # creates an array self.pllBlocks that contains tuples (idxGA, block);
    # one of the two tuple items is always None depending on whether the ground atom is in a block or not;
    def _getPllBlocks(self):
        if hasattr(self, "pllBlocks"):
            return
        handledBlockNames = []
        self.pllBlocks = []
        for idxGA in range(len(self.gndAtoms)):
            if idxGA in self.gndBlockLookup:
                blockName = self.gndBlockLookup[idxGA]
                if blockName in handledBlockNames:
                    continue
                self.pllBlocks.append((None, self.gndBlocks[blockName]))
                handledBlockNames.append(blockName)
            else:
                self.pllBlocks.append((idxGA, None))

    # computes the set of relevant ground formulas for each block
    def _getBlockRelevantGroundFormulas(self):
        mln = self
        self.blockRelevantGFs = [set() for i in range(len(mln.pllBlocks))]
        for idxBlock, (idxGA, block) in enumerate(mln.pllBlocks):
            if block != None:
                for idxGA in block:
                    for gf in self.gndAtomOccurrencesInGFs[idxGA]:
                        self.blockRelevantGFs[idxBlock].add(gf)
            else:
                self.blockRelevantGFs[idxBlock] = self.gndAtomOccurrencesInGFs[idxGA]

    def _getBlockTrueone(self, block):
        idxGATrueone = -1
        for i in block:
            if self._getEvidence(i):
                if idxGATrueone != -1: raise Exception("More than one true ground atom in block %s!" % blockname)
                idxGATrueone = i
                break
        if idxGATrueone == -1: raise Exception("No true gnd atom in block %s!" % self._strBlock(block))
        return idxGATrueone

    def _getBlockName(self, idxGA):
        return self.gndBlockLookup[idxGA]

    def _strBlock(self, block):
        return "{%s}" % (",".join(map(lambda x: str(self.gndAtomsByIdx[x]), block)))
    
    def _strBlockVar(self, varIdx):
        (idxGA, block) = self.pllBlocks[varIdx]
        if block is None:
            return str(self.gndAtomsByIdx[idxGA])
        else:
            return self._strBlock(block)

    def _getBlockExpsums(self, block, wt, world_values, idxGATrueone=None, relevantGroundFormulas=None):
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

    def _getAtomExpsums(self, idxGndAtom, wt, world_values, relevantGroundFormulas=None):
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

    def _getAtom2BlockIdx(self):
        self.atom2BlockIdx = {}
        for idxBlock, (idxGA, block) in enumerate(self.pllBlocks):
            if block != None:
                for idxGA in block:
                    self.atom2BlockIdx[idxGA] = idxBlock
            else:
                self.atom2BlockIdx[idxGA] = idxBlock

    def __createPossibleWorlds(self, values, idx, code, bit):
        if idx == len(self.gndAtoms):
            if code in self.worldCode2Index:
                raise Exception("Too many possible worlds") # this actually never happens because Python can handle "infinitely" long ints
            self.worldCode2Index[code] = len(self.worlds)
            self.worlds.append({"values": values})
            if len(self.worlds) % 1000 == 0:
                #print "%d\r" % len(self.worlds)
                pass
            return
        # values that can be set for the truth value of the ground atom with index idx
        possible_settings = [True, False]
        # check for rigid predicates: for rigid predicates, we consider both values only if the evidence value is
        # unknown, otherwise we use the evidence value
        restricted = False
        if True:
            gndAtom = self.gndAtomsByIdx[idx]
            if gndAtom.predName in self.rigidPredicates:
                v = self._getEvidence(idx, False)
                if v != None:
                    possible_settings = [v]
                    restricted = True
        # check if setting the truth value for idx is critical for a block (which is the case when idx is the highest index in a block)
        if not restricted and idx in self.gndBlockLookup and POSSWORLDS_BLOCKING:
            block = self.gndBlocks[self.gndBlockLookup[idx]]
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
            self.__createPossibleWorlds(values + [x], idx + 1, code + (bit if x else 0), bit << 1)

    def _createPossibleWorlds(self):
        self.worldCode2Index = {}
        self.worlds = []
        self.__createPossibleWorlds([], 0, 0, 1)

    def getWorld(self, worldNo):
        '''
            gets the possible world with the given one-based world number
        '''
        self._getWorlds()
        return self.worlds[worldNo - 1]

    def _getWorlds(self):
        '''
            creates the set of possible worlds and calculates for each world all the necessary values
        '''
        if not hasattr(self, "worlds"):
            self._createPossibleWorlds()
            if self.parameterType == 'weights':
                self._calculateWorldValues()
            elif self.parameterType == 'probs':
                self._calculateWorldValues_prob()

    def _calculateWorldValues(self, wts=None):
        if wts == None:
            wts = self._weights()
        total = 0
        for worldIndex, world in enumerate(self.worlds):
            weights = []
            for gndFormula in self.gndFormulas:
                if self._isTrue(gndFormula, world["values"]):
                    weights.append(wts[gndFormula.idxFormula])
            exp_sum = exp(sum(weights))
            if self.learnWtsMode != 'LL_ISE' or self.allSoft == True or worldIndex != self.idxTrainingDB:
                total += exp_sum
            world["sum"] = exp_sum
            world["weights"] = weights
        self.partition_function = total

    def _calculateWorldExpSum(self, world, wts=None):
        if wts is None:
            wts = self._weights()
        sum = 0
        for gndFormula in self.gndFormulas:
            if self._isTrue(gndFormula, world):
                sum += wts[gndFormula.idxFormula]
        return math.exp(sum)

    def _countNumTrueGroundingsInWorld(self, idxFormula, world):
        numTrue = 0
        for gf in self.gndFormulas:
            if gf.idxFormula == idxFormula:
                if self._isTrue(gf, world["values"]):
                    numTrue += 1
        return numTrue

    # counts the number of true groundings in each possible world and outputs a report
    # with (# of true groundings, # of worlds with that number of true groundings)
    def countWorldsWhereFormulaIsTrue(self, idxFormula):
        counts = {}
        for world in self.worlds:
            numTrue = self._countNumTrueGroundingsInWorld(idxFormula, world)
            old_cnt = counts.get(numTrue, 0)
            counts[numTrue] = old_cnt + 1
        print counts

    # returns array of array of int a with a[i][j] = number of true groundings of j-th formula in i-th world
    def countTrueGroundingsForEachWorld(self, appendToWorlds=False):
        all = []
        self._getWorlds()
        for i, world in enumerate(self.worlds):
            counts = self.countTrueGroundingsInWorld(world["values"])
            all.append(counts)
            if appendToWorlds:
                world["counts"] = counts
        return all

    def countTrueGroundingsInWorld(self, world):
        '''
            computes the number of true groundings of each formula for the given world
            returns a vector v, where v[i] = number of groundings of the i-th MLN formula
        '''
        import numpy
        formulaCounts = numpy.zeros(len(self.mln.formulas), numpy.float64)                
        for gndFormula in self.mrf.mln.gndFormulas:
            if self._isTrue(gndFormula, world):
                formulaCounts[gndFormula.idxFormula] += 1
        return formulaCounts

    def _calculateWorldValues2(self, wts=None):
        if wts == None:
            wts = self._weights()
        total = 0
        for world in self.worlds:
            prob = 1.0
            for gndFormula in self.gndFormulas:
                if self._isTrue(gndFormula, world["values"]):
                    prob *= wts[gndFormula.idxFormula]
                else:
                    prob *= (1 - wts[gndFormula.idxFormula])
            world["prod"] = prob
            total += prob
        self.partition_function = total

    def _calculateWorldValues_prob(self, wts=None):
        if wts == None:
            wts = self._weights()
        total = 0
        for world in self.worlds:
            prod = 1.0
            for gndFormula in self.gndFormulas:
                if self._isTrue(gndFormula, world["values"]):
                    prod *= wts[gndFormula.idxFormula]
            world["prod"] = prod
            total += prod
        self.partition_function = total

    def _toCNF(self, allPositive=False):
        '''
            converts all ground formulas to CNF and also makes changes to the
            MLN's set of formulas, such that the correspondence between groundings
            and formulas still holds
        '''
        self.gndFormulas, self.formulas = toCNF(self.gndFormulas, self.formulas)

    def _isTrue(self, gndFormula, world_values):
        return gndFormula.isTrue(world_values)

    def printGroundFormulas(self, weight_transform=lambda x: x):
        for gf in self.gndFormulas:
            print "%7.3f  %s" % (weight_transform(self.formulas[gf.idxFormula].weight), strFormula(gf))

    def printGroundAtoms(self):
        l = self.gndAtoms.keys()
        l.sort()
        for ga in l:
            print ga

    def strGroundAtom(self, idx):
        return str(self.gndAtomsByIdx[idx])

    def printState(self, world_values, showIndices=False):
        for idxBlock, (idxGA, block) in enumerate(self.pllBlocks):
            if idxGA != None:
                if showIndices: print "%-5d" % idxGA,
                print "%s=%s" % (str(self.gndAtomsByIdx[idxGA]), str(world_values[idxGA]))
            else:
                trueone = -1
                for i in block:
                    if world_values[i]:
                        trueone = i
                        break
                print "%s=%s" % (self._strBlock(block), str(self.gndAtomsByIdx[trueone]))

    # prints relevant data (including the entire state) for the given world (list of truth values) on a single line
    # for details see printWorlds
    def printWorld(self, world, mode=1, format=1):
        if "weights" in world and world["weights"] == []:
            world["weights"] = [0.0]
        literals = []
        for idx in range(len(self.gndAtoms)):
            if idx in self.gndBlockLookup: # process all gnd atoms in blocks in one go and only print the one that's true
                block = self.gndBlocks[self.gndBlockLookup[idx]]
                if idx == min(block): # process each block only once
                    maxlen = 0
                    gndAtom = None
                    for i in block:
                        maxlen = max(maxlen, len(str(self.gndAtomsByIdx[i])))
                        if world["values"][i]:
                            gndAtom = self.gndAtomsByIdx[i]
                    literal = "%-*s" % (maxlen, str(gndAtom))
                else:
                    continue
            else:
                gndAtom = str(self.gndAtomsByIdx[idx])
                value = world["values"][idx]
                literal = {True: " ", False:"!"}[value] + gndAtom
            literals.append(literal)
        if mode == 1:
            prob = world["sum"] / self.partition_function
            weights = "<- " + " ".join(map(lambda s: "%.1f" % s, world["weights"]))
            if format == 1: print "%6.2f%%  %s  %e <- %.2f %s" % (100 * prob, " ".join(literals), world["sum"], sum(world["weights"]), weights)
            elif format == 2: print "%6.2f%%  %s  %s" % (100 * prob, " ".join(literals), str(world["counts"]))
            #print "Pr=%.2f  %s  %15.0f" % (prob, " ".join(literals), world["sum"])
        elif mode == 2:
             print "%6.2f%%  %s  %.2f" % (100 * world["prod"] / self.partition_function, " ".join(literals), world["prod"])
        elif mode == 3:
             print " ".join(literals)

    # prints all the possible worlds implicitly defined by the set of constants with which the MLN was combined
    # Must call combine or combineDB beforehand if the MLN does not define at least one constant for every type/domain
    # The list contains for each world its 1-based index, its probability, the (conjunction of) literals, the exponentiated
    # sum of weights, the sum of weights and the individual weights that applied
    def printWorlds(self, sort=False, mode=1, format=1):
        self._getWorlds()
        if sort:
            worlds = list(self.worlds)
            worlds.sort(key=lambda x:-x["sum"])
        else:
            worlds = self.worlds
        print
        k = 1
        for world in worlds:
            print "%*d " % (int(math.ceil(math.log(len(self.worlds)) / math.log(10))), k),
            self.printWorld(world, mode=mode, format=format)
            k += 1
        print "Z = %f" % self.partition_function

    # prints the worlds where the given formula (condition) is true (otherwise same as printWorlds)
    def printWorldsFiltered(self, condition, mode=1, format=1):
        condition = FOL.parseFormula(condition).ground(self, {})
        self._getWorlds()
        k = 1
        for world in self.worlds:
            if condition.isTrue(world["values"]):
                print "%*d " % (int(math.ceil(math.log(len(self.worlds)) / math.log(10))), k),
                self.printWorld(world, mode=mode, format=format)
                k += 1

    # prints the num worlds with the highest probability
    def printTopWorlds(self, num=10, mode=1, format=1):
        self._getWorlds()
        worlds = list(self.worlds)
        worlds.sort(key=lambda w:-w["sum"])
        for i in range(min(num, len(worlds))):
            self.printWorld(worlds[i], mode=mode, format=format)

    # prints, for the given world, the probability, the literals, the sum of weights, plus for each ground formula the truth value on a separate line
    def printWorldDetails(self, world):
        self.printWorld(world)
        for gf in self.gndFormulas:
            isTrue = gf.isTrue(world["values"])
            print "  %-5s  %f  %s" % (str(isTrue), self.formulas[gf.idxFormula].weight, strFormula(gf))

    def printFormulaProbabilities(self):
        self._getWorlds()
        sums = [0.0 for i in range(len(self.formulas))]
        totals = [0.0 for i in range(len(self.formulas))]
        for world in self.worlds:
            for gf in self.gndFormulas:
                if self._isTrue(gf, world["values"]):
                    sums[gf.idxFormula] += world["sum"] / self.partition_function
                totals[gf.idxFormula] += world["sum"] / self.partition_function
        for i, formula in enumerate(self.formulas):
            print "%f %s" % (sums[i] / totals[i], str(formula))

    def printExpectedNumberOfGroundings(self):
        '''
            prints the expected number of true groundings of each formula
        '''
        self._getWorlds()
        counts = [0.0 for i in range(len(self.formulas))]
        for world in self.worlds:
            for gf in self.gndFormulas:
                if self._isTrue(gf, world["values"]):
                    counts[gf.idxFormula] += world["sum"] / self.partition_function
        #print counts
        for i, formula in enumerate(self.formulas):
            print "%f %s" % (counts[i], str(formula))

    def _fitProbabilityConstraints(self, probConstraints, fittingMethod=InferenceMethods.Exact, fittingThreshold=1.0e-3, fittingSteps=20, fittingMCSATSteps=5000, fittingParams=None, given=None, queries=None, verbose=True, maxThreshold=None, greedy=False, probabilityFittingResultFileName=None, **args):
        '''
            applies the given probability constraints (if any), dynamically modifying weights of the underlying MLN by applying iterative proportional fitting

            probConstraints: list of constraints
            inferenceMethod: one of the inference methods defined in InferenceMethods
            inferenceParams: parameters to pass on to the inference method
            given: if not None, fit parameters of posterior (given the evidence) rather than prior
            queries: queries to compute along the way, results for which will be returned
            threshold:
                when maximum absolute difference between desired and actual probability drops below this value, then stop (convergence)
            maxThreshold:
                if not None, then convergence is relaxed, and we stop when the *mean* absolute difference between desired and
                actual probability drops below "threshold" *and* the maximum is below "maxThreshold"
        '''
        inferenceMethod = fittingMethod
        threshold = fittingThreshold
        maxSteps = fittingSteps
        if fittingParams is None:
            fittingParams = {}
        inferenceParams = fittingParams
        inferenceParams["doProbabilityFitting"] = False # avoid recursive fitting calls when calling embedded inference method
        if given == None:
            given = ""
        if queries is None:
            queries = []
        if inferenceParams is None:
            inferenceParams = {}
        if len(probConstraints) == 0:
            if len(queries) > 0:
                pass # TODO !!!! because this is called from inferIPFPM, should perform inference anyhow
            return
        if verbose:
            print "applying probability fitting...(max. deviation threshold:", fittingThreshold, ")"
        t_start = time.time()

        # determine relevant formulas
        for req in probConstraints:
            # if we don't yet have a ground formula to fit, create one
            if not "gndFormula" in req:
                # if we don't yet have a formula to use, search for one that matches the expression to fit
                if not "idxFormula" in req:
                    idxFormula = None
                    for idxF, formula in enumerate(self.formulas):
                        #print strFormula(formula), req["expr"]
                        if strFormula(formula).replace(" ", "") == req["expr"]:
                            idxFormula = idxF
                            break
                    if idxFormula is None:
                        raise Exception("Probability constraint on '%s' cannot be applied because the formula is not part of the MLN!" % req["expr"])
                    req["idxFormula"] = idxFormula
                # instantiate a ground formula
                formula = self.formulas[req["idxFormula"]]
                vars = formula.getVariables(self)
                groundVars = {}
                for varName, domName in vars.iteritems(): # instantiate vars arbitrarily (just use first element of domain)
                    groundVars[varName] = self.domains[domName][0]
                gndFormula = formula.ground(self, groundVars)
                gotit = True
                req["gndExpr"] = str(gndFormula)
                req["gndFormula"] = gndFormula

        # iterative fitting algorithm
        step = 1 # fitting round
        fittingStep = 1 # actual IPFP iteration
        #print "probConstraints", probConstraints, "queries", queries
        what = [r["gndFormula"] for r in probConstraints] + queries
        done = False
        while step <= maxSteps and not done:
            # calculate probabilities of the constrained formulas (ground formula)
            if inferenceMethod == InferenceMethods.Exact:
                if not hasattr(self, "worlds"):
                    self._getWorlds()
                else:
                    self._calculateWorldValues()
                results = self.inferExact(what, given=given, verbose=False, **inferenceParams)
            elif inferenceMethod == InferenceMethods.EnumerationAsk:
                results = self.inferEnumerationAsk(what, given=given, verbose=False, **inferenceParams)
            #elif inferenceMethod == InferenceMethods.ExactLazy:
            #    results = self.inferExactLazy(what, given=given, verbose=False, **inferenceParams)
            elif inferenceMethod == InferenceMethods.MCSAT:
                results = self.inferMCSAT(what, given=given, verbose=False, maxSteps = fittingMCSATSteps, **inferenceParams)
            else:
                raise Exception("Requested inference method (%s) not supported by probability constraint fitting" % InferenceMethods.getName(inferenceMethod))
            if type(results) != list:
                results = [results]
            # compute deviations
            diffs = [abs(r["p"] - results[i]) for (i, r) in enumerate(probConstraints)]
            maxdiff = max(diffs)
            meandiff = sum(diffs) / len(diffs)
            # are we done?
            done = maxdiff <= threshold
            if not done and maxThreshold is not None: # relaxed convergence criterion
                done = (meandiff <= threshold) and (maxdiff <= maxThreshold)
            if done:
                if verbose: print "  [done] dev max: %f mean: %f" % (maxdiff, meandiff)
                break
            # select constraint to fit
            if greedy:
                idxConstraint = diffs.index(maxdiff)
                strStep = "%d;%d" % (step, fittingStep)
            else:
                idxConstraint = (fittingStep - 1) % len(probConstraints)
                strStep = "%d;%d/%d" % (step, idxConstraint + 1, len(probConstraints))
            req = probConstraints[idxConstraint]
            # get the scaling factor and apply it
            formula = self.formulas[req["idxFormula"]]
            p = results[idxConstraint]
            #print "p", p, "results", results, "idxConstraint", idxConstraint
            pnew = req["p"]
            precision = 1e-3
            if p == 0.0: p = precision
            if p == 1.0: p = 1 - precision
            f = pnew * (1 - p) / p / (1 - pnew)
            old_weight = formula.weight
            formula.weight += float(logx(f)) #make sure to set the weight to a native float and not an mpmath value
            diff = diffs[idxConstraint]
            # print status
            if verbose: print "  [%s] p=%f vs. %f (diff = %f), weight %s: %f -> %f, dev max %f mean %f, elapsed: %.3fs" % (strStep, p, pnew, diff, strFormula(formula), old_weight, formula.weight, maxdiff, meandiff, time.time() - t_start)
            if fittingStep % len(probConstraints) == 0:
                step += 1
            fittingStep += 1

        #write resulting mln:
        if probabilityFittingResultFileName != None:
            mlnFile = file(probabilityFittingResultFileName, "w")
            self.mln.write(mlnFile)
            mlnFile.close()
            print "written MLN with probability constraints to:", probabilityFittingResultFileName

        return (results[len(probConstraints):], {"steps": min(step, maxSteps), "fittingSteps": fittingStep, "maxdiff": maxdiff, "meandiff": meandiff, "time": time.time() - t_start})

    # infer a probability P(F1 | F2) where F1 and F2 are formulas - using the default inference method specified for this MLN
    #   what: a formula, e.g. "foo(A,B)", or a list of formulas
    #   given: either
    #            * another formula, e.g. "bar(A,B) ^ !baz(A,B)"
    #              Note: it can be an arbitrary formula only for exact inference, otherwise it must be a conjunction
    #              This will overwrite any evidence previously set in the MLN
    #            * None if the evidence currently set in the MLN is to be used
    #   verbose: whether to print the results
    #   args: any additional arguments to pass on to the actual inference method
    def infer(self, what, given=None, verbose=True, **args):
        # call actual inference method
        defaultMethod = self.mln.defaultInferenceMethod
        if defaultMethod == InferenceMethods.Exact:
            return self.inferExact(what, given, verbose, **args)
        elif defaultMethod == InferenceMethods.GibbsSampling:
            return self.inferGibbs(what, given, verbose, **args)
        elif defaultMethod == InferenceMethods.MCSAT:
            return self.inferMCSAT(what, given, verbose, **args)
        elif defaultMethod == InferenceMethods.IPFPM_exact:
            return self.inferIPFPM(what, given, inferenceMethod=InferenceMethods.Exact, **args)
        elif defaultMethod == InferenceMethods.IPFPM_MCSAT:
            return self.inferIPFPM(what, given, inferenceMethod=InferenceMethods.MCSAT, **args)
        elif defaultMethod == InferenceMethods.EnumerationAsk:
            return self._infer(EnumerationAsk(self), what, given, verbose=verbose, **args)
        #elif self.defaultInferenceMethod == InferenceMethods.ExactLinear:
        #    return self.inferExactLinear(what, given, **args)
        else:
            raise Exception("Unknown inference method '%s'. Use a member of InferenceMethods!" % str(self.defaultInferenceMethod))

    def inferExact(self, what, given=None, verbose=True, **args):
        return self._infer(ExactInference(self), what, given, verbose, **args)

    def inferExactLinear(self, what, given=None, verbose=True, **args):
        return self._infer(ExactInferenceLinear(self), what, given, verbose, **args)

    def inferEnumerationAsk(self, what, given=None, verbose=True, **args):
        return self._infer(EnumerationAsk(self), what, given, verbose, **args)

    def inferGibbs(self, what, given=None, verbose=True, **args):
        return self._infer(GibbsSampler(self), what, given, verbose=verbose, **args)

    def inferMCSAT(self, what, given=None, verbose=True, **args):
        self.mcsat = MCSAT(self, verbose=verbose) # can be used for later data retrieval
        self.mln.mcsat = self.mcsat # only for backwards compatibility
        return self._infer(self.mcsat, what, given, verbose, **args)

    def inferIPFPM(self, what, given=None, verbose=True, **args):
        '''
            inference based on the iterative proportional fitting procedure at the model level (IPFP-M)
        '''
        self.ipfpm = IPFPM(self) # can be used for later data retrieval
        self.mln.ipfpm = self.ipfpm # only for backwards compatibility
        return self._infer(self.ipfpm, what, given, verbose, **args)

    def _infer(self, inferObj, what, given=None, verbose=True, doProbabilityFitting=True, **args):
        # if there are prior probability constraints, apply them first
        if len(self.probreqs) > 0 and doProbabilityFitting:
            fittingParams = {
                "fittingMethod": self.mln.probabilityFittingInferenceMethod,
                "fittingSteps": self.mln.probabilityFittingMaxSteps,
                "fittingThreshold": self.mln.probabilityFittingThreshold,
                "probabilityFittingResultFileName": None
                #fittingMCSATSteps
            }
            fittingParams.update(args)
            self._fitProbabilityConstraints(self.probreqs, **fittingParams)
        # run actual inference method
        self.inferObj = inferObj
        return inferObj.infer(what, given, verbose=verbose, **args)

    def getResultsDict(self):
        '''
            gets the results computed by the last call to an inference method (infer*)
            in the form of a dictionary that maps ground formulas to probabilities
        '''
        return self.inferObj.getResultsDict()

    def _weights(self):
        ''' returns the weight vector as a list '''
        return [f.weight for f in self.formulas]
    
    def getRandomWorld(self):
        ''' uniformly samples from the set of possible worlds (taking blocks into account) '''
        self._getPllBlocks()
        state = [None] * len(self.gndAtoms)
        mln = self.mln
        for idxBlock, (idxGA, block) in enumerate(self.pllBlocks):
            if block != None: # block of mutually exclusive atoms
                chosen = block[random.randint(0, len(block) - 1)]
                for idxGA in block:
                    state[idxGA] = (idxGA == chosen)
            else: # regular ground atom, which can either be true or false
                chosen = random.randint(0, 1)
                state[idxGA] = bool(chosen)
        return state

    def domSize(self, domName):
        return len(self.domains[domName])

    def writeDotFile(self, filename):
        '''
        write a .dot file for use with GraphViz (in order to visualize the current ground Markov network)
        '''
        if not hasattr(self, "gndFormulas") or len(self.gndFormulas) == 0:
            raise Exception("Error: cannot create graph because the MLN was not combined with a concrete domain")
        f = file(filename, "wb")
        f.write("graph G {\n")
        graph = {}
        for gf in self.gndFormulas:
            idxGndAtoms = gf.idxGroundAtoms()
            for i in range(len(idxGndAtoms)):
                for j in range(i + 1, len(idxGndAtoms)):
                    edge = [idxGndAtoms[i], idxGndAtoms[j]]
                    edge.sort()
                    edge = tuple(edge)
                    if not edge in graph:
                        f.write("  ga%d -- ga%d\n" % edge)
                        graph[edge] = True
        for gndAtom in self.gndAtoms.values():
            f.write('  ga%d [label="%s"]\n' % (gndAtom.idx, str(gndAtom)))
        f.write("}\n")
        f.close()

    def writeGraphML(self, filename):
        import graphml
        G = graphml.Graph()
        nodes = []
        for i in xrange(len(self.gndAtomsByIdx)):
            ga = self.gndAtomsByIdx[i]
            nodes.append(graphml.Node(G, label=str(ga), shape="ellipse", color=graphml.randomVariableColor))
        links = {}
        for gf in self.gndFormulas:
            print gf
            idxGAs = sorted(gf.idxGroundAtoms())
            for idx, i in enumerate(idxGAs):
                for j in idxGAs[idx+1:]:
                    t = (i,j)
                    if not t in links:
                        print "  %s -- %s" % (nodes[i], nodes[j])
                        graphml.UndirectedEdge(G, nodes[i], nodes[j])
                        links[t] = True
        f = open(filename, "w")
        G.write(f)
        f.close()

