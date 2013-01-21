# Markov Logic Networks - Grounding
#
# (C) 2013 by Daniel Nyga (nyga@cs.tum.edu)
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

from AbstractGrounding import DefaultGroundingFactory
from FOL import *

def isConjunctionOfLiterals(f):
    if not type(f) is Conjunction:
        return False
    for child in f.children:
        if not isinstance(child, Lit) and not isinstance(child, GroundLit) and not isinstance(child, GroundAtom):
            return False
    return True

def getitem(d, key):
    result = d.get(key, None)
    if result is None:
        result = set()
        d[key] = result
    return result

def tupleInAssignments(tuple, assignments):
    for ass in assignments:
        if tuple in ass:
            return True
    return False

def getMatchingTuples(assignment, assignments):
    matching = []
    for ass in assignments:
        try:
            for tuple in ass:
                for tuple2 in assignment:
                    if tuple[0] == tuple2[0] and tuple[1] != tuple2[1]:
                        raise
            matching.append(ass)
        except: pass
    return matching
        
class BPLLGroundingFactory(DefaultGroundingFactory):
    '''
    This class implements an efficient grounding algorithm for conjunctions
    when BPLL learning is used. It exploits the fact that we can tell
    the number of true groundings for a conjunction instantaneously:
    it is true if and only if all its conjuncts are true.
    '''

    def getValidVariableAssignments(self, conjunction, trueOrFalse):
        allAssignments = []
        gndAtoms = [self.mrf.gndAtomsByIdx[i] for i, v in enumerate(self.mrf.evidence) if v == trueOrFalse]
        for lit in conjunction.children:
            assignments = set()
            for gndAtom in gndAtoms:
                try:
                    if gndAtom.predName != lit.predName: 
                        continue
#                    print gndAtom, lit, self.mrf.evidence[idx], trueOrFalse
                    assignment = []
                    for (p1, p2) in zip(lit.params, gndAtom.params):
                        if p1[0] == '?': # replace this later by isVar(var)
                            assignment.append((p1, p2))
                        elif p1 != p2: raise
                    assignments.add(tuple(assignment))
                except: pass
            allAssignments.append(assignments)
        return allAssignments
    
    def unifyAssignments(self, validAssignments):
        unifiedAssignments = validAssignments
        var2Assignment = {}
        tuple2Assignment = {}
        var2AtomIdx = {}
        tuple2AtomIdx = {}
        assignment2AtomIdx = {}
        removedAssignments = []
        for i in range(len(validAssignments)):
            removedAssignments.append(set())
        # create some mappings for convenience
        for atomIdx, atom in enumerate(validAssignments):
            for assignment in atom:
                for t in assignment:
                    getitem(var2Assignment, t[0]).add(assignment)
                    getitem(tuple2Assignment, t).add(assignment)
                    getitem(var2AtomIdx, t[0]).add(atomIdx)
                    getitem(tuple2AtomIdx, t).add(atomIdx)
                getitem(assignment2AtomIdx, assignment).add(atomIdx)
        # iteratively unify the assignments
        queue = list(tuple2Assignment.keys())
        while len(queue) > 0:
            t = queue[0]
            del queue[0]
            v = t[0]
            if not t in tuple2AtomIdx.keys():
                continue
            if var2AtomIdx[v] != tuple2AtomIdx[t]:
                # remove all assignments containing the respective tuple
                assignmentsToBeRemoved = tuple2Assignment.get(t, None)
                if assignmentsToBeRemoved is None:
                    continue
                # store assignments to be removed in a separate list for later use
                for tbr in assignmentsToBeRemoved:
                    for atom in assignment2AtomIdx[tbr]:
                        removedAssignments[atom].add(tbr)
                del tuple2Assignment[t]
                for ass in unifiedAssignments:
                    ass.difference_update(assignmentsToBeRemoved)
                # update the queue
                for assign in assignmentsToBeRemoved:
                    var2Assignment[v].remove(assign)
                    for tup in assign:
                        if t != tup:
                            # update the mapping
                            removal = []
                            for idx in tuple2AtomIdx[tup]:
                                if not tupleInAssignments(tup, unifiedAssignments[idx]):
                                    removal.append(idx)
                            tuple2AtomIdx[tup].difference_update(removal)
                            queue.append(tup)
        return unifiedAssignments, removedAssignments
    
    def _countTrueGroundings(self, assignment, domains):
        if len(domains) == 0:
            return 1 # we found a true complete grounding
        matching = getMatchingTuples(assignment, domains[0])
        count = 0
        for m in matching:
            count += self._countTrueGroundings(assignment.union(set(m)), domains[1:])
        return count
    
    def _createGroundFormulas(self, verbose):
        # filter out conjunctions
        mrf = self.mrf 
        mln = mrf.mln        
        conjunctions = []
        others = []
        for i, f in enumerate(self.mrf.formulas):
            if isConjunctionOfLiterals(f):
                conjunctions.append(f)
            else:
                others.append(f)

        # get all true ground atoms
#        trueGndAtoms = [mrf.gndAtomsByIdx[i] for i, v in enumerate(mrf.evidence) if v is True]
        mrf.evidence = map(lambda x: x is True, mrf.evidence)
        print mrf.evidence
#        print map(str, trueGndAtoms)
        
        for conj in conjunctions:
            if str(conj) != '(action_role(?w, Goal) ^ has_sense(?w, ?s) ^ is_a(?s, ?c))':
                continue
            print conj
            
            # count for each true ground atom the number of true ground formulas it is involved
            validAssignments = self.getValidVariableAssignments(conj, True)
            print 'valid assignments:'
            for va in validAssignments:
                print va
            unifiedAssignments, removedAssignments = self.unifyAssignments(validAssignments)
            print 'unified:'
            print unifiedAssignments
            print 'removed:'
            print removedAssignments
#            trueGroundings = 0
#            for i, cta in enumerate(trueAssignments):
#                for gndAtom in cta:
#                    print gndAtom
#                    trueGroundings += self._countTrueGroundings(set(gndAtom), trueAssignments[:i] + trueAssignments[i+1:])
            
            # count for each false ground atom the number of ground formulas rendered true if its truth value was inverted
            
            
        for o in others:
            print o
        
      
      
      
      
      
      
      
      
      
      
      
      
      
        