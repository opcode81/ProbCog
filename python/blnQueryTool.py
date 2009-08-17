# BLN Query Tool
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
import config

CONFIG_FILENAME = "blnquery.config.dat"

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

        # - BIF selection
        Label(self.frame, text="BIF: ").grid(row=row, column=0, sticky=NE)
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
        Label(self.frame, text="BLOG: ").grid(row=row, column=0, sticky=NE)
        self.selected_blog = FilePickEdit(self.frame, ["*.blog", "*.abl"], self.settings.get("blog", ""), 10, self.changedBLOG, rename_on_edit=self.settings.get("blog_rename", False), font=config.fixed_width_font)
        self.selected_blog.grid(row=row, column=1, sticky="NWES")
        self.frame.rowconfigure(row, weight=1)

        # bln selection
        row += 1
        Label(self.frame, text="BLN: ").grid(row=row, column=0, sticky=NE)
        self.selected_bln = FilePickEdit(self.frame, "*.bln", self.settings.get("bln", ""), 16, self.changedBLN, rename_on_edit=self.settings.get("bln_rename", False), font=config.fixed_width_font)
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
        self.methods = {"Likelihood weighting":"-lw", "Gibbs sampling":"-gs", "EPIS-BN": "-epis", "Backward Sampling": "-bs", "Lifted Backward Sampling with Children": "-lbs", "SMILE Backward Sampling": "-sbs", "Backward Sampling with Priors": "-bsp", "Experimental": "-exp", "SAT-IS": "-satis", "SAT-IS Extended": "-satisex", "SAT-IS Extended/Gibbs":"-satisexg", "Likelihood Weighting With Uncertain Evidence": "-lwu", "MC-SAT": "-mcsat"}
        self.selected_method = StringVar(master)
        self.selected_method.set(self.settings.get("method", self.methods.keys()[0])) # default value
        self.list_methods = apply(OptionMenu, (self.frame, self.selected_method) + tuple(self.methods.keys()))
        self.list_methods.grid(row=self.list_methods_row, column=1, sticky="NWE")

        # queries
        row += 1
        Label(self.frame, text="Queries: ").grid(row=row, column=0, sticky=E)
        self.query = StringVar(master)
        self.query.set(self.settings.get("query", "foo"))
        Entry(self.frame, textvariable = self.query).grid(row=row, column=1, sticky="NEW")

        # max. number of steps
        row += 1
        Label(self.frame, text="Max. steps: ").grid(row=row, column=0, sticky=E)
        self.maxSteps = StringVar(master)
        self.maxSteps.set(self.settings.get("maxSteps", ""))
        self.entry_steps = Entry(self.frame, textvariable = self.maxSteps)
        self.entry_steps.grid(row=row, column=1, sticky="NEW")

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
        self.params = StringVar(master)
        self.params.set(self.settings.get("params", ""))
        self.entry_params = Entry(self.frame, textvariable = self.params)
        self.entry_params.grid(row=row, column=1, sticky="NEW")

        '''
        # number of chains
        row += 1
        Label(self.frame, text="Num. chains: ").grid(row=row, column=0, sticky="NE")
        self.numChains = StringVar(master)
        self.numChains.set(self.settings.get("numChains", ""))
        self.entry_chains = Entry(self.frame, textvariable = self.numChains)
        self.entry_chains.grid(row=row, column=1, sticky="NEW")

        # all preds open-world
        row += 1
        self.open_world = IntVar()
        self.cb_open_world = Checkbutton(self.frame, text="Apply open-world assumption to all predicates", variable=self.open_world)
        self.cb_open_world.grid(row=row, column=1, sticky=W)
        self.open_world.set(self.settings.get("openWorld", 1))

        # output filename
        row += 1
        Label(self.frame, text="Output: ").grid(row=row, column=0, sticky="NE")
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        frame.columnconfigure(0, weight=1)
        # - filename
        self.output_filename = StringVar(master)
        self.output_filename.set(self.settings.get("output_filename", ""))
        self.entry_ouconfig.tput_filename = Entry(frame, textvariable = self.output_filename)
        self.entry_output_filename.grid(row=0, column=0, sticky="NEW")
        # - save option
        self.save_results = IntVar()
        self.cb_save_results = Checkbutton(frame, text="save", variable=self.save_results)
        self.cb_save_results.grid(row=0, column=1, sticky=W)
        self.save_results.set(self.settings.get("saveResults", 0))
        '''

        # start button
        row += 1
        start_button = Button(self.frame, text=">> Start Inference <<", command=self.start)
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
        pass
    
    def changedBLOG(self, name):
        pass
            
    def changedDB(self, name):
        self.db_filename = name
        self.setOutputFilename()
        # restore stored query (if any)
        query = self.settings["queryByDB"].get(name)
        if not query is None and hasattr(self, "query"):
            self.query.set(query)
        
    def setOutputFilename(self):
        pass
        #if not self.initialized or not hasattr(self, "db_filename") or not hasattr(self, "mln_filename"):
        #    return
        #fn = config.query_output_filename(self.mln_filename, self.db_filename)
        #self.output_filename.set(fn)
        
    def showBN(self):
        bif = self.selected_bif.get()
        os.spawnl(os.P_NOWAIT, "/bin/sh", "/bin/sh", "-c", "bnj %s" % bif)
        #os.system("bnj %s" % bif)

    def start(self):
        # get mln, db, qf and output filename
        bln = self.selected_bln.get()
        blog = self.selected_blog.get()
        bif = self.selected_bif.get()
        db = self.selected_db.get()
        bln_text = self.selected_bln.get_text()
        db_text = self.selected_db.get_text()
        method = self.selected_method.get()
        params = self.params.get()
        cwPreds = self.cwPreds.get().strip().replace(" ", "")
        
        # update settings
        self.settings["bln"] = bln
        self.settings["bln_rename"] = self.selected_bln.rename_on_edit.get()
        self.settings["bif"] = bif
        self.settings["blog"] = blog
        self.settings["blog_rename"] = self.selected_blog.rename_on_edit.get()
        self.settings["db"] = db
        self.settings["db_rename"] = self.selected_db.rename_on_edit.get()
        self.settings["method"] = method
        self.settings["params"] = params
        self.settings["query"] = self.query.get()
        #self.settings["output_filename"] = output
        #self.settings["openWorld"] = self.open_world.get()
        self.settings["cwPreds"] = cwPreds
        self.settings["maxSteps"] = self.maxSteps.get()
        #self.settings["numChains"] = self.numChains.get()
        self.settings["geometry"] = self.master.winfo_geometry()
        #self.settings["saveResults"] = self.save_results.get()
       
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
        params = '%s -x "%s" -b "%s" -l "%s" -e "%s" -q "%s" %s' % (self.methods[method], bif, blog, bln, db, self.settings["query"], self.settings["params"])
        if cwPreds != "":
            params += " -cw %s" % cwPreds
        #if self.settings["numChains"] != "":
        #    params += " %s %s" % (usage["numChains"], self.settings["numChains"])
        if self.settings["maxSteps"] != "":
            params += " -maxSteps %s" % (self.settings["maxSteps"])
        command = 'BLNinfer %s' % params

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
        
        # reload the files (in case they changed)
        self.selected_bln.reloadFile()
        self.selected_db.reloadFile()
        self.selected_blog.reloadFile()

# -- main app --

if __name__ == '__main__':
    # read settings
    settings = {}
    if os.path.exists(CONFIG_FILENAME):
        try:
            settings = pickle.load(file(CONFIG_FILENAME, "r"))
        except:
            pass
    # process command line arguments
    argv = sys.argv
    i = 1
    arg2setting = {} #{"-q" : "query", "-i" : "mln", "-e" : "db", "-r" : None}
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
    app = BLNQuery(root, ".", settings)
    root.mainloop()
