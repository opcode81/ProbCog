#!/usr/bin/python
# -*- coding: iso-8859-1 -*-
import os
import stat
import sys
import platform

includes = {
    "weka": {"jars": ["$SRLDB_HOME/../WEKA/bin", "$SRLDB_HOME/lib/weka_fipm.jar"]},
    "srldb": {"jars": ["$SRLDB_HOME/bin", "$SRLDB_HOME/lib/srldb.jar", "$SRLDB_HOME/../TUMUtils/bin", "$SRLDB_HOME/lib/tumutils.jar"]},
    "jython": {"jars": ["$SRLDB_HOME/lib/jython250.jar", "$SRLDB_HOME/lib/jython250lib.jar"]},
    "swt": {"jars": ["$SRLDB_HOME/lib/swt_<ARCH>/swt.jar"], "lib":"$SRLDB_HOME/lib/swt_<ARCH>"},
    "bnj": {"jars": ["$SRLDB_HOME/../BNJ/bin", "$SRLDB_HOME/lib/bnj.jar", "$SRLDB_HOME/lib/bnj_res.jar"]},
    "smile": {"jars": ["$SRLDB_HOME/lib/jsmile_<ARCH>/smile.jar"], "lib": "$SRLDB_HOME/lib/jsmile_<ARCH>", "optional": True},
    "proximity": {"jars": ["$SRLDB_HOME/lib/proximity.jar", "$SRLDB_HOME/lib/proximity3.jar"]},
    "ssj": {"jars": ["$SRLDB_HOME/lib/ssj.jar", "$SRLDB_HOME/lib/optimization.jar"]},
    "jahmm": {"jars": ["$SRLDB_HOME/../jahmm/bin", "$SRLDB_HOME/lib/jahmm.jar"]},
    "processing": {"jars": ["$SRLDB_HOME/lib/processing.org/core.jar"], "optional": True},
    "mysql": {"jars": ["$SRLDB_HOME/lib/mysql-connector-java-3.0.11-stable-bin.jar"]},
    "vecmath": {"jars": ["$SRLDB_HOME/lib/vecmath.jar"]},
    "yprolog": {"jars": ["$SRLDB_HOME/lib/yprolog.jar"]},
    "kipm": {"jars": ["$SRLDB_HOME/../KIPM/bin", "$SRLDB_HOME/lib/kipm.jar", "$SRLDB_HOME/lib/fipmbase.jar", "$SRLDB_HOME/lib/kipmdata.jar"], "optional":True},
    "choco": {"jars": ["$SRLDB_HOME/lib/choco-2.1.1.jar"], "optional": True},
    "jdom": {"jars": ["$SRLDB_HOME/lib/jdom.jar"], "optional": True},
    "proximity_new": {"jars": ["$SRLDB_HOME/lib/proximity.jar"], "optional":True},
}

java_apps = {
    "BLNinfer": {"class": "probcog.srl.directed.inference.BLNinfer", "includes": ["srldb", "weka", "jython", "swt", "bnj", "smile", "ssj", "yprolog", "choco","jdom","proximity_new"]},
    "BLN2MLN": {"class": "probcog.BLN2MLN", "includes": ["srldb", "weka", "jython", "swt", "bnj"]},
    "BLNprintCPT": {"class": "probcog.BLNprintCPT", "includes": ["srldb", "jython", "bnj"]},
    "BLOGDB2MLNDB": {"class": "probcog.BLOGDB2MLNDB", "includes": ["srldb", "jython", "bnj", "yprolog"]},
    "BNprintCPT": {"class": "probcog.BNprintCPT", "includes": ["srldb", "bnj"]},
    "BNinfer": {"class": "probcog.bayesnets.inference.BNinfer", "includes": ["srldb", "swt", "bnj", "smile"]},
    "ABL2MLN": {"class": "probcog.ABL2MLN", "includes": ["srldb", "weka", "swt", "bnj"]},
    "BN2CSV": {"class": "probcog.bayesnets.conversion.BN2CSV", "includes": ["srldb", "swt", "bnj"]},
    "BNsaveAs": {"class": "probcog.bayesnets.conversion.BNsaveAs", "includes": ["srldb", "swt", "bnj"]},
    "BNlistCPTs": {"class": "probcog.BNlistCPTs", "includes": ["srldb", "swt", "bnj"]},
    "BNrandomEvidence": {"class": "probcog.BNrandomEvidence", "includes": ["srldb", "swt", "bnj"]},
    "MLN2WCSP": {"class": "probcog.MLN2WCSP", "includes": ["srldb", "jython"]},
    "MLNinfer": {"class": "probcog.MLNinfer", "includes": ["srldb", "bnj", "jython","yprolog"]},
    "bnj": {"class": "probcog.BNJ", "includes": ["srldb", "bnj", "swt"]},
    "genDB": {"class": "probcog.genDB", "includes": ["srldb", "jython", "proximity", "bnj", "weka"]},
    "groundABL": {"class": "probcog.groundABL", "includes": ["srldb", "bnj"]},
    "learnABL": {"class": "probcog.srl.directed.learning.BLNLearner", "includes": ["srldb", "weka", "swt", "bnj", "yprolog"]},
    "learnABLSoft": {"class": "dev.learnABLSoft", "includes": ["srldb", "weka", "swt", "bnj", "yprolog"]},
    "jython": {"class": 'org.python.util.jython', "includes": ["jython"]},
    "syprolog": {"class": "probcog.PrologShell", "includes": ["srldb", "yprolog"]},
    "yprolog": {"class": "yprolog.Go", "includes": ["yprolog"]},
    "blogdb2ergevid": {"class": "blogdb2ergevid", "includes": ["srldb", "weka", "jython", "swt", "bnj", "yprolog"]},
    "bndb2inst": {"class": "probcog.bayesnets.conversion.BNDB2Inst", "includes": ["srldb"]},
}
java_apps["netEd"] = java_apps["bnj"]
java_apps["pcjython"] = java_apps["jython"]

python_apps = [
    {"name": "mlnquery", "script": "$SRLDB_HOME/python/mlnQueryTool.py"},
    {"name": "mlnlearn", "script": "$SRLDB_HOME/python/mlnLearningTool.py"},
    {"name": "amlnlearn", "script": "$SRLDB_HOME/python/amlnLearn.py"},
    {"name": "blnquery", "script": "$SRLDB_HOME/python/blnQueryTool.py"},
    {"name": "bnquery", "script": "$SRLDB_HOME/python/bnQueryTool.py"},
    {"name": "blnlearn", "script": "$SRLDB_HOME/python/blnLearningTool.py"},
    {"name": "fixCR", "script": "$SRLDB_HOME/python/fixCR.py"},
    {"name": "MLN", "script": "$SRLDB_HOME/python/MLN.py"},
    {"name": "trajvis", "script": "$SRLDB_HOME/python/trajvis.py"},
    {"name": "evalSeqLabels", "script": "$SRLDB_HOME/python/evalSeqLabels.py"},
    {"name": "pmml2graphml", "script": "$SRLDB_HOME/python/pmml2graphml.py"},
]

pythonInterpreter = "python"

def adapt(name, arch):
    return name.replace("<ARCH>", arch).replace("$SRLDB_HOME", os.path.abspath(".")).replace("/", os.path.sep)

def getJavaAppData(name, arch):
    jars = []
    libs = []
    skip = False
    missing = []
    for inc in java_apps[name]["includes"]:
        libjars = map(lambda jar: adapt(jar, arch), includes[inc]["jars"])
        libjars_found = len(filter(lambda x: os.path.exists(x.replace("/", os.path.sep).replace("\\", os.path.sep)), libjars))
        if libjars_found == 0 and includes[inc].get("optional", False) == False:
            skip = True
            missing.append(inc)
        jars.extend(libjars)
        if "lib" in includes[inc]:
            libs.append(adapt(includes[inc]["lib"], arch))
    return {"cp": jars, "lib": libs, "skip": skip, "missing": missing}

if __name__ == '__main__':

    archs = ["win32", "win64", "linux_amd64", "linux_i386", "macosx", "macosx64"]
        
    print "\nProbCog Apps Generator\n\n"
    print "  usage: make_apps [--arch=%s] [additional JVM args]\n" % "|".join(archs)
    print
    print "  Note: Some useful JVM args include"
    print "    -Xmx8000m   set maximum Java heap space to 8000 MB"
    print "    -ea         enable assertions"
    print

    args = sys.argv[1:]
    
    # check if probcog binaries exist
    if not os.path.exists("bin") and not os.path.exists("lib/srldb.jar"):
        print "ERROR: No ProbCog binaries found. If you are using the source version of ProbCog, please compile it first using 'ant compile' or an Eclipse build"
        sys.exit(1)

    # determine architecture
    arch = None
    bits = 64 if "64" in platform.architecture()[0] else 32
    if len(args) > 0 and args[0].startswith("--arch="):
        arch = args[0][len("--arch="):].strip()
        args = args[1:]
    elif platform.mac_ver()[0] != "":
        arch = "macosx" if bits == 32 else "macosx64"
    elif platform.win32_ver()[0] != "":
        arch = "win32"
    elif platform.dist()[0] != "":
        arch = "linux_i386" if bits == 32 else "linux_amd64"
    if arch is None:
        print "Could not automatically determine your system's architecture. Please supply the --arch argument"
        sys.exit(1)
    if arch not in archs:
        print "Unknown architecture '%s'" % arch
        sys.exit(1)
        
    jvm_userargs = " ".join(args)

    if not os.path.exists("apps"):
        os.mkdir("apps")

    print "\nCreating application files for %s..." % arch
    isWindows = "win" in arch
    isMacOSX = "macosx" in arch
    preamble = "@echo off\r\n" if isWindows else "#!/bin/sh\n"
    allargs = '%*' if isWindows else '"$@"'
    pathsep = os.path.pathsep
    for appname, app in java_apps.iteritems():
        filename = os.path.join("apps", "%s%s" % (appname, {True:".bat", False:""}[isWindows]))
        data = getJavaAppData(appname, arch)
        print "  %s" % filename
        if data["skip"]:
            print "    skipped because some dependencies are not included in this distribution: ", data["missing"]
            continue
        if len(data["lib"]) > 0:
            libpath = '"-Djava.library.path=%s"' % pathsep.join(data["lib"])
        else:
            libpath = ""
        f = file(filename, "w")
        f.write(preamble)
        addargs = "-XstartOnFirstThread" if arch in ("macosx", "macosx64") else ""
        f.write('java %s -cp "%s" %s %s %s %s\n' % (addargs, os.path.pathsep.join(data["cp"]), libpath, jvm_userargs, adapt(app["class"], arch), allargs))
        f.close()
        if not isWindows: os.chmod(filename, stat.S_IRUSR | stat.S_IWUSR | stat.S_IXUSR | stat.S_IRGRP | stat.S_IXGRP | stat.S_IROTH | stat.S_IXOTH)
    for app in python_apps:
        filename = os.path.join("apps", "%s%s" % (app["name"], {True:".bat", False:""}[isWindows]))
        print "  %s" % filename
        f = file(filename, "w")
        f.write(preamble)
        f.write("%s -O \"%s\" %s\n" % (pythonInterpreter, adapt(app["script"], arch), allargs))
        f.close()
        if not isWindows: os.chmod(filename, stat.S_IRUSR | stat.S_IWUSR | stat.S_IXUSR | stat.S_IRGRP | stat.S_IXGRP | stat.S_IROTH | stat.S_IXOTH)
    print

    # write shell script for environment setup
    appsDir = adapt("$SRLDB_HOME/apps", arch)
    pythonDir = adapt("$SRLDB_HOME/python", arch)
    jythonDir = adapt("$SRLDB_HOME/jython", arch)
    if not "win" in arch:
        f = file("env.sh", "w")
        f.write("export PATH=$PATH:%s\n" % appsDir)
        f.write("export PYTHONPATH=$PYTHONPATH:%s\n" % pythonDir)
        f.write("export JYTHONPATH=$JYTHONPATH:%s:%s\n" % (jythonDir, pythonDir))
        f.write("export PROBCOG_HOME=%s\n" % adapt("$SRLDB_HOME", arch))
        f.close()
        print 'Now, to set up your environment type:'
        print '    source env.sh'
        print
        print 'To permantly configure your environment, add this line to your shell\'s initialization script (e.g. ~/.bashrc):'
        print '    source %s' % adapt("$SRLDB_HOME/env.sh", arch)
        print
    else:
        f = file("env.bat", "w")
        f.write("SET PATH=%%PATH%%;%s\r\n" % appsDir)
        f.write("SET PYTHONPATH=%%PYTHONPATH%%;%s\r\n" % pythonDir)
        f.write("SET JYTHONPATH=%%JYTHONPATH%%;%s;%s\r\n" % (jythonDir, pythonDir))
        f.write("SET PROBCOG_HOME=%s\n" % adapt("$SRLDB_HOME", arch))
        f.close()
        print 'To temporarily set up your environment for the current session, type:'
        print '    env.bat'
        print
        print 'To permanently configure your environment, use Windows Control Panel to set the following environment variables:'
        print '  To the PATH variable add the directory "%s"' % appsDir
        print '  To the PYTHONPATH variable add the directory "%s"' % pythonDir
        print '  To the JYTHONPATH variable add the directories "%s" and "%s"' % (jythonDir, pythonDir)
        print 'Should any of these variables not exist, simply create them.'
