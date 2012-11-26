# First-Order Logic - parsing and processing
# 
# (C) 2007-2012 by Dominik Jain
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
from _pyparsing import *
if __name__ != '__main__':
    from RRF import RRF, RRFConstantLeaf, RRFVariableLeaf, RRF_HARD_WEIGHT

DEBUG_NF = False # whether to display debug information while performing normal form conversion

def isVar(identifier):
    return identifier.islower() or identifier[0] == "+"

def isConstant(identifier):
    return not isVar(identifier)

class Constraint(object):
    def getTemplateVariants(self):
        '''gets all the template variants of the constraint for the given mln/ground markov random field'''
        raise Exception("%s does not implement getTemplateVariants" % str(type(self)))
    
    def isTrue(self, world_values):
        '''returns True if the constraint is satisfied given a complete possible world
                world_values: a possible world as a list of truth values'''
        raise Exception("%s does not implement isTrue" % str(type(self)))

    def isLogical(self):
        '''returns whether this is a logical constraint, i.e. a logical formula'''
        raise Exception("%s does not implement isLogical" % str(type(self)))

    def iterGroundings(self, mrf):
        '''iteratively yields the groundings of the formula for the given ground MRF'''
        raise Exception("%s does not implement iterGroundings" % str(type(self)))
    
    def idxGroundAtoms(self, l = None):
        raise Exception("%s does not implement idxGroundAtoms" % str(type(self)))
    
    def getGroundAtoms(self, l = None):
        raise Exception("%s does not implement getGroundAtoms" % str(type(self)))
    
class Formula(Constraint):
    ''' the base class for all logical constraints'''
    
    def containsGndAtom(self, idxGndAtom):
        if not hasattr(self, "children"):
            return False
        for child in self.children:
            if child.containsGndAtom(idxGndAtom):
                return True
        return False

    def idxGroundAtoms(self, l = None):
        if l == None: l = []
        if not hasattr(self, "children"):
            return l
        for child in self.children:
            child.idxGroundAtoms(l)
        return l

    def getGroundAtoms(self, l = None):
        if l == None: l = []
        if not hasattr(self, "children"):
            return l
        for child in self.children:
            child.getGroundAtoms(l)
        return l

    def getTemplateVariants(self, mln):
        '''gets all the template variants of the formula for the given mln (ground markov random field)'''        
        tvars = self._getTemplateVariables(mln)
        variants = []
        self._getTemplateVariants(mln, tvars.items(), {}, variants, 0)
        return variants

    def _getTemplateVariants(self, mln, vars, assignment, variants, i):
        if i == len(vars): # all template variables have been assigned a value
            # ground the vars in all children
#            print type(self)
            variants.extend(self._groundTemplate(assignment))
            return
        else:
            # ground the next variable
            varname, domname = vars[i]
            for value in mln.domains[domname]:
                assignment[varname] = value
                self._getTemplateVariants(mln, vars, assignment, variants, i+1)
    
    def _getTemplateVariables(self, mln, vars = None):
        '''gets all variables of this formula that are required to be expanded (i.e. variables to which a '+' was appended) and returns a mapping (dict) from variable name to domain name'''
        raise Exception("%s does not implement _getTemplateVariables" % str(type(self)))
    
    def _groundTemplate(self, assignment):
        '''grounds this formula for the given assignment of template variables and returns a list of formulas, the list of template variants
                assignment: a mapping from variable names to constants'''
        raise Exception("%s does not implement _groundTemplate" % str(type(self)))

    def iterGroundings(self, mrf, simplify=False):
        '''
            iteratively yields the groundings of the formula for the given grounder
            mrf: an object, such as an MRF instance, which
                - has an "mln" member (MarkovLogicNetwork instance)
                - has a "domains" member (like an MLN/MRF)
                - has a "gndAtoms" member that can be indexed, i.e. gndAtoms[string] should return a ground atom instance
        '''
        try:
            vars = self.getVariables(mrf.mln)
        except Exception, e:
            raise Exception("Error grounding '%s': %s" % (str(self), str(e)))
        for grounding, referencedGndAtoms in self._iterGroundings(mrf, vars, {}, simplify):
            yield grounding, referencedGndAtoms
        
    def _iterGroundings(self, mrf, variables, assignment, simplify=False):
        # if all variables have been grounded...
        if variables == {}:
            referencedGndAtoms = []
            gndFormula = self.ground(mrf, assignment, referencedGndAtoms, simplify)
            yield gndFormula, referencedGndAtoms
            return
        # ground the first variable...
        varname, domName = variables.popitem()
        for value in mrf.domains[domName]: # replacing it with one of the constants
            assignment[varname] = value
            # recursive descent to ground further variables
            for g, r in self._iterGroundings(mrf, dict(variables), assignment, simplify):
                yield g, r
    
    def getVariables(self, mln, vars = None):
        raise Exception("%s does not implement getVariables" % str(type(self)))
    
    def ground(self, mln, assignment, referencedAtoms = None, simplify=False):
        '''grounds the formula using the given assignment of variables to values/constants and, if given a list in referencedAtoms, fills that list with indices of ground atoms that the resulting ground formula uses
                returns the ground formula object
                assignment: mapping of variable names to values'''
        raise Exception("%s does not implement ground" % str(type(self)))
    
    def getVarDomain(self, varname, mln):
        raise Exception("%s does not implement getVarDomain" % str(type(self)))
        
    def toCNF(self, level=0):
        '''convert to conjunctive normal form'''
        return self
    
    # convert to negation normal form
    def toNNF(self, level=0):
        return self
        
    def printStructure(self, level=0):
        print "%*c" % (level*2,' '),
        print "%s: %s" % (str(type(self)), str(self))
        if hasattr(self, 'children'):
            for child in self.children:
                child.printStructure(level+1)
    
    def isLogical(self):
        return True
    
    def simplify(self, mrf):
        '''
        Simplify the formula by evaluating it with respect to the ground atoms given 
        by the evidence in the mrf.
        '''
        raise Exception('%s does not implement simplify' % str(type(self)))
    
# a formula that has other formulas as subelements (children)
class ComplexFormula(Formula):
    
    def getVariables(self, mln, vars = None, constants = None):
        '''
            get the free (unquantified) variables of the formula in a dict that maps the variable name to the corresp. domain name
            The vars and constants parameters can be omitted.
            If vars is given, it must be a dictionary with already known variables.
            If constants is given, then it must be a dictionary that is to be extended with all constants appearing in the formula;
                it will be a dictionary mapping domain names to lists of constants
            If constants is not given, then constants are not collected, only variables.
            The dictionary of variables is returned.
        '''
        if vars is None: vars = {}
        for child in self.children:
            if not hasattr(child, "getVariables"): continue
            vars = child.getVariables(mln, vars, constants)
        return vars
    
    def getConstants(self, mln, constants = None):
        ''' get the constants appearing in the formula in a dict that maps the constant name to the domain name the constant belongs to '''
        if constants == None: constants = {}
        for child in self.children:
            if not hasattr(child, "getConstants"): continue
            constants = child.getConstants(mln, vars)
        return constants  

    def ground(self, mrf, assignment, referencedGndAtoms = None, simplify=False):
        children = []
        for child in self.children:
            gndChild = child.ground(mrf, assignment, referencedGndAtoms)
            children.append(gndChild)
        gndFormula = apply(type(self), (children,))
        if simplify:
            gndFormula = gndFormula.simplify(mrf)
        return gndFormula

    def _groundTemplate(self, assignment):
        variants = [[]]
        for child in self.children:
            childVariants = child._groundTemplate(assignment)
            new_variants = []
            for variant in variants:
                for childVariant in childVariants:
                    v = list(variant)
                    v.append(childVariant)
                    new_variants.append(v)
            variants = new_variants
        final_variants = []
        for variant in variants:
            if type(self) == Exist:
                final_variants.append(Exist(self.vars, variant[0]))
            else:
                final_variants.append(apply(type(self), (variant,)))
                
        return final_variants

    def getVarDomain(self, varname, mln):
        for child in self.children:
            dom = child.getVarDomain(varname, mln)
            if dom != None:
                return dom
        return None

    def _getTemplateVariables(self, mln, vars = None):
        if vars == None: vars = {}
        for child in self.children:
            if not hasattr(child, "_getTemplateVariables"): continue
            vars = child._getTemplateVariables(mln, vars)
        return vars        
        
class Lit(Formula):
    def __init__(self, negated, predName, params):
        self.negated = negated
        self.predName = predName
        self.params = list(params)

    def __str__(self):
        return {True:"!", False:""}[self.negated] + self.predName + "(" + ", ".join(self.params) + ")"

    def getVariables(self, mln, vars = None, constants = None):
        if vars == None: vars = {}
        paramDomains = mln.predicates[self.predName]
        if len(paramDomains) != len(self.params): raise Exception("Wrong number of parameters in '%s'; expected %d!" % (str(self), len(paramDomains)))
        for i,param in enumerate(self.params):
            if isVar(param[0]):
                varname = param
                domain = paramDomains[i]
                if varname in vars and vars[varname] != domain:
                    raise Exception("Variable '%s' bound to more than one domain" % varname)
                vars[varname] = domain
            elif constants is not None:
                domain = paramDomains[i]
                if domain not in constants: constants[domain] = []
                constants[domain].append(param)
        return vars
    
    def getSingleVariableIndex(self, mln):
        paramDomains = mln.predicates[self.predName]
        if len(paramDomains) != len(self.params): raise Exception("Wrong number of parameters in '%s'; expected %d!" % (str(self), len(paramDomains)))
        varIndex = -1
        for i,param in enumerate(self.params):
            if isVar(param[0]):
                if varIndex == -1:
                    varIndex = i
                else:
                    return -1
        return varIndex
        

    def _getTemplateVariables(self, mln, vars = None):
        if vars == None: vars = {}
        for i,param in enumerate(self.params):
            if param[0] == '+':
                varname = param
                pred = mln.predicates[self.predName]
                domain = pred[i]
                if varname in vars and vars[varname] != domain:
                    raise Exception("Variable '%s' bound to more than one domain" % varname)
                vars[varname] = domain
        return vars

    def ground(self, mrf, assignment, referencedGndAtoms = None, simplify=False):
        params = map(lambda x: assignment.get(x, x), self.params)
        s = "%s(%s)" % (self.predName, ",".join(params))
        try:
            gndAtom = mrf.gndAtoms[s]
        except:
            print "\nground atoms:"
            mrf.printGroundAtoms()
            raise Exception("Could not ground formula containing '%s' - this atom is not among the ground atoms (see above)." % s)
        gndFormula = GroundLit(gndAtom, self.negated)
        if referencedGndAtoms != None: referencedGndAtoms.append(gndAtom.idx)
        return gndFormula

    def getVarDomain(self, varname, mln):
        '''returns the name of the domain of the given variable'''
        if varname in self.params:
            idx = self.params.index(varname)
            return mln.predicates[self.predName][idx]
        return None

    def _groundTemplate(self, assignment):
        params = map(lambda x: assignment.get(x, x), self.params)
        if self.negated == 2: # template
            return [Lit(False, self.predName, params), Lit(True, self.predName, params)]
        else:
            return [Lit(self.negated, self.predName, params)]

class GroundAtom(Formula):
    def __init__(self, predName, params, idx):
        self.predName = predName
        self.params = params
        self.idx = idx

    def isTrue(self, world_values):
        return world_values[self.idx]

    def __str__(self):
        return "%s(%s)" % (self.predName, ",".join(self.params))
    
    def toRRF(self):
        return RRFVariableLeaf(self)
    
    def simplify(self, mrf):
        truth = mrf.evidence[self.idx]
        if truth is None:
            return self
        elif truth is True:
            return TrueFalse(True)
        else:
            return TrueFalse(False)
        
class GroundLit(Formula):
    def __init__(self, gndAtom, negated):
        self.gndAtom = gndAtom
        self.negated = negated

    def isTrue(self, world_values):
        if self.negated:
            return (not world_values[self.gndAtom.idx])
        return world_values[self.gndAtom.idx]

    def __str__(self):
        return {True:"!", False:""}[self.negated] + str(self.gndAtom)

    def containsGndAtom(self, idxGndAtom):
        return (self.gndAtom.idx == idxGndAtom)

    def idxGroundAtoms(self, l = None):
        if l == None: l = []
        if not self.gndAtom.idx in l: l.append(self.gndAtom.idx)
        return l

    def getGroundAtoms(self, l = None):
        if l == None: l = []
        if not self.gndAtom in l: l.append(self.gndAtom)
        return l
    
    def toRRF(self):
        if self.negated:
            return Negation([self.gndAtom]).toRRF()
        return self.gndAtom.toRRF()

    def simplify(self, mrf):
        f = self.gndAtom.simplify(mrf)
        if isinstance(f, TrueFalse):
            if self.negated:
                return f.invert()
            else:
                return f
        return self

class Disjunction(ComplexFormula):
    def __init__(self, children):
        assert len(children) >= 2
        self.children = children

    def __str__(self):
        return "("+" v ".join(map(str, self.children))+")"

    def isTrue(self, world_values):
        for child in self.children:
            if child.isTrue(world_values):
                return True
        return False
        
    def toCNF(self, level=0):
        if DEBUG_NF: print "%-8s %*c%s" % ("disj", level*2, ' ', str(self))
        disj = []
        str_disj = []
        conj = []
        # convert children to CNF and group by disjunction/conjunction; flatten nested disjunction, remove duplicates, check for tautology
        for child in self.children:
            c = child.toCNF(level+1) # convert child to CNF -> must be either conjunction of clauses, disjunction of literals, literal or boolean constant
            if type(c) == Conjunction:
                conj.append(c)
            else:
                if type(c) == Disjunction:
                    l = c.children
                else: # literal or boolean constant
                    l = [c]
                for x in l:
                    # if the literal is always true, the disjunction is always true; if it's always false, it can be ignored
                    if type(x) == TrueFalse:
                        if x.isTrue():
                            return TrueFalse(True)
                        else:
                            continue
                    # it's a regular literal: check if the negated literal is already among the disjuncts
                    s = str(x)
                    if s[0] == '!':
                        if s[1:] in str_disj:
                            return TrueFalse(True)
                    else:
                        if ("!" + s) in str_disj:
                            return TrueFalse(True)
                    # check if the literal itself is not already there and if not, add it
                    if not s in str_disj:
                        disj.append(x)
                        str_disj.append(s)
        # if there are no conjunctions, this is a flat disjunction or unit clause
        if len(conj) == 0: 
            if len(disj) >= 2:
                return Disjunction(disj)
            else:
                return disj[0]
        # there are conjunctions among the disjuncts
        # if there is only one conjunction and no additional disjuncts, we are done
        if len(conj) == 1 and len(disj) == 0:
            return conj[0]
        # otherwise apply distributivity
        # use the first conjunction to distribute: (C_1 ^ ... ^ C_n) v RD = (C_1 v RD) ^ ... ^  (C_n v RD)
        # - C_i = conjuncts[i]
        conjuncts = conj[0].children 
        # - RD = disjunction of the elements in remaining_disjuncts (all the original disjuncts except the first conjunction)
        remaining_disjuncts = disj + conj[1:] 
        # - create disjunctions
        disj = []
        for c in conjuncts:
            disj.append(Disjunction([c] + remaining_disjuncts))
        return Conjunction(disj).toCNF(level+1)

    def toNNF(self, level = 0):
        if DEBUG_NF: print "%-8s %*c%s" % ("disj_nnf", level*2, ' ', str(self))
        disjuncts = []
        for child in self.children:
            c = child.toNNF(level+1)
            if type(c) == Disjunction: # flatten nested disjunction
                disjuncts.extend(c.children)
            else:
                disjuncts.append(c)
        return Disjunction(disjuncts)

    def toRRF(self):
        children = []
        for child in self.children:
            childRRF = child.toRRF()
            childRRF.weight = -RRF_HARD_WEIGHT
            children.append(childRRF)
        conj = RRF(children)
        conj.weight = -RRF_HARD_WEIGHT
        return RRF([conj])

    def simplify(self, mrf):
        sf_children = []
        for child in self.children:
            child = child.simplify(mrf)
            if isinstance(child, TrueFalse):
                if child.isTrue():
                    return TrueFalse(True)
            else:
                sf_children.append(child)
        if len(sf_children) == 1:
            return sf_children[0]
        elif len(sf_children) >= 2:
            return Disjunction(sf_children)
        else:
            return TrueFalse(False)
        
class Conjunction(ComplexFormula):
    def __init__(self, children):
        assert len(children) >= 2
        self.children = children

    def __str__(self):
        return "("+" ^ ".join(map(str, self.children))+")"

    def isTrue(self, world_values):
        for child in self.children:
            if not child.isTrue(world_values):
                return False
        return True
    
    def toCNF(self, level=0):
        if DEBUG_NF: print "%-8s %*c%s" % ("conj", level*2, ' ', str(self))
        clauses = []
        litSets = []
        for child in self.children:
            c = child.toCNF(level+1)
            if type(c) == Conjunction: # flatten nested conjunction
                l = c.children
            else:
                l = [c]
            for clause in l: # (clause is either a disjunction, a literal or a constant)
                # if the clause is always true, it can be ignored; if it's always false, then so is the conjunction
                if type(clause) == TrueFalse:
                    if clause.isTrue():
                        continue
                    else:
                        return TrueFalse(False)
                # get the set of string literals
                if hasattr(clause, "children"):
                    litSet = set(map(str, clause.children))
                else: # unit clause
                    litSet = set([str(clause)])
                # check if the clause is equivalent to another (subset/superset of the set of literals) -> always keep the smaller one
                doAdd = True
                i = 0
                while i < len(litSets):
                    s = litSets[i]
                    if len(litSet) < len(s):
                        if litSet.issubset(s):
                            del litSets[i]
                            del clauses[i]
                            continue
                    else:
                        if litSet.issuperset(s):
                            doAdd = False
                            break
                    i += 1
                if doAdd:
                    clauses.append(clause)
                    litSets.append(litSet)
        if len(clauses) == 1:
            return clauses[0]
        return Conjunction(clauses)
    
    def toNNF(self, level = 0):
        if DEBUG_NF: print "%-8s %*c%s" % ("conj_nnf", level*2, ' ', str(self))
        conjuncts = []
        for child in self.children:
            c = child.toNNF(level+1)
            if type(c) == Conjunction: # flatten nested conjunction
                conjuncts.extend(c.children)
            else:
                conjuncts.append(c)
        return Conjunction(conjuncts)
    
    def toRRF(self):
        children = []
        for child in self.children:
            childRRF = child.toRRF()
            childRRF.weight = RRF_HARD_WEIGHT
            children.append(childRRF)
        return RRF(children)

    def simplify(self, mrf):
        sf_children = []
        for child in self.children:
            child = child.simplify(mrf)
            if isinstance(child, TrueFalse):
                if not child.isTrue():
                    return TrueFalse(False)
            else:
                sf_children.append(child)
        if len(sf_children) == 1:
            return sf_children[0]
        elif len(sf_children) >= 2:
            return Conjunction(sf_children)
        else:
            return TrueFalse(True)
            
class Implication(ComplexFormula):
    def __init__(self, children):
        assert len(children) == 2
        self.children = children

    def __str__(self):
        return "(" + str(self.children[0]) + " => " + str(self.children[1]) + ")"

    def isTrue(self, world_values):
        return ((not self.children[0].isTrue(world_values)) or self.children[1].isTrue(world_values))
        
    def toCNF(self, level=0):
        if DEBUG_NF: print "%-8s %*c%s" % ("impl", level*2, ' ', str(self))
        return Disjunction([Negation([self.children[0]]), self.children[1]]).toCNF(level+1)
    
    def toNNF(self, level=0):
        if DEBUG_NF: print "%-8s %*c%s" % ("impl_nnf", level*2, ' ', str(self))
        return Disjunction([Negation([self.children[0]]), self.children[1]]).toNNF(level+1)
    
    def toRRF(self):
        return Disjunction([Negation([self.children[0]]), self.children[1]]).toRRF()
    
    def simplify(self, mrf):
        return Disjunction([Negation([self.children[0]]), self.children[1]]).simplify(mrf)

class Biimplication(ComplexFormula):    
    def __init__(self, children):
        assert len(children) == 2
        self.children = children

    def __str__(self):
        return "(" + str(self.children[0]) + " <=> " + str(self.children[1]) + ")"

    def isTrue(self, world_values):
        return (self.children[0].isTrue(world_values) == self.children[1].isTrue(world_values))
    
    def toCNF(self, level=0):
        if DEBUG_NF: print "%-8s %*c%s" % ("biimpl", level*2, ' ', str(self))
        return Conjunction([Implication([self.children[0], self.children[1]]), Implication([self.children[1], self.children[0]])]).toCNF(level+1)
    
    def toNNF(self, level = 0):
        if DEBUG_NF: print "%-8s %*c%s" % ("biim_nnf", level*2, ' ', str(self))
        return Conjunction([Implication([self.children[0], self.children[1]]), Implication([self.children[1], self.children[0]])]).toNNF(level+1)
    
    def toRRF(self):
        return Conjunction([Implication([self.children[0], self.children[1]]), Implication([self.children[1], self.children[0]])]).toRRF()

    def simplify(self, mrf):
        c1 = Disjunction([Negation(self.children[0]), self.children[1]])
        c2 = Disjunction([self.children[0], Negation(self.children[1])])
        return Conjunction([c1,c2]).simplify(mrf)
    
class Negation(ComplexFormula):    
    def __init__(self, children):
        assert len(children) == 1
        self.children = children

    def __str__(self):
        return "!(" + str(self.children[0]) + ")"

    def isTrue(self, world_values):
        return not self.children[0].isTrue(world_values)
    
    def toCNF(self, level=0):
        if DEBUG_NF: print "%-8s %*c%s" % ("neg", level*2, ' ', str(self))
        # convert the formula that is negated to negation normal form (NNF), so that if it's a complex formula, it will be either a disjunction
        # or conjunction, to which we can then apply De Morgan's law.
        # Note: CNF conversion would be unnecessarily complex, and, when the children are negated below, most of it would be for nothing!
        child = self.children[0].toNNF(level+1)
        # apply negation to child (pull inwards)
        if hasattr(child, 'children'):
            neg_children = []
            for c in child.children:
                neg_children.append(Negation([c]).toCNF(level+1))
            if type(child) == Conjunction:
                return Disjunction(neg_children).toCNF(level+1)
            elif type(child) == Disjunction:
                return Conjunction(neg_children).toCNF(level+1)
            else:
                raise Exception("Unexpected child type %s while converting '%s' to CNF!" % (str(type(child)), str(self)))
        elif type(child) == Lit:
            return Lit(not child.negated, child.predName, child.params)
        elif type(child) == GroundLit:
            return GroundLit(child.gndAtom, not child.negated)
        elif type(child) == TrueFalse:
            return TrueFalse(not child.value)
        else:
            raise Exception("CNF conversion of '%s' failed (type:%s)" % (str(self), str(type(child))))
    
    def toNNF(self, level = 0):
        if DEBUG_NF: print "%-8s %*c%s" % ("neg_nnf", level*2, ' ', str(self))
        # child is the formula that is negated
        child = self.children[0].toNNF(level+1)
        # apply negation to the children of the formula that is negated (pull inwards)
        # - complex formula (should be disjunction or conjunction at this point), use De Morgan's law
        if hasattr(child, 'children'): 
            neg_children = []
            for c in child.children:
                neg_children.append(Negation([c]).toNNF(level+1))
            if type(child) == Conjunction: # !(A ^ B) = !A v !B
                return Disjunction(neg_children).toNNF(level+1)
            elif type(child) == Disjunction: # !(A v B) = !A ^ !B
                return Conjunction(neg_children).toNNF(level+1)
            # !(A => B) = A ^ !B     
            # !(A <=> B) = (A ^ !B) v (B ^ !A)
            else:
                raise Exception("Unexpected child type %s while converting '%s' to NNF!" % (str(type(child)), str(self)))
        # - non-complex formula, i.e. literal or constant
        elif type(child) == Lit:
            return Lit(not child.negated, child.predName, child.params)
        elif type(child) == GroundLit:
            return GroundLit(child.gndAtom, not child.negated)
        elif type(child) == TrueFalse:
            return TrueFalse(not child.value)
        else:
            raise Exception("NNF conversion of '%s' failed (type:%s)" % (str(self), str(type(child))))

    def toRRF(self):
        child = self.children[0].toRRF()
        child.weight = -RRF_HARD_WEIGHT
        return RRF([child])
    
    def simplify(self, mrf):
        f = self.children[0].simplify(mrf)
        if isinstance(f, TrueFalse):
            return f.invert()
        else:
            return Negation([f])
        
class Exist(ComplexFormula):
    def __init__(self, vars, formula):
        self.children = [formula]
        self.vars = vars

    def __str__(self):
        return "EXIST " + ", ".join(self.vars) + " (" + str(self.children[0]) + ")"

    def getVariables(self, mln, vars = None, constants = None):
        if vars == None: vars = {}
        # get the child's variables:
        newvars = self.children[0].getVariables(mln, None, constants)
        # remove the quantified variable(s)
        for var in self.vars:
            try:
                del newvars[var]
            except:
                raise Exception("Variable '%s' in '%s' not bound to a domain!" % (var, str(self)))
        # add the remaining ones and return
        vars.update(newvars)
        return vars
        
    def ground(self, mrf, assignment, referencedGroundAtoms = None):
        assert len(self.children) == 1
        # find out variable domains
        vars = {}
        for var in self.vars:
            domName = None
            for child in self.children:
                domName = child.getVarDomain(var, mrf.mln)
                if domName is not None:
                    break
            if domName is None:
                raise Exception("Could not obtain domain of variable '%s', which is part of '%s')" % (var, str(self)))
            vars[var] = domName
        # ground
        gndings = []
        self._ground(self.children[0], vars, assignment, gndings, mrf, referencedGroundAtoms)
        if len(gndings) == 1:
            return gndings[0]
        return Disjunction(gndings)
            
    def _ground(self, formula, variables, assignment, gndings, mrf, referencedGroundAtoms = None):
        # if all variables have been grounded...
        if variables == {}:
            gndFormula = formula.ground(mrf, assignment, referencedGroundAtoms)
            gndings.append(gndFormula)
            return
        # ground the first variable...
        varname,domName = variables.popitem()
        for value in mrf.domains[domName]: # replacing it with one of the constants
            assignment[varname] = value
            # recursive descent to ground further variables
            self._ground(formula, dict(variables), assignment, gndings, mrf)
    
    def toCNF(self):
        raise Exception("'%s' cannot be converted to CNF. Ground this formula first!" % str(self))

    def isTrue(self, w):
        raise Exception("'%s' does not implement isTrue()")

class Equality(Formula):
    def __init__(self, params):
        assert len(params)==2
        self.params = params

    def __str__(self):
        return "%s=%s" % (str(self.params[0]), str(self.params[1]))

    def ground(self, mrf, assignment, referencedGndAtoms = None):
        params = map(lambda x: {True: assignment.get(x), False: x}[x[0].islower()], self.params) # if the parameter is a variable (lower case), do a lookup (it must be bound by now), otherwise it's a constant which we can use directly
        if None in params: raise Exception("At least one variable was not grounded in '%s'!" % str(self))
        return TrueFalse(params[0] == params[1])

    def _groundTemplate(self, assignment):
        return [Equality(self.params)]
    
    def _getTemplateVariables(self, mln, vars = None):
        return vars
    
    def getVariables(self, mln, vars = None, constants = None):        
        if constants is not None:
            # determine type of constant appearing in expression such as "x=Foo"
            for i,p in enumerate(self.params):
                other = self.params[(i+1)%2]
                if not isVar(p) and isVar(other):
                    domain = vars.get(other)
                    if domain is None:
                        raise Exception("Type of constant '%s' could not be determined" % p)
                    if domain not in constants: constants[domain] = []
                    constants[domain].append(p)
        return vars
    
    def getVarDomain(self, varname, mln):
        return None

class TrueFalse(Formula):
    def __init__(self, value):
        self.value = value
    
    def __str__(self):
        return str(self.value)

    def isTrue(self, world_values = None):
        return self.value

    def toRRF(self):
        return RRFConstantLeaf(self.value)
    
    def invert(self):
        return TrueFalse(not self.value)
    
    def simplify(self, mrf):
        return self

class NonLogicalConstraint(Constraint):
    '''a constraint that is not somehow made up of logical connectives and (ground) atoms'''
    
    def getTemplateVariants(self, mln):
        # non logical constraints are never templates; therefore, there is just one variant, the constraint itself
        return [self]
    
    def isLogical(self):
        return False
    
    def negate(self):
        raise Exception("%s does not implement negate()" % str(type(self)))
    
class CountConstraint(NonLogicalConstraint):
    '''a constraint that tests the number of relation instances against an integer'''
    
    def __init__(self, predicate, predicate_params, fixed_params, op, count):
        '''op: an operator; one of "=", "<=", ">=" '''
        self.literal = Lit(False, predicate, predicate_params)
        self.fixed_params = fixed_params
        self.count = count
        if op == "=": op = "=="
        self.op = op
    
    def __str__(self):
        op = self.op
        if op == "==": op = "="
        return "count(%s | %s) %s %d" % (str(self.literal), ", ".join(self.fixed_params), op, self.count)
    
    def iterGroundings(self, mrf):
        a = {}
        other_params = []
        for param in self.literal.params:
            if param[0].isupper():
                a[param] = param
            else:
                if param not in self.fixed_params:
                    other_params.append(param)
        #other_params = list(set(self.literal.params).difference(self.fixed_params))
        # for each assignment of the fixed parameters...
        for assignment in self._iterAssignment(mrf, list(self.fixed_params), a):
            gndAtoms = []
            # generate a count constraint with all the atoms we obtain by grounding the other params
            for full_assignment in self._iterAssignment(mrf, list(other_params), assignment):
                gndLit = self.literal.ground(mrf, full_assignment, None)
                gndAtoms.append(gndLit.gndAtom)
            yield GroundCountConstraint(gndAtoms, self.op, self.count), []
        
    def _iterAssignment(self, mrf, variables, assignment):
        '''iterates over all possible assignments for the given variables of this constraint's literal
                variables: the variables that are still to be grounded'''
        # if all variables have been grounded, we have the complete assigment
        if len(variables) == 0:
            yield dict(assignment)
            return
        # otherwise one of the remaining variables in the list...
        varname = variables.pop()
        domName = self.literal.getVarDomain(varname, mrf.mln)
        for value in mln.domains[domName]: # replacing it with one of the constants
            assignment[varname] = value
            # recursive descent to ground further variables            
            for a in self._iterAssignment(mrf, variables, assignment):
                yield a
            
class GroundCountConstraint(NonLogicalConstraint):
    def __init__(self, gndAtoms, op, count):
        self.gndAtoms = gndAtoms
        self.count = count
        self.op = op
    
    def isTrue(self, world_values):
        c = 0
        for ga in self.gndAtoms:
            if(world_values[ga.idx]):
                c += 1
        return eval("c %s self.count" % self.op)

    def __str__(self):
        op = self.op
        if op == "==": op = "="
        return "count(%s) %s %d" % (";".join(map(str, self.gndAtoms)), op, self.count)

    def negate(self):
        if self.op == "==":
            self.op = "!="
        elif self.op == "!=":
            self.op = "=="
        elif self.op == ">=":
            self.op = "<="
            self.count -= 1
        elif self.op == "<=":
            self.op = ">="
            self.count += 1
    
    def idxGroundAtoms(self, l = None):
        if l is None: l = []
        for ga in self.gndAtoms:
            l.append(ga.idx)
        return l

    def getGroundAtoms(self, l = None):
        if l is None: l = []
        for ga in self.gndAtoms:
            l.append(ga)
        return l

class TreeBuilder(object):
    def __init__(self):
        self.stack = []        
    
    def trigger(self, a, loc, toks, op):
        #print op, toks
        if op == 'lit':
            negated = False
            if toks[0] == '!' or toks[0] == '*':
                if toks[0] == '*':
                    negated = 2
                else:
                    negated = True
                toks = toks[1]
            else:
                toks = toks[0]
            self.stack.append(Lit(negated, toks[0], toks[1]))
        elif op == '!':
            if len(toks) == 1:
                formula = Negation(self.stack[-1:])
                self.stack = self.stack[:-1]
                self.stack.append(formula)
        elif op == 'v':
            if len(toks) > 1:
                formula = Disjunction(self.stack[-len(toks):])
                self.stack = self.stack[:-len(toks)]
                self.stack.append(formula)
        elif op == '^':
            if len(toks) > 1:
                formula = Conjunction(self.stack[-len(toks):])
                self.stack = self.stack[:-len(toks)]
                self.stack.append(formula)
        elif op == 'ex':
            if len(toks) == 2:
                formula = self.stack.pop()
                self.stack.append(Exist(toks[0], formula))
        elif op == '=>':
            if len(toks) == 2:
                children = self.stack[-2:]
                self.stack = self.stack[:-2]
                self.stack.append(Implication(children))
        elif op == '<=>':
            if len(toks) == 2:
                children = self.stack[-2:]
                self.stack = self.stack[:-2]
                self.stack.append(Biimplication(children))
        elif op == '=':
            if len(toks) == 2:
                self.stack.append(Equality(list(toks)))
        elif op == 'count':
            print toks
            if len(toks) in (3,4):                
                pred, pred_params = toks[0]
                if len(toks) == 3:
                    fixed_params, op, count = [], toks[1], int(toks[2])
                else:
                    fixed_params, op, count = list(toks[1]), toks[2], int(toks[3])
                self.stack.append(CountConstraint(pred, pred_params, fixed_params, op, count))
        #print str(self.stack[-1])
                
    def getConstraint(self):
        if len(self.stack) > 1:
            raise Exception("Not a valid formula - reduces to more than one element %s" % str(self.stack))
        if len(self.stack) == 0:
            raise Exception("Constraint could not be parsed")
        if not isinstance(self.stack[0], Constraint):
            raise Exception("Not an instance of Constraint!")
        return self.stack[0]

# grammar

identifierCharacter = alphanums + '_' + '-' + "'"
lcCharacter = alphas.lower()
ucCharacter = alphas.upper()
lcName = Word(lcCharacter, alphanums + '_')

openRB = Literal("(").suppress()
closeRB = Literal(")").suppress()

domName = lcName

constant = Word(ucCharacter, identifierCharacter) | Word(nums)
variable = Word(lcCharacter, identifierCharacter)

atomArgs = Group(delimitedList(constant | Combine(Optional("+") + variable)))
predDeclArgs = Group(delimitedList(domName))

predName = Word(identifierCharacter)

atom = Group(predName + openRB + atomArgs + closeRB)
literal = Optional(Literal("!") | Literal("*")) + atom

predDecl = Group(predName + openRB + predDeclArgs + closeRB) + StringEnd()

varList = Group(delimitedList(variable))
count_constraint = Literal("count(").suppress() + atom + Optional(Literal("|").suppress() + varList) + Literal(")").suppress() + (Literal("=") | Literal(">=") | Literal("<=")) + Word(nums)

formula = Forward()
exist = Literal("EXIST ").suppress() + Group(delimitedList(variable)) + openRB + Group(formula) + closeRB
equality = (constant|variable) + Literal("=").suppress() + (constant|variable)
negation = Literal("!").suppress() + openRB + Group(formula) + closeRB
item = literal | exist | equality | openRB + formula + closeRB | negation
disjunction = Group(item) + ZeroOrMore(Literal("v").suppress() + Group(item))
conjunction = Group(disjunction) + ZeroOrMore(Literal("^").suppress() + Group(disjunction))
implication = Group(conjunction) + Optional(Literal("=>").suppress() + Group(conjunction))
biimplication = Group(implication) + Optional(Literal("<=>").suppress() + Group(implication))
constraint = biimplication | count_constraint
formula << constraint

# parsing constraints/formulas

def parseFormula(input):
    tree = TreeBuilder()
    literal.setParseAction(lambda a,b,c: tree.trigger(a,b,c,'lit'))
    negation.setParseAction(lambda a,b,c: tree.trigger(a,b,c,'!'))
    #item.setParseAction(lambda a,b,c: foo(a,b,c,'item'))
    disjunction.setParseAction(lambda a,b,c: tree.trigger(a,b,c,'v'))
    conjunction.setParseAction(lambda a,b,c: tree.trigger(a,b,c,'^'))
    exist.setParseAction(lambda a,b,c: tree.trigger(a,b,c,"ex"))
    implication.setParseAction(lambda a,b,c: tree.trigger(a,b,c,"=>"))
    biimplication.setParseAction(lambda a,b,c: tree.trigger(a,b,c,"<=>"))
    equality.setParseAction(lambda a,b,c: tree.trigger(a,b,c,"="))
    count_constraint.setParseAction(lambda a,b,c: tree.trigger(a,b,c,'count'))
    f = formula + StringEnd()
    #print "parsing %s..." % input
    f.parseString(input)
    #print "done"
    return tree.getConstraint()


# main app for testing purposes only
if __name__=='__main__':
    test = 'count'
    if test == 'parsing':
        tests = ["numberEats(o,2) <=> EXIST p, p2 (eats(o,p) ^ eats(o,p2) ^ !(o=p) ^ !(o=p2) ^ !(p=p2) ^ !(EXIST q (eats(o,q) ^ !(p=q) ^ !(p2=q))))",
                 "EXIST y (rel(x,y) ^ EXIST y2 (!(y2=y) ^ rel(x,y2)) ^ !(EXIST y3 (!(y3=y) ^ !(y3=y2) ^ rel(x,y3))))",
                 "((a(x) ^ b(x)) v (c(x) ^ !(d(x) ^ e(x) ^ g(x)))) => f(x)"
                 ]#,"foo(x) <=> !(EXIST p (foo(p)))", "numberEats(o,1) <=> !(EXIST p (eats(o,p) ^ !(o=p)))", "!a(c,d) => c=d", "c(b) v !(a(b) ^ b(c))"]
        tests = ["EXIST y1 (rel(x,y1) ^ EXIST y2 (rel(x,y2) ^ !(y1=y2) ^ !(EXIST y3 (rel(x,y3) ^ !(y1=y3) ^ !(y2=y3)))))"]
        #tests = ["EXIST x (a(x))"]
        for test in tests:
            print "trying to parse %s..." % test
            f = parseFormula(test)
            print "got this: %s" % str(f)
            f.printStructure()
    elif test == 'NF':
        f = "a(x) <=> b(x)"
        f = "((a(x) ^ b(x)) v (c(x) ^ !(d(x) ^ e(x) ^ g(x)))) => f(x)"
        f = "(a(x) v (b(x) ^ c(x))) => f(x)"
        f = "(a(x) ^ b(x)) <=> (c(x) ^ d(x))"
        #f = "(a(x) ^ b(x)) v (c(x) ^ d(x))"        
        #f = "(a(x) ^ b(x)) v (c(x) ^ d(x)) v (e(x) ^ f(x))"
        #f = "(a(x) ^ b(x)) v (c(x) ^ d(x)) v (e(x) ^ f(x)) v (g(x) ^ h(x))"
        #f = "(a(x) ^ b(x) ^ e(x)) v (c(x) ^ d(x) ^ f(x))"
        #f = "(a(x) ^ b(x) ^ g(x)) v (c(x) ^ d(x) ^ h(x)) v (e(x) ^ f(x) ^ i(x))"
        #f = "(a(x) ^ !b(x) ^ !c(x)) v (!a(x) ^ b(x) ^ !c(x)) v (!a(x) ^ !b(x) ^ c(x))"
        #f = "(a(x) ^ b(x) ^ !c(x)) v (a(x) ^ !b(x) ^ c(x)) v (!a(x) ^ b(x) ^ c(x))"
        #f = "(a(x) ^ !b(x)) v (!a(x) ^ b(x))"
        #f = "(a(x) ^ b(x) ^ !c(x)) v (a(x) ^ !b(x) ^ c(x)) v (!a(x) ^ b(x) ^ c(x))"
        #f = "(a(x) ^ b(x) ^ !c(x) ^ !d(x)) v (a(x) ^ !b(x) ^ c(x) ^ !d(x)) v (!a(x) ^ b(x) ^ c(x) ^ !d(x)) v (a(x) ^ !b(x) ^ !c(x) ^ d(x)) v (!a(x) ^ b(x) ^ !c(x) ^ d(x)) v (!a(x) ^ !b(x) ^ c(x) ^ d(x))"
        #f = "consumesAny(P,Coffee) <=> ((consumedBy(C3,P) ^ goodsT(C3,Coffee)) v (consumedBy(C2,P) ^ goodsT(C2,Coffee)) v (consumedBy(C1,P) ^ goodsT(C1,Coffee)) v (consumedBy(C4,P) ^ goodsT(C4,Coffee)))"
        #f = "consumesAny(P,Coffee) <=> ((consumedBy(C3,P) ^ goodsT(C3,Coffee)) v (consumedBy(C2,P) ^ goodsT(C2,Coffee)) v (consumedBy(C1,P) ^ goodsT(C1,Coffee)))"
        f = parseFormula(f)
        f = f.toCNF()
        f.printStructure()
    elif test == 'count':
        c = "count(directs(a,m)|m) >= 4"
        c = "count(foo(a,Const)) = 2"
        #c = count_constraint.parseString(c)
        c = parseFormula(c)
        print str(c)
        pass
    
    
