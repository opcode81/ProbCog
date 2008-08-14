# MLN Query Tool
#
# (C) 2006-2007 by Dominik Jain
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
import MLN

def config_value(key, default):
    if key in dir(config):
        return eval("config.%s" % key)
    return default

# --- main gui class ---

class MLNQuery:

    def __init__(self, master, dir, settings):
        self.initialized = False
        master.title("MLN Query Tool")
        
        self.master = master
        self.settings = settings
        if not "queryByDB" in self.settings: self.settings["queryByDB"] = {}

        self.frame = Frame(master)
        self.frame.pack(fill=BOTH, expand=1)
        self.frame.columnconfigure(1, weight=1)

        # engine selection
        row = 0
        self.alchemy_versions = config.alchemy_versions
        Label(self.frame, text="Engine: ").grid(row=row, column=0, sticky="E")
        alchemy_engines = config.alchemy_versions.keys()
        alchemy_engines.sort()
        engines = ["internal"]
        engines.extend(alchemy_engines)
        self.selected_engine = StringVar(master)
        engine = self.settings.get("engine")
        if not engine in engines: engine = engines[0]
        self.selected_engine.set(engine)
        self.selected_engine.trace("w", self.onChangeEngine)
        list = apply(OptionMenu, (self.frame, self.selected_engine) + tuple(engines))
        list.grid(row=row, column=1, sticky="NWE")

        # mln selection
        row += 1
        Label(self.frame, text="MLN: ").grid(row=row, column=0, sticky=NE)
        self.selected_mln = FilePickEdit(self.frame, config.query_mln_filemask, self.settings.get("mln", ""), 22, self.changedMLN, rename_on_edit=self.settings.get("mln_rename", 0), font=config.fixed_width_font)
        self.selected_mln.grid(row=row, column=1, sticky="NWES")
        self.frame.rowconfigure(row, weight=1)
        
        self.convert_to_alchemy = IntVar()
        self.cb_convert_to_alchemy = Checkbutton(self.selected_mln.options_frame, text="convert to Alchemy format", variable=self.convert_to_alchemy)
        self.cb_convert_to_alchemy.pack(side=LEFT)
        self.convert_to_alchemy.set(self.settings.get("convertAlchemy", 0))

        # evidence database selection
        row += 1
        Label(self.frame, text="Evidence: ").grid(row=row, column=0, sticky=NE)
        self.selected_db = FilePickEdit(self.frame, config.query_db_filemask, self.settings.get("db", ""), 12, self.changedDB, rename_on_edit=self.settings.get("mln_rename", 0), font=config.fixed_width_font)
        self.selected_db.grid(row=row,column=1, sticky="NWES")
        self.frame.rowconfigure(row, weight=1)

        # inference method selection
        row += 1
        self.list_methods_row = row
        Label(self.frame, text="Method: ").grid(row=row, column=0, sticky=E)      
        self.alchemy_methods = {"MC-SAT":"-ms", "Gibbs sampling":"-p", "simulated tempering":"-simtp", "MAP":"-a"}
        self.selected_method = StringVar(master)
        ## create list in onChangeEngine

        # queries
        row += 1
        Label(self.frame, text="Queries: ").grid(row=row, column=0, sticky=E)
        self.query = StringVar(master)
        self.query.set(self.settings.get("query", "foo"))
        Entry(self.frame, textvariable = self.query).grid(row=row, column=1, sticky="NEW")

        # query formula selection
        #row += 1
        #Label(self.frame, text="Query formulas: ").grid(row=row, column=0, sticky=NE)
        self.selected_qf = FilePickEdit(self.frame, "*.qf", self.settings.get("qf", ""), 6)
        #self.selected_qf.grid(row=row,column=1)        

        # max. number of steps
        row += 1
        Label(self.frame, text="Max. steps: ").grid(row=row, column=0, sticky=E)
        self.maxSteps = StringVar(master)
        self.maxSteps.set(self.settings.get("maxSteps", ""))
        self.entry_steps = Entry(self.frame, textvariable = self.maxSteps)
        self.entry_steps.grid(row=row, column=1, sticky="NEW")

        # number of chains
        row += 1
        Label(self.frame, text="Num. chains: ").grid(row=row, column=0, sticky="NE")
        self.numChains = StringVar(master)
        self.numChains.set(self.settings.get("numChains", ""))
        self.entry_chains = Entry(self.frame, textvariable = self.numChains)
        self.entry_chains.grid(row=row, column=1, sticky="NEW")

        # additional parameters
        row += 1
        Label(self.frame, text="Add. params: ").grid(row=row, column=0, sticky="NE")
        self.params = StringVar(master)
        self.entry_params = Entry(self.frame, textvariable = self.params)
        self.entry_params.grid(row=row, column=1, sticky="NEW")

        # closed-world predicates
        row += 1
        Label(self.frame, text="CW preds: ").grid(row=row, column=0, sticky="NE")
        self.cwPreds = StringVar(master)
        self.cwPreds.set(self.settings.get("cwPreds", ""))
        self.entry_cw = Entry(self.frame, textvariable = self.cwPreds)
        self.entry_cw.grid(row=row, column=1, sticky="NEW")

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
        self.entry_output_filename = Entry(frame, textvariable = self.output_filename)
        self.entry_output_filename.grid(row=0, column=0, sticky="NEW")
        # - save option
        self.save_results = IntVar()
        self.cb_save_results = Checkbutton(frame, text="save", variable=self.save_results)
        self.cb_save_results.grid(row=0, column=1, sticky=W)
        self.save_results.set(self.settings.get("saveResults", 0))

        # start button
        row += 1
        start_button = Button(self.frame, text=">> Start Inference <<", command=self.start)
        start_button.grid(row=row, column=1, sticky="NEW")

        self.initialized = True
        self.onChangeEngine()
        
        self.setGeometry()
    
    def setGeometry(self):
        g = self.settings.get("geometry")
        if g is None: return
        self.master.geometry(g)

    def changedMLN(self, name):
        self.mln_filename = name
        self.setOutputFilename()
            
    def changedDB(self, name):
        self.db_filename = name
        self.setOutputFilename()
        # restore stored query (if any)
        query = self.settings["queryByDB"].get(name)
        if query is None: # try file
            query_file = "%s.query" % name
            if os.path.exists(query_file) and "query" in dir(self):
                f = file(query_file, "r")
                query = f.read()
                f.close()
        if not query is None and hasattr(self, "query"):
            self.query.set(query)
        
    def onChangeEngine(self, name = None, index = None, mode = None):
        # enable/disable controls
        if self.selected_engine.get() == "internal":
            self.internalMode = True
            methods = MLN.InferenceMethods._names.values()
            #self.entry_output_filename.configure(state=NORMAL)
            self.cb_open_world.configure(state=DISABLED)
            self.cb_save_results.configure(state=NORMAL)
        else:
            self.internalMode = False
            methods = self.alchemy_methods.keys()
            #self.entry_output_filename.configure(state=NORMAL)
            self.cb_open_world.configure(state=NORMAL)
            self.cb_save_results.configure(state=DISABLED)
        
        # change additional parameters
        self.params.set(self.settings.get("params%d" % int(self.internalMode), ""))
        
        # change supported inference methods
        self.selected_method.set(self.settings.get("method%d" % int(self.internalMode), methods[0])) # default value
        if "list_methods" in dir(self): self.list_methods.grid_forget()
        self.list_methods = apply(OptionMenu, (self.frame, self.selected_method) + tuple(methods))
        self.list_methods.grid(row=self.list_methods_row, column=1, sticky="NWE")

    def setOutputFilename(self):
        if not self.initialized or not hasattr(self, "db_filename") or not hasattr(self, "mln_filename"):
            return
        fn = config.query_output_filename(self.mln_filename, self.db_filename)
        self.output_filename.set(fn)

    def start(self):
        #try:
            # get mln, db, qf and output filename
            mln = self.selected_mln.get()
            db = self.selected_db.get()
            qf = self.selected_qf.get()
            mln_text = self.selected_mln.get_text()
            db_text = self.selected_db.get_text()
            qf_text = self.selected_qf.get_text()
            output = self.output_filename.get()
            method = self.selected_method.get()
            keep_written_db = True
            params = self.params.get()
            # update settings
            self.settings["mln"] = mln
            self.settings["mln_rename"] = self.selected_mln.rename_on_edit.get()
            self.settings["db"] = db
            self.settings["db_rename"] = self.selected_db.rename_on_edit.get()
            self.settings["method%d" % int(self.internalMode)] = method
            self.settings["params%d" % int(self.internalMode)] = params
            self.settings["query"] = self.query.get()
            self.settings["engine"] = self.selected_engine.get()
            self.settings["qf"] = qf
            self.settings["output_filename"] = output
            self.settings["openWorld"] = self.open_world.get()
            self.settings["cwPreds"] = self.cwPreds.get()
            self.settings["convertAlchemy"] = self.convert_to_alchemy.get()
            self.settings["maxSteps"] = self.maxSteps.get()
            self.settings["numChains"] = self.numChains.get()
            self.settings["geometry"] = self.master.winfo_geometry()
            self.settings["saveResults"] = self.save_results.get()
            # write query
            # - to file
            write_query_file = False
            if write_query_file:
                query_file = "%s.query" % db
                f = file(query_file, "w")
                f.write(self.settings["query"])
                f.close()
            # - to settings
            self.settings["queryByDB"][db] = self.settings["query"]
            # write settings
            pickle.dump(self.settings, file(configname, "w+"))
            # hide main window
            self.master.withdraw()
            # some information
            print "\n--- query ---\n%s" % self.settings["query"]
            print "\n--- evidence (%s) ---\n%s" % (db, db_text.strip())
            # engine
            haveOutFile = False
            if self.settings["engine"] == "internal": # internal engine
                try: 
                    print "\nStarting %s...\n" % method
                    # read queries
                    queries = []
                    query = ""
                    for s in map(str.strip, self.settings["query"].split(",")):
                        if query != "": query += ','
                        query += s
                        if MLN.balancedParentheses(query):
                            queries.append(query)
                            query = ""
                    if query != "": raise Exception("Unbalanced parentheses in queries!")
                    # create MLN and evidence conjunction
                    mln = MLN.MLN(mln, verbose=True, defaultInferenceMethod=MLN.InferenceMethods._byName.get(method))
                    evidence = MLN.evidence2conjunction(mln.combineDB(db, verbose=True))
                    # set closed-world predicates
                    cwPreds = map(str.strip, self.settings["cwPreds"].split(","))
                    for pred in cwPreds:
                        if pred != "": mln.setClosedWorldPred(pred)
                    # collect inference arguments
                    args = {"details":True, "verbose":True, "shortOutput":True, "debugLevel":1}
                    args.update(eval("dict(%s)" % params)) # add additional parameters
                    if args.get("debug", False) and args["debugLevel"] > 1:
                        print "\nground formulas:"
                        mln.printGroundFormulas()
                        print
                    if self.settings["numChains"] != "":
                        args["numChains"] = int(self.settings["numChains"])
                    if self.settings["maxSteps"] != "":
                        args["maxSteps"] = int(self.settings["maxSteps"])
                    outFile = None
                    if self.settings["saveResults"]:
                        haveOutFile = True
                        outFile = file(output, "w")
                        args["outFile"] = outFile
                    # check for print requests
                    if "printGroundAtoms" in args:
                        mln.printGroundAtoms()
                    # invoke inference
                    results = mln.infer(queries, evidence, **args)
                    # close output file and open if requested
                    if outFile != None:
                        outFile.close()
                except:
                    cls, e, tb = sys.exc_info()
                    sys.stderr.write("Error: %s\n" % str(e))
                    traceback.print_tb(tb)
            else: # engine is Alchemy
                haveOutFile = True
                infile = mln
                mlnObject = None
                # explicitly convert MLN to Alchemy format, i.e. resolve weights that are arithm. expressions (on request) -> create temporary file
                if self.settings["convertAlchemy"]:
                    print "\n--- temporary MLN ---\n"
                    mlnObject = MLN.MLN(mln)
                    infile = mln[:mln.rfind(".")]+".alchemy.mln"
                    f = file(infile, "w")
                    mlnObject.write(f)
                    f.close()
                    mlnObject.write(sys.stdout)
                    print "\n---"
                # get alchemy version-specific data
                alchemy_version = self.alchemy_versions[self.selected_engine.get()]
                print alchemy_version
                print type(alchemy_version)
                if type(alchemy_version) != dict:
                    alchemy_version = {"path": str(alchemy_version)}
                usage = config.default_infer_usage
                if "usage" in alchemy_version:
                    usage = alchemy_version["usage"]
                # parse additional parameters for input files
                input_files = [infile]
                add_params = params.split()
                i = 0
                while i < len(add_params):
                    if add_params[i] == "-i":
                        input_files.append(add_params[i+1])
                        del add_params[i]
                        del add_params[i]
                        continue
                    i += 1
                # create command to execute
                params = ' -i "%s" -e "%s" -r "%s" -q "%s" %s %s' % (",".join(input_files), db, output, self.settings["query"], self.alchemy_methods[method], " ".join(add_params))
                if self.settings["numChains"] != "":
                    params += " %s %s" % (usage["numChains"], self.settings["numChains"])
                if self.settings["maxSteps"] != "":
                    params += " %s %s" % (usage["maxSteps"], self.settings["maxSteps"])
                path = alchemy_version["path"]
                path2 = os.path.join(path, "bin")
                if os.path.exists(path2):
                    path = path2
                if self.settings["openWorld"] == 1:
                    print "\nFinding predicate names..."
                    if mlnObject is None:
                        mlnObject = MLN.MLN(mln)
                    params += " %s %s" % (usage["openWorld"], ",".join(mlnObject.predicates))
                command = '%s %s' % (os.path.join(path, "infer"), params)
                # remove old output file (if any)
                if os.path.exists(output):
                    os.remove(output)
                # execute 
                print "\nStarting Alchemy..."
                print "\ncommand:\n%s\n" % command
                t_start = time.time()
                os.system(command)
                t_taken = time.time() - t_start
                # print results file
                if True:
                    print "\n\n--- output ---\n"
                    os.system("cat %s" % output)
                    print "\n"
                # append information on query and mln to results file
                f = file(output, "a")
                f.write("\n\n/*\n\n--- command ---\n%s\n\n--- evidence ---\n%s\n\n--- mln ---\n%s\ntime taken: %fs\n\n*/" % (command, db_text.strip(), mln_text.strip(), t_taken))
                f.close()
                # delete written db
                if not(keep_written_db) and wrote_db:
                    os.unlink(db)
                # delete temporary mln
                if self.settings["convertAlchemy"] and not config_value("keep_alchemy_conversions", True):
                    os.unlink(infile)
            # open results file
            if haveOutFile and config.query_edit_outfile_when_done:
                editor = config.editor
                print 'starting editor: %s %s' % (editor, output)
                run = os.spawnl
                if "spawnlp" in dir(os):
                    run = os.spawnlp
                run(os.P_NOWAIT, editor, editor, output)
            # restore main window
            self.master.deiconify()
            self.setGeometry()
            # reload the files (in case they changed)
            self.selected_mln.reloadFile()
            self.selected_db.reloadFile()
        #except Exception, e:
        #    print e
        #    sys.exit(1)

# -- main app --

if __name__ == '__main__':
    # read settings
    settings = {}
    confignames = ["mlnquery.config.dat", "query.config.dat"]
    for filename in confignames:
        configname = filename
        if os.path.exists(configname):
            try:
                settings = pickle.load(file(configname, "r"))
            except:
                pass
            break
    # process command line arguments
    argv = sys.argv
    i = 1
    arg2setting = {"-q" : "query", "-i" : "mln", "-e" : "db", "-r" : None}
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
    app = MLNQuery(root, ".", settings)
    root.mainloop()
