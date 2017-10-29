
import os

# --- settings for all GUI tools ---

fixed_width_font = ("Lucida Console", -12) # name of font and size (if negative, in pixels)
editor = os.getenv("EDITOR", "vi") # your favorite editor with which output files should be opened, e.g. "kate" or "vi"
coloring = True # whether to use syntax highlighting in editors (may be slow when using large files)
