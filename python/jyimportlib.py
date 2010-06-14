'''
importing of Java libraries for jython apps
'''

import sys
import os

libpath = os.path.join(os.path.dirname(__file__), "..", "lib")

def importjar(jar):
	''' imports the given jar file (path relative to ProbCog lib/ directory)'''
	sys.path.append(os.path.join(libpath, jar))

def importbin():
	''' adds the ProbCog bin/ directory as an import '''
	sys.path.append(os.path.join(libpath, "..", "bin"))
