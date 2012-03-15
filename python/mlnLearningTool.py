#!/usr/bin/python

# MLN Parameter Learning Tool
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
import re
import pickle
import traceback
import MLN
from widgets import *
import configMLN as config
import subprocess
import shlex
import tkMessageBox
from fnmatch import fnmatch

# --- gui class ---

class LearnWeights:

    def file_pick(self, label, mask, row, default, change_hook = None):
        # create label
        Label(self.frame, text=label).grid(row=row, column=0, sticky="E")
        # read filenames
        files = []
        for filename in os.listdir(self.dir):
            if fnmatch(filename, mask):
                files.append(filename)
        files.sort()
        if len(files) == 0: files.append("(no %s files found)" % mask)
        # create list
        stringvar = StringVar(self.master)
        if default in files:
            stringvar.set(default) # default value
        list = apply(OptionMenu, (self.frame, stringvar) + tuple(files))
        list.grid(row=row, column=1, sticky="EW")
        #list.configure(width=self.stdWidth)
        if change_hook != None:
            stringvar.trace("w", change_hook)
        return stringvar

    def __init__(self, master, dir, settings):
        self.master = master
        self.master.title("MLN Parameter Learning Tool")
        self.dir = dir
        self.settings = settings

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

        row += 1
        Label(self.frame, text="MLN: ").grid(row=row, column=0, sticky="NE")
        self.selected_mln = FilePickEdit(self.frame, config.learnwts_mln_filemask, self.settings.get("mln"), 20, self.changedMLN, font=config.fixed_width_font)
        self.selected_mln.grid(row=row,column=1, sticky="NEWS")
        self.frame.rowconfigure(row, weight=1)
        #self.selected_mln = self.file_pick("MLN: ", "*.mln", row, self.settings.get("mln"), self.changedMLN)

        # method selection
        row += 1
        self.list_methods_row = row
        Label(self.frame, text="Method: ").grid(row=row, column=0, sticky=E)
        self.alchemy_methods = {
            "pseudo-log-likelihood via BFGS": (["-g"], False, "pll"),
            "sampling-based log-likelihood via diagonal Newton": (["-d", "-dNewton"], True, "slldn"),
            "sampling-based log-likelihood via rescaled conjugate gradient": (["-d", "-dCG"], True, "sllcg"),
            "[discriminative] sampling-based log-likelihood via diagonal Newton": (["-d", "-dNewton"], False, "dslldn"),
            "[discriminative] sampling-based log-likelihood via rescaled conjugate gradient": (["-d", "-dCG"], False, "dsllcg"),
        }
        self.selected_method = StringVar(master)
        ## create list in onChangeEngine
        
        # additional parametrisation
        row += 1
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        # option: use prior
        self.use_prior = IntVar()
        self.cb_use_prior = Checkbutton(frame, text="use prior with std. dev. ", variable=self.use_prior)
        self.cb_use_prior.pack(side=LEFT)
        self.use_prior.set(self.settings.get("usePrior", 0))
        # std. dev.
        self.priorStdDev = StringVar(master)
        self.priorStdDev.set(self.settings.get("priorStdDev", "100"))
        Entry(frame, textvariable = self.priorStdDev, width=5).pack(side=LEFT)
        Label(frame, text="(e.g. 100 for generative, 2 for discriminative)").pack(side=LEFT)
        # add unit clauses
        self.add_unit_clauses = IntVar()
        self.cb_add_unit_clauses = Checkbutton(frame, text="add unit clauses", variable=self.add_unit_clauses)
        self.cb_add_unit_clauses.pack(side=LEFT)
        self.add_unit_clauses.set(self.settings.get("addUnitClauses", 0))
        # non-evidence predicates
        row += 1
        frame = Frame(self.frame)        
        frame.grid(row=row, column=1, sticky="NEWS")
        self.l_nePreds = Label(frame, text="non-evidence predicates:")
        self.l_nePreds.grid(row=0, column=0, sticky="NE")        
        self.nePreds = StringVar(master)
        self.nePreds.set(self.settings.get("nePreds", ""))
        frame.columnconfigure(1, weight=1)
        self.entry_nePreds = Entry(frame, textvariable = self.nePreds)
        self.entry_nePreds.grid(row=0, column=1, sticky="NEW")        

        # evidence database selection
        row += 1
        Label(self.frame, text="Training data: ").grid(row=row, column=0, sticky="NE")
        self.selected_db = FilePickEdit(self.frame, config.learnwts_db_filemask, self.settings.get("db"), 15, self.changedDB, font=config.fixed_width_font, allowNone=True)
        self.selected_db.grid(row=row, column=1, sticky="NEWS")
        self.frame.rowconfigure(row, weight=1)

        row += 1
        frame = Frame(self.frame)
        frame.grid(row=row, column=1, sticky="NEW")
        col = 0
        Label(frame, text="OR Pattern:").grid(row=0, column=col, sticky="W")
        # - pattern entry
        col += 1
        frame.columnconfigure(col, weight=1)
        self.pattern = var = StringVar(master)
        var.set(self.settings.get("pattern", ""))
        self.entry_pattern = Entry(frame, textvariable = var)
        self.entry_pattern.grid(row=0, column=col, sticky="NEW")

        row += 1
        Label(self.frame, text="Add. Params: ").grid(row=row, column=0, sticky="E")
        self.params = StringVar(master)
        self.params.set(self.settings.get("params", ""))
        Entry(self.frame, textvariable = self.params).grid(row=row, column=1, sticky="WE")

        row += 1
        Label(self.frame, text="Output filename: ").grid(row=row, column=0, sticky="E")
        self.output_filename = StringVar(master)
        self.output_filename.set(self.settings.get("output_filename", ""))
        Entry(self.frame, textvariable = self.output_filename).grid(row=row, column=1, sticky="EW")

        row += 1
        learn_button = Button(self.frame, text=" >> Learn << ", command=self.learn)
        learn_button.grid(row=row, column=1, sticky="EW")

        self.onChangeEngine()
        self.onChangeMethod()
        self.setGeometry()

    def setGeometry(self):
        g = self.settings.get("geometry")
        if g is None: return
        self.master.geometry(g)

    def onChangeEngine(self, name = None, index = None, mode = None):
        # enable/disable controls
        if self.selected_engine.get() == "internal":
            state = DISABLED
            self.internalMode = True
            methods = sorted(MLN.ParameterLearningMeasures.getNames())
        else:
            state = NORMAL
            self.internalMode = False
            methods = sorted(self.alchemy_methods.keys())
        self.cb_add_unit_clauses.configure(state=state)

        # change additional parameters
        self.params.set(self.settings.get("params%d" % int(self.internalMode), ""))

        # change supported inference methods
        selected_method = self.settings.get("method%d" % int(self.internalMode))
        if selected_method not in methods:
            if selected_method == "discriminative learning": selected_method = "[discriminative] sampling-based log-likelihood via rescaled conjugate gradient"
            else: selected_method = "pseudo-log-likelihood via BFGS"
            
        self.selected_method.set(selected_method) # default value
        if "list_methods" in dir(self): self.list_methods.grid_forget()
        self.list_methods = apply(OptionMenu, (self.frame, self.selected_method) + tuple(methods))
        self.list_methods.grid(row=self.list_methods_row, column=1, sticky="NWE")
        self.selected_method.trace("w", self.changedMethod)

    def isFile(self, f):
        return os.path.exists(os.path.join(self.dir, f))

    def setOutputFilename(self):
        if not hasattr(self, "output_filename") or not hasattr(self, "db_filename") or not hasattr(self, "mln_filename"):
            return
        mln = self.mln_filename
        db = self.db_filename
        if "" in (mln, db): return
        if self.internalMode:
            engine = "py"
            method = MLN.ParameterLearningMeasures.byName(self.selected_method.get())
            method = MLN.ParameterLearningMeasures.getShortName(method).lower()
        else:
            engine = "alch"
            method = self.alchemy_methods[self.selected_method.get()][2]
        filename = config.learnwts_output_filename(mln, engine, method, db)
        self.output_filename.set(filename)

    def changedMLN(self, name):
        self.mln_filename = name
        self.setOutputFilename()

    def changedDB(self, name):
        self.db_filename = name
        self.setOutputFilename()

    def onChangeMethod(self):
        method = self.selected_method.get()
        state = NORMAL if "[discriminative]" in method else DISABLED
        self.entry_nePreds.configure(state=state)
        self.l_nePreds.configure(state=state)        

    def changedMethod(self, name, index, mode):
        self.onChangeMethod()
        self.setOutputFilename()

    def learn(self, saveGeometry=True):
        try:
            # update settings
            mln = self.selected_mln.get()
            db = self.selected_db.get()
            if mln == "":
                raise Exception("No MLN was selected")
            method = self.selected_method.get()
            params = self.params.get()
            self.settings["mln"] = mln
            self.settings["db"] = db
            self.settings["output_filename"] = self.output_filename.get()
            self.settings["params%d" % int(self.internalMode)] = params
            self.settings["engine"] = self.selected_engine.get()
            self.settings["method%d" % int(self.internalMode)] = method
            self.settings["pattern"] = self.entry_pattern.get()
            self.settings["usePrior"] = int(self.use_prior.get())
            self.settings["priorStdDev"] = self.priorStdDev.get()
            self.settings["nePreds"] = self.nePreds.get()
            self.settings["addUnitClauses"] = int(self.add_unit_clauses.get())
            if saveGeometry:
                self.settings["geometry"] = self.master.winfo_geometry()
            #print "dumping config..."
            pickle.dump(self.settings, file("learnweights.config.dat", "w+"))

            # determine training databases(s)
            if db != "":
                dbs = [db]
            else:
                dbs = []
                pattern = settings["pattern"]
                if pattern == "":
                    raise Exception("No training data given; A training database must be selected or a pattern must be specified")
                dir, mask = os.path.split(os.path.abspath(pattern))
                for fname in os.listdir(dir):
                    if fnmatch(fname, mask):
                        dbs.append(os.path.join(dir, fname))
                if len(dbs) == 0:
                    raise Exception("The mask '%s' matches no files" % mask)
            print "training databases:", ",".join(dbs)

            # hide gui
            self.master.withdraw()
            
            discriminative = "discriminative" in method

            if self.settings["engine"] == "internal": # internal engine
                # arguments
                args = {"initialWts":False}                
                args.update(eval("dict(%s)" % params)) # add additional parameters
                if discriminative:
                    args["queryPreds"] = self.settings["nePreds"].split(",")
                if self.settings["usePrior"]:
                    args["gaussianPriorSigma"] = float(self.settings["priorStdDev"])
                # learn weights
                mln = MLN.MLN(self.settings["mln"])
                mln.learnWeights(dbs, method=MLN.ParameterLearningMeasures.byName(method), **args)
                # determine output filename
                fname = self.settings["output_filename"]
                mln.write(file(fname, "w"))
                print "\nWROTE %s\n\n" % fname
                #mln.write(sys.stdout)
            else: # Alchemy
                alchemy_version = self.alchemy_versions[self.selected_engine.get()]
                if type(alchemy_version) != dict:
                    alchemy_version = {"path": str(alchemy_version)}
                # find binary
                path = alchemy_version["path"]
                path2 = os.path.join(path, "bin")
                if os.path.exists(path2):
                    path = path2
                alchemyLearn = os.path.join(path, "learnwts")
                if not os.path.exists(alchemyLearn) and not os.path.exists(alchemyLearn+".exe"):
                    error = "Alchemy's learnwts/learnwts.exe binary not found in %s. Please configure Alchemy in python/configMLN.py" % path
                    tkMessageBox.showwarning("Error", error)
                    raise Exception(error)
                # run Alchemy's learnwts
                method_switches, discriminativeAsGenerative, shortname = self.alchemy_methods[method]
                params = [alchemyLearn] + method_switches + ["-i", self.settings["mln"], "-o", self.settings["output_filename"], "-t", ",".join(dbs)] + shlex.split(params)
                if discriminative:
                    params += ["-ne", self.settings["nePreds"]]
                elif discriminativeAsGenerative:                    
                    preds = MLN.getPredicateList(self.settings["mln"])
                    params += ["-ne", ",".join(preds)]
                if not self.settings["addUnitClauses"]:
                    params.append("-noAddUnitClauses")
                if not self.settings["usePrior"]:
                    params.append("-noPrior")
                else:
                    if self.settings["priorStdDev"] != "":
                        params += ["-priorStdDev", self.settings["priorStdDev"]]
                        
                command = subprocess.list2cmdline(params)
                print "\n", command, "\n"
                
                self.master.withdraw() # hide gui
                
                #print "running Alchemy's learnwts..."
                p = subprocess.Popen(params, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
                cin, cout = p.stdin, p.stdout
                #cin, cout = os.popen2(command)
                output_text = ""
                while True:
                    l = cout.readline()
                    if l == "":
                        break
                    print l,
                    output_text += l

                # add data reported by learnwts and, from the input mln, domain declarations and rules for mutual exclusiveness and exhaustiveness
                if True:
                    # read the input file
                    f = file(self.settings["mln"], "r")
                    text = f.read()
                    f.close()
                    comment = re.compile(r'//.*?^|/\*.*\*/', re.DOTALL | re.MULTILINE)
                    text = re.sub(comment, '', text)
                    merules  = []
                    domain_decls = []
                    for l in text.split("\n"):
                        l = l.strip()
                        # domain decls
                        if "{" in l:
                            domain_decls.append(l)
                        # mutex rules
                        m = re.match(r"\w+\((.*?)\)", l)
                        if m != None and m.group(0) == l and ("!" in m.group(1)):
                            merules.append(m.group(0))
                    # read the output file
                    f = file(self.settings["output_filename"], "r")
                    outfile = f.read()
                    f.close()
                    # rewrite the output file
                    f = file(self.settings["output_filename"], "w")
                    # - get report with command line and learnwts output
                    if config.learnwts_full_report:
                        report = output_text
                    else:
                        report = output_text[output_text.rfind("Computing counts took"):]
                    report = "/*\n%s\n\n%s*/\n" % (command, report)
                    # - write
                    outfile = outfile.replace("//function declarations", "\n".join(merules))
                    if not config.learnwts_report_bottom: f.write(report + "\n")
                    f.write("// domain declarations\n" + "\n".join(domain_decls) + "\n\n")
                    f.write(outfile)
                    if config.learnwts_report_bottom: f.write("\n\n" + report)
                    f.close()

            if config.learnwts_edit_outfile_when_done:
                params = [config.editor, self.settings["output_filename"]]
                print "starting editor: %s" % subprocess.list2cmdline(params)
                subprocess.Popen(params, shell=False)
        except:
            cls, e, tb = sys.exc_info()
            sys.stderr.write("%s: %s\n" % (str(type(e)), str(e)))
            traceback.print_tb(tb)
            raise
        finally:
            # restore gui
            self.master.deiconify()
            self.setGeometry()

            sys.stdout.flush()

# -- main app --

if __name__ == '__main__':
    # read command-line options
    from optparse import OptionParser
    parser = OptionParser()
    parser.add_option("--run", action="store_true", dest="run", default=False, help="run last configuration without showing gui")
    parser.add_option("-i", "--mln-filename", dest="mln_filename", help="input MLN filename", metavar="FILE", type="string")
    parser.add_option("-t", "--db-filename", dest="db", help="training database filename", metavar="FILE", type="string")
    parser.add_option("-o", "--output-file", dest="output_filename", help="output MLN filename", metavar="FILE", type="string")
    (options, args) = parser.parse_args()

    # read previously saved settings
    settings = {}
    if os.path.exists("learnweights.config.dat"):
        try:
            settings = pickle.loads("\n".join(map(lambda x: x.strip("\r\n"), file("learnweights.config.dat", "r").readlines())))
        except:
            pass
    # update settings with command-line options
    settings.update(dict(filter(lambda x: x[1] is not None, options.__dict__.iteritems())))

    # run learning task/GUI
    root = Tk()
    app = LearnWeights(root, ".", settings)
    #print "options:", options
    if options.run:
        app.learn(saveGeometry=False)
    else:
        root.mainloop()

