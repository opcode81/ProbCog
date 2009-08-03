package edu.tum.cs.srl.mln;

import edu.tum.cs.bayesnets.relational.core.BLOGModel;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Database extends edu.tum.cs.bayesnets.relational.core.Database {

    public MarkovLogicNetwork mln;
    
    /**
     * 
     * @param database
     * @param mln
     */
    public Database(String database, MarkovLogicNetwork mln) {
        super(mln);
        this.mln = mln;
    }

    /**
     * 
     * @param databaseFilename
     * @throws java.lang.Exception
     */
    public void readMLNDB(String databaseFilename) throws Exception {
        readMLNDB(databaseFilename, false);
    }


    /**
     * 
     * */
    public void readMLNDB(String databaseFilename, boolean ignoreUndefinedNodes) throws Exception {
        boolean verbose = true;

        // read file content
        if (verbose) System.out.println("  reading file contents...");
        String dbContent = BLOGModel.readTextFile(databaseFilename);

        // remove comments
        if (verbose) System.out.println("  removing comments...");
        Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = comments.matcher(dbContent);
        dbContent = matcher.replaceAll("");

        // read lines
        if (verbose) System.out.println("  reading items...");
        Pattern re_entry = Pattern.compile("[!]?[a-z]+[\\w]*[(]{1}([a-z|A-Z|0-9]+[\\w]*[!]?){1}(,[\\s]*([a-z|A-Z|0-9]+[\\w]*[!]?))*[)]{1}");
        Pattern funcName = Pattern.compile("([!]?\\w+)(\\()(\\s*[A-Z|0-9]+[\\w+\\s*(,)]*\\s*)(\\))");
        Pattern domName = Pattern.compile("[a-z]+\\w+");
        Pattern domCont = Pattern.compile("\\{([\\s*[A-Z|0-9]+\\w*\\s*[,]?]+)\\}");
        Pattern re_domDecl = Pattern.compile("[\\s]*[a-z]+[\\w]*[\\s]*[=][\\s]*[{][\\s]*[\\w]*[\\s]*([,][\\s]*[\\w]*[\\s]*)*[}][\\s]*");
        BufferedReader br = new BufferedReader(new StringReader(dbContent));
        String line;
        Variable var;
        int numVars = 0;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            // parse variable assignment
            matcher = re_entry.matcher(line);
            if (matcher.matches()) {
                matcher = funcName.matcher(line);
                matcher.find();
                if (matcher.group(1).contains("!"))
                    var = new Variable(matcher.group(1).substring(1), matcher.group(3).trim().split("\\s*,\\s*"), "False");
                else 
                    var = new Variable(matcher.group(1), matcher.group(3).trim().split("\\s*,\\s*"), "True");
			
                addVariable(var, ignoreUndefinedNodes);
                if (++numVars % 100 == 0 && verbose) System.out.println("    " + numVars + " vars read\r");
                continue;
            }

            // parse domain decls
            Matcher matcher1 = re_domDecl.matcher(line);
            Matcher domNamemat = domName.matcher(line);
            Matcher domConst = domCont.matcher(line);

            if (matcher1.matches() && domNamemat.find() && domConst.find()) { // parse domain decls
                String domNam = domNamemat.group(0);
                String[] constants = domConst.group(1).trim().split("\\s*,\\s*");
                for (String c : constants)
                    fillDomain(domNam, c);
                continue;
            }
            // something else
            if (line.length() != 0) System.err.println("Line could not be read: " + line);
        }
    }

    /**
     * 
     * @return
     */
    public HashMap<String, HashSet<String>> getDomains() {
        return domains;
    }
}
