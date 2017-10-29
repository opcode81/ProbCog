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

import random

# simulated annealing maximum sat (silly)
class SAMaxWalkSAT(object):
    def __init__(self, state, mln, evidenceBlocks, threshold=None, hardWeight=10):
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
            idxBlock = random.randint(0, len(self.mln.pllBlocks) - 1)
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
                chosen = random.randint(0, len(block) - 1)
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
                prob = (1.0 - min(1.0, abs(improvement / self.sum))) * (1 - (float(i) / i_max))
                keep = random.uniform(0.0, 1.0) <= prob
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
