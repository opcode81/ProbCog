package edu.tum.cs.logic;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.tum.cs.srl.bayesnets.Database;
import edu.tum.cs.tools.StringTool;

public class GroundAtom extends Formula {
	public String predicate;
	public String[] args;
	public int index;

	public GroundAtom(String predicate, String[] args) {
		this.predicate = predicate;
		this.args = args;
		index = -1;
	}

	public GroundAtom(String gndAtom) {
		Pattern p = Pattern.compile("(\\w+)\\(([^\\)]+)\\)");
		Matcher m = p.matcher(gndAtom);
		if(!m.matches()) {
			throw new RuntimeException("Could not parse ground atom '" + gndAtom + "'");
		}
		predicate = m.group(1);
		args = m.group(2).split("\\s*,\\s*");
		index = -1;
	}

	public boolean isTrue(IPossibleWorld w) {
		return w.isTrue(this);
	}

	public void setIndex(int i) {
		index = i;
	}

	@Override
	public void getVariables(Database db, Map<String, String> ret) {
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, Database db) {
		return this;
	}

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
		ret.add(this);
	}

	@Override
	public String toString() {
		return predicate + "(" + StringTool.join(",", args) + ")";
	}



	@Override
	public Formula toCNF() {
		return this;
	}

	@Override
	public boolean equals(Object other) {
		return this.index == ((GroundAtom)other).index;
	}

	@Override
	public int hashCode() {
		return this.index;
	}

    /**
     * this method checks whether an evidence is given for the current groundatom
     * @param evidence evidence of current scenario
     * @return returns an instance of TrueFalse by given evidence or this groundatom
     */
    @Override
    public Formula simplify(Database evidence) {
        try {
            // check whether evidence contains this groundatom and return instance of TrueFalse
            if (evidence.contains(this.toString())) {
                if (evidence.getVariableValue(this.toString(), false).equals("True"))
                    return TrueFalse.TRUE;
                else if (evidence.getVariableValue(this.toString(), false).equals("False"))
                    return TrueFalse.FALSE;
            }
        } catch (Exception ex) {
            System.out.println("Groundatom could not be simplified! " + this.toString());
        }
        // if evidence dosn't contains this atom, return atom
        return this;
    }
}
