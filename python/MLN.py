# Markov Logic Networks
#
# (C) 2006-2007 by Dominik Jain (jain@cs.tum.edu)
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
reads Alchemy-style .mln files (see manual of the Alchemy system)
with some limitations:
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

Your MLN files may contain:
    - domain declarations, e.g.
          domainName = {value1, value2, value3}
    - predicate declarations, e.g.
          pred1(domainName1, domainName2)
    - constraints for mutual exclusiveness and exhaustiveness, e.g.
          pred1(a,b!)   to state that for every constant value that variable a can take on, there is exactly one value that b can take on
    - formulas, e.g.
          12   cloudy(d)
    - use C++-style comments (i.e. // and /* */) anywhere

As a special construct of our implementation, an MLN file may contain constraints of the sort
    P(foo(x, Bar)) = 0.5
i.e. constraints on formula probabilities that cause weights to be automatically adjusted to conform to the constraint.
Note that the MLN must contain the corresponding formula.

We also support an alternate representation where the parameters are factors between 0 and 1 instead of regular weights.
To use this representation, make use of infer2 rather than infer to compute probabilities.
To learn factors rather than regular weights, use "LL_fac" rather than "LL" as the method you supply to learnwts.
(pseudolikelihood is currently unsupported for learning such representations)


deprecated features: 

We tried to add dynamic weight scaling based on the number of groundings of a formula:
  * use the "_scaled" variants of the learning methods for learnwts
  * use applyFormulaScales prior to inference to perform the scaling
Currently, we do not think that all this makes a whole lot of sense though... (There are special cases where it actually helps to
better handle domain shifts though)
'''

from __future__ import generators # required for jython 2.2

DEBUG = False

import math
import re
import sys
import os
import random
import time
import traceback
if "java" not in sys.platform:
    from pyparsing import ParseException
else: # using Jython (assuming 2.2)
    from jyparsing import ParseException
    from jythonext import set    
try:
    import numpy
    from scipy.optimize import fmin_bfgs, fmin_cg, fmin_ncg, fmin_tnc, fmin_l_bfgs_b
except:
    sys.stderr.write("Warning: Failed to import SciPy/NumPy (http://www.scipy.org)! Parameter learning with the MLN class is disabled.\n")
from math import exp, log, floor, ceil, e, sqrt
try:
    if not DEBUG:
        import psyco # Don't use Psyco when debugging!
        psyco.full()    
except:
    sys.stderr.write("Warning: Psyco (http://psyco.sourceforge.net) could not be loaded! Performance will be impaired.\n")

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

POSSWORLDS_BLOCKING = True

DYNAMIC_SCALING_THRESHOLD = 0.01 # maximum difference between desired and computed probability
DYNAMIC_SCALING_MAX_STEPS = 20 # maximum number of iterations

class InferenceMethods:
    exact, GibbsSampling, MCSAT = range(3)
    _names = {exact: "exact inference", GibbsSampling: "Gibbs sampling", MCSAT: "MC-SAT"}
    _byName = dict([(x,y) for (y,x) in _names.iteritems()])
    
class ParameterLearningMeasures:
    LL, PLL, BPLL = range(3)
    _names = {LL: "log-likelihood", PLL: "pseudo-log-likelihood", BPLL: "pseudo-log-likelihood with blocking"}
    _shortnames = {LL: "LL", PLL: "PLL", BPLL: "BPLL"}
    _byName = dict([(x,y) for (y,x) in _names.iteritems()])

# TODO Note: when counting diffs (PLL), the assumption is made that no formula contains two atoms that are in the same block

# -- helper functions

def logx(x):
    if x == 0:
        return -100
    return math.log(x)

def stripComments(text):
    comment = re.compile(r'//.*?$|/\*.*?\*/', re.DOTALL | re.MULTILINE)
    return re.sub(comment, '', text)

def avg(*a):
    return sum(map(float,a))/len(a)

# parses a predicate such as p(A,B) and returns a tuple where the first item is the predicate name
# and the second is a list of parameters, e.g. ("p", ["A", "B"])
def parsePredicate(line):
    m = re.match(r'(\w+)\((.*?)\)$', line)
    if m is not None:
        return (m.group(1), map(str.strip, m.group(2).split(",")))
    raise Exception("Could not parse predicate '%s'" % line)

def parseLiteral(line):
    ''' parses a literal such as !p(A,B) or p(A,B)=False and returns a tuple where the first item is whether the literal is true, the second is the predicate name
        and the third is a list of parameters, e.g. (False, "p", ["A", "B"]) '''
    # try regular MLN syntax
    m = re.match(r'(!?)(\w+)\((.*?)\)$', line)
    if m is not None:
        return (m.group(1) != "!", m.group(2), map(str.strip, m.group(3).split(",")))
    # try BLOG syntax where instead of p(A,B) we have p(A)=B or instead of !q(A) we have q(A)=False
    m = re.match(r'(\w+)\((.*?)\)\s*=\s*(\w+)$', line)
    if m is not None:
        params = map(str.strip, m.group(2).split(","))
        value = m.group(3).strip()
        isTrue = True
        if value == 'True':
            pass
        elif value == 'False':
            isTrue = False
        else:
            params.append(value)
        return (isTrue, m.group(1), params)
    raise Exception("Could not parse literal '%s'" % line)
    
    
# parses a domain declaration and returns a tuple (domain name, list of constants)
def parseDomDecl(line):
    m = re.match(r'(\w+)\s*=\s*{(.*?)}', line)
    if m == None:
        raise Exception("Could not parse the domain declaration '%s'" % line)
    return (m.group(1), map(str.strip, m.group(2).split(',')))

def balancedParentheses(s):
    cnt = 0
    for c in s:
        if c == '(':
            cnt += 1
        elif c == ')':
            if cnt <= 0:
                return False
            cnt -= 1
    return cnt == 0
  
def strFormula(f):
    s = str(f)
    while s[0] == '(' and s[-1] == ')':
        s2 = s[1:-1]
        if not balancedParentheses(s2):
            return s
        s = s2
    return s

# converts the evidence obtained from a database (dict mapping ground atom names to truth values) to a conjunction (string)
def evidence2conjunction(evidence):
    evidence = map(lambda x: {True:"", False:"!"}[x[1]] + x[0], evidence.iteritems())
    return " ^ ".join(evidence)

# -- Markov logic network

class MLN:
    '''
    represents a Markov logic network and/or a ground Markov network

    members:
        blocks:
            dict: predicate name -> list of booleans that indicate which arguments of the pred are functionally determined by the others
            (one boolean per argument, True = is functionally determined)
        closedWorldPreds:
            list of predicates that are assumed to be closed-world (for inference)
        gndAtoms:
            maps a string representation of a ground atom to a FOL.GroundAtom object
        gndAtomsByIdx:
            dict: ground atom index -> FOL.GroundAtom object
        gndBlocks:
            dict: block name -> list of ground atom indices
        gndBlockLookup:
            dict: ground atom index -> block name
        formulas:
            list of (ungrounded) formula objects
        gndAtomOccurrencesInGFs
            dict: ground atom index -> ground formula
        gndFormulas:
            list of grounded formula objects
        pllBlocks:
            list of *all* the ground blocks, including trivial blocks consisting of a single ground atom
            each element is a tuple (ground atom index, list of ground atom indices) where one element is always None
        predicates:
            dict: predicate name -> list of domain names that apply to the predicate's parameters
        worldCode2Index:
            dict that maps a world's identification code to its index in self.worlds
        worlds
            list of possible worlds, each entry is a dict with
                'values' -> list of booleans - one for each gnd atom
    '''

    # constructs an MLN object
    #   filename: the name of a .mln file
    def __init__(self, filename, defaultInferenceMethod = InferenceMethods.MCSAT, parameterType = 'weights', verbose=False):
        t_start = time.time()
        self.domains = {}
        self.predicates = {}
        self.rigidPredicates = []
        self.formulas = []
        self.blocks = {}
        self.domDecls = []
        self.evidenceBackup = {}
        self.probreqs = []
        self.evidence = {}
        self.defaultInferenceMethod = defaultInferenceMethod
        self.parameterType = parameterType
        self.formulaGroups = []
        self.closedWorldPreds = []
        formulatemplates = []
        # read MLN file
        f = file(filename)
        text = f.read()
        f.close()
        # replace some meta-directives in comments
        text = re.compile(r'//\s*<group>\s*$', re.MULTILINE).sub("#group", text)
        text = re.compile(r'//\s*</group>\s*$', re.MULTILINE).sub("#group.", text)
        # remove comments
        text = stripComments(text)
        # read lines
        hard_formulas = []
        max_weight = -1000000
        if verbose: print "reading MLN..."
        templateIdx2GroupIdx = {}
        inGroup = False
        idxGroup = -1
        for line in text.split("\n"):
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
                # domain decl
                if '{' in line:
                    domName, constants = parseDomDecl(line)
                    if domName in self.domains: raise Exception("Domain redefinition: '%s' already defined" % domName)
                    self.domains[domName] = constants
                    self.domDecls.append(line)
                    continue
                # probability requirement
                m = re.match(r"P\((.*?)\)\s*=\s*([\.\de]+)", line)
                if m != None:
                    self.probreqs.append((strFormula(FOL.parseFormula(m.group(1))), m.group(2)))
                    continue
                # mutex constraint
                if re.search(r"[a-z_][-_'a-zA-Z0-9]*\!", line) != None: 
                    pred = parsePredicate(line)
                    mutex = []
                    for param in pred[1]:
                        if param[-1] == '!':
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
                        if line[-1] == '.': # hard (without explicit weight -> determine later)
                            isHard = True
                            formula = line[:-1]
                        else: # with weight
                            spacepos = line.find(' ')
                            try: 
                                #weight = eval(line[:spacepos])
                                weight = line[:spacepos]
                            except:
                                raise Exception("Could not evaluate weight '%s'" % (line[:spacepos]))
                            try:
                                max_weight = max(max_weight, eval(weight))
                            except:
                                pass
                            formula = line[spacepos:].strip()
                        try:
                            formula = FOL.parseFormula(formula)
                            if not isHard:
                                formula.weight = weight
                            else:
                                hard_formulas.append(formula)
                            idxTemplate = len(formulatemplates)
                            formulatemplates.append(formula)
                            if inGroup:
                                templateIdx2GroupIdx[idxTemplate] = idxGroup
                        except ParseException, e:
                            sys.stderr.write("Error parsing formula '%s'\n" % formula)
            except:
                sys.stderr.write("Error processing line '%s'\n" % line)
                cls, e, tb = sys.exc_info()
                traceback.print_tb(tb)
                raise e
        # set weights of hard formula/templates
        hard_weight = max(20, max_weight+20)
        for formula in hard_formulas:
            formula.weight = hard_weight
        # materialize formula templates
        if verbose: print "materializing formula templates..."
        idxGroup = None
        prevIdxGroup = None
        group = []
        for idxTemplate,tf in enumerate(formulatemplates):
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
                idxFormula = len(self.formulas)
                self.formulas.append(f)
                f.idxFormula = idxFormula
                # add the formula indices to the group if any
                if idxGroup != None:
                    group.append(idxFormula)
        if group != []: # add the last group (if any)
            self.formulaGroups.append(group)
        #print "time taken: %fs" % (time.time()-t_start)

    def _groundAtoms(self, cur, predName, domNames):
        # if there are no more parameters to ground, we're done
        if domNames == []:
            idxAtom = len(self.gndAtoms)
            
            # add atom
            gndAtom = "%s(%s)" % (predName, ",".join(cur))
            #print gndAtom
            self.gndAtoms[gndAtom] = FOL.GroundAtom(predName, cur, idxAtom)

            # check if atom is in block
            mutex = self.blocks.get(predName)
            if mutex != None:
                blockName = "%s_" % predName
                for i,v in enumerate(mutex):
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
     
    def _generateGroundAtoms(self, domain):        
        self.gndAtoms = {}
        self.gndBlockLookup = {}
        self.gndBlocks = {}
        # create ground atoms
        atoms = []
        for predName, domNames in self.predicates.iteritems():
            self._groundAtoms([], predName, domNames)
        # reverse lookup
        self.gndAtomsByIdx = dict([(g.idx,g) for g in self.gndAtoms.values()]) 

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
            self.__createPossibleWorlds(values + [x], idx + 1, code + {True: bit, False: 0}[x], bit << 1)
        
    def _createPossibleWorlds(self):
        self.worldCode2Index = {}
        self.worlds = []
        self.__createPossibleWorlds([], 0, 0, 1)

    # get the possible world with the given one-based world number
    def getWorld(self, worldNo):
        self._getWorlds()
        return self.worlds[worldNo-1]

    def _groundFormula(self, formula, variables, assignment, idxFormula):
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
        varname,domName = variables.popitem()
        for value in self.domains[domName]: # replacing it with one of the constants
            assignment[varname] = value
            # recursive descent to ground further variables
            self._groundFormula(formula, dict(variables), assignment, idxFormula)

    def _createFormulaGroundings(self):
        '''this is the method that creates the ground MRF'''
        self.gndFormulas = []
        self.gndAtomOccurrencesInGFs = [[] for i in range(len(self.gndAtoms))]
        for idxFormula, formula in enumerate(self.formulas):
            #vars = formula.getVariables(self)
            #self._groundFormula(formula, vars, {}, idxFormula)
            for gndFormula, referencedGndAtoms in formula.iterGroundings(self):
                for idxGA in referencedGndAtoms:
                    self.gndAtomOccurrencesInGFs[idxGA].append(gndFormula)
                gndFormula.idxFormula = idxFormula
                self.gndFormulas.append(gndFormula)
        # materialize all formula weights
        for f in self.formulas:
            w = str(f.weight)
            w = re.sub(r'domSize\((.*?)\)', r'self.domSize("\1")', w)
            f.weight = eval(w)
    
    def domSize(self, domName):
        return len(self.domains[domName])
    
    # convert all the MLN's ground formulas to CNF
    # if allPositive=True, then formulas with negative weights are negated to make all weights positive
    def _toCNF(self, allPositive=False):
        # get list of formula indices where we must negate
        negate = []
        if allPositive:
            for idxFormula,f in enumerate(self.formulas):
                if f.weight < 0:
                    negate.append(idxFormula)
                    f.weight *= -1
        # get CNF version of each ground formula
        gndFormulas = []
        for gf in self.gndFormulas:
            # non-logical constraint
            if not gf.isLogical(): # don't apply any transformations to non-logical constraints
                if gf.idxFormula in negate:
                    gf.negate()
                gndFormulas.append(gf)
                continue
            # logical constraint
            if gf.idxFormula in negate:
                cnf = FOL.Negation([gf]).toCNF()
            else:
                cnf = gf.toCNF()
            if type(cnf) == FOL.TrueFalse: # formulas that are always true or false can be ignored
                continue
            cnf.idxFormula = gf.idxFormula
            gndFormulas.append(cnf)
        # update the MLN
        self.gndFormulas = gndFormulas

    def _isTrue(self, gndFormula, world_values):
        return gndFormula.isTrue(world_values)
        
    def _calculateWorldValues(self, wts = None):
        if wts == None:
            wts = self._weights()
        if ('wtsLastWorldValueComputation' in dir(self)) and (self.wtsLastWorldValueComputation == list(wts)): # avoid computing the values we already have
            return
        total = 0
        for world in self.worlds:
            weights = []
            for gndFormula in self.gndFormulas:
                if self._isTrue(gndFormula, world["values"]):
                    weights.append(wts[gndFormula.idxFormula])
            exp_sum = math.exp(sum(weights))
            total += exp_sum
            world["sum"] = exp_sum
            world["weights"] = weights
        self.partition_function = total
        self.wtsLastWorldValueComputation = list(wts)

    def _calculateWorldValues_scaled(self, wts = None):
        if wts == None:
            wts = self._weights()
        if ('wtsLastWorldValueComputation' in dir(self)) and (self.wtsLastWorldValueComputation == list(wts)): # avoid computing the values we already have
            return
        total = 0
        for world in self.worlds:
            weights = []
            for gndFormula in self.gndFormulas:
                if self._isTrue(gndFormula, world["values"]):
                    weights.append(self.formulaScales[gndFormula.idxFormula] * wts[gndFormula.idxFormula])
            exp_sum = math.exp(sum(weights))
            total += exp_sum            
            world["sum"] = exp_sum
            world["weights"] = weights
        self.partition_function = total
        self.wtsLastWorldValueComputation = list(wts)

    # get the names of all ground atoms of the given predicate
    def _getPredGroundings(self, predName):
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
    
    # get a list of all the indices of all groundings of the given predicate
    def _getPredGroundingsAsIndices(self, predName):
        # get the index of the first grounding of the predicate and the number of groundings
        domNames = self.predicates[predName]
        params = []
        numGroundings = 1
        for domName in domNames:
            params.append(self.domains[domName][0])
            numGroundings *= len(self.domains[domName])
        gndAtom = "%s(%s)" % (predName, ",".join(params))
        idxFirst = self.gndAtoms[gndAtom].idx
        return range(idxFirst, idxFirst+numGroundings)
    
    # expands the list of queries where necessary, i.e. queries that are just predicate names are expanded to the corresponding list of atoms
    def _expandQueries(self, queries):
        equeries = []
        for query in queries:
            if "(" in query: # a fully or partially grounded atom
                predName, args = parsePredicate(query)
                newargs = []
                fullygrounded = True
                for arg in args:
                    if not arg[0].isalpha() or arg[0].isupper(): # argument is constant
                        newargs.append(arg)
                    else: # not a constant, so replace by regex that non-greedily matches anyting
                        fullygrounded = False
                        newargs.append(r".*?")
                if fullygrounded: # fully grounded atom, so we can directly use the original string as a query
                    equeries.append(query)
                else: # partially grounded atom: look through all groundings of the predicate for matches
                    ex = "%s(%s)" % (predName, ",".join(newargs))
                    #print ex
                    pat = re.compile(r"%s\(%s\)" % (predName, ",".join(newargs)))
                    for gndAtom in self._getPredGroundings(predName):
                        if pat.match(gndAtom):
                            equeries.append(gndAtom)
                        #else:
                        #    print "%s does not match " % gndAtom
            else: # just a predicate name
                try: 
                    equeries.extend(self._getPredGroundings(query))
                except:
                    raise Exception("Could not expand query '%s'" % query)
        if len(equeries) == 0:
            raise Exception("No ground atoms match the given query/queries.")
        return equeries
        
    # infer a probability P(F1 | F2) where F1 and F2 are formulas - using the default inference method specified for this MLN
    #   what: a formula, e.g. "foo(A,B)", or a list of formulas
    #   given: another formula, e.g. "bar(A,B) ^ !baz(A,B)"
    #          may be None if the prior probability of F1 is to be computed
    #          If it'S not None, it can be an arbitrary formula only for exact inference, otherwise it must be a conjunction
    #   verbose: whether to print the results
    #   args: any additional arguments to pass on to the actual inference method
    def infer(self, what, given = None, verbose = True, **args):
        # call actual inference method
        if self.defaultInferenceMethod == InferenceMethods.exact:
            return self.inferExact(what, given, verbose, **args)
        elif self.defaultInferenceMethod == InferenceMethods.GibbsSampling:
            return self.inferGibbs(what, given, verbose, **args)
        elif self.defaultInferenceMethod == InferenceMethods.MCSAT:
            return self.inferMCSAT(what, given, verbose, **args)
        else:
            raise Exception("Unknown inference method '%s'. Use a member of InferenceMethods!" % str(self.defaultInferenceMethod))

    def inferExact(self, what, given = None, verbose = True, **args):
        return ExactInference(self).infer(what, given, verbose, **args)
            
    def inferGibbs(self, what, given = None, verbose = True, **args):
        if not hasattr(self, "gibbsSampler"):
            self.gibbsSampler = GibbsSampler(self)
        return self.gibbsSampler.infer(what, given, verbose=verbose, **args)

    def inferMCSAT(self, what, given = None, verbose = True, **args):
        if not hasattr(self, "mcsat"):
            self.mcsat = MCSAT(self, verbose = args.get("details", False))
        result = self.mcsat.infer(what, given, verbose=verbose, **args)
        return result

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
            prob = world["sum"]/self.partition_function
            weights = "<- " + " ".join(map(lambda s: "%.1f" % s, world["weights"]))
            if format==1: print "%6.2f%%  %s  %e <- %.2f %s" % (100*prob, " ".join(literals), world["sum"], sum(world["weights"]), weights)
            elif format==2: print "%6.2f%%  %s  %s" % (100*prob, " ".join(literals), str(world["counts"]))
            #print "Pr=%.2f  %s  %15.0f" % (prob, " ".join(literals), world["sum"])
        elif mode == 2:
             print "%6.2f%%  %s  %.2f" % (100*world["prod"]/self.partition_function, " ".join(literals), world["prod"])
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
            worlds.sort(key=lambda x: -x["sum"])
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
        worlds.sort(key=lambda w: -w["sum"])
        for i in range(min(num,len(worlds))):
            self.printWorld(worlds[i], mode=mode, format=format)

    # prints, for the given world, the probability, the literals, the sum of weights, plus for each ground formula the truth value on a separate line
    def printWorldDetails(self, world):
        self.printWorld(world)
        for gf in self.gndFormulas:
            isTrue = gf.isTrue(world["values"])
            print "  %-5s  %f  %s" % (str(isTrue), self.formulas[gf.idxFormula].weight, strFormula(gf))

    def combine(self, domain, verbose=False, groundFormulas = True):
        '''combines the existing domain (if any) with the given one
                domain: a dictionary with domainName->list of string constants to add'''
        # combine domains
        domNames = self.domains.keys() + domain.keys()
        #print domNames
        domNames = set(self.domains.keys() + domain.keys())
        for domName in domNames:
            a = self.domains.get(domName, [])
            b = domain.get(domName, [])
            self.domains[domName] = list(set(a+b))
        # collect data
        self._generateGroundAtoms(domain)
        if groundFormulas: self._createFormulaGroundings()
        if verbose:
            print "ground atoms: %d" % len(self.gndAtoms)
            print "ground formulas: %d" % len(self.gndFormulas)
        
        # apply probability constraints (if any)
        if len(self.probreqs) > 0:
            print "applying dynamic scaling... "
            step = 1
            maxdiff = 1
            while step <= DYNAMIC_SCALING_MAX_STEPS and maxdiff >= DYNAMIC_SCALING_THRESHOLD:
                maxdiff = 0
                for req in self.probreqs:
                    gotit = False
                    for formula in self.formulas:
                        if strFormula(formula) == req[0]:
                            # instantiate a ground formula
                            vars = formula.getVariables(self)
                            groundVars = {}
                            for varName, domName in vars.iteritems():
                                groundVars[varName] = self.domains[domName][0]
                            gndFormula = formula.ground(self, groundVars)
                            # calculate probability of that ground formula
                            p = self.infer(str(gndFormula), verbose=False)
                            # get the scaling factor and apply it
                            pnew = float(req[1])
                            f = pnew * (1-p) / p / (1-pnew)
                            old_weight = formula.weight 
                            formula.weight += math.log(f)
                            diff = abs(p-pnew)
                            print "  [%d] changed weight of %s from %f to %f (diff = %f)" % (step, strFormula(formula), old_weight, formula.weight, diff)
                            gotit = True
                            maxdiff = max(maxdiff, diff)
                            # recalculate world values (for exact inference)
                            if self.defaultInferenceMethod == InferenceMethods.exact:
                                self._calculateWorldValues()
                            break
                    if not gotit:
                        raise Exception("Probability constraint on '%s' cannot be applied because the formula is not part of the MLN!" % req[0])
                step += 1

    # minimize the weights of formulas in groups by subtracting from each formula weight the minimum weight in the group
    # this results in weights relative to 0, therefore this equivalence transformation can be thought of as a normalization
    def minimizeGroupWeights(self):
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
    
    # creates the set of possible worlds and calculates for each world all the necessary values
    def _getWorlds(self):
        if not hasattr(self, "worlds"):
            self._createPossibleWorlds()
            if self.parameterType == 'weights':
                self._calculateWorldValues()
            elif self.parameterType == 'probs':
                self._calculateWorldValues_prob()

    # reads a database file containing literals and/or domains
    # returns (domains, evidence) where domains is dictionary mapping domain names to lists of constants defined in the database
    # and evidence is a dictionary mapping ground atom strings to truth values
    def _readDBFile(self, dbfile):
        domains = {}
        # read file
        f = file(dbfile, "r")
        db = f.read()
        f.close()
        db = stripComments(db)
        # expand domains with db constants and save evidence
        evidence = {}
        for l in db.split("\n"):
            l = l.strip()
            if l == "":
                continue
            # domain declaration
            if "{" in l:
                domName, constants = parseDomDecl(l)
                domNames = [domName for c in constants]
            # literal
            else:
                if l[0] == "?":
                    raise Exception("Unknown literals not supported (%s)" % l) # this is an Alchemy feature
                isTrue, predName, constants = parseLiteral(l)
                domNames = self.predicates[predName]
                # save evidence
                evidence["%s(%s)" % (predName, ",".join(constants))] = isTrue
            # expand domains
            if len(domNames) != len(constants):
                raise Exception("Ground atom %s in database %s has wrong number of parameters" % (l, dbfile))
            for i in range(len(constants)):
                if domNames[i] not in domains:
                    domains[domNames[i]] = []
                d = domains[domNames[i]]
                if constants[i] not in d:
                    d.append(constants[i])
        return (domains, evidence)

    # This method serves two purposes:
    # a) extend the domain - analogous to method combine only that the constants are taken from a database base file rather than a dictionary
    # b) stores the literals in the given db as evidence for future use (e.g. as a training database for weight learning)
    # dbfile: name of a database file
    # returns the evidence defined in the database (dictionary mapping ground atom strings to truth values)
    def combineDB(self, dbfile, verbose=False, groundFormulas = True):
        domain, evidence = self._readDBFile(dbfile)
        # combine domains
        self.combine(domain, verbose=verbose, groundFormulas=groundFormulas)
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
        domain, evidence = self._readDBFile(dbfile)
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
        self._generateGroundAtoms(domain)
        self._createFormulaGroundings()

    def _setEvidence(self, idxGndAtom, value):
        self.evidence[idxGndAtom] = value

    def _setTemporaryEvidence(self, idxGndAtom, value):
        self.evidenceBackup[idxGndAtom] = self._getEvidence(idxGndAtom, closedWorld=False)
        self._setEvidence(idxGndAtom, value)        

    def _getEvidence(self, idxGndAtom, closedWorld = True):
        v = self.evidence[idxGndAtom]
        if closedWorld and v == None:
            return False
        return v

    def _clearEvidence(self):
        self.evidence = [None for i in range(len(self.gndAtoms))]

    def printEvidence(self):
        for idxGA, value in enumerate(self.evidence):
            print "%s = %s" % (str(self.gndAtomsByIdx[idxGA]), str(value))

    def _removeTemporaryEvidence(self):
        for idx,value in self.evidenceBackup.iteritems():
            self._setEvidence(idx,value)
        self.evidenceBackup.clear()
    
    def _isTrueGndFormulaGivenEvidence(self, gf):
        return gf.isTrue(self.evidence)

    # returns, from the current evidence list, a dictionary that maps ground atom names to truth values
    def getEvidenceDatabase(self):
        d = {}
        for idxGA, tv in enumerate(self.evidence):
            if tv != None:
                d[str(self.gndAtomsByIdx[idxGA])] = tv
        return d
        
    # sets the given predicate as closed-world (for inference)
    # a predicate that is closed-world is assumed to be false for any parameters not explicitly specified otherwise in the evidence
    def setClosedWorldPred(self, predicateName):
        if predicateName not in self.predicates:
            raise Exception("Unknown predicate '%s'" % predicateName)
        self.closedWorldPreds.append(predicateName)
        
    # determines the probability of the given ground atom (string) given its Markov blanket
    # (the MLN must have been provided with evidence using combineDB)
    def getAtomProbMB(self, atom):
        idxGndAtom = self.gndAtoms[atom].idx
        weights = self._weights()
        return self._getAtomProbMB(idxGndAtom, weights)

    # get the probability of the assignment for the block the given atom is in
    def getBlockProbMB(self, atom):
        idxGndAtom = self.gndAtoms[atom].idx       
        self._getBlockProbMB(idxBlock, self._weights())

    # gets the probability of the ground atom with index idxGndAtom when given its Markov blanket (evidence set)
    # using the specified weight vector
    def _getAtomProbMB(self, idxGndAtom, wt, relevantGroundFormulas = None):            
        old_tv = self._getEvidence(idxGndAtom)
        # check if the ground atom is in a block
        block = None
        if idxGndAtom in self.gndBlockLookup and PMB_METHOD != 'old':
            blockname = self.gndBlockLookup[idxGndAtom]
            block = self.gndBlocks[blockname] # list of gnd atom indices that are in the block
            sums = [0 for i in range(len(block))] # init sum of weights for each possible assignment of block
                                                  # sums[i] = sum of weights for assignment where the block[i] is set to true
            idxBlockMainGA = block.index(idxGndAtom)
            # find out which one of the ground atoms in the block is true
            idxGATrueone= -1
            for i in block:
                if self._getEvidence(i):
                    if idxGATrueone != -1: raise Exception("More than one true ground atom in block %s!" % blockname)
                    idxGATrueone = i                    
            if idxGATrueone == -1: raise Exception("No true gnd atom in block!" % blockname)
            mainAtomIsTrueone = idxGATrueone == idxGndAtom
        else: # not in block
            wts_inverted = 0
            wts_regular = 0
            wr, wi = [],[]
        # determine the set of ground formulas to consider
        checkRelevance = False
        if relevantGroundFormulas == None:
            try:
                relevantGroundFormulas = self.atomRelevantGFs[idxGndAtom]
            except:
                relevantGroundFormulas = self.gndFormulas
                checkRelevance = True
        # check the ground formulas
        if PMB_METHOD == 'old' or block == None: # old method (only consider formulas that contain the ground atom)
            for gf in relevantGroundFormulas:
                if checkRelevance:
                    if not gf.containsGndAtom(idxGndAtom):
                        continue
                # gnd atom maintains regular truth value
                if self._isTrueGndFormulaGivenEvidence(gf):
                    wts_regular += wt[gf.idxFormula]
                    wr.append(wt[gf.idxFormula])
                # flipped truth value
                self._setTemporaryEvidence(idxGndAtom, not old_tv)
                if self._isTrueGndFormulaGivenEvidence(gf):
                    wts_inverted += wt[gf.idxFormula]
                    wi.append(wt[gf.idxFormula])
                self._removeTemporaryEvidence()
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

    # prints the probability of each ground atom truth assignment given its Markov blanket
    def printAtomProbsMB(self):
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
    def printBlockProbsMB(self):
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

    def _strBlock(self, block):
        return "{%s}" % (",".join(map(lambda x: str(self.gndAtomsByIdx[x]), block)))

    # returns the weight vector of the MLN as a list       
    def _weights(self):
        return [f.weight for f in self.formulas]

    # gets the PLL of the database that is given by the current evidence data
    def getPLL(self):
        return self._pll(self._weights())

    # evaluate this mln with regard to the given DB
    # useful to determine how good the weights of this mln are, assuming that they were learned with the given db
    def evaluate(self, db_filename):
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
        worlds.sort(key=lambda w: -w["sum"])
        top_prob = worlds[0]["sum"] / self.partition_function
        print "Top observed probability of a possible world:", top_prob
        print "Number of worlds:", len(self.worlds)

    def _calculateAtomProbsMB(self, wt):
        if ('wtsLastAtomProbMBComputation' not in dir(self)) or self.wtsLastAtomProbMBComputation != list(wt):
            print "recomputing atom probabilities...",
            self.atomProbsMB = [self._getAtomProbMB(i, wt) for i in range(len(self.gndAtoms))]
            self.wtsLastAtomProbMBComputation = list(wt)
            print "done."

    def _pll(self, wt):
        self._calculateAtomProbsMB(wt)
        probs = map(lambda x: {True: 1e-10, False:x}[x==0], self.atomProbsMB) # prevent 0 probs
        return sum(map(math.log, probs))

    def _addToDiff(self, idxFormula, idxGndAtom, diff):
        key = (idxFormula, idxGndAtom)
        cur = self.diffs.get(key, 0)
        self.diffs[key] = cur+diff        

    def _computeDiffs(self):
        self.diffs = {}
        for gndFormula in self.gndFormulas:
            for idxGndAtom in gndFormula.idxGroundAtoms():
                cnt1,cnt2 = 0,0
                # check if formula is true if gnd atom maintains its truth value
                if self._isTrueGndFormulaGivenEvidence(gndFormula): cnt1 = 1
                # check if formula is true if gnd atom's truth value is inversed
                cnt2 = 0
                old_tv = self._getEvidence(idxGndAtom)
                self._setTemporaryEvidence(idxGndAtom, not old_tv)
                if self._isTrueGndFormulaGivenEvidence(gndFormula): cnt2 = 1
                self._removeTemporaryEvidence()
                # save difference
                diff = cnt2-cnt1
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
                                        self._addToDiff(gndFormula.idxFormula, i, diff/(len(block)-1))


    def _grad_pll(self, wt):        
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        self._calculateAtomProbsMB(wt)
        for (idxFormula, idxGndAtom), diff in self.diffs.iteritems():
            v = diff * (self.atomProbsMB[idxGndAtom] - 1)
            grad[idxFormula] += v
        print "wts =", wt
        print "grad =", grad
        return grad

    def _negated_grad_pll(self, wt, *args):
        return -self._grad_pll(wt)

    def _negated_grad_pll_scaled(self, wt):
        wt_scaled = numpy.zeros(len(wt), numpy.float64)
        for i in range(len(wt)):
            wt_scaled[i] = wt[i] * self.formulaScales[i]
        return -self._grad_pll(wt_scaled)

    def _negated_pll(self, wt, *args):
        pll = self._pll(wt)
        print "pll = %f" % pll
        return -pll   

    def _negated_pll_scaled(self, wt):
        wt_scaled = numpy.zeros(len(wt), numpy.float64)
        for i in range(len(wt)):
            wt_scaled[i] = wt[i] * self.formulaScales[i]
        return -self._pll(wt_scaled)

    def _computeCounts(self):
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
        self.counts = {}
        # for each possible world
        for i in range(len(self.worlds)):
            world = self.worlds[i]
            # count how many true and false groundings there are for each formula
            for gf in self.gndFormulas:                
                key = (i, gf.idxFormula)
                cnt = self.counts.get(key, [0,0])
                if self._isTrue(gf, world["values"]):
                    cnt[0] += 1
                else:
                    cnt[1] += 1
                self.counts[key] = cnt

    # computes the gradient of the log-likelihood given the weight vector wt
    def _grad_ll(self, wt, *args):
        idxTrainDB = args[0]
        self._calculateWorldValues(wt)        
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        #ctraining = [0 for i in range(len(self.formulas))]
        #cothers = [0 for i in range(len(self.formulas))]
        for ((idxWorld, idxFormula), count) in self.counts.iteritems():
            if idxTrainDB == idxWorld:                
                grad[idxFormula] += count
                #ctraining[idxFormula] += count
            n = count * self.worlds[idxWorld]["sum"] / self.partition_function
            grad[idxFormula] -= n
            #cothers[idxFormula] += n
        #print "grad_ll"
        print "wts =", wt
        print "grad =", grad
        print
        return grad

    def _ll(self, wt, *args):
        idxTrainDB = args[0]
        self._calculateWorldValues(wt)
        ll = math.log(self.worlds[idxTrainDB]["sum"] / self.partition_function)
        print "ll =", ll
        return ll

    def _grad_ll_fixed(self, wt, *args):
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
                grad[idxFormula] += count[0]/fac[idxFormula] - count[1]/(1.0-fac[idxFormula])
            n = (count[0]/fac[idxFormula] - count[1]/(1.0-fac[idxFormula])) * self.worlds[idxWorld]["prod"] / self.partition_function
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
                grad[idxFormula] += count[0]/probs[idxFormula]
            n = (count[0]/probs[idxFormula]) * self.worlds[idxWorld]["prod"] / self.partition_function
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
                grad[idxFormula] += count[0]/probs[idxFormula]
            n = (count[0]/probs[idxFormula]) * self.worlds[idxWorld]["prod"] / self.partition_function
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

    def _negated_grad_ll_prob(self, wt, *args):
        return -self._grad_ll_prob(wt, args)

    def _negated_ll_prob(self, wt, *args):
        return -self._ll_prob(wt, args)

    def _ll_scaled(self, wt, *args):
        idxTrainDB = args[0]
        self._calculateWorldValues_scaled(wt)
        ll = math.log(self.worlds[idxTrainDB]["sum"] / self.partition_function)
        print "ll =", ll
        return ll

    def _getEvidenceWorldIndex(self):
        code = 0
        bit = 1
        for i in range(len(self.gndAtoms)):
            if self._getEvidence(i):
                code += bit
            bit *= 2
        return self.worldCode2Index[code]

    def _addToBlockDiff(self, idxFormula, idxBlock, diff):
        key = (idxFormula, idxBlock)
        cur = self.blockdiffs.get(key, 0)
        self.blockdiffs[key] = cur+diff        

    def _computeBlockDiffs(self):
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
                    diff = cnt2-cnt1
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
                    idxGATrueone= -1
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
                    diff = cnt2-cnt1
                    if diff != 0:
                        self._addToBlockDiff(gndFormula.idxFormula, idxPllBlock, diff)

    def _getBlockProbMB(self, idxPllBlock, wt, relevantGroundFormulas = None):
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
            expsums = self._getBlockExpsums(block, wt, self.evidence, idxGATrueone, relevantGroundFormulas)
            # return prob
            return expsums[idxInBlockTrueone] / sum(expsums)

    def _getBlockTrueone(self, block):
        idxGATrueone= -1
        for i in block:
            if self._getEvidence(i):
                if idxGATrueone != -1: raise Exception("More than one true ground atom in block %s!" % blockname)
                idxGATrueone = i
                break
        if idxGATrueone == -1: raise Exception("No true gnd atom in block %s!" % self._strBlock(block))
        return idxGATrueone

    def _getBlockExpsums(self, block, wt, world_values, idxGATrueone = None, relevantGroundFormulas = None):
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
        return map(math.exp, sums)

    def _getAtomExpsums(self, idxGndAtom, wt, world_values, relevantGroundFormulas = None):
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

    def _grad_blockpll(self, wt):        
        grad = numpy.zeros(len(self.formulas), numpy.float64)
        self._calculateBlockProbsMB(wt)
        for (idxFormula, idxBlock), diff in self.blockdiffs.iteritems():
            v = diff * (self.blockProbsMB[idxBlock] - 1)
            grad[idxFormula] += v
        print "wts =", wt
        print "grad =", grad
        norm = math.sqrt(sum(map(lambda x: x*x, grad)))
        print "norm = %f" % norm
        return grad

    def _calculateBlockProbsMB(self, wt):
        if ('wtsLastBlockProbMBComputation' not in dir(self)) or self.wtsLastBlockProbMBComputation != list(wt):
            #print "recomputing block probabilities...",
            self.blockProbsMB = [self._getBlockProbMB(i, wt, self.blockRelevantGFs[i]) for i in range(len(self.pllBlocks))]
            self.wtsLastBlockProbMBComputation = list(wt)
            #print "done."

    def _blockpll(self, wt):
        self._calculateBlockProbsMB(wt)
        probs = map(lambda x: {True: 1e-10, False:x}[x==0], self.blockProbsMB) # prevent 0 probs
        return sum(map(math.log, probs))

    def getBPLL(self):
        return self._blockpll(self._weights())

    def _negated_grad_blockpll(self, wt, *args):
        return -self._grad_blockpll(wt)

    def _negated_blockpll(self, wt, *args):
        bpll = self._blockpll(wt)
        print "bpll = %f" % bpll
        return -bpll

    # creates an array self.pllBlocks that contains tuples (idxGA, block);
    # one of the two tuple items is always None depending on whether the ground atom is in a block or not;
    def _getPllBlocks(self):
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
    
    def _getAtomRelevantGroundFormulas(self):
        if PMB_METHOD == 'old':
            self.atomRelevantGFs = self.gndAtomOccurrencesInGFs
        else:
            raise Exception("Not implemented")
    
    def _getAtom2BlockIdx(self):
        self.atom2BlockIdx = {}
        for idxBlock, (idxGA, block) in enumerate(self.pllBlocks):
            if block != None:
                for idxGA in block:
                    self.atom2BlockIdx[idxGA] = idxBlock
            else:
                self.atom2BlockIdx[idxGA] = idxBlock

    # learn the weights of the mln given the training data previously loaded with combineDB
    #   mode: the measure that is used
    #           'PLL'    pseudo-log-likelihood based on ground atom probabilities
    #           'LL'     log-likelihood (warning: *very* slow and resource-heavy in all but the smallest domains)
    #           'BPLL'   pseudo-log-likelihood based on block probabilities
    #           'LL_fac' log-likelihood; factors between 0 and 1 are learned instead of regular weights
    #   initialWts: whether to use the MLN's current weights as the starting point for the optimization
    def learnwts(self, mode = ParameterLearningMeasures.BPLL, initialWts = False, **params):
        if type(mode) == int:
            mode = ParameterLearningMeasures._shortnames[mode]
        if not 'scipy' in sys.modules:
            raise Exception("Scipy was not imported! Install numpy and scipy if you want to use weight learning.")
        # intial parameter vector: all zeros or weights from formulas
        wt = numpy.zeros(len(self.formulas), numpy.float64)
        if initialWts:
            for i in range(len(self.formulas)):
                wt[i] = self.formulas[i].weight
        # optimization
        if mode == 'PLL' or mode == 'PLL_scaled':
            print "computing differences..."
            self._computeDiffs()
            print "  %d differences recorded" % len(self.diffs)
            print "determining relevant formulas for each ground atom..."
            self._getAtomRelevantGroundFormulas()
            if mode == 'PLL_scaled':
                self._getFormulaScales()
                # scale the differences
                for key, diff in self.diffs.iteritems():
                    #self.diffs[key] *= self.formulaScales[key[0]]
                    pass
                # set functions
                pllfunc = self._negated_pll_scaled
                gradfunc = self._negated_grad_pll_scaled
            else:
                pllfunc = self._negated_pll
                gradfunc = self._negated_grad_pll
            print "starting optimization..."
            wt, pll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags  = fmin_bfgs(pllfunc, wt, fprime=gradfunc, full_output=1)
            #wt, pll_opt, func_calls, grad_calls, warn_flags  = fmin_cg(pllfunc, wt, fprime=gradfunc, full_output=1)
            self.learnwts_message = "pseudo-log-likelihood: %.16f\ngradient: %s\nfunction evaluations: %d\nwarning flags: %d\n" % (-pll_opt, str(-grad_opt), func_calls, warn_flags)
        elif mode in ['LL', 'LL_scaled', 'LL_neg', 'LL_fixed', 'LL_fixed_neg']:
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
            idxTrainingDB = self._getEvidenceWorldIndex()
            # mode-specific stuff
            gradfunc = self._grad_ll
            if mode == 'LL_scaled':
                self._getFormulaScales()
                old_function = self._calculateWorldValues
                self._calculateWorldValues = self._calculateWorldValues_scaled
                llfunc = self._ll_scaled
                for (key, count) in self.counts.iteritems():
                    self.counts[key] *= self.formulaScales[key[1]]
            else:
                llfunc = self._ll
            args = [idxTrainingDB]
            if mode == 'LL_fixed' or mode == 'LL_fixed_neg':
                args.append(params['fixedWeights'])
                gradfunc = self._grad_ll_fixed
                for idxFormula, weight in params["fixedWeights"].iteritems():
                    wt[idxFormula] = weight
            # opt
            print "starting optimization..."
            gtol = 1.0000000000000001e-005
            neg_llfunc = lambda params, *args: -llfunc(params, *args)
            neg_gradfunc = lambda params, *args: -gradfunc(params, *args)
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
            if mode == 'LL_scaled':
                self._calculateWorldValues = old_function
        elif mode == 'BPLL':
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
                mm = [-30, 30]
                step = 3
                w1 = mm[0]
                while w1 <= mm[1]:
                    w2 = mm[0]
                    while w2 <= mm[1]:
                        print "%s," % [w1, w2, self._blockpll([w1, w2])],
                        w2 += 3
                    w1 += 3
                return
            
            wt, pll_opt, grad_opt, Hopt, func_calls, grad_calls, warn_flags  = fmin_bfgs(self._negated_blockpll, wt, fprime=self._negated_grad_blockpll, full_output=1)
            #wt, pll_opt, func_calls, grad_calls, warn_flags  = fmin_cg(self._negated_blockpll, wt, fprime=self._negated_grad_blockpll, full_output=1)
            #wt, func_calls, grad_calls, hcalls, warn_flags  = fmin_ncg(self._negated_blockpll, wt, fprime=self._negated_grad_blockpll, full_output=1)
            #wt, ll_opt, warn_flags  = fmin_tnc(self._negated_blockpll, wt, fprime=self._negated_grad_blockpll)
            if grad_opt != None:
                grad_str = "\n%s" % str(-grad_opt)
            else:
                grad_str = ""
            if pll_opt == None:
                pll_opt = self._blockpll(wt)
            self.learnwts_message = "pseudo-log-likelihood: %.16f%s\nfunction evaluations: %d\nwarning flags: %d\n" % (-pll_opt, grad_str, func_calls, warn_flags)
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
            bounds = [(1e-10, 1.0-1e-10) for i in range(len(wt))]
            if mode == 'LL_fac':
                gradfunc = self._grad_ll_fac
                llfunc = self._ll_fac
            elif mode == 'LL_prob':
                gradfunc = self._grad_ll_prob
                llfunc = self._ll_prob
            neg_llfunc = lambda params, *args: -llfunc(params, *args)
            neg_gradfunc = lambda params, *args: -gradfunc(params, *args)
            wt, ll_opt, d = fmin_l_bfgs_b(neg_llfunc, wt, fprime=neg_gradfunc, args=[idxTrainingDB], bounds=bounds)
            warn_flags = d['warnflag']
            func_calls = d['funcalls']
            grad_opt = d['grad']
            self.learnwts_message = "log-likelihood: %f\ngradient: %s\nfunction evaluations: %d\nwarning flags: %d\n" % (-ll_opt, str(-grad_opt), func_calls, warn_flags)
        else:
            raise Exception("Unknown mode")
        # use obtained vector to reset formula weights
        for i,f in enumerate(self.formulas):
            f.weight = wt[i]

    def write(self, f, mutexInDecls = True):
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
            f.write("%f  %s\n" % (float(eval(str(formula.weight))), strFormula(formula)))

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
            counts[numTrue] = old_cnt+1
        print counts

    # returns array of array of int a with a[i][j] = number of true groundings of j-th formula in i-th world
    def countTrueGroundingsForEachWorld(self, appendToWorlds = False):
        all = []
        self._getWorlds()
        for world in self.worlds:
            counts = [0 for i in range(len(self.formulas))]
            for gf in self.gndFormulas:
                if self._isTrue(gf, world["values"]):
                    counts[gf.idxFormula] += 1
            all.append(counts)
        if appendToWorlds:
                for (i,world) in enumerate(self.worlds):
                        self.worlds[i]["counts"] = all[i]
        return all

    def _calculateWorldValues2(self, wts = None):
        if wts == None:
            wts = self._weights()
        total = 0
        for world in self.worlds:
            prob = 1.0
            for gndFormula in self.gndFormulas:
                if self._isTrue(gndFormula, world["values"]):
                    prob *= wts[gndFormula.idxFormula]
                else:
                    prob *= (1-wts[gndFormula.idxFormula])
            world["prod"] = prob
            total += prob
        self.partition_function = total

    def _calculateWorldValues_prob(self, wts = None):
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

    # prints the expected number of true groundings of each formula
    def printExpectedNumberOfGroundings(self):
        self._getWorlds()
        counts = [0.0 for i in range(len(self.formulas))]
        for world in self.worlds:            
            for gf in self.gndFormulas:
                if self._isTrue(gf, world["values"]):
                    counts[gf.idxFormula] += world["sum"] / self.partition_function
        #print counts
        for i, formula in enumerate(self.formulas):
            print "%f %s" % (counts[i], str(formula))

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
            print "%f %s" % (sums[i]/totals[i], str(formula))

    # prints all the ground formulas
    def printGroundFormulas(self):
        for gf in self.gndFormulas:
            print "%7.3f  %s" % (self.formulas[gf.idxFormula].weight, strFormula(gf))
    
    def printGroundAtoms(self):
        l = self.gndAtoms.keys()
        l.sort()
        for ga in l:
            print ga
    
    def strGroundAtom(self, idx):
        return str(self.gndAtomsByIdx[idx])

    def printFormulas(self):
        for f in self.formulas:
            print "%7.3f  %s" % (f.weight, strFormula(f))

    def setRigidPredicate(self, predName):
        self.rigidPredicates.append(predName)

    def _getFormulaScales(self):
        maxcounts = {}
        counts = []
        for formula in self.formulas:
            vars = formula.getVariables(self)
            domcount = {}
            for (var,dom) in vars.iteritems():
                domcount[dom] = domcount.get(dom,0)+1
            counts.append(domcount)
            for dom in domcount:
                maxcounts[dom] = max(maxcounts.get(dom,0), domcount[dom])
        scales = []
        for cnt in counts:
            scale = 1
            for dom in maxcounts:
                scale *= len(self.domains[dom]) ** (maxcounts[dom] - cnt.get(dom,0))
            scales.append(scale)
        self.formulaScales = scales

    def applyFormulaScales(self):
        self._getFormulaScales()
        for i,formula in enumerate(self.formulas):
            formula.weight *= self.formulaScales[i]

    # write a .dot file for use with GraphViz (in order to visualize the current ground Markov network)
    # must call one of the combine* functions first
    def writeDotFile(self, filename):
        if not hasattr(self, "gndFormulas") or len(self.gndFormulas) == 0:
            raise Exception("Error: cannot create graph because the MLN was not combined with a concrete domain")
        f = file(filename, "wb")
        f.write("graph G {\n")
        graph = {}
        for gf in self.gndFormulas:
            idxGndAtoms = gf.idxGroundAtoms()
            for i in range(len(idxGndAtoms)):
                for j in range(i+1, len(idxGndAtoms)):
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
    
    def printState(self, world_values, showIndices = False):
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


class Inference:
    def __init__(self, mln):
        self.mln = mln
        self.t_start = time.time()
    
    def _readQueries(self, queries):
        # check for single/multiple query and expand
        if type(queries) != list:
            queries = [queries]
        queries = self.mln._expandQueries(queries)
        queries.sort()
        # parse queries
        self.queries = []
        for q in queries:
            q = FOL.parseFormula(q)
            self.queries.append(q.ground(self.mln, {}))
    
    # set evidence in the MLN according to the given conjunction of ground literals
    def _setEvidence(self, conjunction):
        if conjunction is not None:
            givenAtoms = map(lambda x: x.strip().replace(" ", ""), conjunction.split("^"))
            for gndAtom in givenAtoms:
                if gndAtom == '': continue
                tv = True
                if(gndAtom[0] == '!'):
                    tv = False
                    gndAtom = gndAtom[1:]
                idxGA = self.mln.gndAtoms[gndAtom].idx
                self.mln._setEvidence(idxGA, tv) # set evidence in MLN
                if idxGA in self.mln.gndBlockLookup: # if the ground atom is in a block and its value is true, set all the other atoms in the same block to false
                    block = self.mln.gndBlocks[self.mln.gndBlockLookup[idxGA]]
                    if tv:
                        for i in block:
                            if i != idxGA:
                                self.mln._setEvidence(i, False)
        # handle closed-world predicates: Set all their instances that aren't yet known to false
        for pred in self.mln.closedWorldPreds:
            for idxGA in self.mln._getPredGroundingsAsIndices(pred):
                if self.mln._getEvidence(idxGA, False) == None:
                    self.mln._setEvidence(idxGA, False)

    def _getElapsedTime(self):
        elapsed = time.time() - self.t_start
        hours = int(elapsed / 3600)
        elapsed -= hours * 3600
        minutes = int(elapsed / 60)
        elapsed -= minutes * 60
        secs = int(elapsed)
        msecs = int((elapsed - secs) * 1000)
        return "%d:%02d:%02d.%03d" % (hours, minutes, secs, msecs)

    def infer(self, queries, given = None, verbose = True, details = False, shortOutput = False, outFile = None, **args):
        self.given = given
        self.evidenceString = given
        if self.evidenceString == None: self.evidenceString = "True"
        # read queries
        self._readQueries(queries)
        self.additionalQueryInfo = [""] * len(self.queries)
        # perform actual inference (polymorphic)
        self.results = self._infer(verbose=verbose, details=details, **args)
        # output
        if verbose:
            if details: print "\nresults:"
            self.writeResults(sys.stdout, shortOutput = shortOutput)
        if outFile != None:
            self.writeResults(outFile, shortOutput = True)
        # return results
        if len(self.queries) > 1:
            return self.results
        else:
            return self.results[0]
    
    def writeResults(self, out, shortOutput = True):
        # determine maximum query length to beautify output
        if shortOutput:
            maxLen = 0
            for q in self.queries:
                maxLen = max(maxLen, len(strFormula(q)))
        # print results, one per line
        for i in range(len(self.queries)):
            addInfo = self.additionalQueryInfo[i]
            if not shortOutput:
                out.write("P(%s | %s) = %f  %s\n" % (strFormula(self.queries[i]), self.evidenceString, self.results[i], addInfo))
            else:
                out.write("%f  %-*s  %s\n" % (self.results[i], maxLen, strFormula(self.queries[i]), addInfo))

class ExactInference(Inference):
    def __init__(self, mln):
        Inference.__init__(self, mln)
    
    # verbose: whether to print results (or anything at all, in fact)
    # details: (given that verbose is true) whether to output additional status information
    # debug: (given that verbose is true) if true, outputs debug information, in particular the distribution over possible worlds
    # debugLevel: level of detail for debug mode
    def _infer(self, verbose = True, details = False, shortOutput = False, debug = False, debugLevel = 1, **args):
        worldValueKey = {"weights": "sum", "probs": "prod"}[self.mln.parameterType]
        # get set of possible worlds along with their probabilities
        if verbose and details: print "generating possible worlds..."
        self.mln._getWorlds() 
        if verbose and details: print "  %d worlds instantiated" % len(self.mln.worlds)
        # get the query formula(s)
        what = self.queries
        # ground evidence formula
        # - set evidence according to given conjunction (plus apply the closed-world assumption if requested)
        self._setEvidence(self.given)
        # - get the new evidence conjunction
        given = evidence2conjunction(self.mln.getEvidenceDatabase())
        # - obtain the corresponding formula object
        if given is not None and given.strip() == "": given = None
        if given != None:
            given = FOL.parseFormula(given)
            given = given.ground(self.mln, {})
        # start summing
        if verbose and details: print "summing..."
        numerators = [0.0 for i in range(len(what))]
        denominator = 0
        k = 1
        numerator_worlds = [[] for i in range(len(what))]
        denominator_worlds = []
        for world in self.mln.worlds:
            precond = (given == None)
            if not precond:
                precond = given.isTrue(world["values"])
            if precond:
                for i in range(len(what)):
                    if what[i].isTrue(world["values"]):
                        numerators[i] += world[worldValueKey]
                        numerator_worlds[i].append(k)
                denominator += world[worldValueKey]
                denominator_worlds.append(k)
            k += 1
        # normalize answers
        answers = []
        for i in range(len(what)):
            answers.append(numerators[i] / denominator)
        # some debug info
        if debug and verbose:  
            self.mln.printWorlds()
            for i in range(len(what)):
                self.additionalQueryInfo[i] = "\n   %s / %s " % (numerator_worlds[i], denominator_worlds)
        # return results
        return answers

# abstract super class for Markov chain Monte Carlo-based inference
class MCMCInference(Inference):
    # set a random state, taking the evidence blocks and block exclusions into account
    # blockInfo [out]
    def setRandomState(self, state, blockInfo = None):
        mln = self.mln
        for idxBlock, (idxGA, block) in enumerate(mln.pllBlocks):
            if idxBlock not in self.evidenceBlocks:
                if block != None: # block of mutually exclusive atoms
                    blockExcl = self.blockExclusions.get(idxBlock)
                    if blockExcl == None:
                        chosen = block[random.randint(0, len(block)-1)]
                        for idxGA in block:
                            state[idxGA] = (idxGA == chosen)
                        if blockInfo != None:
                            falseOnes = filter(lambda x: x != chosen, block)
                            blockInfo[idxBlock] = [chosen, falseOnes]
                    else:
                        choosable = []
                        for i,idxGA in enumerate(block):
                            if i not in blockExcl:
                                choosable.append(idxGA)
                        chosen = choosable[random.randint(0, len(choosable)-1)]
                        for idxGA in choosable:
                            state[idxGA] = (idxGA == chosen)
                        if blockInfo != None:
                            choosable.remove(chosen)
                            blockInfo[idxBlock] = [chosen, choosable]
                else: # regular ground atom, which can either be true or false
                    chosen = random.randint(0, 1)
                    state[idxGA] = bool(chosen)

    def _readEvidence(self, conjunction):
        if conjunction == "": conjunction = None
        self.evidence = conjunction
        self.evidenceBlocks = [] # list of pll block indices where we know the true one (and thus the setting for all of the block's atoms)
        self.blockExclusions = {} # dict: pll block index -> list (of indices into the block) of atoms that mustn't be set to true
        self.mln._clearEvidence() # clear any existing evidence
        if conjunction != None:
            # read atoms in evidence conjunction and set evidence accordingly
            self._setEvidence(conjunction)
            # fill the list of blocks that we have evidence for
            for idxBlock, (idxGA, block) in enumerate(self.mln.pllBlocks):
                if block != None:
                    haveTrueone = False
                    falseones = []
                    for i, idxGA in enumerate(block):
                        ev = self.mln._getEvidence(idxGA, False)
                        if ev == True:
                            haveTrueone = True
                            break
                        elif ev == False:
                            falseones.append(i)
                    if haveTrueone:
                        self.evidenceBlocks.append(idxBlock)
                    elif len(falseones) > 0:
                        self.blockExclusions[idxBlock] = falseones
                else:
                    if self.mln._getEvidence(idxGA, False) != None:
                        self.evidenceBlocks.append(idxBlock)

    class Chain:
        def __init__(self, inferenceObject, queries):
            self.queries = queries
            self.numSteps = 0
            self.numTrue = [0 for i in range(len(self.queries))]
            self.converged = False
            self.lastResult = 10
            # copy the current mln evidence as this chain's state
            self.state = list(inferenceObject.mln.evidence)
            # initialize remaining variables randomly (but consistently)
            inferenceObject.setRandomState(self.state)
        
        def update(self):
            debug = False
            self.numSteps += 1
            # keep track of counts for queries
            for i in range(len(self.queries)):
                if self.queries[i].isTrue(self.state):
                    self.numTrue[i] += 1
            # check if converged !!! TODO check for all queries
            if self.numSteps % 50 == 0:
                currentResult = self.getResults()[0]
                diff = abs(currentResult - self.lastResult)
                #print diff
                if diff < 0.001:
                    self.converged = True
                self.lastResult = currentResult
            # debug output
            if self.numSteps % 50 == 0 and debug:
                #print "  --> %s" % str(self.state),
                #print "after %d steps: P(%s | e) = %f" % (self.numSteps, str(self.query), float(self.numTrue) / self.numSteps)
                pass
        
        def getResults(self):
            results = []
            for i in range(len(self.queries)):
                results.append(float(self.numTrue[i]) / self.numSteps)
            return results
    
            
    class ChainGroup:
        def __init__(self, inferObject):
            self.chains = []
            self.inferObject = inferObject
    
        def addChain(self, chain):
            self.chains.append(chain)
    
        def getResults(self):
            numChains = len(self.chains)
            queries = self.chains[0].queries
            # compute average
            results = [0.0 for i in range(len(queries))]
            for chain in self.chains:
                cr = chain.getResults()
                for i in range(len(queries)):
                    results[i] += cr[i] / numChains
            # compute variance
            var = [0.0 for i in range(len(queries))]
            for chain in self.chains:
                cr = chain.getResults()
                for i in range(len(self.chains[0].queries)):
                    var[i] += (cr[i]-results[i])**2 / numChains
            self.var = var
            self.results = results
            return results
        
        def printResults(self, shortOutput = False):
            # determine maximum query length to beautify output
            if shortOutput:
                maxLen = 0
                for q in self.inferObject.queries:
                    maxLen = max(maxLen, len(strFormula(q)))
            # print results, one per line
            for i in range(len(self.inferObject.queries)):
                chainInfo = "[%dx%d steps, sd=%.3f]" % (len(self.chains), self.chains[0].numSteps, sqrt(self.var[i]))
                if not shortOutput:
                    print "P(%s | %s) = %f  %s" % (strFormula(self.inferObject.queries[i]), self.inferObject.evidence, self.results[i], chainInfo)
                else:
                    print "%f  %-*s  %s" % (self.results[i], maxLen, strFormula(self.inferObject.queries[i]), chainInfo)


class GibbsSampler(MCMCInference):
    class Chain(MCMCInference.Chain):
        def __init__(self, gibbsSampler):
            self.gs = gibbsSampler
            MCMCInference.Chain.__init__(self, gibbsSampler, gibbsSampler.queries)
            # run walksat
            mws = SAMaxWalkSAT(self.state, self.gs.mln, self.gs.evidenceBlocks)
            mws.run()
        
        def step(self, debug = False):
            mln = self.gs.mln
            if debug: 
                print "step %d" % (self.numSteps+1)
                #time.sleep(1)
                pass
            # reassign values by sampling from the conditional distributions given the Markov blanket
            wt = mln._weights()
            for idxBlock, (idxGA, block) in enumerate(mln.pllBlocks):
                if idxBlock in self.gs.evidenceBlocks: # do not sample if we have evidence 
                    continue
                if block != None:
                    expsums = mln._getBlockExpsums(block, wt, self.state, None, mln.blockRelevantGFs[idxBlock])
                    if idxBlock in self.gs.blockExclusions:
                        for i in self.gs.blockExclusions[idxBlock]:
                            expsums[i] = 0
                else:
                    expsums = mln._getAtomExpsums(idxGA, wt, self.state, mln.blockRelevantGFs[idxBlock])
                r = random.uniform(0, sum(expsums))
                idx = 0
                s = expsums[0]
                while r > s:
                    idx += 1
                    s += expsums[idx]
                if block != None:
                    for i, idxGA in enumerate(block):
                        tv = (i == idx)
                        self.state[idxGA] = tv
                    if debug: print "  setting block %s to %s, odds = %s" % (str(map(lambda x: str(mln.gndAtomsByIdx[x]), block)), str(mln.gndAtomsByIdx[block[idx]]), str(expsums))
                else:
                    self.state[idxGA] = bool(idx)
                    if debug: print "  setting atom %s to %s" % (str(mln.gndAtomsByIdx[idxGA]), bool(idx))
            # update results
            self.update()
    
    def __init__(self, mln):
        print "initializing Gibbs sampler...",
        Inference.__init__(self, mln)
        # check compatibility with MLN
        for f in mln.formulas:
            if not f.isLogical():
                raise Exception("GibbsSampler does not support non-logical constraints such as '%s'!" % strFormula(f))
        # get the pll blocks
        mln._getPllBlocks()
        # get the list of relevant ground atoms for each block
        mln._getBlockRelevantGroundFormulas()
        print "done."

    # infer one or more probabilities P(F1 | F2)
    #   what: a ground formula (string) or a list of ground formulas (list of strings) (F1)
    #   given: a formula as a string (F2)
    def _infer(self, verbose = True, numChains = 3, maxSteps = 5000, shortOutput = False, details = False, debug = False, debugLevel = 1, infoInterval = 10, resultsInterval = 100):
        random.seed(time.time())
        # set evidence according to given conjunction (if any)
        self._readEvidence(self.given)
        # initialize chains
        if verbose and details:
            print "initializing %d chain(s)..." % numChains
        chainGroup = MCMCInference.ChainGroup(self)
        for i in range(numChains):
            chainGroup.addChain(GibbsSampler.Chain(self))
        # do gibbs sampling
        if verbose and details: print "sampling..."
        converged = 0
        numSteps = 0
        minSteps = 200
        while converged != numChains and numSteps < maxSteps:
            converged = 0
            numSteps += 1
            for chain in chainGroup.chains:
                chain.step(debug=debug)
                if chain.converged and numSteps >= minSteps:
                    converged += 1
            if verbose and details:
                if numSteps % infoInterval == 0:
                    print "step %d (fraction converged: %.2f)" % (numSteps, float(converged) / numChains)
                if numSteps % resultsInterval == 0:
                    chainGroup.getResults()
                    chainGroup.printResults(shortOutput=True)
        # get the results
        return chainGroup.getResults()


class MCSAT(MCMCInference):
    def __init__(self, mln, verbose = False):
        Inference.__init__(self, mln)
        # minimize the formulas' weights by exploiting group information (in order to speed up convergence)
        if verbose: print "normalizing weights..."
        #mln.minimizeGroupWeights() # TODO!!! disabled because weights are not yet evaluated
        # initialize the KB and gather required info
        self._initKB(verbose)
        # get the pll blocks
        if verbose: print "getting blocks..."
        mln._getPllBlocks()
        # get the list of relevant ground atoms for each block (!!! only needed for SAMaxWalkSAT actually)
        mln._getBlockRelevantGroundFormulas()
        # get the block index for each ground atom
        mln._getAtom2BlockIdx()
    
    # initialize the knowledge base to the required format and collect structural information for optimization purposes
    def _initKB(self, verbose = False):
        # convert the MLN ground formulas to CNF
        if verbose: print "converting formulas to CNF..."
        self.mln._toCNF(allPositive=True)
        # get clause data
        if verbose: print "gathering clause data..."
        self.gndFormula2ClauseIdx = {} # ground formula index -> tuple (idxFirstClause, idxLastClause+1) for use with range
        self.clauses = [] # list of clauses, where each entry is a list of ground literals
        #self.GAoccurrences = {} # ground atom index -> list of clause indices (into self.clauses)
        idxClause = 0
        # process all ground formulas
        for idxGndFormula,f in enumerate(self.mln.gndFormulas):
            # get the list of clauses
            if type(f) == FOL.Conjunction:
                lc = f.children
            else:
                lc = [f]
            self.gndFormula2ClauseIdx[idxGndFormula] = (idxClause, idxClause + len(lc))
            # process each clause
            for c in lc:
                if hasattr(c, "children"):
                    lits = c.children
                else: # unit clause
                    lits = [c]
                # add clause to list
                self.clauses.append(lits)
                # next clause index
                idxClause += 1
    
    def _infer(self, numChains = 1, maxSteps = 5000, verbose = True, shortOutput = False, details = True, debug = False, debugLevel = 1, initAlgo = "SampleSAT", randomSeed=None, infoInterval=None, resultsInterval=None):
        self.debug = debug
        self.debugLevel = debugLevel
        t_start = time.time()
        details = verbose and details
        # set the random seed if it was given
        if randomSeed != None:
            random.seed(randomSeed)
        # read evidence
        if details: print "reading evidence..."
        self._readEvidence(self.given)
        if details:
            print "evidence blocks: %d" % len(self.evidenceBlocks)
            print "block exclusions: %d" % len(self.blockExclusions)
            print "initializing %d chain(s)..." % numChains
        # create chains
        chainGroup = MCMCInference.ChainGroup(self)
        mln = self.mln
        self.wt = mln._weights()
        for i in range(numChains):
            chain = MCMCInference.Chain(self, self.queries)
            chainGroup.addChain(chain)
            # satisfy hard constraints using initialization algorithm
            if initAlgo == "SAMaxWalkSAT":
                mws = SAMaxWalkSAT(chain.state, self.mln, self.evidenceBlocks)
                mws.run()
            elif initAlgo == "SampleSAT":
                M = []
                NLC = []
                for idxGF,gf in enumerate(mln.gndFormulas):
                    if self.wt[gf.idxFormula] >= 10:
                        if gf.isLogical():
                            clauseRange = self.gndFormula2ClauseIdx[idxGF]
                            M.extend(range(clauseRange[0], clauseRange[1]))
                        else:
                            NLC.append(gf)
                ss = SampleSAT(mln, chain.state, M, NLC, self, p=0.8, debug=debug and debugLevel >= 2) # Note: can't use p=1.0 because there is a chance of getting into an oscillating state
                ss.run()
            else:
                raise Exception("MC-SAT Error: Unknown initialization algorithm specified")
            # some debug info
            if debug:
                print "\ninitial state:"
                mln.printState(chain.state)
        # do MCSAT sampling
        if details: print "sampling... time elapsed: %s" % self._getElapsedTime()
        if debug: print
        if infoInterval is None: infoInterval = {True:1, False:10}[debug]
        if resultsInterval is None: resultsInterval = {True:1, False:50}[debug]
        step = 1
        while step <= maxSteps:
            for chain in chainGroup.chains:
                if debug: 
                    print "step %d..." % step
                # choose a subset of the satisfied formulas and sample a state that satisfies them
                numSatisfied = self._satisfySubset(chain)
                # update chain counts
                chain.update()
                # print progress
                if details and step % infoInterval == 0:
                    print "step %d (%d constraints were to be satisfied), time elapsed: %s" % (step, numSatisfied, self._getElapsedTime())
                    if debug:
                        self.mln.printState(chain.state)
                        print
            if details and step % resultsInterval == 0 and step != maxSteps:
                chainGroup.getResults()
                chainGroup.printResults(shortOutput=True)
                if debug: print
            step += 1
        # get results
        return chainGroup.getResults()
    
    def _satisfySubset(self, chain):
        # choose a set of logical formulas M to be satisfied (more specifically, M is a set of clause indices)
        # and also choose a set of non-logical constraints NLC to satisfy
        t0 = time.time()
        M = []
        NLC = []
        for idxGF, gf in enumerate(self.mln.gndFormulas):
            if gf.isTrue(chain.state):                
                    u = random.uniform(0, exp(self.wt[gf.idxFormula]))
                    if u > 1:
                        if gf.isLogical():
                            clauseRange = self.gndFormula2ClauseIdx[idxGF]
                            M.extend(range(clauseRange[0], clauseRange[1]))
                        else:
                            NLC.append(gf)
                        if self.debug and self.debugLevel >= 3:
                            print "  to satisfy:", strFormula(gf)
        # (uniformly) sample a state that satisfies them
        t1 = time.time()
        ss = SampleSAT(self.mln, chain.state, M, NLC, self, debug=self.debug and self.debugLevel >= 2)
        t2 = time.time()
        ss.run()
        t3 = time.time()
        #print "select formulas: %f, SS init: %f, SS run: %f" % (t1-t0, t2-t1, t3-t2)
        return len(M) + len(NLC)


class SampleSAT:
    # clauseIdxs: list of indices of clauses to satisfy
    # p: probability of performing a random walk move
    # state: the state (array of booleans) to work with (is reinitialized randomly by this constructor)
    # NLConstraints: list of grounded non-logical constraints
    def __init__(self, mln, state, clauseIdxs, NLConstraints, inferObject, p = 0.5, debug = False):
        t_start = time.time()
        self.debug = debug
        self.inferObject = inferObject
        self.state = state
        self.mln = mln        
        self.p = p
        # initialize the state randomly (considering the evidence) and obtain block info
        t1 = time.time()
        self.blockInfo = {}
        self.inferObject.setRandomState(self.state, blockInfo=self.blockInfo)
        t2 = time.time()
        if debug: mln.printState(self.state)
        # list of unsatisfied constraints
        self.unsatisfiedConstraints = []
        # keep a map of bottlenecks: index of the ground atom -> list of constraints where the corresponding lit is a bottleneck
        self.bottlenecks = {}
        # ground atom occurrences in constraints: ground atom index -> list of constraints
        self.GAoccurrences = {}
        # instantiate clauses        
        for idxClause in clauseIdxs:            
            SampleSAT._Clause(self, idxClause)
        # instantiate non-logical constraints
        for nlc in NLConstraints:
            if isinstance(nlc, FOL.GroundCountConstraint): # count constraint
                SampleSAT._CountConstraint(self, nlc)
            else:
                raise Exception("SampleSAT cannot handle constraints of type '%s'" % str(type(nlc)))
        t3 = time.time()
        #print "init time: %f" % (time.time()-t_start)
        #print "random state: %f, init constraints: %f" % (t3-t2,t2-t1)
    
    def _addGAOccurrence(self, idxGA, constraint):
        '''add ground atom occurrence in constraint'''
        occ = self.GAoccurrences.get(idxGA)
        if occ == None:
            occ = []
            self.GAoccurrences[idxGA] = occ
        occ.append(constraint)
    
    class _Clause:
        def __init__(self, sampleSAT, idxClause):
            self.ss = sampleSAT
            self.idxClause = idxClause
            self.lits = sampleSAT.inferObject.clauses[idxClause]
            # check all the literals
            numTrue = 0
            idxTrueGndAtoms = {}
            for gndLit in self.lits:
                idxGA = gndLit.gndAtom.idx
                if gndLit.isTrue(self.ss.state):
                    numTrue += 1
                    idxTrueGndAtoms[idxGA] = True
                # save ground atom occurrence
                self.ss._addGAOccurrence(idxGA, self)
            # save clause data
            self.trueGndLits = idxTrueGndAtoms
            if numTrue == 1:
                self.ss._addBottleneck(idxTrueGndAtoms.keys()[0], self)
            elif numTrue == 0:
                self.ss.unsatisfiedConstraints.append(self)
        
        def greedySatisfy(self):
            self.ss._pickAndFlipLiteral(map(lambda x: x.gndAtom.idx, self.lits), self)
        
        def handleFlip(self, idxGA):
            '''handle all effects of the flip except bottlenecks of the flipped gnd atom and clauses that became unsatisfied as a result of a bottleneck flip'''
            trueLits = self.trueGndLits
            numTrueLits = len(trueLits)
            if idxGA in trueLits: # the lit was true and is now false, remove it from the clause's list of true lits
                del trueLits[idxGA]
                numTrueLits -= 1
                # if no more true lits are left, the clause is now unsatisfied; this is handled in flipGndAtom
            else: # the lit was false and is now true, add it to the clause's list of true lits
                if numTrueLits == 0: # the clause was previously unsatisfied, it is now satisfied
                    self.ss.unsatisfiedConstraints.remove(self)
                elif numTrueLits == 1: # we are adding a second true lit, so the first one is no longer a bottleneck of this clause
                    self.ss.bottlenecks[trueLits.keys()[0]].remove(self)
                trueLits[idxGA] = True
                numTrueLits += 1
            if numTrueLits == 1:
                self.ss._addBottleneck(trueLits.keys()[0], self)
        
        def flipSatisfies(self, idxGA):
            '''returns true iff the constraint is currently unsatisfied and flipping the given ground atom would satisfy it'''
            return len(self.trueGndLits) == 0
        
        def __str__(self):
            return " v ".join(map(lambda x: strFormula(x), self.lits))
    
        def getFormula(self):
            ''' gets the original formula that the clause with the given index is part of
                (this is slow and should only be used for informational purposes, e.g. error reporting)'''
            i = 0
            for f in self.ss.mln.gndFormulas:
                if not f.isLogical():
                    continue
                if type(f) == FOL.Conjunction:
                    n = len(f.children)
                else:
                    n = 1
                if self.idxClause < i+n:
                    return f
                i += n
                
    class _CountConstraint:
        def __init__(self, sampleSAT, groundCountConstraint):
            self.ss = sampleSAT
            self.cc = groundCountConstraint
            self.trueOnes = []
            self.falseOnes = []
            # determine true and false ones
            for ga in groundCountConstraint.gndAtoms:
                idxGA = ga.idx
                if self.ss.state[idxGA]:
                    self.trueOnes.append(idxGA)
                else:
                    self.falseOnes.append(idxGA)
                self.ss._addGAOccurrence(idxGA, self)
            # determine bottlenecks
            self._addBottlenecks()
            # if the formula is unsatisfied, add it to the list
            if not self._isSatisfied():
                self.ss.unsatisfiedConstraints.append(self)
        
        def _isSatisfied(self):
            return eval("len(self.trueOnes) %s self.cc.count" % self.cc.op)
        
        def _addBottlenecks(self):
            # there are only bottlenecks if we are at the border of the interval
            numTrue = len(self.trueOnes)
            if self.cc.op == "!=":
                trueNecks = numTrue == self.cc.count + 1
                falseNecks = numTrue == self.cc.count - 1
            else:
                border = numTrue == self.cc.count
                trueNecks = border and self.cc.op in ["==", ">="]
                falseNecks = border and self.cc.op in ["==", "<="]
            if trueNecks:
                for idxGA in self.trueOnes:
                    self.ss._addBottleneck(idxGA, self)
            if falseNecks:
                for idxGA in self.falseOnes:
                    self.ss._addBottleneck(idxGA, self)
        
        def greedySatisfy(self):
            c = len(self.trueOnes)
            satisfied = self._isSatisfied()
            assert not satisfied
            if c < self.cc.count and not satisfied:
                self.ss._pickAndFlipLiteral(self.falseOnes, self)
            elif c > self.cc.count and not satisfied:
                self.ss._pickAndFlipLiteral(self.trueOnes, self)
            else: # count must be equal and op must be !=
                self.ss._pickAndFlipLiteral(self.trueOnes+self.falseOnes, self)
        
        def flipSatisfies(self, idxGA):
            if self._isSatisfied():
                return False
            c = len(self.trueOnes)            
            if idxGA in self.trueOnes:
                c2 = c-1
            else:
                assert idxGA in self.falseOnes
                c2 = c+1
            return eval("c2 %s self.cc.count" % self.cc.op)
        
        def handleFlip(self, idxGA):
            '''handle all effects of the flip except bottlenecks of the flipped gnd atom and clauses that became unsatisfied as a result of a bottleneck flip'''
            wasSatisfied = self._isSatisfied()
            # update true and false ones
            if idxGA in self.trueOnes:
                self.trueOnes.remove(idxGA)
                self.falseOnes.append(idxGA)
            else:
                self.trueOnes.append(idxGA)
                self.falseOnes.remove(idxGA)
            isSatisfied = self._isSatisfied()
            # if the constraint was previously satisfied and is now unsatisfied or
            # if the constraint was previously satisfied and is still satisfied (i.e. we are pushed further into the satisfying interval, away from the border),
            # remove all the bottlenecks (if any)
            if wasSatisfied:
                for idxGndAtom in self.trueOnes + self.falseOnes: 
                    if idxGndAtom in self.ss.bottlenecks and self in self.ss.bottlenecks[idxGndAtom]: # TODO perhaps have a smarter method to know which ones actually were bottlenecks (or even info about whether we had bottlenecks)
                        if idxGA != idxGndAtom:
                            self.ss.bottlenecks[idxGndAtom].remove(self)
                # the constraint was added to the list of unsatisfied ones in SampleSAT._flipGndAtom (bottleneck flip)
            # if the constraint is newly satisfied, remove it from the list of unsatisfied ones
            elif not wasSatisfied and isSatisfied:
                self.ss.unsatisfiedConstraints.remove(self)
            # bottlenecks must be added if, because of the flip, we are now at the border of the satisfying interval
            self._addBottlenecks()
            
        def __str__(self):
            return str(self.cc)
    
        def getFormula(self):
            return self.cc
    
    def _addBottleneck(self, idxGndAtom, constraint):
        bn = self.bottlenecks.get(idxGndAtom)
        if bn == None:
            bn = []
            self.bottlenecks[idxGndAtom] = bn
        bn.append(constraint)
    
    def _printUnsatisfiedConstraints(self):
        for constraint in self.unsatisfiedConstraints:
            print "    %s" % str(constraint)
    
    def run(self):
        t_start = time.time()
        p = self.p # probability of performing a WalkSat move
        iter = 1
        steps = [0,0]
        times = [0.0, 0.0]
        while len(self.unsatisfiedConstraints) > 0:
            
            # in debug mode, check if really exactly the unsatisfied clauses are in the corresponding list
            if False and self.debug:
                for idxClause,clause in enumerate(self.realClauses):
                    isTrue = clause.isTrue(self.state)
                    if not isTrue:
                        if idxClause not in self.unsatisfiedClauseIdx:
                            print "    %s is unsatisfied but not in the list" % strFormula(clause)
                    else:
                        if idxClause in self.unsatisfiedClauseIdx:
                            print "    %s is satisfied but in the list" % strFormula(clause)
            
            if self.debug and False:
                self.mln.printState(self.state, True)
                print "bottlenecks:", self.bottlenecks
            
            # make a WalkSat move or a simulated annealing move
            if random.uniform(0, 1) <= p:
                if self.debug:
                    print "%d random walk (%d left)" % (iter, len(self.unsatisfiedConstraints))
                    self._printUnsatisfiedConstraints()
                #t = time.time()
                self._walkSatMove()
                #steps[0] += 1
                #times[0] += time.time()-t
            else:
                if self.debug:
                    print "%d SA (%d left)" % (iter, len(self.unsatisfiedConstraints))
                    self._printUnsatisfiedConstraints()
                #t = time.time()
                self._SAMove()
                #steps[1] += 1
                #times[1] += time.time()-t
            iter += 1
        #print "run time: %f" % (time.time()-t_start)
        #print "avg rw time: %f" % (times[0]/steps[0])
        #print "avg sa time: %f" % (times[1]/steps[1])
    
    def _walkSatMove(self):
        '''randomly pick one of the unsatisfied constraints and satisfy it (or at least make one step towards satisfying it'''
        constraint = self.unsatisfiedConstraints[random.randint(0, len(self.unsatisfiedConstraints)-1)]
        constraint.greedySatisfy()
    
    def _pickAndFlipLiteral(self, candidates, constraint):
        '''chooses from the list of given literals (as ground atom indices: candidates) the best one to flip, i.e. the one that causes the fewest constraints to become unsatisified'''
        # get the literal that makes the fewest other formulas false
        bestNum = len(self.inferObject.clauses) * 3
        bestGA = None
        bestGAsecond = None
        mln = self.mln
        inferObject = self.inferObject
        for idxGA in candidates:
            #strGA = str(self.mln.gndAtomsByIdx[idxGA])
            idxGAsecond = None
            # ignore ground atoms for which we have evidence
            idxBlock = mln.atom2BlockIdx[idxGA]
            if idxBlock in inferObject.evidenceBlocks:
                #print "%s is in evidence" % strGA
                continue
            blockExcl = inferObject.blockExclusions.get(idxBlock, [])
            if idxGA in blockExcl:
                #print "%s is excluded" % strGA
                continue
            # get the number of unsatisfied clauses the flip would cause
            num = 0
            block = mln.pllBlocks[idxBlock][1]
            if block is not None: # if the atom is in a real block, select a second atom to flip to get a consistent state
                trueOne, falseOnes = self.blockInfo[idxBlock]
                if trueOne in falseOnes: 
                    print "Error: The true one is part of the false ones!"
                if len(falseOnes) == 0: # there are no false atoms that we could set to true, so skip this ground atom
                    #print "no false ones for %s" % strGA
                    continue
                if idxGA == trueOne: # if the current GA is the true one, then randomly choose one of the false ones to flip
                    idxGAsecond = falseOnes[random.randint(0,len(falseOnes)-1)]
                elif idxGA in falseOnes: # if the current GA is false, the second literal to flip is the true one
                    idxGAsecond = trueOne
                else: # otherwise, this literal must be excluded and must not be flipped
                    continue
                num += len(self.bottlenecks.get(idxGAsecond, [])) # !!!!!! additivity ignores the possibility that the first and the second GA could occur together in the same formula (should perhaps perform the first flip temporarily)
            num += len(self.bottlenecks.get(idxGA, []))
            # check if it's better than the previous best (or equally good)
            newBest = False
            if num < bestNum:
                newBest = True
            elif num == bestNum: # in case of equality, decide randomly
                newBest = random.randint(0,1) == 1
            if newBest:
                bestGA = idxGA
                bestGAsecond = idxGAsecond
                bestNum = num
            #else:
            #   print "%s is not good enough (%d)" % (strGA, num)
        if bestGA == None:
            #c = self.realClauses[clauseIdx]
            #print "UNSATISFIABLE: %s" % str(c)
            #print self.mln.printState(self.state)
            gf = constraint.getFormula()
            raise Exception("SampleSAT error: unsatisfiable constraint '%s' given the evidence! It is an instance of '%s'." % (strFormula(gf), strFormula(self.mln.formulas[gf.idxFormula])))
        # flip the best one and, in case of a blocked ground atom, a second one
        self._flipGndAtom(bestGA)
        if bestGAsecond != None:
            self._flipGndAtom(bestGAsecond)
            self._updateBlockInfo(bestGA, bestGAsecond)

    # update the true one and the false ones for a flip of both the given ground atoms (which are in the same block)
    def _updateBlockInfo(self, idxGA, idxGA2):
        # update the block information
        idxBlock = self.mln.atom2BlockIdx[idxGA]
        bi = self.blockInfo[idxBlock]
        if bi[0] == idxGA: # idxGA is the true one, so add it to the false ones and make idxGA2 the true one
            bi[1].append(idxGA)
            bi[1].remove(idxGA2)
            bi[0] = idxGA2
        else: # idxGA2 is the true one
            try:
                bi[1].append(idxGA2)
                bi[1].remove(idxGA)
                bi[0] = idxGA
            except:
                raise Exception("Could not change true one in block from %s to %s" % (self.mln.strGroundAtom(idxGA2), self.mln.strGroundAtom(idxGA)))
                
    # flips the truth value of a literal (referred to by the ground atom index). This is the only place where changes to the state are made!
    # If an atom in a block is flipped, be sure to also call _updateBlockInfo
    def _flipGndAtom(self, idxGA):
        # flip the ground atom
        if self.debug: print "  flipping %s" % str(self.mln.gndAtomsByIdx[idxGA])
        self.state[idxGA] = not self.state[idxGA]
        # the constraints where the literal was a bottleneck are now unsatisfied
        bn = self.bottlenecks.get(idxGA)
        if bn is not None:
            self.unsatisfiedConstraints.extend(bn)
            #print "  %s now unsatisfied" % str(bn)
            self.bottlenecks[idxGA] = []
        # update: 
        #  - list of true literals for each clause containing idxGA
        #  - bottlenecks: clauses where this results in one true literal now have a bottleneck
        #  - unsatisfied clauses: clauses where there are no true lits before the flip are now satisfied
        affectedConstraints = self.GAoccurrences.get(idxGA, [])
        #print "  %d constraints affected" % len(affectedConstraints)
        for constraint in affectedConstraints:
            #print "  %s" % str(constraint)
            constraint.handleFlip(idxGA)      
               
    def _SAMove(self):
        # TODO are block exclusions handled correctly here? check it!
        # pick one of the blocks at random until we get one where we can flip (true one not known)
        idxPllBlock = 0
        while True:
            idxPllBlock = random.randint(0, len(self.mln.pllBlocks)-1)
            if idxPllBlock in self.inferObject.evidenceBlocks: # skip evidence block
                #print "skipping evidence block"
                #pass
                return
            else:
                break
        # randomly pick one of the block's ground atoms to flip
        delta = 0
        idxGA, block = self.mln.pllBlocks[idxPllBlock]
        trueOne = None
        if block is not None: # if it's a proper block, we need to look at the truth values to make a consistent setting
            trueOne, falseOnes = self.blockInfo[idxPllBlock]
            if len(falseOnes) == 0: # no false atom can be flipped
                print "no false ones in block"
                return
            delta += self._delta(trueOne) # consider the delta cost of making the true one false
            # already perform the flip of the true one - and reverse it later if the new state is ultimately rejected
            # Note: need to perform it here, so that the delta calculation for the other gnd Atom that is flipped is correct
            self._flipGndAtom(trueOne)
            idxGA = falseOnes[random.randint(0, len(falseOnes)-1)]
        # consider the delta cost of flipping the selected gnd atom 
        delta += self._delta(idxGA)
        # if the delta is non-negative, we always use the resulting state
        if delta >= 0:
            p = 1.0
        else:
            # !!! the temperature has a great effect on the uniformity of the sampled states! it's a "magic" number that needs to be chosen with care. if it's too low, then probabilities will be way off; if it's too high, it will take longer to find solutions
            temp = 14.0 # the higher the temperature, the greater the probability of deciding for a flip
            p = exp(-float(delta)/temp)
            p = 1.0 #!!!
        # decide and flip
        if random.uniform(0,1) <= p:
            self._flipGndAtom(idxGA)
            if trueOne is not None:
                self._updateBlockInfo(idxGA, trueOne)
        else: # not flipping idxGA, so reverse the flip of the true one if it was previously performed
            if trueOne is not None: 
                self._flipGndAtom(trueOne)
    
    # get the delta cost of flipping the given ground atom (newly satisfied constraints - now unsatisfied constraints)    
    def _delta(self, idxGA):
        bn = self.bottlenecks.get(idxGA, [])
        # minus now unsatisfied clauses (as indicated by the bottlenecks)
        delta = -len(bn)
        # plus newly satisfied clauses
        for constraint in self.GAoccurrences.get(idxGA, []):
            if constraint.flipSatisfies(idxGA):                
                delta += 1
        return delta


# !!! TODO: Write MaxWalkSat: satisfy a maximally large sum of weights of formulas in CNF, because SAWalkSat often fails to satisfy all hard clauses
# (or modify SAWalkSat to consider in its sum only hard weights, but it will probably require more steps because it doesn't exploit clausal structure)

# simulated annealing maximum satisfiability
class SAMaxWalkSAT:
    def __init__(self, state, mln, evidenceBlocks, threshold = None, hardWeight = 10):
        self.state = state
        self.mln = mln
        self.sum = 0
        self.evidenceBlocks = evidenceBlocks
        wt = self.mln._weights()
        auto_threshold = -5
        for gf in self.mln.gndFormulas:
            gfw = wt[gf.idxFormula]
            if gf.isTrue(state):
                self.sum += gfw
            if gfw >= hardWeight:
                auto_threshold += gfw
        if threshold == None:
            threshold = auto_threshold
        self.threshold = threshold
    
    def run(self, verbose=False):
        debug = False
        mln = self.mln
        wt = self.mln._weights()
        i = 0
        i_max = 1000
        alpha = 0.5
        threshold = self.threshold
        #if debug: print "Random walk threshold: %f" % threshold
        while i < i_max and self.sum <= threshold:
            # randomly choose a pll block to modify
            idxBlock = random.randint(0, len(self.mln.pllBlocks)-1)
            if idxBlock in self.evidenceBlocks:
                continue
            #time.sleep(2)            
            # compute the sum of relevant gf weights before the modification
            sum_before = 0
            for gf in self.mln.blockRelevantGFs[idxBlock]:
                if gf.isTrue(self.state):
                    sum_before += wt[gf.idxFormula]
            # modify the state
            (idxGA, block) = self.mln.pllBlocks[idxBlock]
            oldstate = []
            if block != None:
                chosen = random.randint(0, len(block)-1)
                changedGA = block[chosen]
                for j, idxGA in enumerate(block):
                    oldstate.append(self.state[idxGA])
                    self.state[idxGA] = (j == chosen)
                #if debug: print "setting block %s to %s" % (str(map(lambda x: str(mln.gndAtomsByIdx[x]), block)), str(mln.gndAtomsByIdx[block[chosen]]))
            else:
                oldstate.append(self.state[idxGA])
                self.state[idxGA] = not self.state[idxGA]
                changedGA = idxGA
                #if debug: print "setting atom %s to %s" % (str(mln.gndAtomsByIdx[idxGA]), bool(chosen))
            # compute the sum after the modification
            sum_after = 0
            for gf in self.mln.blockRelevantGFs[idxBlock]:
                if gf.isTrue(self.state):
                    sum_after += wt[gf.idxFormula]
            # determine whether to keep the new state            
            keep = False
            improvement = sum_after - sum_before
            if improvement >= 0: 
                prob = 1.0
                keep = True
            else: 
                prob = (1.0-min(1.0, abs(improvement/self.sum))) * (1-(float(i)/i_max))
                keep = random.uniform(0.0,1.0) <= prob
                keep = True # !!! no annealing
            # apply new objective value
            if keep:
                self.sum += improvement
            if debug:
                #print "\nSAMaxWalkSAT %d: %f  %s=%s   %.2f %s   improvement=%f (%f->%f)" % (i, self.sum, str(mln.gndAtomsByIdx[changedGA]), str(self.state[changedGA]), prob, str(keep), improvement, sum_before, sum_after)
                #mln.printState(self.state)
                pass
            # restore old state if necessary
            if not keep:
                if block != None:
                    for j, idxGA in enumerate(block):
                        self.state[idxGA] = oldstate[j]
                else:
                    self.state[idxGA] = oldstate[0]
            # next iteration
            i += 1
        if debug or verbose:
            print "SAMaxWalkSAT: %d iterations, sum=%f, threshold=%f" % (i, self.sum, threshold)
            #mln.printState(self.state)
            pass

import FOL # import here so that cyclic import from RRF works and RRFMLN can be subclassed from MLN

# --- The MLN Tool --- (exposes only a tiny fraction of the capabilities of this class)
if __name__ == '__main__':
    #sys.argv = [sys.argv[0], "test", "graph"]
    args = sys.argv[1:]
    if len(args) == 0:
        print "\nMLNs in Python - helper tool\n\n  usage: mln.py <action> <params>\n\n"
        print "  actions: print <mln file>"
        print "              print the MLN file\n"
        print "           printGF <mln file> <db file>"
        print "              print the ground formulas we obtain when instantiating an MRF with the given database\n"
        print "           printGC <mln file> <db file>"
        print "              print the ground clauses we obtain when instantiating an MRF with the given database\n"
        print "           printGA <mln file> <db file>"
        print "              print the ground atoms we obtain when instantiating an MRF with the given database\n"
        print "           inferExact <mln file> <domain> <query> <evidence>"
        print "              domain: a dictionary mapping domain names to lists of constants, e.g."
        print "                      \"{'dom1':['const1', 'const2'], 'dom2':['const3']}\""
        print "                      To use just the constants declared in the MLN, use {}"
        print "              query, evidence: ground formulas\n" 
        print "           inferGibbs <mln file> <domain> <query> <evidence>\n"
        print "           topWorlds <mln file> <domain>\n"
        print "           test <test name>"
        print "              run the test with the given name (dev only)\n"
        print "  NOTE: This script exposes but a tiny fraction of the functionality of the MLN class!\n"
        sys.exit(0)
    if args[0] == "print":
        mln = MLN(args[1])
        mln.write(sys.stdout)
    elif args[0] == 'printGF':
        mln = MLN(args[1])
        mln.combineDB(args[2])
        mln.printGroundFormulas()
    elif args[0] == 'printGC':
        mln = MLN(args[1])
        mln.combineDB(args[2])
        mln._toCNF()
        mln.printGroundFormulas()
    elif args[0] == 'printGA':
        mln = MLN(args[1])
        mln.combineDB(args[2], groundFormulas=False)
        mln.printGroundAtoms()
    elif args[0] == "inferExact":
        mln = MLN(args[1])
        mln.combine(eval(args[2]))
        mln.inferExact(args[3], args[4])
    elif args[0] == "topWorlds":
        mln = MLN(args[1])
        mln.combineDB(args[2])
        mln.printTopWorlds(10)
    elif args[0] == "inferGibbs":
        mln = MLN(args[1])
        mln.combine(eval(args[2]))
        mln.inferGibbs(args[3], args[4])
    elif args[0] == "printDomains":
        mln = MLN(args[1])
        print mln.domains
    elif args[0] == "test":
        test = args[1]
        #os.chdir(r"c:\dev\Java\AI\SRLDB\mln\drinking")
        #os.chdir(r"c:\dev\Java\AI\SRLDB\mln\kitchen")
        #os.chdir("/usr/wiss/jain/work/code/SRLDB/mln/kitchen")
        if test == 'gndFormulas':
            mln = MLN("in.actsit-tiny-conj-two.mln")
            mln.combine({"person": ["P"], "drink": ["D1", "D2"]})
            mln.printGroundFormulas()
        elif test == "predGndings":
            mln = MLN("wts.blog.meal_goods.mln", verbose=True)
            mln.combineDB("q7.db")
            mln._getPredGroundings("usedByForWithIn")
            pass
        elif test == 'infer':
            mln = MLN("wts.test.mln")
            mln.combine({"drink": ["D"], "person": ["P"]})
            mln.infer("hasRank(P,Student)")
            mln.infer("hasRank(P,Student)", "consumed(P, D) ^ drinkType(D, Tea)")
            #mln.printGroundFormulas()
            #mln.printWorlds()   
        elif test == 'count':
            #mln = MLN("wts.pyll.actsit-tinyk1-two.mln")
            #mln = MLN("in.actsit-tiny-conj-two.mln")
            #mln = MLN("in.actsit-tiny-conj-two-norel.mln")
            mln = MLN("in.actsit-tiny-conj-two-norel.mln")
            #mln.combineDB("tinyk1.db")
            mln.combineDB("tinyk1norel.db")
            idxFormula = 0
            idxFormulaHard = (idxFormula+1) % 2
            idxWorld = mln._getEvidenceWorldIndex()
            print "idxWorld %d (%d worlds in total)" % (idxWorld, len(mln.worlds))
            print "true groundings of formula in training DB:", mln._countNumTrueGroundingsInWorld(idxFormula, mln.worlds[idxWorld])
            # count how many times the formula is true in possible worlds
            mln.countWorldsWhereFormulaIsTrue(idxFormula)
            # exclude worlds where the other formula isn't always true
            counts = mln.countTrueGroundingsForEachWorld()
            musthave = counts[idxWorld][idxFormulaHard]
            counts = filter(lambda x: x[idxFormulaHard] == musthave, counts)
            f = {}
            for c in counts:
                f[c[idxFormula]] = f.get(c[idxFormula], 0) + 1
            print f
            #mln.printWorlds()
            #mln.worlds.sort(key=lambda w:-w["sum"])
            #mln.printWorlds()
        elif test == "count2":
            #mln = MLN("wts.pyll.tinyk1norel-two-norel.mln")
            mln = MLN("wts.pyll.tinyk1symm-four.mln")
            #mln.setRigidPredicate("hasRank")
            #mln.setRigidPredicate("drinkType")
            mln.combineDB("tinyk1symm.db")
            counts = mln.countTrueGroundingsForEachWorld(True)
            mln.printWorlds(format=2)
        elif test == 'blockprob':
            mln = MLN("wts.pybpll.tinyk1-two.mln")
            mln.combineDB("tinyk1.db")
            mln.printBlockProbsMB()
        elif test == 'learnwts':
            def learn(infile, mode, dbfile, startpt = False, rigidPreds = []):
                mln = MLN(infile)    
                #db = "tinyk%d%s" %  (k, db)
                mln.combineDB(dbfile)
                for predName in rigidPreds:
                    mln.setRigidPredicate(predName)
                mln.learnwts(mode, initialWts=startpt)
                prefix = 'wts'
                if mode == 'PLL':
                    tag = "pll-%s-%s" % (PMB_METHOD, DIFF_METHOD[:2])
                else:
                    tag = mode.lower()
                    if mode == 'LL' and not POSSWORLDS_BLOCKING:
                        tag += "-nobl"
                    if mode == 'LL_fac':
                        prefix = 'fac'
                fname = ("%s.py%s.%s" % (prefix, tag, infile[3:]))
                mln.write(file(fname, "w"))
                print "WROTE %s\n\n" % fname
            #PMB_METHOD='excl'
            #POSSWORLDS_BLOCKING=True
            #DIFF_METHOD='blocking' #'simple'
            learn("in.tiny-paper.mln", "LL", "tinyk1b.db")
        elif test == 'pll':
            mln = MLN('wts.actsit-unaryallmink1-perfectwts.mln')
            mln.combineDB("unaryallmink1.db")
            mln.getAtomProbMB("goalkeeper(F)")
        elif test == 'expect':
            #mln = MLN("wts.actsit-tinyk1-doConnection-manual-perfect2.mln")
            mln = MLN("wts.pyll.tinyk1symm-process-norigid.mln")
            #mln = MLN("wts.pyll.tinyk1symm-process-hasRank.mln")
            #mln.setRigidPredicate("hasRank")
            mln.combineDB("tinyk1symm.db")
            print "expected # of gndings:"
            mln.printExpectedNumberOfGroundings()
            print "\nformula probs:"
            mln.printFormulaProbabilities()
            print "\ninference:"
            mln.infer("drinkType(D1,Tea)", "hasRank(Steve, Student) ^ consumed(Steve,D1)")
            mln.infer("drinkType(D1,Coffee)", "hasRank(Steve, Student) ^ consumed(Steve,D1)")
            mln.infer("drinkType(D1,Tea)", "hasRank(Steve, Professor) ^ consumed(Steve,D1)")
            mln.infer("drinkType(D1,Coffee)", "hasRank(Steve, Professor) ^ consumed(Steve,D1)")
            mln.infer("hasRank(Steve,Student)", "drinkType(D1, Tea) ^ consumed(Steve,D1)")
        elif test == "Gibbs":
            def infer(what, given, gs):
                gs.mln.infer(what, given)
                gs.infer(what, given)
            mln = MLN("wts.test.mln")
            mln.combine({"person": ["P"], "drink": ["D1"]})
            gs = GibbsSampler(mln)
            infer("hasRank(P,Student)", "consumed(P,D1) ^ drinkType(D1,Tea)", gs)
            infer("hasRank(P,Student)", None, gs)
        elif test == 'graph':
            mln = MLN("in.tiny-process2-noforcedrink.mln")
            mln.combine({"person": ["Steve", "Pete"], "drink": ["C1", "T1", "T2"]})
            mln.writeDotFile("test.dot")
        elif test == "MCSAT":
            mln = MLN("wts.blog.meal_goods.mln", verbose=True)
            query = ("personT", "q3.db", 30)
            query = ("utensilT", "q10.db", 5)
            evidence = evidence2conjunction(mln.combineDB(query[1], verbose=True))
            mcsat = MCSAT(mln, verbose=True)
            mcsat.infer(query[0], evidence, debug=False, randomSeed=0, verbose=True, details=True, maxSteps=query[2], shortOutput=True)
        elif test == 'profile_mcsat':
            mln = MLN("wts.blog.meal_goods.mln", verbose=True)
            query = ("personT", "q3.db")
            query = ("utensilT", "q10.db")
            evidence = evidence2conjunction(mln.combineDB(query[1], verbose=True))
            mcsat = MCSAT(mln, verbose=True)
            print "\nnow profiling..."
            import profile
            import pstats
            profile.run("mcsat.infer(query[0], evidence, debug=False, randomSeed=0, verbose=True, details=True, maxSteps=5, shortOutput=True)", "prof_mcsat")
            stats = pstats.Stats("prof_mcsat")
            stats.strip_dirs()
            stats.sort_stats('cumulative')
            stats.print_stats()
            stats.print_callees()
        elif test == 'profile_mcsat2':
            mln = MLN("meal_goods2/wts.meals1.mln", verbose=True)
            query = ("takesPartIn", "meal_goods2/new-1.db")
            #query = ("utensilT", "q10.db")
            evidence = evidence2conjunction(mln.combineDB(query[1], verbose=True))
            mcsat = MCSAT(mln, verbose=True)
            print "\nnow profiling..."
            import profile
            import pstats
            profile.run("mcsat.infer(query[0], evidence, debug=False, randomSeed=0, verbose=True, details=True, maxSteps=25, shortOutput=True)", "prof_mcsat")
            stats = pstats.Stats("prof_mcsat")
            stats.strip_dirs()
            stats.sort_stats('cumulative')
            stats.print_stats()
            stats.print_callees()
        elif test == 'count_constraint':
            mln = MLN('test.mln')
            mln.combine({})
            mln.printGroundFormulas()
            mln.inferMCSAT("directs", infoInterval=1, details=True, maxSteps=1000, verbose=True, resultsInterval=1, debug=False, debugLevel=3)
        elif test == "count_constraint2": # compare count constraint size to existential quantification
            mln = MLN('count_constraint_vs_exists.mln')
            mln.combine({})
            mln._toCNF()
            mln.printGroundFormulas()
            
