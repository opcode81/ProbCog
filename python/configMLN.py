# configuration script for the MLN query & parameter learning tools
#
#   If you want to use PyMLNs with the Alchemy System (http://alchemy.cs.washington.edu),
#   set the path where it is installed on your system in the alchemy_versions dictionary below.
#
#   Depending on your naming convention for mln and database files, you may need to change
#      query_db_filemask, query_mln_filemask, learnwts_mln_filemask and learnwts_db_filemask
#   to suit your needs.
#
#   You can use os.getenv("VARIABLE") to retrieve the value of an environment variable

from configGUI import *

# --- settings for the parameter learning tool ---

learnwts_mln_filemask = "*.mln"
learnwts_db_filemask = "*.db"
def learnwts_output_filename(infile, engine, method, dbfile): # formats the output filename
    if infile[:3] == "in.": infile = infile[3:]
    elif infile[:4] == "wts.": infile = infile[4:]
    if infile[-4:] == ".mln": infile = infile[:-4]
    if dbfile[-3:] == ".db": dbfile = dbfile[:-3]
    return "wts.%s%s.%s-%s.mln" % (engine, method, dbfile, infile)
learnwts_full_report = True # if True, add all the printed output to the Alchemy output file, otherwise (False) use a short report
learnwts_report_bottom = True # if True, the comment with the report is appended to the end of the file, otherwise it is inserted at the beginning


#  --- settings for the query tool ---

query_mln_filemask = "*.mln"
query_db_filemask = ["*.db", "*.blogdb"]
def query_output_filename(mlnfile, dbfile):
    if mlnfile[:4] == "wts.": mlnfile = mlnfile[4:]
    if mlnfile[-4:] == ".mln": mlnfile = mlnfile[:-4]
    if dbfile[-3:] == ".db": dbfile = dbfile[:-3]
    return "%s-%s.results" % (dbfile, mlnfile)
query_edit_outfile_when_done = False # if True, open the output file that is generated by the Alchemy system in the editor defined above
keep_alchemy_conversions = True

# --- Alchemy settings ---

# define how the Alchemy system is to be used, i.e. what certain command line switches are
old_usage = {
    "openWorld": "-o",
    "maxSteps": "-mcmcMaxSteps",
    "numChains": "-mcmcNumChains",
}
new_usage = {
    "openWorld": "-ow",
    "maxSteps": "-maxSteps",
    "numChains": "-numChains"
}
default_infer_usage = new_usage # the usage that is to apply when the "usage" of an Alchemy installation is not set explicitly in the dictionary below

# installed Alchemy versions:
# - Keys are names of the installations as they should appear in the two tools.
# - Values should be either paths to the Alchemy root or "bin" directory or
#   a dictionary with at least the key "path" set to the Alchemy root or bin directory.
#   The dictionary can additionally set "usage" to one of the above mappings
alchemy_versions = {
    #"Alchemy - current (AMD64)": {"path": os.getenv("ALCHEMY_HOME"), "usage": new_usage},
    "Alchemy - July 2009 (AMD64)": {"path": r"/usr/wiss/jain/work/code/alchemy-2009-07-07/bin", "usage": new_usage},
    "Alchemy - June 2008 (AMD64)": {"path": r"/usr/wiss/jain/work/code/alchemy-2008-06-30/bin/amd64", "usage": new_usage},
    "Alchemy - August 2010 (AMD64)": {"path": os.getenv("ALCHEMY_HOME"), "usage": new_usage},
    #"Alchemy - June 2008 (i386)": {"path": r"/usr/wiss/jain/work/code/alchemy-2008-06-30/bin/i386", "usage": new_usage},
	"Alchemy (Win32 desktop)": {"path": r"c:\users\Domini~1\Research\code\alchemy-2010-08-23\bin", "usage": new_usage},
    "Alchemy (Win32 laptop)": {"path": r"c:\research\code\alchemy\bin", "usage": new_usage},
}
'''
# snapshot, snapshot original and (if the weights in the input MLN are all 0) all yield the same results when weight learning
# snapshot is a lot faster than snapshot original though
# snapshot learnwts-startpnt modifies the start point of the optimization, using the weights given in the mln (if any) instead of a zero vector - these weights are not used as priors for penalizing though (reset to 0)
# snapshot dev has a different counting method (which I think is correct - unlike the original one)
'''
