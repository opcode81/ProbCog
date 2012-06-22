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

import re
import sys

import FOL
from util import *

# math functions

try:
    import mpmath
    mpmath.mp.dps = 80
    from mpmath import exp, fsum, log
except:
    from math import exp, log
    try:
        from math import fsum 
    except: # not supported in Python 2.5
        fsum = sum
#sys.stderr.write("Warning: Falling back to standard math module because mpmath is not installed. If overflow errors occur, consider installing mpmath.")
from math import floor, ceil, e, sqrt

import math

def logx(x):
    if x == 0:
        return - 100
    return math.log(x) #used for weights -> no high precision (mpmath) necessary


def stripComments(text):
    comment = re.compile(r'//.*?$|/\*.*?\*/', re.DOTALL | re.MULTILINE)
    return re.sub(comment, '', text)

def getPredicateList(filename):
    ''' gets the set of predicate names from an MLN file '''
    content = file(filename, "r").read() + "\n"
    content = stripComments(content)
    lines = content.split("\n")
    predDecl = re.compile(r"(\w+)\([^\)]+\)")
    preds = set()
    for line in lines:
        line = line.strip()
        m = predDecl.match(line)
        if m is not None:
            preds.add(m.group(1))
    return list(preds)

def avg(*a):
    return sum(map(float, a)) / len(a)

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
    while s[0] == '(' and s[ -1] == ')':
        s2 = s[1:-1]
        if not balancedParentheses(s2):
            return s
        s = s2
    return s

def evidence2conjunction(evidence):
    ''' converts the evidence obtained from a database (dict mapping ground atom names to truth values) to a conjunction (string) '''
    evidence = map(lambda x: ("" if x[1] else "!") + x[0], evidence.iteritems())
    return " ^ ".join(evidence)

def toCNF(gndFormulas, formulas, allPositive=False):
    '''
    convert the given ground formulas to CNF
    if allPositive=True, then formulas with negative weights are negated to make all weights positive
    @return a new pair (gndFormulas, formulas)
    '''    
    # get list of formula indices where we must negate
    newFormulas = []    
    negate = []
    if allPositive:
        for idxFormula,formula in enumerate(formulas):
            f = formula
            if formula.weight < 0:
                negate.append(idxFormula)
                if isinstance(formula, FOL.Negation):
                    f = formula.children[0]                        
                else:
                    f = FOL.Negation([formula])                 
                f.weight = -formula.weight
                f.idxFormula = idxFormula
            newFormulas.append(f)
    # get CNF version of each ground formula
    newGndFormulas = []
    for gf in gndFormulas:
        # non-logical constraint
        if not gf.isLogical(): # don't apply any transformations to non-logical constraints
            if gf.idxFormula in negate:
                gf.negate()
            newGndFormulas.append(gf)
            continue
        # logical constraint
        if gf.idxFormula in negate:
            cnf = FOL.Negation([gf]).toCNF()
        else:
            cnf = gf.toCNF()
        if type(cnf) == FOL.TrueFalse: # formulas that are always true or false can be ignored
            continue
        cnf.idxFormula = gf.idxFormula
        newGndFormulas.append(cnf)
    # return modified formulas
    return (newGndFormulas, newFormulas)


def gaussianZeroMean(x, sigma):
    return 1.0/sqrt(2 * math.pi * sigma**2) * math.exp(- (x**2) / (2 * sigma**2))

def gradGaussianZeroMean(x, sigma):
    return - (0.3990434423 * x * math.exp(-0.5 * x**2 / sigma**2) ) / (sigma**3)

def mergeDomains(*domains):
    ''' returning a new domains dictionary that contains the elements of all the given domains '''
    fullDomain = {}
    for domain in domains:            
        for domName, values in domain.iteritems():
            if domName not in fullDomain:
                fullDomain[domName] = set(values)
            else:
                fullDomain[domName].update(values)
    for key, s in fullDomain.iteritems():
        fullDomain[key] = list(s)
    return fullDomain