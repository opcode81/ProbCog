package probcog.logic;

import java.util.Map;

import probcog.srl.GenericDatabase;
import probcog.srl.RelationalModel;


public class Literal extends UngroundedFormula {
	public boolean isPositive;
	public Atom atom;
	
	public Literal(boolean isPositive, Atom atom) {
		this.atom = atom;
		this.isPositive = isPositive;
	}
	
	public String toString() {
		return isPositive ? atom.toString() : "!" + atom;
	}

	@Override
	public void getVariables(GenericDatabase<?, ?> db, Map<String, String> ret) throws Exception {
		atom.getVariables(db, ret);	
	}
	
	@Override
	public void addConstantsToModel(RelationalModel m) throws Exception {
		atom.addConstantsToModel(m);
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, GenericDatabase<?, ?> db) throws Exception {
		return new GroundLiteral(isPositive, (GroundAtom)atom.ground(binding, vars, db));
	}

	@Override
	public Formula toCNF() {
		return this;
	}
	
	@Override
	public Formula toNNF() {
		return this;
	}
}
