#-----------------------------------------------------------------------------
# Purpose:     
'''
Created on 08.09.2010

@author: maierpa
'''
#
# Author:      Paul Maier
#
# Created:     08.09.2010
# RCS-ID:      $Id:  $
# SVN-ID:      $Id$
# Copyright:   
# Licence:     <your licence>
#-----------------------------------------------------------------------------
import numpy as np
from scipy.stats.distributions import beta
from matplotlib.patches import Polygon
import pylab
import os

POSTER_GRAPHIC_NAME = "../ciGraphic"
DPI = 350

class Plot:
    def __init__(self, name, LaTeX = True, deferredApply = False, widthPt = 347.12354, fontSize = 7, fontFamily = "serif"):
        self.fapps = []
        self.name = name
        self.latex = LaTeX
        self.subplots = []
        self.deferredApply = deferredApply

        from math import sqrt
        # make some latex-specific settings for drawing
        if self.latex and True:
            # set general parameters for the generation of figures for print
            fig_width_pt = widthPt # width of figure (in pt); Get this from LaTeX using \showthe\columnwidth
            inches_per_pt = 1.0/72.27                # Convert pt to inch
            aspect_ratio = (sqrt(5)-1.0)/2.0         # Aesthetic ratio
            fig_height_pt = fig_width_pt*aspect_ratio
            fig_width = fig_width_pt*inches_per_pt   # width in inches
            fig_height = fig_width*aspect_ratio      # height in inches
            fig_size = [fig_width,fig_height]
            tick_font_size = fontSize
            label_font_size = fontSize
            self.rcParams = {'backend': 'ps',
                      'axes.labelsize': label_font_size,
                      'text.fontsize': tick_font_size,
                      'legend.fontsize': tick_font_size,
                      'xtick.labelsize': tick_font_size,
                      'ytick.labelsize': tick_font_size,
                      'text.usetex': True,
                      'figure.figsize': fig_size,
                      'font.family': fontFamily}

            # configure axes position
            # - variables
            left_label_chars = 2
            border = 0.05
            tick_extend_into_right_border_chars = 2 # rightmost x-axis tick label may extend into border
            # - compute left and bottom borders
            yaxis_tick_width = left_label_chars * 0.75 * tick_font_size / fig_width_pt
            yaxis_label_width = label_font_size / fig_width_pt          
            left = border+yaxis_tick_width+yaxis_label_width
            xaxis_label_height = label_font_size * 1.5 / fig_height_pt
            xaxis_tick_height = tick_font_size * 1.5 / fig_height_pt
            bottom = border+xaxis_tick_height+xaxis_label_height
            # - compute actual graph dimensions (relative to total dimensions of figure)
            width = 1.0 - border - left - tick_extend_into_right_border_chars * 0.75 * tick_font_size / fig_width_pt
            height = 1.0 - border - bottom      
            self.latex_axes_bounds = [left,bottom,0.90-left,0.95-bottom]
        
        if not deferredApply:
            self.init()

    def init(self):
        pylab.rcParams.update(self.rcParams)
        pylab.figure(1)
        pylab.clf()
        if self.latex:
            pylab.axes(self.latex_axes_bounds)

    def apply(self, function, *args, **kwargs):
        if self.deferredApply:
            self.fapps.append((function, args, kwargs))
        else:
            function = eval("pylab.%s" % function)
            function(*args, **kwargs)
        
    def __getattr__(self, name):
        return lambda *x, **y: self.apply(name, *x, **y)
    
    def subplot(self, *args, **kwargs):
        ax = pylab.subplot(*args, **kwargs)
        self.subplots.append(ax)
        return ax
    
    def draw(self):
        if self.deferredApply:
            self.init()
            for f in self.fapps:
                function = eval("pylab.%s" % f[0])
                function(*f[1], **f[2])
        if self.latex:
            filename = "%s.pdf" % self.name
            print "saving %s" % filename
            pylab.savefig(filename)
        else:
            pylab.show()


def plotCIAreas(ax, N, nG):
    gamma = 0.95
    inverseBetaCDF = lambda x, a, b:beta.isf(1 - x, a, b)
    lower = inverseBetaCDF((1 - gamma) / 2, nG, N - nG)
    upper = inverseBetaCDF(1 - (1 - gamma) / 2, nG, N - nG)
    # lower bound area
    lx = np.linspace(0, lower, 200)
    ly = beta.pdf(lx, nG, N - nG)
    verts = [(0, 0)] + zip(lx, ly) + [(lower, 0)]
    poly = Polygon(verts, facecolor='0.8')#, linestyle='dashed')
    ax.add_patch(poly)
    # upper bound area
    lx = np.linspace(upper, 1, 200)
    ly = beta.pdf(lx, nG, N - nG)
    verts = [(upper, 0)] + zip(lx, ly) + [(1, 0)]
    poly = Polygon(verts, facecolor='0.8')#, linestyle='dashed')
    ax.add_patch(poly)
    
    
if __name__ == '__main__':
    
    plt = Plot(POSTER_GRAPHIC_NAME, True, widthPt = 2*125.77853, fontSize=10)
    ax = plt.subplot(111)
    
    N = 100
    
    x = np.linspace(0, 1, 200)
    
    for nG in xrange(0,N,5):
        plt.plot(x, beta.pdf(x, nG, N-nG), 'grey')
#        plotCIAreas(ax, N, nG)

#    for nG in xrange(0,N*10,50):
#        plt.plot(x, beta.pdf(x, nG, N*10-nG), 'r')

    nG = 70
    plt.plot(x, beta.pdf(x, nG, N-nG), 'b')
    plotCIAreas(ax, N, nG)
    
    plt.draw()
    os.system("pdfcrop %s.pdf %s.pdf" % (POSTER_GRAPHIC_NAME, POSTER_GRAPHIC_NAME))
