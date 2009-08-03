/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.tum.cs.logic.parser;

import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.KnowledgeBase;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author wernickr
 */
public class mlnParser {

    private File mlnFile;
    private FileReader freader;
    private BufferedReader breader;
    String actLine;
    File testFile;
    List<String> predicates = new ArrayList<String>();
    List<String> formulas = new ArrayList<String>();
    List<String> domains = new ArrayList<String>();
    ArrayList<String> weights = new ArrayList<String>();
    Boolean b = false;

    public mlnParser(String fileLoc) {
        try {
            mlnFile = new File(fileLoc);
            freader = new FileReader(mlnFile);
            breader = new BufferedReader(freader);
        } catch (FileNotFoundException fnfe) {
            System.out.println("Sorry! The given mlnfile was not found, try again!");
            fnfe.printStackTrace();
        }
    }

    public void parsemlnFile() {
        Boolean leftbr = false;

        try {
            actLine = breader.readLine();

            while (breader != null && actLine != null) {
                if (actLine.equals("")) {
                    actLine = breader.readLine();
                    continue;
                } else if (actLine.contains("//")) {
                    if (actLine.indexOf("//") == 0) {
                        actLine = breader.readLine();
                        continue;
                    } else {
                        actLine = actLine.substring(0, actLine.indexOf("//") - 1);
                    }
                } else if (actLine.contains("/*")) {
                    leftbr = true;
                    if (actLine.contains("*/")) {
                        if (actLine.indexOf("/*") == 0) {
                            if (actLine.indexOf("*/") + 2 == actLine.length()) {
                                actLine = breader.readLine();
                                leftbr = false;
                                continue;
                            } else {
                                actLine = actLine.substring(actLine.indexOf("*/") + 2);
                                leftbr = false;
                            }
                        } else {
                            if (actLine.indexOf("*/") + 2 == actLine.length()) {
                                actLine = actLine.substring(0, actLine.indexOf("/*") - 1);
                                leftbr = false;
                            } else {
                                if (actLine.indexOf("/*") < actLine.indexOf("*/")) {
                                    actLine = actLine.substring(0, actLine.indexOf("/*") - 1) + actLine.substring(actLine.indexOf("*/") + 2);
                                    leftbr = false;
                                }

                            }
                        }
                    } else {
                        if (actLine.indexOf("/*") == 0) {
                            while (breader != null && actLine != null) {
                                if (actLine.contains("*/")) {
                                    break;
                                }
                                actLine = breader.readLine();
                            }
                            if (actLine.indexOf("*/") + 2 == actLine.length()) {
                                actLine = breader.readLine();
                                leftbr = false;
                                continue;
                            } else {
                                actLine = actLine.substring(actLine.indexOf("*/") + 2);
                                leftbr = false;
                            }
                        } else {
                            actLine = actLine.substring(0, actLine.indexOf("/*"));
                            if (!actLine.equals("") && actLine != null){
                                insertintoList(actLine);
                            }
                            while (breader != null && actLine != null) {
                                if (actLine.contains("*/")) {
                                    leftbr = false;
                                    break;
                                }
                                actLine = breader.readLine();
                            }
                            if (actLine != null) {
                                if (actLine.indexOf("*/") + 2 == actLine.length()) {
                                    actLine = breader.readLine();
                                    leftbr = false;
                                    continue;
                                } else {
                                    actLine = actLine.substring(actLine.indexOf("*/") + 2);
                                    leftbr = false;
                                }
                            }
                        }
                    }
                }
                if (breader != null && actLine != null) {
                    if (leftbr == false && actLine.contains("*/")) {
                        throw new Exception();
                    }
                    if (actLine.contains("//") || (actLine.contains("/*")) || (actLine.contains("*/"))) {
                        continue;
                    }
                    if (!actLine.equals("") && actLine != null){
                        insertintoList(actLine);
                    }
                }
                actLine = breader.readLine();
            }
        } catch (FileNotFoundException fnfe) {
            System.out.println("Sorry! The file not found, try again!");
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            System.out.println("There was an error during process! Could not parse file!");
            ioe.printStackTrace();
        } catch (Exception e) {
            System.out.println("There was an error during parsing! No valid mlnfile!");
            System.out.println(actLine);
            e.printStackTrace();
        }

    }

    public void insertintoList(String line) {
        // EXIST - Parser
        int brleft = 0;
        int brright = 0;


        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '(') {
                brleft++;
            } else if (line.charAt(i) == ')') {
                brright++;
            }
        }

        if (brleft != brright) {
            System.out.println("Error parsing file! There is an unequal number of left and right brackets in your predicate/formula!");
            System.out.println("Line: " + line);            
            throw new IllegalArgumentException();
        }

        //Pattern für die Deklaration von Prädikaten
        Pattern predicate = Pattern.compile("[a-z]+[\\w]*[(]{1}([a-z|A-Z]+[\\w]*[!]?){1}(,[\\s]*([a-z|A-Z]+[\\w]*[!]?))*[)]{1}");
        //Pattern für die zulässigen Operatoren
        Pattern operators = Pattern.compile("[\\s]*([v]{1}|[=>]{2}|[\\^]{1})[\\s]*");
        //Pattern für zulässige Literale
        Pattern literals = Pattern.compile("[(]*[!]?[(]*" + predicate + "[)]*");
        //Pattern für Vergleiche
        Pattern literals2 = Pattern.compile("[!]?[(]*[a-z|A-Z]+[\\w]*[=|<|>][a-z|A-Z]+[\\w]*[)]*");
        // Pattern für Existenzquantor
        Pattern existence = Pattern.compile("[\\s]*[(]*[!]?[(]*[E][X][I][S][T][\\s]+[a-z|A-Z]+[\\w]*([\\s]+|[(]+)*");
        //Pattern für zulässige Formel allgemein
        Pattern formula = Pattern.compile("(" + existence + ")*" + "(" + literals + "|" + literals2 + ")" + "(" + operators + "(" + existence + ")*" + "(" + literals + "|" + literals2 + ")" + ")*[\\s]*");
        //Pattern einer zulässigen Zahl für Rechenoperationen (log, sqrt)
        Pattern validNumber = Pattern.compile("[-]?([0-9]+[.]?[0-9]*){1,18}");
        //Pattern einer zulässigen Gewichtungsangabe
        Pattern validWeight = Pattern.compile("[\\s]*[(]*[\\s]*" + validNumber + "[\\s]*[)]*[\\s]+");
        //Pattern einer Formel mit Gewichtungsangabe (double, log)
        Pattern formula2 = Pattern.compile("(" + validWeight + ")" + formula);
        //Pattern einer Formel mit Gewichtungsangabe durch Punkt (-> hartes Constraint)
        Pattern formula3 = Pattern.compile("(" + existence + ")*" + "(" + literals + "|" + literals2 + ")" + "(" + operators + "(" + existence + ")*" + "(" + literals + "|" + literals2 + ")" + ")*.");
        //Pattern für Zeilen mit Leerzeichen
        Pattern blank = Pattern.compile("[\\s]+");
        // Pattern für eine Domain
        Pattern domain = Pattern.compile("[\\s]*[a-z]+[\\w]*[\\s]*[=][\\s]*[{][\\s]*[\\w]*[\\s]*([,][\\s]*[\\w]*[\\s]*)*[}][\\s]*");
        
        
        Matcher m = formula3.matcher(line);
        Matcher m2 = formula2.matcher(line);
        Matcher m3 = predicate.matcher(line);
        Matcher m4 = blank.matcher(line);
        Matcher m5 = domain.matcher(line);
        Matcher m6 = validNumber.matcher(line);
        Matcher m8 = formula.matcher(line);
        
        if (m.matches()){
            weights.add(".");
            formulas.add(line.substring(0, line.length()-1));
        }else if (m2.matches()) {
            if (m6.find()){
                weights.add(m6.group());
                m8.find();
                formulas.add(m8.group());
            } else {
                throw new IllegalArgumentException();
            }
        } else if (m3.matches()) {
            predicates.add(line);
        }else if (m4.matches()){
            return;
        } else if (m5.matches()) {
            domains.add(line);
        }else  {
            System.out.println("Error parsing predicate/formula! Line: " + actLine);
            throw new IllegalArgumentException();
        }
    }

    public void printlists() {
        if (formulas != null && weights != null) {
            for (int i = 0; i < formulas.size(); i++) {
                System.out.println("Weight: " + weights.get(i) + ", Formula: " + formulas.get(i));
            }
        }
        if (predicates != null) {
            for (int i = 0; i < predicates.size(); i++) {
                System.out.println("Predicate: " + predicates.get(i));
            }
        }
        if (domains != null) {
            for (int i = 0; i < domains.size(); i++) {
                System.out.println("Domain: " + domains.get(i));
            }
        }
    }
    
    public void testFormulas(){
        for(String f : predicates){
            try {
                System.out.println("Parsing Formula: " + f);
                FormulaParser.parse(f);
            } catch (ParseException ex) {
                Logger.getLogger(mlnParser.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }
    
    public void testgrnAtoms(){
        for (String pred : this.predicates){
            GroundAtom grnd = new GroundAtom(pred);
            System.out.println(grnd.toString());
            for (String arg : grnd.args){
                System.out.println(" Arg: " + arg);
            }

        }
        
    }

    
    
    public static void main(String[] args){

          
        
        mlnParser mlnfr = new mlnParser("C:/raumplanung1.mln");
        mlnfr.parsemlnFile();
        //mlnfr.printlists();
        mlnfr.testgrnAtoms();
        
       
        
        //mlnfr.testFormulas();
        

    }
}
