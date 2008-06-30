# widgets module for use with MLN tools
# 
# (C) 2006-2008 by Dominik Jain
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
from ScrolledText import ScrolledText
from string import ascii_letters, digits, punctuation
try:
    import Pmw
    havePMW = False
except:
    havePMW = False
import os
from fnmatch import fnmatch
#import keyword

class SyntaxHighlightingText(ScrolledText):
    # syntax highlighting definitions
    tags = {'com': dict(foreground='#aaa'), # comment
            'mlcom': dict(foreground='#aaa'), # multi-line comment
            'str': dict(foreground='darkcyan'), # string
            'kw': dict(foreground='orange'), # keyword
            'obj': dict(foreground='#00F'), # function/class name
            'number': dict(foreground='blue'), # number
            'op' : dict(foreground='darkblue'), # operator
            'bracket_hl': dict(background="yellow") # bracket highlighting
            }
    brackets = (('(',')'), ('{', '}'))
    open_brackets = map(lambda x: x[0], brackets)
    close_brackets = map(lambda x: x[1], brackets)
    operators = ['v', '^', '!', '+', '=>', '<=>']

    # constructor
    def __init__(self, root, change_hook = None):
        ScrolledText.__init__(self,root,wrap=NONE,bd=0,width=80,height=25,undo=1,maxundo=50,padx=0,pady=0,background="white",foreground="black")
        # Non-wrapping, no border, undo turned on, max undo 50
        self.text = self # For the methods taken from IDLE
        self.root = root
        self.change_hook = change_hook
        self.characters = ascii_letters + digits + punctuation
        self.tabwidth = 8    # for IDLE use, must remain 8 until Tk is fixed
        self.indentwidth = 4 
        self.indention = 0   # The current indention level
        self.set_tabwidth(self.indentwidth) # IDLE...
        self.previous_line = "0"

        # create a popup menu
        self.menu = Menu(root, tearoff=0)
        self.menu.add_command(label="Undo", command=self.edit_undo)
        self.menu.add_command(label="Redo", command=self.edit_redo)
        #self.menu.add_command(type="separator")
        self.menu.add_command(label="Cut", command=self.cut)
        self.menu.add_command(label="Copy", command=self.copy)
        self.menu.add_command(label="Paste", command=self.paste)

        self.bind('<KeyRelease>', self.key_release)      # For scanning input
        self.bind('<Return>',self.autoindent)   # Overides default binding
        #self.bind('<Tab>',self.autoindent) # increments self.indention
        #self.bind('<BackSpace>',self.autoindent) # decrements self.indention
        self.bind('<Button-3>', self.popup) # right mouse button opens popup
        self.bind('<Button-1>', self.recolorCurrentLine) # left mouse can reposition cursor, so recolor (e.g. bracket highlighting necessary)
        self.bind('<Control-Any-KeyPress>', self.ctrl)
        
        # sets up the tags
        for tag, settings in self.tags.items():
            self.tag_config(tag, **settings)

    def popup(self, event):
        self.menu.post(event.x_root, event.y_root)

    def get_tabwidth(self):
        # From IDLE
        current = self['tabs'] or 5000
        return int(current)
    
    def set_tabwidth(self, newtabwidth):
        # From IDLE
        text = self
        if self.get_tabwidth() != newtabwidth:
            pixels = text.tk.call("font", "measure", text["font"],
                                  "-displayof", text.master,
                                  "n" * newtabwidth)
            text.configure(tabs=pixels)
        
    def remove_singleline_tags(self, start, end):
        for tag in self.tags.keys():
            if tag[:2] != 'ml':
                self.tag_remove(tag, start, end)

    def get_selection_indices(self):
         # If a selection is defined in the text widget, return (start,
        # end) as Tkinter text indices, otherwise return (None, None)
        try:
            first = self.text.index("sel.first")
            last = self.text.index("sel.last")
            return first, last
        except TclError:
            return None

    def cut(self,event=0):
        self.clipboard_clear()
        Selection=self.get_selection_indices()
        if Selection is not None:
            SelectedText = self.get(Selection[0],Selection[1])
            self.delete(Selection[0],Selection[1])
            self.clipboard_append(SelectedText)
            self.onChange()

    def copy(self,event=0):
        self.clipboard_clear()
        Selection=self.get_selection_indices()
        if Selection is not None:
            SelectedText = self.get(Selection[0],Selection[1])
            self.clipboard_append(SelectedText)

    def paste(self,event=0):
        # This should call colorize for the pasted lines.
        SelectedText = self.root.selection_get(selection='CLIPBOARD')
        self.insert(INSERT, SelectedText)
        self.onChange()
        return "break"

    def autoindent(self,event):
        if event.keysym == 'Return':
            self.edit_separator() # For undo/redo
            index = self.index(INSERT).split('.')
            #print index
            line = int(index[0])
            column = int(index[1])
            if self.get('%s.%d'%(line, column-1)) == ':':
                self.indention += 1
            #print '\n',
            #print '\t'*self.indention
            self.insert(INSERT,'\n')
            self.insert(INSERT,'\t'*self.indention)
            return 'break' # Overides standard bindings
        elif event.keysym == 'Tab':
            self.edit_separator()
            self.indention += 1
            #print self.indention
        elif event.keysym == 'BackSpace':
            self.edit_separator()
            index = self.index(INSERT).split('.')
            #print index
            line = int(index[0])
            column = int(index[1])
            if self.get('%s.%d'%(line, column-1)) == '\t':
                self.indention -= 1

    def recolorCurrentLine(self, *foo):
        pos = self.index(INSERT)
        cline = pos.split('.')[0]
        #print "recoloring %s, %s" % (cline, self.previous_line)
        if cline != self.previous_line: self.colorize(self.previous_line)
        self.colorize(cline)
        self.previous_line = cline
               
    def key_release(self, key):
        #print "pressed", key.keysym, dir(key)
        if key.char in ' :[(]),"\'':
            self.edit_separator() # For undo/redo
        # recolorize the current line and the previous line (if it's a different one)
        self.recolorCurrentLine()
        # if delete or backspace were pressed, check if a multiline comment has to be removed
        pos = self.index(INSERT)
        if key.keysym in ("BackSpace", "Delete"):
            #print "removal at %s" % pos
            ranges = self.tag_ranges('mlcom')
            i = 0
            while i < len(ranges):
                range = ranges[i:i+2]
                second_range = (self.index(range[0] + " + 1 char"), self.index(range[1] + " - 1 char"))
                #print pos, range, second_range
                if pos in range or pos in second_range:
                    self.tag_remove('mlcom', range[0], range[1])
                i += 2
        # notify of change if any
        if key.char != '' or key.keysym in ("BackSpace", "Delete"):
            self.onChange()
        else:
            pass
            #print key
    
    def onChange(self):
        if self.change_hook is not None:
            self.change_hook()
    
    def ctrl(self, key):
        if key.keysym == 'c': self.copy()
        elif key.keysym == 'x': self.cut()
        elif key.keysym == 'v': self.paste()

    def colorize(self, cline):
        cursorPos = self.index(INSERT)
        buffer = self.get('%s.%d' % (cline,0), '%s.end' % (cline))
       
        # remove non-multiline tags
        self.remove_singleline_tags('%s.%d'% (cline, 0), '%s.end'% (cline))

        in_quote = False
        quote_start = 0
        for i in range(len(buffer)):
            here = '%s.%d' % (cline, i)
            # strings
            if buffer[i] in ['"',"'"]: # Doesn't distinguish between single and double quotes...
                if in_quote:
                    self.tag_add('str', '%s.%d' % (cline, quote_start), '%s.%d' % (cline, i+1))
                    in_quote = False
                else:
                    quote_start = i
                    in_quote = True
            if not in_quote:
                # operators
                if False:
                    for op in self.operators:
                        if buffer[i:i+len(op)] == op:
                            self.tag_add('op', "%s.%d" % (cline, i), "%s.%d" % (cline, i+len(op)))
                # comments
                if buffer[i:i+2] == "//":
                    self.tag_add('com', '%s.%d' % (cline, i), '%s.end' % (cline))
                # multiline comments
                elif buffer[i:i+2] == "/*":
                    if not here in self.tag_ranges('mlcom'):
                        end_pos = self.search("*/", here, forwards=True) # get the end of the comment
                        if not end_pos:
                            continue
                        if self.search("/*", here + " + 2 chars", stopindex=end_pos): # if there's a nested comment, ignore it (it might just be a nested /* with a */)
                            continue
                        #!!! make sure the area does not contain any "/*", because the "*/" is not the right one otherwise
                        #print "multiline comment from %s to %s" % (here, str(end_pos))
                        self.tag_add('mlcom', here, end_pos + " + 2 chars")
                elif buffer[i:i+2] == "*/":
                    end_pos = self.index(here + " + 2 chars")
                    if not end_pos in self.tag_ranges('mlcom'):
                        start_pos = self.search("/*", here, backwards=True) # get the beginning of the comment
                        if not start_pos:
                            continue
                        if self.search("*/", here, stopindex=start_pos, backwards=True): # if there's a nested comment, ignore it (it might just be a nested */ without a /*)
                            continue
                        #print "multiline comment from %s to %s" % (start_pos, end_pos)
                        self.tag_add('mlcom', start_pos, end_pos)
                # bracket highlighting
                elif buffer[i] in self.open_brackets and here == cursorPos:
                    idxBracketType = self.open_brackets.index(buffer[i])
                    openb, closeb = self.brackets[idxBracketType]
                    stack = 1
                    for j,c in enumerate(buffer[i+1:]):
                        if c == openb: 
                            stack += 1
                        elif c == closeb:
                            stack -= 1
                            if stack == 0:
                                self.tag_add('bracket_hl', here, here + " + 1 char")
                                self.tag_add('bracket_hl', "%s.%d" % (cline, i+1+j), "%s.%d" % (cline, i+1+j+1))
                                break
                elif buffer[i] in self.close_brackets and self.index(here + " + 1 char") == cursorPos:
                    idxBracketType = self.close_brackets.index(buffer[i])
                    openb, closeb = self.brackets[idxBracketType]
                    stack = 1
                    l = list(buffer[:i])
                    l.reverse()
                    for j,c in enumerate(l):
                        if c == closeb: 
                            stack += 1
                        elif c == openb:
                            stack -= 1
                            if stack == 0:
                                self.tag_add('bracket_hl', here, here + " + 1 char")
                                self.tag_add('bracket_hl', "%s.%d" % (cline, i-1-j), "%s.%d" % (cline, i-1-j+1))
                                break
        # tokens
        start, end = 0, 0
        obj_flag = 0
        for token in buffer.split(' '):
            end = start + len(token)
            start_index = '%s.%d' % (cline, start)
            end_index = '%s.%d' % (cline, end)
            if obj_flag:
                self.tag_add('obj', start_index, end_index)
                obj_flag = 0
            # keywords
            if token.strip() in []: #keyword.kwlist:
                self.tag_add('kw', start_index, end_index)
                if token.strip() in ['def','class']:
                    obj_flag = 1
            else:
                # numbers
                try:
                    float(token)
                except ValueError:
                    pass
                else:
                    self.tag_add('number', '%s.%d' % (cline, start), "%s.%d" % (cline, end))
            start += len(token)+1

    def insert(self, index, text, *args):
        line = int(self.index(index).split(".")[0])        
        Text.insert(self, index, text, *args)
        for i in range(text.count("\n")):
            self.colorize(str(line+i))

class FilePickEdit(Frame):
    def reloadFile(self):
        self.editor.delete("1.0", END)
        filename = self.picked_name.get()
        new_text = file(filename).read()
        if new_text.strip() == "":
            new_text = "// %s is empty\n" % filename;
        new_text = new_text.replace("\r", "")
        self.editor.insert(INSERT, new_text)

    def onSelChange(self, name, index=0, mode=0):
        self.reloadFile()
        filename = self.picked_name.get()
        self.save_name.set(filename)
        self.save_edit.configure(state=DISABLED)
        self.unmodified = True
        if self.user_onChange != None:
            self.user_onChange(filename)

    def onSaveChange(self, name, index, mode):
        if self.user_onChange != None:
            self.user_onChange(self.save_name.get())        
    
    def autoRename(self):
        # modify "save as" name
        filename = self.picked_name.get()
        if filename == "": filename = "new" + self.file_extension # if no file selected, create new filename
        ext = ""
        extpos = filename.rfind(".")
        if extpos != -1: ext = filename[extpos:]
        base = filename[:extpos]
        hpos = base.rfind("-")
        num = 0
        if hpos != -1:
            try:
                num = int(base[hpos+1:])
                base = base[:hpos]
            except:
                pass
        while True:
            num += 1
            filename = "%s-%d%s" % (base, num, ext)
            if not os.path.exists(filename):
                break
        self.save_name.set(filename)
        # user callback
        if self.user_onChange != None:
            self.user_onChange(filename)

    def onEdit(self):    
        if self.unmodified == True:
            self.unmodified = False
            # do auto rename if it's enabled or there is no file selected (editing new file)
            if self.rename_on_edit.get() == 1 or self.picked_name.get() == "":
                self.autoRename()
            # enable editing of save as name
            self.save_edit.configure(state=NORMAL)
    
    def onChangeRename(self):
        # called when clicking on "rename on edit" checkbox
        if self.rename_on_edit.get() == 1:
            if (not self.unmodified) and self.save_name.get() == self.picked_name.get():
                self.autoRename()
        else:
            self.save_name.set(self.picked_name.get())
    
    def __init__(self, master, file_mask, default_file, edit_height = None, user_onChange = None, rename_on_edit=0, font = None):
        '''
            file_mask: file mask (e.g. "*.foo") or list of file masks (e.g. ["*.foo", "*.abl"])
        '''
        self.master = master
        self.user_onChange = user_onChange
        Frame.__init__(self, master)
        row = 0
        self.unmodified = True
        self.file_extension = ""
        if "." in file_mask:
            self.file_extension = file_mask[file_mask.rfind('.'):]
        # read filenames
        if type(file_mask) != list:
            file_mask = [file_mask]        
        self.files = []
        for filename in os.listdir("."):
            for fm in file_mask:
                if fnmatch(filename, fm):
                    self.files.append(filename)
        self.files.sort()
        if len(self.files) == 0: self.files.append("(no %s files found)" % file_mask)
        # filename frame
        self.list_frame = Frame(self)
        self.list_frame.grid(row=row, column=0, sticky="WE")
        self.list_frame.columnconfigure(0, weight=1)
        # create list
        self.picked_name = StringVar(self)
        self.makelist()
        # save button
        self.save_button = Button(self.list_frame, text="save", command=self.save, height=1)
        self.save_button.grid(row=0, column=1, sticky="E")
        # editor
        row += 1
        self.editor = SyntaxHighlightingText(self, self.onEdit)
        if font != None:
            self.editor.configure(font=font)
        if edit_height is not None:
            self.editor.configure(height=edit_height)
        self.editor.grid(row=row, column=0, sticky="NEWS")
        self.rowconfigure(row, weight=1)
        self.columnconfigure(0, weight=1)
        # option to change filename on edit
        row += 1
        self.options_frame = Frame(self)
        self.options_frame.grid(row=row, column=0, sticky=W)
        self.rename_on_edit = IntVar()
        cb = Checkbutton(self.options_frame, text="rename on edit", variable=self.rename_on_edit)
        cb.pack(side=LEFT)
        cb.configure(command=self.onChangeRename)
        self.rename_on_edit.set(rename_on_edit)
        # filename frame
        row += 1
        self.filename_frame = Frame(self)
        self.filename_frame.grid(row=row, column=0, sticky="WE")
        self.filename_frame.columnconfigure(0, weight=1)
        # save as filename
        self.save_name = StringVar(self)
        self.save_edit = Entry(self.filename_frame, textvariable = self.save_name)
        self.save_edit.grid(row=0, column=0, sticky="WE")
        self.save_name.trace("w", self.onSaveChange)
        # pick default if applicable
        if default_file in self.files:
            if not havePMW:
                self.picked_name.set(default_file) # default value
            else:
                self.list.selectitem(default_file)
                self.onSelChange(default_file)
                pass
        
    def makelist(self):
        if havePMW:
            self.list = Pmw.ComboBox(self.list_frame,
                    selectioncommand = self.onSelChange,
                    scrolledlist_items = self.files,
            )
            self.list.grid(row=0, column=0, padx=10, sticky="NEW")
            self.list.component('entryfield').component('entry').configure(state = 'readonly', relief = 'raised')
            self.picked_name = self.list            
        else:
            self.list = apply(OptionMenu, (self.list_frame, self.picked_name) + tuple(self.files))
            self.list.grid(row=0, column=0, sticky="NEW")
            self.picked_name.trace("w", self.onSelChange)

    def save(self):
        self.get()
    
    def get(self):
        filename = self.save_name.get()
        if self.unmodified == False:
            self.unmodified = True
            # save the file
            f = file(filename, "w")
            f.write(self.editor.get("1.0", END))
            f.close()
            # add it to the list of files
            if not filename in self.files:
                self.files.append(filename)
                self.files.sort()
                self.list.destroy()
                self.makelist()
            # set it as the new pick
            self.picked_name.set(filename)
            self.save_edit.configure(state=DISABLED)
        return filename

    def get_text(self):
        return self.editor.get("1.0", END)
    
    def get_filename(self):
        return self.save_name.get()


class FilePick(Frame):

    def onSelChange(self, name, index=0, mode=0):
        filename = self.picked_name.get()
        if self.user_onChange != None:
            self.user_onChange(filename)

    def __init__(self, master, file_mask, default_file, user_onChange = None, font = None):
        ''' file_mask: file mask or list of file masks '''
        self.master = master
        self.user_onChange = user_onChange
        Frame.__init__(self, master)
        self.columnconfigure(0, weight=1)
        self.unmodified = True
        self.file_extension = ""
        if "." in file_mask:
            self.file_extension = file_mask[file_mask.rfind('.'):]
        # read filenames
        self.files = []
        if type(file_mask) != list:
            file_mask = [file_mask]
        for fm in file_mask:
            for filename in os.listdir("."):
                if fnmatch(filename, fm):
                    self.files.append(filename)
        self.files.sort()
        if len(self.files) == 0: self.files.append("(no %s files found)" % file_mask)
        # create list
        self.picked_name = StringVar(self)
        self.makelist()
        # pick default if applicable
        if default_file in self.files:
            if not havePMW:
                self.picked_name.set(default_file) # default value
            else:
                self.list.selectitem(default_file)
                self.onSelChange(default_file)
                pass
        
    def makelist(self):
        if havePMW:
            self.list = Pmw.ComboBox(self.list_frame,
                    selectioncommand = self.onSelChange,
                    scrolledlist_items = self.files,
            )
            self.list.grid(row=0, column=0, padx=10, sticky="NEW")
            self.list.component('entryfield').component('entry').configure(state = 'readonly', relief = 'raised')
            self.picked_name = self.list            
        else:
            self.list = apply(OptionMenu, (self, self.picked_name) + tuple(self.files))
            self.list.grid(row=0, column=0, sticky="NEW")
            self.picked_name.trace("w", self.onSelChange)

    def get(self):
        filename = self.picked_name.get()
        return filename
