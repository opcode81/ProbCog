# BLN Query Tool
#
# (C) 2008-2011 by Dominik Jain
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

CONFIG_FILENAME = config.queryToolSettingsFilename

# --- main gui class ---

class BLNQuery:

    def __init__(self, master, dir, settings):
        self.initialized = False
        master.title("BLN Query Tool")
        
        self.master = master
        self.settings = settings
        if not "queryByDB" in self.settings: self.settings["queryByDB"] = {}

        self.frame = Frame(master)
        self.frame.pack(fill=BOTH, expand=1)
        self.frame.columnconfigure(1, weight=1)

        row = 0

        # - fragments selection
        Label(self.frame, text="Fragments: ").grid(row=row, column=0, sticky=NE)
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
        
        # declarations selection
        row += 1
        Label(self.frame, text="Declarations: ").grid(row=row, column=0, sticky=NE)
        self.selected_blog = FilePickEdit(self.frame, ["*.blnd", "*.blog", "*.abl"], self.settings.get("blog", ""), 12, self.changedBLOG, rename_on_edit=self.settings.get("blog_rename", False), font=config.fixed_width_font)
        self.selected_blog.grid(row=row, column=1, sticky="NWES")
        self.frame.rowconfigure(row, weight=1)

        # logical constraints selection
        row += 1
        Label(self.frame, text="Logic: ").grid(row=row, column=0, sticky=NE)
        self.selected_bln = FilePickEdit(self.frame, ["*.blnl", "*.bln"], self.settings.get("bln", ""), 8, self.changedBLN, rename_on_edit=self.settings.get("bln_rename", False), font=config.fixed_width_font)
        self.selected_bln.grid(row=row, column=1, sticky="NWES")
        self.frame.rowconfigure(row, weight=1)
        
        # evidence database selection
        row += 1
        Label(self.frame, text="Evidence: ").grid(row=row, column=0, sticky=NE)
        self.selected_db = FilePickEdit(self.frame, "*.blogdb", self.settings.get("db", ""), 12, self.changedDB, rename_on_edit=self.settings.get("db_rename", False), font=config.fixed_width_font)
        self.selected_db.grid(row=row,column=1, sticky="NWES")
        self.frame.rowconfigure(row, weight=1)

        # inference method selection
        row += 1
        self.list_methods_row = row
        Label(self.frame, text="Method: ").grid(row=row, column=0, sticky=E)        
        self.methods = {
            "Likelihood Weighting":"LikelihoodWeighting",
            "Gibbs Sampling":"GibbsSampling",
            "Backward SampleSearch": "BackwardSampleSearch",
            "EPIS-BN": "EPIS",
            "Backward Sampling": "BackwardSampling",
            "Enumeration-Ask (exact)": "EnumerationAsk",
            "Lifted Backward Sampling with Children": "LiftedBackwardSampling",
            "SMILE Backward Sampling": "SmileBackwardSampling",
            "Backward Sampling with priors": "BackwardSamplingPriors",
            "Backward Sampling with children":"BackwardSamplingChildren",
            "SampleSearch with intelligent backtracking": "SampleSearchIB",
            "Experimental2": "Experimental2",
            "Experimental2b": "Experimental2b",
            "Experimental3": "Experimental3",
            "SAT-IS": "SATIS",
            "SAT-IS Extended": "SATISEx",
            "SAT-IS Extended/Gibbs":"SATISExGibbs",
            "Likelihood Weighting with Uncertain Evidence": "LWU",
            "MC-SAT": "MCSAT",
            "Pearl's algorithm":"Pearl",
            "Variable Elimination": "VarElim",
            "Belief Propagation": "BeliefPropagation",
            "Iterative Join-Graph Propagation": "IJGP",
            "SampleSearch": "SampleSearch",
            "ACE": "ACE",
            "SampleSearch with Choco Solver" : "SampleSearchChoco",
            "QGraph inference via database counts": "QGraphInference"
        }
        method_names = sorted(self.methods.keys())
        self.selected_method = StringVar(master)
        stored_method = self.settings.get("method")
        if stored_method is None or stored_method not in method_names:
            stored_method = "Likelihood Weighting" # default value
        self.selected_method.set(stored_method) 
        self.list_methods = apply(OptionMenu, (self.frame, self.selected_method) + tuple(method_names))
        self.list_methods.grid(row=self.list_methods_row, column=1, sticky="NWE")
        self.selected_method.trace("w", self.changedMethod)        

        # inference duration parameters
        row += 1
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        col = 0        
        # steps
        # - checkbox
        self.use_max_steps = var = IntVar()
        self.cb_max_steps = cb = Checkbutton(frame, text="Max. steps/info interval:", variable=var)
        cb.grid(row=0, column=col, sticky="W")
        var.set(self.settings.get("useMaxSteps", 1))
        # - max step entry
        col += 1
        frame.columnconfigure(col, weight=1)
        self.maxSteps = var = StringVar(master)
        var.set(self.settings.get("maxSteps", "1000"))
        self.entry_steps = entry = Entry(frame, textvariable = var)
        entry.grid(row=0, column=col, sticky="NEW")        
        # - interval entry
        col += 1
        frame.columnconfigure(col, weight=1)
        self.infoInterval = var = StringVar(master)
        var.set(self.settings.get("infoInterval", "100"))
        self.entry_infoInterval = Entry(frame, textvariable = var)
        self.entry_infoInterval.grid(row=0, column=col, sticky="NEW")
        # time
        # - checkbox
        col += 1
        self.use_time_limit = var = IntVar()
        self.cb_time = cb = Checkbutton(frame, text="Time limit/inverval (s):", variable=var)
        cb.grid(row=0, column=col, sticky="E")
        var.set(self.settings.get("useTimeLimit", 0))
        # - time limit entry
        col += 1
        frame.columnconfigure(col, weight=1)
        self.timeLimit = var = StringVar(master)
        var.set(self.settings.get("timeLimit", "10.0"))
        self.entry_time_limit = entry = Entry(frame, textvariable = var)
        entry.grid(row=0, column=col, sticky="NEW")
        # - time interval entry
        col += 1
        frame.columnconfigure(col, weight=1)
        self.timeInterval = var = StringVar(master)
        var.set(self.settings.get("timeInterval", "1"))
        self.entry_time_interval = entry = Entry(frame, textvariable = var)
        entry.grid(row=0, column=col, sticky="NEW")        

        # queries
        row += 1
        Label(self.frame, text="Queries: ").grid(row=row, column=0, sticky=E)
        self.query = StringVar(master)
        self.query.set(self.settings.get("query", "foo"))
        Entry(self.frame, textvariable = self.query).grid(row=row, column=1, sticky="NEW")

        # closed-world predicates
        row += 1
        Label(self.frame, text="CW preds: ").grid(row=row, column=0, sticky="NE")
        self.cwPreds = StringVar(master)
        self.cwPreds.set(self.settings.get("cwPreds", ""))
        self.entry_cw = Entry(self.frame, textvariable = self.cwPreds)
        self.entry_cw.grid(row=row, column=1, sticky="NEW")

        # additional parameters
        row += 1
        Label(self.frame, text="Add. params: ").grid(row=row, column=0, sticky="NE")
        self.paramsDict = self.settings.get("params", {})
        if type(self.paramsDict) == str:
            self.paramsDict = {stored_method: self.paramsDict}
        self.params = StringVar(master)
        self.params.set(self.paramsDict.get(stored_method, ""))
        self.entry_params = Entry(self.frame, textvariable = self.params)
        self.entry_params.grid(row=row, column=1, sticky="NEW")
        self.setAddParams()

        # output distribution filename
        row += 1
        Label(self.frame, text="Output dist.: ").grid(row=row, column=0, sticky="NE")
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        frame.columnconfigure(0, weight=1)
        # - filename
        self.output_filename = StringVar(master)
        self.output_filename.set(self.settings.get("output_filename", ""))
        self.entry_output_filename = Entry(frame, textvariable = self.output_filename)
        self.entry_output_filename.grid(row=0, column=0, sticky="NEW")
        # - save option
        self.save_results = IntVar()
        self.cb_save_results = Checkbutton(frame, text="save", variable=self.save_results)
        self.cb_save_results.grid(row=0, column=1, sticky=W)
        self.save_results.set(self.settings.get("saveResults", 0))

        # reference distribution filename
        row += 1
        Label(self.frame, text="Reference dist.:").grid(row=row, column=0, sticky="NE")
        self.selected_refdist = FilePick(self.frame, ["*.dist"], self.settings.get("reference_distribution", ""), None, font=config.fixed_width_font, allowNone=True)
        self.selected_refdist.grid(row=row, column=1, sticky="NWES")

        # start button
        row += 1
        start_button = Button(self.frame, text=">> Start Inference <<", command=self.start)
        start_button.grid(row=row, column=1, sticky="NEW", pady=(0,10))

        self.initialized = True
        
        self.setOutputFilename()
        self.setGeometry()
    
    def setAddParams(self):
        self.params.set(self.paramsDict.get(self.selected_method.get(), self.params.get()))
    
    def setGeometry(self):
        g = self.settings.get("geometry")
        if g is None: return
        self.master.geometry(g)

    def changedBLN(self, name):
        self.bln_filename = name
        #self.setOutputFilename()
    
    def changedBIF(self, name):
        self.bif_filename = name
        self.setOutputFilename()
    
    def changedBLOG(self, name):
        pass
            
    def changedDB(self, name):
        self.db_filename = name
        self.setOutputFilename()
        # restore stored query (if any)
        query = self.settings["queryByDB"].get(name)
        if not query is None and hasattr(self, "query"):
            self.query.set(query)
            
    def changedMethod(self, name, *args):
        self.setAddParams()
        self.setOutputFilename()
        
    def setOutputFilename(self):
        if not self.initialized or not hasattr(self, "bif_filename") or not hasattr(self, "db_filename") or not hasattr(self, "selected_method"):
            return
        method = self.methods[self.selected_method.get()]
        fn = "%s-%s-%s.dist" % (os.path.splitext(self.bif_filename)[0], os.path.splitext(self.db_filename)[0], method)
        self.output_filename.set(fn)
        
    def showBN(self):
        bif = self.selected_bif.get()
        if "spawnvp" in dir(os):
            os.spawnvp(os.P_NOWAIT, "bnj", ["bnj", bif])
        else:
            os.system("bnj %s" % bif)

    def start(self, saveGeometry=True):
        # get mln, db, qf and output filename
        bln = self.selected_bln.get()
        blog = self.selected_blog.get()
        bif = self.selected_bif.get()
        db = self.selected_db.get()
        bln_text = self.selected_bln.get_text()
        db_text = self.selected_db.get_text()
        method = self.selected_method.get()
        addparams = self.params.get().strip()
        self.paramsDict[method] = addparams
        outfile = self.output_filename.get()
        cwPreds = self.cwPreds.get().strip().replace(" ", "")
        refdist = self.selected_refdist.get().strip()
        
        # update settings
        self.settings["bln"] = bln
        self.settings["bln_rename"] = self.selected_bln.rename_on_edit.get()
        self.settings["bif"] = bif
        self.settings["blog"] = blog
        self.settings["blog_rename"] = self.selected_blog.rename_on_edit.get()
        self.settings["db"] = db
        self.settings["db_rename"] = self.selected_db.rename_on_edit.get()
        self.settings["method"] = method
        self.settings["useMaxSteps"] = self.use_max_steps.get()
        self.settings["maxSteps"] = self.maxSteps.get()
        self.settings["infoInterval"] = self.infoInterval.get()
        self.settings["useTimeLimit"] = self.use_time_limit.get()
        self.settings["timeLimit"] = self.timeLimit.get()
        self.settings["timeInterval"] = self.timeInterval.get()
        self.settings["params"] = self.paramsDict
        self.settings["query"] = self.query.get()        
        #self.settings["openWorld"] = self.open_world.get()
        self.settings["cwPreds"] = cwPreds
        #self.settings["numChains"] = self.numChains.get()
        if saveGeometry:
            self.settings["geometry"] = self.master.winfo_geometry()
        self.settings["output_filename"] = outfile
        self.settings["reference_distribution"] = refdist
        self.settings["saveResults"] = self.save_results.get()
       
        # write query to settings
        self.settings["queryByDB"][db] = self.settings["query"]

        # write settings
        pickle.dump(self.settings, file(CONFIG_FILENAME, "w+"))
        
        # hide main window
        self.master.withdraw()
        
        # some information
        print "\n--- query ---\n%s" % self.settings["query"]
        print "\n--- evidence (%s) ---\n%s" % (db, db_text.strip())
        
        # create command to execute
        params = ["-ia", self.methods[method], '-x "%s"' % bif, '-b "%s"' % blog, '-l "%s"' % bln, '-e "%s"' % db, '-q "%s"' % self.settings["query"].replace(" ", "")]
        if addparams != "":
            params.append(addparams)
        if cwPreds != "":
            params.append('-cw "%s"' % cwPreds)
        #if self.settings["numChains"] != "":
        #    params += " %s %s" % (usage["numChains"], self.settings["numChains"])
        if self.settings["useMaxSteps"]:
            params.append("-maxSteps %s" % (self.settings["maxSteps"]))
        params.append("-infoInterval %s" % self.settings["infoInterval"])
        if self.settings["useTimeLimit"]:
            params.append("-t %s" % self.settings["timeLimit"])
            params.append("-infoTime %s" % self.settings["timeInterval"])
        if self.settings["saveResults"] and outfile != "":
            params.append('-od "%s"' % outfile)
        if refdist != "":
            params.append('-cd "%s"' % refdist)
        if method == "ACE":
            params.append('"--acePath=%s"' % config.acePath)
        command = 'BLNinfer %s' % " ".join(params)

        # execute 
        print "\nstarting BLNinfer..."
        print "\ncommand:\n%s\n" % command
        t_start = time.time()
        os.system(command)
        t_taken = time.time() - t_start
        print "\ntotal execution time: %.3f seconds" % t_taken

        # restore main window
        self.master.deiconify()
        self.setGeometry()
        
        # update GUI
        # - reload files (in case they changed)
        self.selected_bln.reloadFile()
        self.selected_db.reloadFile()
        self.selected_blog.reloadFile()
        # - update lists of files
        self.selected_refdist.updateList()

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
    from optparse import OptionParser
    parser = OptionParser()
    parser.add_option("--run", action="store_true", dest="run", default=False, help="run with last settings (without showing GUI)")
    (options, args) = parser.parse_args()
    
    settings.update(dict(filter(lambda x: x[1] is not None, options.__dict__.iteritems())))
    if len(args) > 0:
        add_params = settings.get("params", "")
        settings["params"] = (add_params + " ".join(args)).strip()
        
    # create gui/run
    root = Tk()    
    app = BLNQuery(root, ".", settings)
    if not options.run:
        root.mainloop()
    else:
        app.start(saveGeometry=False)
