import unittest
from MLN import MLN
import os
import sys
from MLN.methods import *
from pprint import pprint

class MLNTestCase(unittest.TestCase):
    
    def setUp(self):
        self.modelsDir = os.path.join("..", "models")
        self.smokingDir = os.path.join(self.modelsDir, "smokers")
        self.smokingDB = os.path.join(self.smokingDir, "smoking-train.db")
        self.studentDir = os.path.join(self.modelsDir, "student_course")
        self.studentDB = os.path.join(self.studentDir, "1each.db")
        self.probConstrDir = os.path.join(self.modelsDir, "probConstraints")
        self.posteriorCDir = os.path.join(self.probConstrDir, "posterior")
        self.priorCDir = os.path.join(self.probConstrDir, "prior")
    
    def assertApproxEqual(self, a, b, delta = 1e-7):
        self.assertTrue(abs(a-b) <= delta)
    
    def assertApproxListEqual(self, a, b, delta = 1e-7):
        self.assertEqual(len(a), len(b))
        for i in xrange(len(a)):
            self.assertApproxEqual(a[i], b[i], delta)
    
    def getSmokersModel(self):
        return MLN(os.path.join(self.smokingDir, "smoking.mln"))
    
    def test_learnBPLL(self):        
        mln = self.getSmokersModel()
        mln.learnWeights([self.smokingDB], ParameterLearningMeasures.BPLL, optimizer="bfgs")
        correctWeights = [1.1267686091064575, 1.5777760206174236]
        for i, f in enumerate(mln.formulas):
            self.assertAlmostEqual(correctWeights[i], f.weight)
    
    def test_learnPLL(self):        
        mln = self.getSmokersModel()
        mln.learnWeights([self.smokingDB], ParameterLearningMeasures.PLL, optimizer="bfgs")
        correctWeights = [0.664496216335284, 1.8004196461831026]
        for i, f in enumerate(mln.formulas):
            self.assertAlmostEqual(correctWeights[i], f.weight)
    
    def test_groundAMLN(self):
        mln = MLN(os.path.join(self.studentDir, "student_course2.a.mln"))
        mrf = mln.groundMRF(self.studentDB)
        weights = map(lambda p: float(p[0]), mrf.getGroundFormulas())
        correctWeights = [1.5604290525533999, 3.6511741317144004, -0.7968133588156, 7.0145072690164003, 0.151252120947, -1.6360797755282002, -1.5131502151878, -8.4313192249819995, -0.38454063647300002, -1.16283955905, 1.3519705598595, -2.8760315103522003]
        for i, f in enumerate(mln.formulas):
            self.assertAlmostEqual(correctWeights[i], f.weight)
    
    def test_groundWithVars(self):
        mln = MLN(os.path.join(self.studentDir, "student_course2_simLearned.mln"))
        mrf = mln.groundMRF(self.studentDB)
        weights = map(lambda p: float(p[0]), mrf.getGroundFormulas())
        correctWeights = [-0.40546510810816444, -1.0986122886681098, -0.22314355131420957, -1.6094379124341012, -7.3508372506616082, -0.0006422607799453446, -1.0986122886681098, -0.40546510810816427, -6.1468644463356723, -0.0021424753776469402, 27.350837250661606, 27.350837250661606]
        for i, f in enumerate(mln.formulas):
            self.assertAlmostEqual(correctWeights[i], f.weight)
    
    def test_softEvidenceIPFPM(self):
        mln = MLN(os.path.join(self.posteriorCDir, "simple.mln"))
        mrf = mln.groundMRF(os.path.join(self.posteriorCDir, "test.db"))
        results = map(float, (mrf.inferIPFPM("attr")))
        correctResults = [0.6666666666666666, 0.10]
        self.assertApproxListEqual(results, correctResults)

    def test_softEvidenceMCSATPC(self):
        mln = MLN(os.path.join(self.posteriorCDir, "simple.mln"))
        mrf = mln.groundMRF(os.path.join(self.posteriorCDir, "test.db"))
        results = map(float, (mrf.inferMCSAT("attr")))
        correctResults = [0.6666666666666666, 0.10]
        self.assertApproxListEqual(results, correctResults, 0.04)
        
if __name__ == '__main__':
    unittest.main()
