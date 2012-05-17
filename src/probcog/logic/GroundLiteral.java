package probcog.logic;

import java.util.Set;

import probcog.srl.GenericDatabase;


public class GroundLiteral extends GroundedFormula {
	public boolean isPositive;
	public GroundAtom gndAtom;
	
	public GroundLiteral(boolean isPositive, GroundAtom gndAtom) {
		this.gndAtom = gndAtom;
		this.isPositive = isPositive;
	}
	
	public boolean isTrue(IPossibleWorld w) {
		boolean v = w.isTrue(gndAtom);
		return isPositive ? v : !v;
	}

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {   
        ret.add(gndAtom);              
	}
	
	@Override
	public String toString() {
		return isPositive ? gndAtom.toString() : "!" + gndAtom.toString();
	}

	@Override
	public Formula toCNF() {		
		return this;
	}

	@Override
	public Formula toNNF() {		
		return this;
	}
	
	public void negate() {
		isPositive = !isPositive;
	}

    /**
     * this method checks whether an evidence is given for the current groundliteral
     * @param evidence evidence of current scenario
     * @return returns an instance of TrueFalse by given evidence or this groundatom
     */
    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
        Formula f = this.gndAtom.simplify(evidence);
        if(f instanceof TrueFalse) {
        	if(isPositive)
        		return f;
        	else
        		return ((TrueFalse)f).opposite();       	
        }
        return this;
    }
}