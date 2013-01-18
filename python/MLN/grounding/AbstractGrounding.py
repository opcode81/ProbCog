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

import FOL
import re

class AbstractGroundingFactory(object):
    
    def __init__(self, mrf, db):
        self.mrf = mrf
        self.mln = mrf.mln
        self.db = db
        
    def _createGroundAtoms(self):
        raise Exception('Not implemented')
    
    def _createGroundFormulas(self):
        raise Exception('Not implemented')

    def groundMRF(self):
        self._createGroundAtoms()
        self.mrf.setEvidence(self.db.evidence)
        self.mrf.softEvidence = self.db.softEvidence
        self._createGroundFormulas()
        return self.mrf
        

class DefaultGroundingFactory(AbstractGroundingFactory):
    '''
    Implementation of the default grounding algorithm, which
    creates ALL ground atoms and ALL ground formulas.
    '''
    
    def _createGroundAtoms(self):
        # create ground atoms
        atoms = []
        for predName, domNames in self.mln.predicates.iteritems():
            self._groundAtoms([], predName, domNames)

    def _groundAtoms(self, cur, predName, domNames, verbose=False):
        # if there are no more parameters to ground, we're done
        # and we cann add the ground atom to the MRF
        mrf = self.mrf
        assert len(mrf.gndFormulas) == 0
        if domNames == []:
            atom = FOL.GroundAtom(predName, cur)
            mrf.addGroundAtom(atom)
            return

        # create ground atoms for each way of grounding the first of the 
        # remaining variables whose domains are given in domNames
        dom = mrf.domains.get(domNames[0])
        if dom is None or len(dom) == 0:
            raise Exception("Domain '%s' is empty!" % domNames[0])
        for value in dom:
            self._groundAtoms(cur + [value], predName, domNames[1:])
        
    def _createGroundFormulas(self, verbose=False):
        mrf = self.mrf
        assert len(mrf.gndAtoms) > 0
        
        # generate all groundings
        if verbose: 
            print "Grounding formulas..."
        for idxFormula, formula in enumerate(mrf.formulas):
            if verbose: 
                print "  %s" % strFormula(formula)
            for gndFormula, referencedGndAtoms in formula.iterGroundings(mrf, mrf.simplify):
                gndFormula.isHard = formula.isHard
                gndFormula.weight = formula.weight
                if isinstance(gndFormula, FOL.TrueFalse):
                    continue
                mrf._addGroundFormula(gndFormula, idxFormula, referencedGndAtoms)

        # materialize all formula weights
        max_weight = 0
        for f in mrf.formulas:
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
        if verbose: 
            print "setting %d hard weights to %f" % (len(mrf.hard_formulas), hard_weight)
        for f in mrf.hard_formulas:
            if verbose: 
                print "  ", strFormula(f)
            f.weight = hard_weight
        
        self.mln.gndFormulas = mrf.gndFormulas
        self.mln.gndAtomOccurrencesInGFs = mrf.gndAtomOccurrencesInGFs

        
        
        
        
        