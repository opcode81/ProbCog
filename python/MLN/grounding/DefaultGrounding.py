# -*- coding: iso-8859-1 -*-
#
# Markov Logic Networks
#
# (C) 2013 by Dominik Jain (jain@cs.tum.edu)
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
import sys
from MLN.util import *
from AbstractGrounding import AbstractGroundingFactory

class DefaultGroundingFactory(AbstractGroundingFactory):
    '''
    Implementation of the default grounding algorithm, which
    creates ALL ground atoms and ALL ground formulas.
    '''
    
    def _createGroundAtoms(self, verbose=False):
        # create ground atoms
        for predName, domNames in self.mln.predicates.iteritems():
            self._groundAtoms([], predName, domNames, verbose)

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

    def _iterGroundFormulas(self, verbose=False):
        mrf = self.mrf
        assert len(mrf.gndAtoms) > 0
        
        # generate all groundings
        if verbose: 
            print "Generating ground formulas..."
        for idxFormula, formula in enumerate(mrf.formulas):
            if verbose: 
                print "  %s" % strFormula(formula)
            for gndFormula, referencedGndAtoms in formula.iterGroundings(mrf, mrf.simplify):
                if isinstance(gndFormula, FOL.TrueFalse):
                    continue
                gndFormula.isHard = formula.isHard
                gndFormula.weight = formula.weight
                gndFormula.idxFormula = idxFormula
                yield (gndFormula, referencedGndAtoms)
        
    def _createGroundFormulas(self, verbose=False):
        mrf = self.mrf
        for (gndFormula, referencedGndAtoms) in self._iterGroundFormulas(verbose=verbose):
            mrf._addGroundFormula(gndFormula, gndFormula.idxFormula, referencedGndAtoms)
        
        self.mln.gndFormulas = mrf.gndFormulas
        self.mln.gndAtomOccurrencesInGFs = mrf.gndAtomOccurrencesInGFs

class GroundedAtomsGroundFormulasIterableFactory(DefaultGroundingFactory):
    '''
    Generates only ground atoms but not ground formulas. For the generation of ground formulas,
    an iterator method is added to the MRF: iterGroundFormulas
    '''

    def _createGroundFormulas(self, verbose=False):
        self.mrf.gndFormulas = None
        self.mrf.gndAtomOccurrencesInGFs = None
        self.mrf.iterGroundFormulas = self.__iterGroundFormulas
    
    def __iterGroundFormulas(self, verbose=False):
        for item in self._iterGroundFormulas(verbose=verbose):
            yield item[0]
        
