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

import time
import sys

import FOL
from MLN.util import *


class Inference(object):
    def __init__(self, mrf):
        self.mrf = mrf 
        self.mln = mrf # for backward compatibility (virtually all code uses this)
        self.mrfEvidenceBackup = None
        self.t_start = time.time()
    
    def _readQueries(self, queries):
        # check for single/multiple query and expand
        if type(queries) != list:
            queries = [queries]
        self.queries = self._expandQueries(queries)

    def _expandQueries(self, queries):
        ''' expands the list of queries where necessary, e.g. queries that are just predicate names are expanded to the corresponding list of atoms '''
        equeries = []
        for query in queries:
            #print "got query '%s' of type '%s'" % (str(query), str(type(query)))
            if type(query) == str:
                prevLen = len(equeries)
                if "(" in query: # a fully or partially grounded formula
                    f = FOL.parseFormula(query)
                    for gndFormula in f.iterGroundings(self.mln):
                        equeries.append(gndFormula[0])
                else: # just a predicate name
                    try:
                        for gndPred in self.mln._getPredGroundings(query):
                            equeries.append(FOL.parseFormula(gndPred).ground(self.mln, {}))
                    except:
                        raise #Exception("Could not expand query '%s'" % query)
                if len(equeries) - prevLen == 0:
                    raise Exception("String query '%s' could not be expanded." % query)
            elif isinstance(query, FOL.Formula):
                equeries.append(query)
            else:
                raise Exception("Received query of unsupported type '%s'" % str(type(query)))
        return equeries
    
    def _setEvidence(self, conjunction):
        '''
            set evidence in the MRF according to the given conjunction of ground literals, keeping a backup to undo the assignment later
        '''
        if conjunction is not None:
            literals = map(lambda x: x.strip().replace(" ", ""), conjunction.split("^"))
            evidence = {}
            for gndAtom in literals:
                if gndAtom == '': continue
                tv = True
                if(gndAtom[0] == '!'):
                    tv = False
                    gndAtom = gndAtom[1:]
                evidence[gndAtom] = tv
            self.mrfEvidenceBackup = self.mrf.evidence
            self.mrf.setEvidence(evidence)
            
    def _getEvidenceBlockData(self, conjunction):
        # set evidence
        self._setEvidence(conjunction)
        # build up data structures
        self.evidence = conjunction
        self.evidenceBlocks = [] # list of pll block indices where we know the true one (and thus the setting for all of the block's atoms)
        self.blockExclusions = {} # dict: pll block index -> list (of indices into the block) of atoms that mustn't be set to true
        for idxBlock, (idxGA, block) in enumerate(self.mln.pllBlocks): # fill the list of blocks that we have evidence for
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

    def _getElapsedTime(self):
        ''' returns a pair (t,s) where t is the time in seconds elapsed thus far (since construction) and s is a readable string representation thereof '''
        elapsed = time.time() - self.t_start
        hours = int(elapsed / 3600)
        elapsed -= hours * 3600
        minutes = int(elapsed / 60)
        elapsed -= minutes * 60
        secs = int(elapsed)
        msecs = int((elapsed - secs) * 1000)
        return (elapsed, "%d:%02d:%02d.%03d" % (hours, minutes, secs, msecs))

    def infer(self, queries, given=None, verbose=True, details=False, shortOutput=False, outFile=None, saveResultsProlog=False, **args):
        
        '''
            queries: a list of queries - either strings (predicate names or partially/fully grounded atoms) or ground formulas
        '''
        self.given = given
        
        # read queries
        self._readQueries(queries)
        self.additionalQueryInfo = [""] * len(self.queries)
        
        # perform actual inference (polymorphic)
        if verbose: print type(self)
        self.results = self._infer(verbose=verbose, details=details, **args)
        self.totalInferenceTime = self._getElapsedTime()
        
        # output
        if verbose:
            if details: print "\nresults:"
            self.writeResults(sys.stdout, shortOutput=shortOutput)
        if outFile != None:
            self.writeResults(outFile, shortOutput=True, saveResultsProlog=saveResultsProlog)
        
        # undo potential side-effects
        if self.mrfEvidenceBackup is not None:
            self.mrf.evidence = self.mrfEvidenceBackup
            
        # return results
        if len(self.queries) > 1:
            return self.results
        else:
            return self.results[0]
    
    def getResultsDict(self):
        '''
            gets the results previously computed via a call to infer in the form of a dictionary
            that maps ground formulas to probabilities
        '''
        return dict(zip(self.queries, self.results))
        
    def getTotalInferenceTime(self):
        ''' returns a pair (t,s) where t is the total inference time in seconds and s is a readable string representation thereof '''
        return self.totalInferenceTime
    
    def writeResults(self, out, shortOutput=True, saveResultsProlog=False):
        self._writeResults(out, self.results, shortOutput, saveResultsProlog)
    
    def _writeResults(self, out, results, shortOutput=True, saveResultsProlog=False):

        if False == saveResultsProlog:
            
            # determine maximum query length to beautify output
            if shortOutput:
                maxLen = 0
                for q in self.queries:
                    maxLen = max(maxLen, len(strFormula(q)))
            # if necessary, get string representation of evidence
            if not shortOutput:
                if self.given is not None:
                    evidenceString = self.given
                else:
                    evidenceString = evidence2conjunction(self.mln.getEvidenceDatabase())
            # print sorted results, one per line
            strQueries = map(strFormula, self.queries)
            query2Index = {}
            for i, q in enumerate(strQueries): query2Index[q] = i
            for q in sorted(strQueries):
                i = query2Index[q]
                addInfo = self.additionalQueryInfo[i]
                if not shortOutput:
                    out.write("P(%s | %s) = %f  %s\n" % (q, evidenceString, results[i], addInfo))
                else:
                    out.write("%f  %-*s  %s\n" % (results[i], maxLen, q, addInfo))
        else:
            #HACK, TODO
            if self.given is not None:
                evidenceString = self.given
            else:
                evidenceString = evidence2conjunction(self.mln.getEvidenceDatabase())
            # print sorted results, one per line
            strQueries = map(strFormula, self.queries)
            query2Index = {}
            for i, q in enumerate(strQueries): query2Index[q] = i
            for q in sorted(strQueries):
                i = query2Index[q]
                addInfo = self.additionalQueryInfo[i]
                out.write("queryResult('"+self.queries[0].getGroundAtoms()[0].params[0].lower()+"', Value) :- Value is "+str(results[i])+".")
                #print "self.q",self.queries[0].getGroundAtoms()[0].params[0].lower()
    
    def _compareResults(self, results, referenceResults):
        if len(results) != len(referenceResults):
            raise Exception("Cannot compare results - different number of results in reference")
        me = 0
        mse = 0
        maxdiff = 0
        for i in xrange(len(results)):
            diff = abs(results[i] - referenceResults[i])
            maxdiff = max(maxdiff, diff)
            me += diff
            mse += diff * diff
        me /= len(results)
        mse /= len(results)
        return {"reference_me": me, "reference_mse": mse, "reference_maxe": maxdiff}


