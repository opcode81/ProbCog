# Recursive Random Fields - purely experimental at this stage!
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

import MLN
import sys
import math

RRF_HARD_WEIGHT = 10

class RRF:
    def __init__(self, children, weight = None):
        self.weight = weight
        self.children = children
    
    def getValue(self, world_values):
        sum = 0
        sum_weights = 0
        for child in self.children:
            feature = child.getValue(world_values)
            sum += feature * child.weight
            sum_weights += child.weight
        # pseudo-normalization to keep numbers small (actually no normalization would be needed, as it is done at the highest level anyway)
        norm = math.exp(sum_weights)
        if norm < 1: norm = 1.0
        return math.exp(sum)/norm
    
    def output(self, out, level = 0):
        out.write("%s%s\n" % (" " * level * 4, str(self.weight)))
        for child in self.children:
            child.output(out, level+1)

class RRFVariableLeaf(RRF):
    def __init__(self, gndAtom):
        self.gndAtom = gndAtom
    
    def getValue(self, world_values):
        return self.gndAtom.isTrue(world_values)
    
    def output(self, out, level = 0):
        out.write("%*c%s  %s\n" % (level * 4, ' ', str(self.weight), str(self.gndAtom)))

class RRFConstantLeaf(RRF):
    def __init__(self, value):
        self.value = value
    
    def getValue(self, world_values):
        return self.value
    
    def output(self, out, level = 0):
        out.write("%*c%s  %s\n" % (level * 4, ' ', str(self.weight), str(self.value)))

# represents a combination of an MLN/ground MRF and an RRF
# Essentially, it is a regular MLN that has an additional 'children' member, which contains 
# a recursive random field representation of each ground formula.
# Exact inference is based on the RRF part.
'''
class RRFMLN(RRF, MLN.MLN):
    def __init__(self, mlnFile, domain):
        MLN.MLN.__init__(self, mlnFile)
        self.combine(domain)
        children = []
        for f in self.gndFormulas:
            child = f.toRRF()
            child.weight = self.formulas[f.idxFormula].weight
            children.append(child)
        RRF.__init__(self, children)
    
    def _getWorlds(self):
        # create the possible worlds
        self._createPossibleWorlds()
        # calculate the world values, i.e. the exp sum for each world
        total = 0
        for world in self.worlds:
            weights = []
            for feature in self.children:
                weights.append(feature.getValue(world["values"]) * feature.weight)
            exp_sum = math.exp(sum(weights))
            total += exp_sum
            world["sum"] = exp_sum
            world["weights"] = weights
        print total
        self.partition_function = total
'''

if __name__=='__main__':
    #rrf = RRFMLN("kitchen/wts.meal_goods_any-simplest.mln", {"objType_p": ["P"], "objType_g": ["G1", "G2"]})
    rrf = RRFMLN("implication/wts.implication.mln", {"object": ["X"]})
    rrf.output(sys.stdout)
    rrf.printWorlds()
