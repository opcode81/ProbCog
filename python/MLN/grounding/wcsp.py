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

from bpll import BPLLGroundingFactory
from collections import defaultdict
from sys import stdout
from FOL import Conjunction, GroundAtom, GroundLit, Lit
import FOL


class WCSPGroundingFactory(BPLLGroundingFactory):
    
    def getAdmissibleVarAssignments(self, f, trueGndAtoms):
        if not type(f) is Conjunction:
            return None
        cwPred = False
        assignments = []
        for child in f.children:
            if not isinstance(child, Lit) and not isinstance(child, GroundLit) and not isinstance(child, GroundAtom):
                return None
            if child.predName in self.mrf.mln.closedWorldPreds and not cwPred:
                cwPred = True
                for gndAtom in trueGndAtoms:
                    if gndAtom.predName != child.predName: 
                        continue
                    assignment = []
                    try:
                        for (p1, p2) in zip(child.params, gndAtom.params):
                            if FOL.isVar(p1):
                                assignment.append((p1, p2))
                            elif p1 != p2: raise
                        assignments.append(tuple(assignment))
                    except: pass
        return assignments
    
    def _createGroundFormulas(self, verbose):
        # filter out conjunctions
        mrf = self.mrf 
        mln = mrf.mln    
        trueGndAtoms = [self.mrf.gndAtomsByIdx[i] for i, v in enumerate(self.mrf.evidence) if v == True]

        for i, f in enumerate(mrf.formulas):
            stdout.write('%d/%d\r' % (i, len(mrf.formulas)))
            stdout.flush()
            varAssignments = self.getAdmissibleVarAssignments(f, trueGndAtoms)
            if varAssignments is not None:
                while len(varAssignments) > 0:
                    assignment = varAssignments.pop()
                    vars = f.getVariables(mln)
                    for tup in assignment:
                        del vars[tup[0]]
                    for gf, atoms in f._iterGroundings(mrf, vars, dict(assignment), simplify=True):
                        gf.isHard = f.isHard
                        gf.weight = f.weight
                        if isinstance(gf, FOL.TrueFalse):
                            continue
                        mrf._addGroundFormula(gf, i, atoms)
            else:
                for gndFormula, referencedGndAtoms in f.iterGroundings(mrf, simplify=True):
                    gndFormula.isHard = f.isHard
                    gndFormula.weight = f.weight
                    if isinstance(gndFormula, FOL.TrueFalse):
                        continue
                    mrf._addGroundFormula(gndFormula, i, referencedGndAtoms)
      
        self.mln.gndFormulas = mrf.gndFormulas
        self.mln.gndAtomOccurrencesInGFs = mrf.gndAtomOccurrencesInGFs