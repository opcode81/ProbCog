from __future__ import print_function
import platform
import re
import sys

archs = ["win32", "win64", "linux_amd64", "linux_i386", "macosx", "macosx64"]

def checkPythonVersion():
    t = platform.python_version_tuple()
    if t[0] != '2' or int(t[1]) < 7:
        print("\nERROR: ProbCog requires Python 2.7 while this script was run with Python %s" % platform.python_version())
        sys.exit(1) 

def detectArch():
    arch = None
    bits = 64 if "64" in platform.architecture()[0] else 32
    if platform.mac_ver()[0] != "":
        arch = "macosx" if bits == 32 else "macosx64"
    elif platform.win32_ver()[0] != "":
        arch = "win32" if bits == 32 else "win64"
    elif platform.dist()[0] != "":
        arch = "linux_i386" if bits == 32 else "linux_amd64"
    return arch
 
if __name__ == '__main__':
    checkPythonVersion()
    from sys import argv
    argv = argv[1:]
    
    # determine architecture
    if len(argv) > 0:
        arch = argv[0].strip()
    else:
        arch = detectArch()
    if arch is None:
        print("Failed to detect architecture. Please provide one of the following as an argument:\n    %s" % ", ".join(archs))
        sys.exit(1)
    if arch not in archs:
        print("Unknown architecture '%s'" % arch)
        sys.exit(1)
    print("Adapting POM for architecture %s..." % arch)
    
    with open("pom.xml", "r") as f: pom = f.read()
    
    swtArtifactId = {
        "win32": "org.eclipse.swt.win32.win32.x86", 
        "win64": "org.eclipse.swt.win32.win32.x86_64", 
        "linux_i386": "org.eclipse.swt.gtk.linux.x86", 
        "linux_amd64": "org.eclipse.swt.gtk.linux.x86_64",         
        "macosx": "org.eclipse.swt.cocoa.macosx", 
        "macosx64": "org.eclipse.swt.cocoa.macosx.x86_64"
    }[arch]
    print("Replacing SWT artifact with %s..." % swtArtifactId)
    pom = re.sub(r'<artifactId>(org\.eclipse\.swt.*?)</artifactId>', '<artifactId>%s</artifactId>' % swtArtifactId, pom)
    
    with open("pom.xml", "w") as f: f.write(pom)
    print("pom.xml written")