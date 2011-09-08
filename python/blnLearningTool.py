# -*- coding: iso-8859-1 -*-
# BLN Learning Tool
#
# (C) 2008 by Dominik Jain
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

from Tkinter import *
import sys
import os
import struct
import time
import re
import pickle
from fnmatch import fnmatch
import traceback
from widgets import *
import configBLN as config
import subprocess

CONFIG_FILENAME = "blnlearn.config.dat"

def spawn(*args):
    try:
        subprocess.Popen(args)
    except:
        args = list(args)
        args[0] = args[0] + ".bat"
        subprocess.Popen(args)

def call(args):
    try:
        subprocess.call(args)
    except:
        args = list(args)
        args[0] = args[0] + ".bat"
        subprocess.call(args)


# --- main gui class ---

class BLNLearn:

    def __init__(self, master, dir, settings):
        self.initialized = False
        master.title("BLN Learning Tool")
        
        self.master = master
        self.settings = settings

        self.frame = Frame(master)
        self.frame.pack(fill=BOTH, expand=1)
        self.frame.columnconfigure(1, weight=1)

        row = 0

        # BIF selection
        Label(self.frame, text="Network: ").grid(row=row, column=0, sticky=NE)
        # frame
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        frame.columnconfigure(0, weight=1)
        # file picker
        self.selected_bif = FilePick(frame, ["*.xml", "*.pmml"], self.settings.get("bif", ""), self.changedBIF, font=config.fixed_width_font)
        self.selected_bif.grid(row=0, column=0, sticky="NWES")
        frame.rowconfigure(0, weight=1)
        # show button
        start_button = Button(frame, text="show", command=self.showBN)
        start_button.grid(row=0, column=1, sticky="NEWS")
        
        # blog/abl selection
        row += 1
        Label(self.frame, text="Declarations: ").grid(row=row, column=0, sticky=NE)
        self.selected_blog = FilePickEdit(self.frame, ["*.blog", "*.abl", "*.blnd"], self.settings.get("blog", ""), 10, self.changedBLOG, rename_on_edit=self.settings.get("blog_rename", False), font=config.fixed_width_font, highlighter=BLNHighlighter())
        self.selected_blog.grid(row=row, column=1, sticky="NWES")
        self.frame.rowconfigure(row, weight=1)

        # bln selection
        #row += 1
        #Label(self.frame, text="BLN: ").grid(row=row, column=0, sticky=NE)
        #self.selected_bln = FilePickEdit(self.frame, "*.bln", self.settings.get("bln", ""), 16, self.changedBLN, rename_on_edit=self.settings.get("bln_rename", False), font=config.fixed_width_font)
        #self.selected_bln.grid(row=row, column=1, sticky="NWES")
        #self.frame.rowconfigure(row, weight=1)
        
        # evidence database selection
        row += 1
        Label(self.frame, text="Training Data: ").grid(row=row, column=0, sticky=NE)
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        col = 0        
        # steps
        # - checkbox
        #self.use_max_steps = var = IntVar()
        #self.cb_max_steps = cb = Checkbutton(frame, text="Max. steps/info interval:", variable=var)
        #cb.grid(row=0, column=col, sticky="W")
        Label(frame, text="Single:").grid(row=0, column=col, sticky="W")
        #var.set(self.settings.get("useMaxSteps", 1))
        # - database selection
        col += 1
        frame.columnconfigure(col, weight=1)
        self.selected_db = FilePick(frame, "*.blogdb", self.settings.get("db", ""), self.changedDB, font=config.fixed_width_font)
        self.selected_db.grid(row=0,column=1, sticky="NWES")
        # - label
        col += 1
        Label(frame, text="OR Pattern:").grid(row=0, column=col, sticky="W")
        # - pattern entry
        col += 1
        frame.columnconfigure(col, weight=1)
        self.pattern = var = StringVar(master)
        var.set(self.settings.get("pattern", ""))
        self.entry_pattern = Entry(frame, textvariable = var)
        self.entry_pattern.grid(row=0, column=col, sticky="NEW")

        # method selection
        #row += 1
        #self.list_methods_row = row
        #Label(self.frame, text="Method: ").grid(row=row, column=0, sticky=E)        
        #self.methods = {"Likelihood weighting":"-lw", "Gibbs sampling":"-gs", "EPIS-BN": "-epis", "Backward Sampling": "-bs", "SMILE Backward Sampling": "-sbs", "Backward Sampling with Priors": "-bsp", "Experimental": "-exp"}
        #self.selected_method = StringVar(master)
        #self.selected_method.set(self.settings.get("method", self.methods.keys()[0])) # default value
        #self.list_methods = apply(OptionMenu, (self.frame, self.selected_method) + tuple(self.methods.keys()))
        #self.list_methods.grid(row=self.list_methods_row, column=1, sticky="NWE")
    
        # options
        row += 1
        Label(self.frame, text="Options: ").grid(row=row, column=0, sticky="NE")
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        #frame.columnconfigure(0, weight=1)
        # - domain learning
        self.learn_domains = IntVar()
        self.cb_learn_domains = Checkbutton(frame, text="learn domains", variable=self.learn_domains)
        self.cb_learn_domains.grid(row=0, column=1, sticky=W)
        self.learn_domains.set(self.settings.get("learnDomains", 1))
        # - domain learning
        self.ignore_data = IntVar()
        self.cb_ignore_data = Checkbutton(frame, text="ignore data on undefined predicates", variable=self.ignore_data)
        self.cb_ignore_data.grid(row=0, column=2, sticky=W)
        self.ignore_data.set(self.settings.get("ignoreData", 0))

        # additional parameters
        row += 1
        Label(self.frame, text="Add. params: ").grid(row=row, column=0, sticky="NE")
        self.params = StringVar(master)
        self.params.set(self.settings.get("params", ""))
        self.entry_params = Entry(self.frame, textvariable = self.params)
        self.entry_params.grid(row=row, column=1, sticky="NEW")
        
        # output filename
        row += 1
        Label(self.frame, text="BLOG/ABL output: ").grid(row=row, column=0, sticky="NE")
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        frame.columnconfigure(0, weight=1)
        # - filename
        self.output_filename = StringVar(master)
        self.output_filename.set(self.settings.get("outputFilename", ""))
        self.entry_output_filename = Entry(frame, textvariable = self.output_filename)
        self.entry_output_filename.grid(row=0, column=0, sticky="NEW")
        # - save option
        #self.save_results = IntVar()
        #self.cb_save_results = Checkbutton(frame, text="save", variable=self.save_results)
        #self.cb_save_results.grid(row=0, column=1, sticky=W)
        #self.save_results.set(self.settings.get("saveResults", 0))

        # output filename
        row += 1
        Label(self.frame, text="Network output: ").grid(row=row, column=0, sticky="NE")
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        frame.columnconfigure(0, weight=1)
        # - filename
        self.net_output_filename = StringVar(master)
        self.net_output_filename.set(self.settings.get("netOutputFilename", ""))
        self.entry_net_output_filename = Entry(frame, textvariable = self.net_output_filename)
        self.entry_net_output_filename.grid(row=0, column=0, sticky="NEW")

        # start button
        row += 1
        start_button = Button(self.frame, text=">> Start Learning <<", command=self.start)
        start_button.grid(row=row, column=1, sticky="NEW")

        self.initialized = True
        
        self.setGeometry()
    
    def setGeometry(self):
        g = self.settings.get("geometry")
        if g is None: return
        self.master.geometry(g)

    def changedBLN(self, name):
        self.bln_filename = name
        self.setOutputFilename()
    
    def changedBIF(self, name):
        self.bif_filename = name
        self.setOutputFilename()
    
    def changedBLOG(self, name):
        self.blog_filename = name
        self.setOutputFilename()
            
    def changedDB(self, name):
        self.db_filename = name
        self.setOutputFilename()
        
    def setOutputFilename(self):
        if not self.initialized or not hasattr(self, "blog_filename") or not hasattr(self, "bif_filename"):
            return
        #fn = config.query_output_filename(self.mln_filename, self.db_filename)
        dotpos = self.blog_filename.rfind(".")
        ext = self.blog_filename[dotpos+1:]
        basename = self.blog_filename[:dotpos]
        self.output_filename.set("%s.learnt.%s" % (basename, ext))
        # network output
        dotpos = self.bif_filename.rfind(".")
        ext = self.bif_filename[dotpos+1:]
        basename = self.bif_filename[:dotpos]
        self.net_output_filename.set("%s.learnt.%s" % (basename, ext))
        
    def showBN(self):
        bif = self.selected_bif.get()
        spawn("bnj", bif)

    def start(self):
        # get mln, db, qf and output filename
        #bln = self.selected_bln.get()
        blog = self.selected_blog.get()
        bif = self.selected_bif.get()
        db = self.selected_db.get()
        pattern = self.pattern.get()
        #bln_text = self.selected_bln.get_text()
        #method = self.selected_method.get()
        params = self.params.get()
        output = self.output_filename.get()
        netOutput = self.net_output_filename.get()
        #cwPreds = self.cwPreds.get().strip().replace(" ", "")
        
        # update settings
        #self.settings["bln"] = bln
        #self.settings["bln_rename"] = self.selected_bln.rename_on_edit.get()
        self.settings["bif"] = bif
        self.settings["blog"] = blog
        self.settings["blog_rename"] = self.selected_blog.rename_on_edit.get()
        self.settings["db"] = db
        self.settings["pattern"] = pattern
        #self.settings["db_rename"] = self.selected_db.rename_on_edit.get()
        #self.settings["method"] = method
        self.settings["params"] = params
        self.settings["outputFilename"] = output
        self.settings["netOutputFilename"] = netOutput
        #self.settings["openWorld"] = self.open_world.get()
        #self.settings["cwPreds"] = cwPreds
        #self.settings["maxSteps"] = self.maxSteps.get()
        #self.settings["numChains"] = self.numChains.get()
        self.settings["geometry"] = self.master.winfo_geometry()
        self.settings["learnDomains"] = self.learn_domains.get()
        self.settings["ignoreData"] = self.ignore_data.get()
       
        # write settings
        pickle.dump(self.settings, file(CONFIG_FILENAME, "w+"))
        
        # hide main window
        self.master.withdraw()
        
        # create command to execute
        traindb = db
        if pattern != "":
            traindb = pattern
        params = '-x "%s" -b "%s" -t "%s" -ob "%s" -ox "%s"' % (bif, blog, traindb, output, netOutput)
        #if cwPreds != "":
        #    params += " -cw %s" % cwPreds
        if self.settings["learnDomains"]: params += " -d"
        if self.settings["ignoreData"]: params += " -i"
        params  += " %s" % self.settings["params"]
        command = 'learnABL %s' % params

        # execute 
        print "\nstarting learnABL..."
        print "\ncommand:\n%s\n" % command
        t_start = time.time()
        os.system(command)
        t_taken = time.time() - t_start
        print "\ntotal execution time: %.3f seconds" % t_taken

        # restore main window
        self.master.deiconify()
        self.setGeometry()
        
        # reload the files (in case they changed)
        #self.selected_bln.reloadFile()
        #self.selected_db.reloadFile()
        self.selected_blog.reloadFile()

# -- main app --

if __name__ == '__main__':
    # read settings
    settings = {}
    if os.path.exists(CONFIG_FILENAME):
        try:
            settings = pickle.loads("\n".join(map(lambda x: x.strip("\r\n"), file(CONFIG_FILENAME, "r").readlines())))
        except:
            pass
    # process command line arguments
    argv = sys.argv
    i = 1
    arg2setting = {"-t": "db"} #{"-q" : "query", "-i" : "mln", "-e" : "db", "-r" : None}
    while i < len(argv):
        if argv[i] in arg2setting and i+1 < len(argv):
            setting = arg2setting[argv[i]]
            if setting != None:
                settings[setting] = argv[i+1]
            del argv[i+1]
            del argv[i]            
            continue
        i += 1
    if len(argv) > 1:
        settings["params"] = " ".join(argv[1:])
    # create gui
    root = Tk()
    app = BLNLearn(root, ".", settings)
    root.mainloop()
