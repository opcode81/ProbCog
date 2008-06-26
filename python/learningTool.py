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
import config

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
        self.alchemy_methods = {"generative learning": "-g", "discriminative learning": "-d"}        
        self.selected_method = StringVar(master)
        ## create list in onChangeEngine
        
        row += 1
        Label(self.frame, text="Training data: ").grid(row=row, column=0, sticky="NE")
        self.selected_db = FilePickEdit(self.frame, config.learnwts_db_filemask, self.settings.get("db"), 15, self.changedDB, font=config.fixed_width_font)
        self.selected_db.grid(row=row, column=1, sticky="NEWS")
        self.frame.rowconfigure(row, weight=1)

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
            methods = MLN.ParameterLearningMeasures._names.values()
        else:
            state = NORMAL
            self.internalMode = False
            methods = self.alchemy_methods.keys()
        #self.entry_output_filename.configure(state=state)
        #self.entry_params.configure(state=state)
        #self.cb_open_world.configure(state=state)
        
        # change additional parameters
        self.params.set(self.settings.get("params%d" % int(self.internalMode), ""))
        
        # change supported inference methods
        self.selected_method.set(self.settings.get("method%d" % int(self.internalMode), methods[0])) # default value
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
            method = MLN.ParameterLearningMeasures._byName[self.selected_method.get()]
            method = MLN.ParameterLearningMeasures._shortnames[method].lower()
        else:
            engine = "alch"
            method = self.selected_method.get()[:1]
        filename = config.learnwts_output_filename(mln, engine, method, db)
        self.output_filename.set(filename)

    def changedMLN(self, name):
        self.mln_filename = name
        self.setOutputFilename()
            
    def changedDB(self, name):
        self.db_filename = name
        self.setOutputFilename()
    
    def changedMethod(self, name, index, mode):
        self.setOutputFilename()

    def learn(self):
        try:
            # update settings
            mln = self.selected_mln.get()
            db = self.selected_db.get()
            if "" in (db,mln): return
            method = self.selected_method.get()
            params = self.params.get()
            self.settings["mln"] = mln
            self.settings["db"] = db
            self.settings["output_filename"] = self.output_filename.get()
            self.settings["params%d" % int(self.internalMode)] = params
            self.settings["engine"] = self.selected_engine.get()
            self.settings["method%d" % int(self.internalMode)] = method
            self.settings["geometry"] = self.master.winfo_geometry()
            #print "dumping config..."
            pickle.dump(self.settings, file("learnweights.config.dat", "w+"))
            
            # hide gui
            self.master.withdraw()
            
            if self.settings["engine"] == "internal": # internal engine
                # load MLN and training database
                mln = MLN.MLN(self.settings["mln"])    
                mln.combineDB(self.settings["db"])
                # arguments
                args = {"initialWts":False}
                args.update(eval("dict(%s)" % params)) # add additional parameters
                # learn weights
                mln.learnwts(MLN.ParameterLearningMeasures._byName[method], **args)
                # determine output filename
                fname = self.settings["output_filename"]
                mln.write(file(fname, "w"))
                print "\nWROTE %s\n\n" % fname
                #mln.write(sys.stdout)
            else: # Alchemy
                alchemy_version = self.alchemy_versions[self.selected_engine.get()]
                if type(alchemy_version) != dict:
                    alchemy_version = {"path": str(alchemy_version)}
                # run Alchemy's learnwts
                method_switch = self.alchemy_methods[method]
                params = '%s -i "%s" -o "%s" -t %s %s' % (method_switch, self.settings["mln"], self.settings["output_filename"], self.settings["db"], params)
                path = alchemy_version["path"]
                path2 = os.path.join(path, "bin")
                if os.path.exists(path2):
                    path = path2
                command = '%s %s' % (os.path.join(path, "learnwts"), params)
                print "\n", command, "\n"
                self.master.withdraw() # hide gui
                #print "running Alchemy's learnwts..."
                cin, cout = os.popen2(command)
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
    
                editor = config.editor
                print "starting editor %s %s" % (editor, self.settings["output_filename"])
                os.spawnl(os.P_NOWAIT, editor, editor, self.settings["output_filename"])
            
            # restore gui
            self.master.deiconify() 
            self.setGeometry()
        except:
            cls, e, tb = sys.exc_info()
            print "Error: %s " % str(e)
            traceback.print_tb(tb)

# -- main app --

if __name__ == '__main__':
    #os.chdir("d:/java/ai/da/mln/ball_actions-reduced-test")
    settings = {}
    if os.path.exists("learnweights.config.dat"):
        try:
            settings = pickle.load(file("learnweights.config.dat", "r"))
        except:
            pass
    root = Tk()    
    app = LearnWeights(root, ".", settings)    
    root.mainloop()
    
