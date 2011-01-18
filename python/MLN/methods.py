
class InferenceMethods:
    exact, GibbsSampling, MCSAT, exactLazy, IPFPM_exact, IPFPM_MCSAT = range(6)
    _names = {exact: "exact inference", GibbsSampling: "Gibbs sampling", MCSAT: "MC-SAT", exactLazy: "lazy exact inference", IPFPM_exact: "IPFP-M[exact]", IPFPM_MCSAT: "IPFP-M[MC-SAT]"}
    _byName = dict([(x, y) for (y, x) in _names.iteritems()])
    
class ParameterLearningMeasures:
    LL, PLL, BPLL, PLL_fixed, BPLL_fixed, NPL_fixed, LL_ISE, PLL_ISE, LL_ISEWW, SLL_ISE, DSLL_ISEWW = range(11)
    _names = {LL: "log-likelihood",
              PLL: "pseudo-log-likelihood",
              BPLL: "pseudo-log-likelihood with blocking",
              PLL_fixed: "pseudo-log-likelihood with fixed unitary clauses",
              BPLL_fixed: "pseudo-log-likelihood with blocking and fixed unitary clauses",
              NPL_fixed: "negative pseudo-likelihood with fixed unitary clauses",
              LL_ISE: "log-likelihood with independent soft evidence and weighting of formulas",
              PLL_ISE: "pseudo-log-likelihood with independent soft evidence",
              LL_ISEWW: "log-likelihood with independent soft evidence and weighting of worlds",
              SLL_ISE: "sampled log-likelihood with independent soft evidence and weighting of formulas", # sampled worlds for Z and for gradient
              DSLL_ISEWW: "double sampled log-likelihood with independent soft evidence and weighting of worlds" # sampled worlds for evidence world in F and for Z
    }
    _shortnames = {LL: "LL", PLL: "PLL", BPLL: "BPLL", PLL_fixed: "PLL_fixed", BPLL_fixed: "BPLL_fixed", NPL_fixed: "NPL_fixed", LL_ISE : "LL_ISE", PLL_ISE : "PLL_ISE", LL_ISEWW : "LL_ISEWW", SLL_ISE: "SLL_ISE", DSLL_ISEWW: "DSLL_ISEWW"}
    _byName = dict([(x, y) for (y, x) in _names.iteritems()])