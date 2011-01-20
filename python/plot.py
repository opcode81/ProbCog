#usage:
#from plot import Plot
#import pickle
#
#p=Plot()
#data = pickle.load(file("debug.txt"))
#colors = ["green", "red", "blue", "cyan"]
#i = 0
#for name,points in data.iteritems():
#    p.plot(points, color=colors[i], label=name)
#    i += 1
#p.legend()
#p.draw()

from math import sqrt

class Plot(object):
    '''
    on a Plot object, call any pylab function to add a plot
    and legend() to add your legend
    '''
    
    def __init__(self, name = "Plot", LaTeX = False):
        self.fapps = []
        self.name = name
        self.latex = LaTeX
    
    def apply(self, function, *args, **kwargs):
        self.fapps.append((function, args, kwargs))
        
    def __getattr__(self, name):
        return lambda *x, **y: self.apply(name, *x, **y)
    
    def draw(self):
        import pylab        
        
        # make some latex-specific settings for drawing
        if self.latex:
            # set general parameters for the generation of figures for print
            lncs_col_width = 347.12354 # (in pt) Get this from LaTeX using \showthe\columnwidth
            fig_width_pt = lncs_col_width/2  
            inches_per_pt = 1.0/72.27                # Convert pt to inch
            aspect_ratio = (sqrt(5)-1.0)/2.0         # Aesthetic ratio
            fig_height_pt = fig_width_pt*aspect_ratio
            fig_width = fig_width_pt*inches_per_pt   # width in inches
            fig_height = fig_width*aspect_ratio      # height in inches
            fig_size = [fig_width,fig_height]
            tick_font_size = 7
            label_font_size = 7
            params = {'backend': 'ps',
                      'axes.labelsize': label_font_size,
                      'text.fontsize': tick_font_size,
                      'legend.fontsize': tick_font_size,
                      'xtick.labelsize': tick_font_size,
                      'ytick.labelsize': tick_font_size,
                      'text.usetex': True,
                      'figure.figsize': fig_size,
                      'font.family': 'serif'}
            pylab.rcParams.update(params)            

            # configure axes position
            # - variables
            left_label_chars = 4
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

        # apply the plot
        pylab.figure(1)
        pylab.clf()
        if self.latex:
            pylab.axes([left,bottom,0.90-left,0.95-bottom])            
        for f in self.fapps:
            function = eval("pylab.%s" % f[0])
            function(*f[1], **f[2])
        if self.latex:
            filename = "%s.pdf" % self.name
            print "saving %s" % filename
            pylab.savefig(filename)
        else:
            pylab.show()