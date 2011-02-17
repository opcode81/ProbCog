
class Enum(object):
    def __init__(self, d):
        self.value2name = d
        self.name2value = dict([(y,x) for (x,y) in self.value2name.iteritems()])
    
    def __getattr__(self, attr):
        if attr in self.value2name:
            return attr
        raise Exception("Enum does not define %s, only %s" % (attr, self.value2name.keys()))
    
    def byShortName(self, shortName):
        return shortName
    
    def byName(self, name):
        return self.name2value[name]
    
    def getName(self, value):
        return self.value2name[value]
    
    def getNames(self):
        return self.value2name.values()
    
    def getShortName(self, value):
        return value
    
InferenceMethods = Enum(
    {"Exact": "exact inference", 
     "GibbsSampling": "Gibbs sampling", 
     "MCSAT": "MC-SAT", 
     "ExactLinear": "exact inference (linear space)", 
     "IPFPM_exact": "IPFP-M[exact]", 
     "IPFPM_MCSAT": "IPFP-M[MC-SAT]",
     "EnumerationAsk": "Enumeration-Ask (exact)"
    })

ParameterLearningMeasures = Enum(
     {"LL": "log-likelihood",
      "PLL": "pseudo-log-likelihood",
      "BPLL": "pseudo-log-likelihood with blocking",
      "PLL_fixed": "pseudo-log-likelihood with fixed unitary clauses",
      "BPLL_fixed": "pseudo-log-likelihood with blocking and fixed unitary clauses",
      "NPL_fixed": "negative pseudo-likelihood with fixed unitary clauses",
      "LL_ISE": "log-likelihood with independent soft evidence and weighting of formulas",
      "PLL_ISE": "pseudo-log-likelihood with independent soft evidence",
      "DPLL_ISE": "discriminative pseudo-log-likelihood with independent soft evidence for query atLocation",
      "LL_ISEWW": "log-likelihood with independent soft evidence and weighting of worlds",
      "E_ISEWW": "error with independent soft evidence and weighting of worlds",
      "SLL_ISE": "sampled log-likelihood with independent soft evidence and weighting of formulas", # sampled worlds for Z and for gradient
      "DSLL_WW": "double sampled log-likelihood with (implicit) weighting of worlds" # sampled worlds for evidence world in F and for Z
    })