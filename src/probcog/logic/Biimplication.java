package probcog.logic;


import java.util.Collection;

import probcog.srl.GenericDatabase;

public class Biimplication extends ComplexFormula {
	
	public Biimplication(Collection<Formula> parts) throws Exception {
		super(parts);
		if(parts.size() != 2)
			throw new Exception("A biimplication must have exactly two children.");
	}

	public Biimplication(Formula f1, Formula f2) {
		super(new Formula[]{f1, f2});
	}

	public String toString() {
		return "(" + children[0] + " <=> " + children[1] + ")";
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		return children[0].isTrue(w) == children[1].isTrue(w);
	}

	@Override
	public Formula toCNF() {
		Formula c1 = new Disjunction(new Negation(children[0]), children[1]);
		Formula c2 = new Disjunction(children[0], new Negation(children[1]));
		return new Conjunction(c1, c2).toCNF();
	}
	
	@Override
	public Formula toNNF() {
		Formula c1 = new Disjunction(new Negation(children[0]), children[1]);
		Formula c2 = new Disjunction(children[0], new Negation(children[1]));
		return new Conjunction(c1, c2).toNNF();		
	}

    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
        Formula c1 = new Disjunction(new Negation(children[0]), children[1]);
        Formula c2 = new Disjunction(children[0], new Negation(children[1]));
        return (new Conjunction(c1, c2)).simplify(evidence);
    }
}
