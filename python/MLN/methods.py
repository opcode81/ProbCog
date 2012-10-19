
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
     #"ExactLinear": "exact inference (linear space)",  # deprecated, as EnumerationAsk is preferable
     "IPFPM_exact": "IPFP-M[exact]", 
     "IPFPM_MCSAT": "IPFP-M[MC-SAT]",
     "EnumerationAsk": "Enumeration-Ask (exact)"
    })

ParameterLearningMeasures = Enum(
     {"LL": "log-likelihood",
      #"SLL": "sampling-based log-likelihood via direct descent",
      "SLL_DN": "sampling-based log-likelihood via diagonal Newton",
      "PLL": "pseudo-log-likelihood",
      "DPLL": "[discriminative] pseudo-log-likelihood",
      "BPLL": "pseudo-log-likelihood with blocking",
      #"BPLLMemoryEfficient": "pseudo-log-likelihood with blocking, memory-efficient", # NOTE: this method has now been merged into BPLL
      "PLL_fixed": "pseudo-log-likelihood with fixed unitary clauses [deprecated]",
      "BPLL_fixed": "pseudo-log-likelihood with blocking and fixed unitary clauses [deprecated]",
      "NPL_fixed": "negative pseudo-likelihood with fixed unitary clauses [deprecated]",
      "LL_ISE": "[soft evidence] log-likelihood with soft features (independent soft evidence)",
      "PLL_ISE": "[soft evidence] pseudo-log-likelihood with soft features (independent soft evidence)",
      "DPLL_ISE": "[soft evidence][discriminative] pseudo-log-likelihood with soft features (indep. soft ev.)",
      "LL_ISEWW": "[soft evidence] log-likelihood with independent soft evidence and weighting of worlds",
      "E_ISEWW": "[soft evidence] error with independent soft evidence and weighting of worlds",
      #"SLL_ISE": "[soft evidence] sampling-based log-likelihood with soft features (independent soft evidence)", 
      "SLL_SE": "[soft evidence] sampling-based log-likelihood",
      "SLL_SE_DN": "[soft evidence] sampling-based log-likelihood via diagonal Newton" 
    })