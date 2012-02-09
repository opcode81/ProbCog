
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


class DiagonalNewton(object):
	def __init__(self, wt, problem):
		self.problem = problem
		self.wt = wt
	
	def run(self):
		p = self.problem
		wt = numpy.matrix(self.wt).transpose()
		N = len(wt)
		
		prev_g = numpy.matrix(numpy.zeros(N)).transpose()
		prev_d = prev_g
		prev_H = numpy.zeros((N,N))
		prev_l = 0.5
		prev_dT = prev_d.transpose()
		
		step = 1
		maxSteps = None
		
		while maxSteps is None or step <= maxSteps:
			wtlist = wt.transpose().tolist()[0]
			H = p._hessian(numpy.array(wtlist))
			g = numpy.matrix(p._grad(wt)).transpose()
			d = numpy.sign(g)
			dT = d.transpose()

			invH = numpy.zeros((N,N), numpy.float64)
			illdefined = False
			for i in xrange(N):
				v = H[i][i]
				if v == 0.0: # HACK
					v = 1e-6 
					g[i] = 0.0
				invH[i][i] = 1.0 / v
			
			sgrad = invH * g
			
			delta_actual = dT * g
			delta_predict = prev_dT * (prev_g + prev_H * prev_g) / 2.0
			frac = delta_actual / delta_predict
			if frac > 0.75 : l = prev_l / 2.0
			elif frac < 0.25: l = prev_l * 4.0
			else: l = prev_l
			
			alpha = dT * g
			alpha /= dT * H * d  + l * dT * d
			alpha = float(alpha)
			
			print
			print "H:\n%s" % H
			print "sgrad: %s" % g.transpose()
			print "sgrad: %s" % sgrad.transpose()
			print "delta_p: %f" % delta_predict
			print "delta_a: %f" % delta_actual
			print "lambda: %f" % l
			print "alpha: %f" % alpha			
			print "old wt: %s" % wt.transpose()
			wt = wt + alpha * sgrad
			print "new wt: %s" % wt.transpose()
			
			breakAlpha = 0.001
			if alpha < breakAlpha:
				print "alpha dropped below %f, stopping." % breakAlpha
				break
		
			prev_g = g
			prev_d = d
			prev_H = H
			prev_l = l
			prev_dT = dT
			
			step += 1
		
		return wt.transpose().tolist()[0]