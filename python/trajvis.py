#!/usr/bin/python
#
# Trajectory Visualisation GUI
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
import traceback
from widgets import *

# --- main gui class ---

class TrajVis:

    def __init__(self, master, dir, settings):        
        self.initialized = False
        fixed_width_font = ("Lucida Console", -12) # name of font and size (if negative, in pixels)
        master.title("Trajectory Visualisation")
        
        self.master = master
        self.settings = settings        

        self.frame = Frame(master)
        self.frame.pack(fill=BOTH, expand=1)
        self.frame.columnconfigure(1, weight=1)

        row = 0

        # trajectory selection
        row += 1
        Label(self.frame, text="Human Motion Data Files: ").grid(row=row, column=0, sticky=NE)
        self.selected_orig = FilePick(self.frame, "*Joints*.asc", self.settings.get("asc_orig", ""), dirs=("trajectoryData", ), font=fixed_width_font, allowNone=True)
        self.selected_orig.grid(row=row, column=1, sticky="NWES")
        #self.frame.rowconfigure(row, weight=1)
        
        row += 1
        #Label(self.frame, text="Add. Human Data: ").grid(row=row, column=0, sticky=NE)
        self.selected_orig2 = FilePick(self.frame, "*Joints*.asc", self.settings.get("asc_orig2", ""), dirs=("trajectoryData", ), font=fixed_width_font, allowNone=True)
        self.selected_orig2.grid(row=row, column=1, sticky="NWES")
        #self.frame.rowconfigure(row, weight=1)

        # embedding selection
        row += 1
        Label(self.frame, text="Low-Dimensional Data Files: ").grid(row=row, column=0, sticky=NE)
        self.selected_embed = FilePick(self.frame, "*.asc", self.settings.get("asc_embed", ""), dirs = ('.', os.path.join("trajectoryData", "gplvm"), os.path.join("trajectoryData", "stisomap")), font=fixed_width_font)
        self.selected_embed.grid(row=row,column=1, sticky="NWES")
        #self.frame.rowconfigure(row, weight=1)

        row += 1
        #Label(self.frame, text="Embedding: ").grid(row=row, column=0, sticky=NE)
        self.selected_embed2 = FilePick(self.frame, "*.asc", self.settings.get("asc_embed2", ""), dirs = ('.', os.path.join("trajectoryData", "gplvm"), os.path.join("trajectoryData", "stisomap")), font=fixed_width_font, allowNone=True)
        self.selected_embed2.grid(row=row,column=1, sticky="NWES")
        #self.frame.rowconfigure(row, weight=1)

        # label selection
        row += 1
        Label(self.frame, text="Label Files: ").grid(row=row, column=0, sticky=NE)
        self.selected_label = FilePick(self.frame, "*_l*.asc", self.settings.get("asc_label", ""), dirs = ('.', os.path.join("trajectoryData")), font=fixed_width_font, allowNone = True)
        self.selected_label.grid(row=row,column=1, sticky="NWES")
        #self.frame.rowconfigure(row, weight=1)

        row += 1
        #Label(self.frame, text="Embedding: ").grid(row=row, column=0, sticky=NE)
        self.selected_label2 = FilePick(self.frame, "*_l*.asc", self.settings.get("asc_label2", ""), dirs = ('.', os.path.join("trajectoryData")), font=fixed_width_font, allowNone=True)
        self.selected_label2.grid(row=row,column=1, sticky="NWES")
        #self.frame.rowconfigure(row, weight=1)

        row += 1
        Label(self.frame, text="TUM Kitchen Dataset: ").grid(row=row, column=0, sticky="NE")
        joints = ('BEC', 'ULW', 'OLW', 'UBW', 'OBW', 'UHW', 'BRK', 'OHW', 'KO', 'SEH', 'OSL', 'USL', 'FUL', 'FBL', 'OSR', 'USR', 'FUR', 'FBR', 'SBL', 'OAL', 'UAL', 'HAL', 'FIL', 'SBR', 'OAR', 'UAR', 'HAR', 'FIR')
        frame = Frame(self.frame)        
        sequences = ["1-%d" % i for i in range(8)] + ["0-%d" % i for i in range(13)]
        labelFields = ("righthand", "lefthand", "trunk")
        frame.grid(row=row, column=1, sticky="EW")
        self.tumkd = []
        for i in range(2):
            d = {}
            Label(frame, text="Sequence:").grid(row=i, column=0)
            d["seq"] = DropdownList(frame, tuple(sequences), allowNone=True, default=self.settings.get("db_seq_%d" % i, ""))
            d["seq"].grid(row=i, column=1)
            frame.columnconfigure(1, weight=1)
            d["relative"] = Checkbox(frame, text="relative; ", default=self.settings.get("db_rel_%d" % i, 0))
            d["relative"].grid(row=i, column=2)
            Label(frame, text="Label:").grid(row=i, column=3)
            d["labelField"] = DropdownList(frame, labelFields, default=self.settings.get("db_label_%d" % i, labelFields[0]))
            d["labelField"].grid(row=i, column=4, sticky="NWES")
            frame.columnconfigure(4, weight=1)
            Label(frame, text="Low-Dim. Data:").grid(row=i, column=5)
            d["joint"] = DropdownList(frame, joints, allowNone=True, default=self.settings.get("db_joint_%d" % i, "HAR"))
            d["joint"].grid(row=i, column=6)
            frame.columnconfigure(6, weight=1)
            self.tumkd.append(d)
        
        # additional parameters
        row += 1
        Label(self.frame, text="Add. params: ").grid(row=row, column=0, sticky="NE")
        self.params = StringVar(master)        
        self.entry_params = Entry(self.frame, textvariable = self.params)
        self.entry_params.grid(row=row, column=1, sticky="NEW")
        self.params.set(self.settings.get("add_params", ""))

        # start button
        row += 1
        start_button = Button(self.frame, text=">> Visualise <<", command=self.start)
        start_button.grid(row=row, column=1, sticky="NEW")

        self.initialized = True
        
        self.setGeometry()
    
    def setGeometry(self):
        g = self.settings.get("geometry")
        if g is None: return
        self.master.geometry(g)

    def start(self):
        # update settings
        self.settings["asc_orig"] = self.selected_orig.get()
        self.settings["asc_orig2"] = self.selected_orig2.get()
        self.settings["asc_embed"] = self.selected_embed.get()
        self.settings["asc_embed2"] = self.selected_embed2.get()
        self.settings["asc_label"] = self.selected_label.get()
        self.settings["asc_label2"] = self.selected_label2.get()
        self.settings["geometry"] = self.master.winfo_geometry()
        self.settings["add_params"] = self.params.get()
        dbs = []
        for i,d in enumerate(self.tumkd):
            a = self.settings["db_seq_%d" % i] = d["seq"].get()
            b = self.settings["db_relative_%d" % i] = d["relative"].get()
            c = self.settings["db_labelField_%d" % i] = d["labelField"].get()
            d = self.settings["db_joint_%d" % i] = d["joint"].get()
            if(self.settings["db_seq_%d" % i] != ""):
                dbs.append(":".join((a,b,c,d)))
        # create command to execute
        params = ""
        for db in dbs:
            params += " -db %s" % db
        for f in (self.settings["asc_orig"], self.settings["asc_orig2"]):
            if f != "":
                params += ' -h "%s"' % f
        for f in (self.settings["asc_embed"], self.settings["asc_embed2"]):
            if f != "":
                params += ' -e "%s"' % f
        for f in (self.settings["asc_label"], self.settings["asc_label2"]):
            if f != "":
                params += ' -l "%s"' % f
        params += " %s" % self.params.get()
        if "win" in sys.platform.lower():
            app = "trajvis.bat"
        else:
            app = "trajvis"
        command = '%s %s' % (app, params)
        # write settings
        pickle.dump(self.settings, file(configname, "w+"))
        # hide main window
        self.master.withdraw()
        # execute 
        print "\nStarting TrajVis..."
        print "\ncommand:\n%s\n" % command
        os.system(command)
        # restore main window
        self.master.deiconify()
        self.setGeometry()


# -- main app --

if __name__ == '__main__':
    # read settings
    settings = {}
    configname = "trajvis.cfg"
    if os.path.exists(configname):
        try:
            settings = pickle.load(file(configname, "r"))
        except:
            pass        
    # process command line arguments
    argv = sys.argv
    i = 1
    arg2setting = {}
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
    app = TrajVis(root, ".", settings)
    root.mainloop()
