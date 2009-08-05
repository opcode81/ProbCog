package edu.tum.cs.logic;

import edu.tum.cs.srl.bayesnets.Database;

import java.util.Collection;
import java.util.Vector;

public class Negation extends ComplexFormula {
	public Negation(Formula f) {
        super(new Formula[]{f});
    }

    public Negation(Collection<Formula> children) throws Exception {
        super(children);
        if (children.size() != 1)
            throw new Exception("A negation can have but one child.");
    }

    public String toString() {
        return "!(" + this.children[0].toString() + ")";
    }

    @Override
    public boolean isTrue(IPossibleWorld w) {
        return !children[0].isTrue(w);
    }

    @Override
    public Formula toCNF() {
        Formula f = children[0].toCNF();
        if (f instanceof ComplexFormula) {
            Vector<Formula> negChildren = new Vector<Formula>();
            for (Formula child : ((ComplexFormula) f).children)
                negChildren.add(new Negation(child));
            if (f instanceof Disjunction)
                return new Conjunction(negChildren).toCNF();
            else
                return new Disjunction(negChildren).toCNF();
        } else {
            if (f instanceof GroundLiteral) {
                GroundLiteral l = (GroundLiteral) f;
                return new GroundLiteral(!l.isPositive, l.gndAtom);
            } else if (f instanceof TrueFalse) {
                TrueFalse tf = (TrueFalse) f;
                return TrueFalse.getInstance(!tf.isTrue());
            }
            throw new RuntimeException("CNF conversion of negation of " + children[0].getClass().getSimpleName() + " not handled.");
        }
    }

    /**
     * this method simplifies the formula (atoms of this formuala that are given by the evidence are evaluated to TrueFalse)
     * @param evidence (evidence of the current szenario)
     * @return  returns a Formula simplified by the evidence or an instance of TrueFalse
     */
    @Override
    public Formula simplify(Database evidence) {
        Formula f = this.children[0];
        if (f instanceof ComplexFormula) {
            Vector<Formula> negChildren = new Vector<Formula>();
            // negate all children of the formula
            for (Formula child : ((ComplexFormula) f).children)
                negChildren.add(new Negation(child));
            if (f instanceof Disjunction)   // if it's a disjunction, return a conjunction
                return new Conjunction(negChildren).simplify(evidence);
            else    // else it's a conjunction -> return a disjunction
                return new Disjunction(negChildren).simplify(evidence);
        } else {
            if (f instanceof GroundLiteral) { // if it's a groundliteral, terurn the negated groundliteral
                GroundLiteral l = (GroundLiteral) f;
                return new GroundLiteral(!l.isPositive, l.gndAtom).simplify(evidence);
            } else if (f instanceof TrueFalse) { // if it's a instance of TrueFalse, return the negated instance of TrueFalse
                TrueFalse tf = (TrueFalse) f;
                return TrueFalse.getInstance(!tf.isTrue());
            }
            System.out.println(children[0].toString());
            throw new RuntimeException("Simplifiy of negation of " + children[0].getClass().getSimpleName() + " not handled.");
        }
    }
}
