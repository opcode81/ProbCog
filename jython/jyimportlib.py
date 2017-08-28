'''
importing of Java libraries for jython apps
'''

import sys
import os

PROBCOG_ROOT = os.path.join(os.path.dirname(__file__), "..")

def importjar(jar):
	''' imports the given jar file (path relative to ProbCog lib/ directory)'''
	sys.path.append(os.path.join(PROBCOG_ROOT, "lib", jar))

def importdir(relative_path):
	''' adds the given path (relative to ProbCog root) to the classpath '''
	sys.path.append(os.path.join(PROBCOG_ROOT, relative_path))

def importbin():
	''' adds the ProbCog bin/ directory as an import '''
	importdir(os.path.join("target", "classes"))
