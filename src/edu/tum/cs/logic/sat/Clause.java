package edu.tum.cs.logic.sat;

import edu.tum.cs.logic.ComplexFormula;
import edu.tum.cs.logic.Disjunction;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundLiteral;
import edu.tum.cs.logic.IPossibleWorld;
import edu.tum.cs.srl.Database;
import edu.tum.cs.tools.StringTool;

public class Clause extends ComplexFormula {
	
	public GroundLiteral[] lits;
	
	public Clause(Formula f) throws Exception {
		//System.out.println("generating clause from " + f.toString());
		if(f instanceof GroundLiteral) {
			lits = new GroundLiteral[1];
			lits[0] = (GroundLiteral)f;
		}
		else if(f instanceof Disjunction) {
			Disjunction d = (Disjunction)f;
			lits = new GroundLiteral[d.children.length];
			for(int i = 0; i < lits.length; i++) {
				if(d.children[i] instanceof GroundLiteral)
					lits[i] = (GroundLiteral)d.children[i];
				else
					throw new Exception("Disjunction contains child of unacceptable type " + d.children[i].getClass().getSimpleName() + "; only GroundLiterals allowed.");
			}
		}
		else
			throw new Exception("Instance of type " + f.getClass().getSimpleName() + " cannot be treated as a clause");
	}

	@Override
	public boolean isTrue(IPossibleWorld w) {
		for(GroundLiteral lit : lits) 
			if(lit.isTrue(w))
				return true;
		return false;		
	}

	@Override
	public Formula toCNF() {
		return this;
	}
	
	public String toString() {
		return StringTool.join(" v ", this.lits);
	}

    @Override
    public Formula simplify(Database evidence) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
