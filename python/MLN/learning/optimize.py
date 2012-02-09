
import numpy

class DirectDescent(object):
	
	def __init__(self, wt, grad, gtol):
		self.grad = grad
		self.wt = wt
		self.threshold = gtol
		self.maxSteps = 1000
	
	def run(self):
		norm = 1
		alpha = 0.1
		step = 1
		while True:
			grad = -self.grad(self.wt)
			norm = numpy.linalg.norm(grad)
			print "step %d, norm: %f" % (step, norm)
			print grad
			print self.wt
			if norm < self.threshold or step > self.maxSteps:
				break
			step += 1
			self.wt += grad * alpha
		return self.wt