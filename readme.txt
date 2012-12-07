
    ProbCog: A Toolbox for Probabilistic Cognition in Technical Systems
    -------------------------------------------------------------------


*** About ProbCog ***

ProbCog is a toolbox for statistical relational learning and reasoning and as such also
includes tools for standard graphical models.

* Bayesian logic networks (BLNs): learning and inference
* Markov logic networks (MLNs): learning and inference
* Bayesian networks: learning and inference
* Logic: representation, propositionalization, stochastic SAT sampling, etc.


*** Compatibility ***

This software suite works out-of-the-box on Linux/AMD64, Linux/i386 
and Windows/32bit. 

For other environments, you will need to obtain an appropriate binary package 
of the Standard Widget Toolkit library (http://www.eclipse.org/swt/) and modify 
the application files created during installation (see below) to use them.


*** Prerequisites *** 

* Java 5 runtime environment (or newer)

* Python 2.5 (or newer) with Tkinter installed
  Note: On Windows, Tkinter is usually shipped with Python.
        On Linux, you may need to install a package (e.g. "python-tk" on Ubuntu)


*** Installation ***

1) Generating Apps

Run the make_apps script:
    python make_apps.py

This will generate a number of shell scripts (or batch files for Windows)
in the ./apps directory. 

2) Setting up your Environment

make_apps will report how to set up your environment.
To temporarily configure your environment, you can simply use the "env" script/batch
file it creates to get everything set up.

If you use ProbCog a lot, consider adding the ./apps directory to your PATH variable
or copy the files created therein to an appropriate directory.

If you intend to make use of scripting, also set PYTHONPATH and JYTHONPATH as described
by make_apps.


*** Getting Started ***

There are example models in the ./examples/ directory.

Simply run the "blnquery" or "mlnquery" applications in one of the subdirectories
to try out some inference tasks.

In the ./examples/meals/ directory, you can also try out learning.
To train a BLN or MLN model run "blnlearn" or "mlnlearn". 


*** Further Reading ***

Visit the ProbCog wiki for tutorials and additional documentation:
http://ias.in.tum.de/probcog-wiki/


*** Contributors ***

Dominik Jain
Stefan Waldherr
Klaus von Gleissenthall
Andreas Barthels
Ralf Wernicke
Gregor Wylezich
Martin Schuster
Philipp Meyer
Daniel Nyga


*** Acknowledgements ***

This project builds upon third-party software including
* Bayesian network tools in Java (http://bnj.sourceforge.net)
* The WEKA machine learning library (http://www.cs.waikato.ac.nz/ml/weka/)
* ... to be continued
