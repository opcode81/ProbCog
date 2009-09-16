from sys import argv
import os
import sys

if len(argv) != 2:
    print "usage: fixCR <file>"
    print "  converts all line feeds in a text file to native format"
    sys.exit(0)

print "reading...",
f = file(argv[1], "r")
lines = map(lambda x: x.strip("\r\n"), f.readlines())
f.close()
print "%d lines read." % len(lines)

bakname = "%s~" % argv[1]
print "renaming original file to %s..." % bakname
os.rename(argv[1], bakname)

print "writing %s with fixed line feeds..." % argv[1]
f = file(argv[1], "w")
f.write(os.linesep.join(lines))
f.close()

