package probcog.logic;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import probcog.srl.GenericDatabase;

import edu.tum.cs.util.StringTool;

public class Disjunction extends ComplexFormula {

	public Disjunction(Collection<Formula> children) {
        super(children);
    }

    public Disjunction(Formula... children) {
        super(children);
    }

    public String toString() {
        return "(" + StringTool.join(" v ", children) + ")";
    }

    @Override
    public boolean isTrue(IPossibleWorld w) {
        for (Formula child : children)
            if (child.isTrue(w))
                return true;
        return false;
    }

    @Override
    public Formula toCNF() {
    	/*try{
    	Negation.cnfDepth++; System.out.printf(String.format("%%%dc", Negation.cnfDepth), ' ');
        System.out.println(this);
        */
        if(this.children.length == 1)
        	return this.children[0].toCNF();
        HashSet<Formula> clause = new HashSet<Formula>();
        HashSet<String> strClause = new HashSet<String>();
        Vector<Conjunction> conjunctions = new Vector<Conjunction>();
        // convert children to CNF and group by disjunction (flattened) and conjunction
        // make sure that the flattened disjunction contains no duplicates
        for (Formula child : children) {
            child = child.toCNF();
            if (child instanceof Conjunction) {
                conjunctions.add((Conjunction) child);
            } else if (child instanceof Disjunction) {
                for (Formula c : ((Disjunction) child).children)
                    if (!strClause.contains(child.toString())) {
                        clause.add(c);
                        strClause.add(c.toString());
                    }
            //clause.addAll(Arrays.asList(((Disjunction)child).children));
            } else if (child instanceof TrueFalse) {
                if (((TrueFalse) child).isTrue())
                    return child;
            } else { // must be literal/atom

                if (!strClause.contains(child.toString())) {
                    clause.add(child);
                    strClause.add(child.toString());
                }
            }
        }
        if (conjunctions.isEmpty())
            return clause.size() == 1 ? clause.iterator().next() : new Disjunction(clause);
        else {
        	if(conjunctions.size() == 1 && clause.size() == 0)
        		return conjunctions.get(0);
            // apply distributivity
            // use the first conjunction to distribute: (C_1 ^ ... ^ C_n) v RD = (C_1 v RD) ^ ... ^  (C_n v RD)
            Iterator<Conjunction> i = conjunctions.iterator();
            Formula[] conjuncts = i.next().children;
            while (i.hasNext())
                clause.add(i.next());
            //Formula RD = new Disjunction(clause);
            Vector<Formula> elems = new Vector<Formula>();
            for (Formula Ci : conjuncts) {
            	@SuppressWarnings("unchecked")
				HashSet<Formula> newClause = (HashSet<Formula>)clause.clone();
            	newClause.add(Ci);
                elems.add(new Disjunction(newClause));
            }
            return new Conjunction(elems).toCNF();
        }
        /*
    	}
    	finally {
    		Negation.cnfDepth--;
    	}
    	*/
    }
    
    @Override
    public Formula toNNF() {
    	Vector<Formula> disjuncts = new Vector<Formula>();
    	for(Formula child : this.children) {
    		Formula newChild = child.toNNF();
    		if(newChild instanceof Disjunction) { // flatten nested disjunction
    			for(Formula nestedChild : ((Disjunction)newChild).children)
    				disjuncts.add(nestedChild);
    		}
    		else
    			disjuncts.add(newChild);			
    	}
    	return new Disjunction(disjuncts);
    }

    /**
     * This method simplifies the formula (atoms that are given by the evidence are evaluated to TrueFalse)
     * @param evidence (evidence of the current szenario)
     * @return returns a formula simplified by the evidence or an instance of TrueFalse
     */
    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
        Vector<Formula> simplifiedChildren = new Vector<Formula>();
        // check for each child, whether an entry in evidenceDB exists
        for (Formula child : this.children) {
            child = child.simplify(evidence);
             // if the child is true, then complete disjunction is true
            if (child instanceof TrueFalse) {
                if (((TrueFalse) child).isTrue())
                    return TrueFalse.TRUE;
                else
                    continue;
            } else
                // adds the child to simplified children if it isn't instance of TrueFalse
                simplifiedChildren.add(child);
        }
        // return the simplified formula if the vector isn't empty
        if (!simplifiedChildren.isEmpty())
            return new Disjunction(simplifiedChildren);
        else
            // otherwise return false
            return TrueFalse.FALSE;
    }
}
