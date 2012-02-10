
import numpy
import math
import sys


class DirectDescent(object):
	''' naive gradient descent '''	
	
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


class DiagonalNewton(object):
	
	def __init__(self, wt, problem, gtol=0.01, maxSteps=None):
		self.problem = problem
		self.wt = wt
		self.gtol = gtol
		self.maxSteps = maxSteps
	
	def run(self):
		p = self.problem
		wt = numpy.matrix(self.wt).transpose()
		wtarray = numpy.asarray(wt.transpose())[0]		
		N = len(wt)
		l = 0.5				
		
		# initial gradient
		g = numpy.asmatrix(p._grad(wtarray)).transpose()
		d = numpy.sign(g)
		dT = d.transpose()		
		
		step = 1
		while self.maxSteps is None or step <= self.maxSteps:			
			
			# determine convergence
			normg = numpy.linalg.norm(g)
			if normg <= self.gtol:
				print "\nThreshold reached after %d steps. final gradient: %s (norm: %f <= %f)" % (step, g.transpose(), normg, self.gtol)
				break
			
			H = p._hessian(wtarray)
			
			# scale gradient with inverse diagonal Hessian
			sgrad = numpy.asmatrix(numpy.zeros(N)).transpose()
			for i in xrange(N):
				v = H[i][i]
				if v == 0.0: # HACK: if any variance component is zero, set corresponding gradient component to zero
					sgrad[i] = g[i] = 0.0
				else:				
					sgrad[i] = g[i] / v

			# compute predicted change in likelihood
			delta_predict = dT * (g + H * g) / 2.0
	
			alpha_numerator = dT * g
			alpha_q = dT * H * d
			
			while True:
				# determine step coefficient
				alpha = numpy.float(alpha_numerator / (alpha_q + l * dT * d))
	
				# make the step
				wt_old = numpy.matrix(wt)
				wt += alpha * sgrad
				wtarray = numpy.asarray(wt.transpose())[0]				
				
				# compute new gradient
				g = numpy.asmatrix(p._grad(wtarray)).transpose()
				d = numpy.sign(g)
				dT = d.transpose()
				
				# estimate actual likelihood change
				delta_actual = dT * g

				# update lambda
				frac = delta_actual / delta_predict
				if frac > 0.75 : l /= 2.0 # (lambda may become zero due to precision loss; worry?)
				elif frac < 0.25: l *= 4.0
				
				if delta_actual >= 0: # accept move					
					print
					print "H:\n%s" % H
					print "|g|: %f" % normg
					print "sgrad: %s" % sgrad.transpose()
					print "delta_a: %f" % delta_actual
					print "delta_p: %f" % delta_predict					
					print "lambda: %f" % l
					print "alpha: %f" % alpha			
					print "old wt: %s" % wt_old.transpose()
					print "new wt: %s" % wt.transpose()
					break
				else: # reject move
					print "delta_a=%f, adjusted lambda to %f" % (delta_actual, l)
					wt = wt_old

			step += 1
		
		return numpy.asarray(wt.transpose())[0]