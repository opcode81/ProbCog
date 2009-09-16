from sys import argv
import os
import sys

if len(argv) != 2:
    print "usage: fixCR <file>"
    print "  converts all line feeds in a text file to native format"
    sys.exit(0)

f = file(argv[1], "r")
lines = map(lambda x: x.strip("\r\n"), f.readlines())
f.close()

f = file(argv[1], "w")
f.write(os.linesep.join(lines))
f.close()

