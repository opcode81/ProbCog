package probcog.logic;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.srl.GenericDatabase;

import edu.tum.cs.util.StringTool;

public class GroundAtom extends GroundedFormula {
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
	public Formula toNNF() {		
		return this;
	}

	@Override
	public boolean equals(Object other) {
		int otherIdx = ((GroundAtom)other).index;
		if(this.index == -1 || otherIdx == -1)
			throw new RuntimeException("Cannot compare GroundAtoms that are not yet part of a WorldVariables collection.");
		return this.index == otherIdx;
	}

	@Override
	public int hashCode() {
		if(this.index == -1)
			throw new RuntimeException("Tried to compute hash code of GroundAtom '" + this.toString() + "' that was not yet added to a collection of world variables.");
		return this.index;
	}

    /**
     * simplifies the formula based on the evidence
     * @param evidence evidence of current scenario (or null)
     * @return returns an instance of TrueFalse if the value of the ground atom is contained in the evidence (or evidence is null); otherwise returns this very ground atom 
     */
    @Override
    public Formula simplify(GenericDatabase<?, ?> evidence) {
        try {
            // check whether evidence contains this ground atom and return instance of TrueFalse
        	if(evidence != null) {
	        	String value = evidence.getSingleVariableValue(this.toString(), false);
	            if(value != null) {            	 
	                if(value.equals("True"))
	                    return TrueFalse.TRUE;
	                else if (value.equals("False"))
	                    return TrueFalse.FALSE;
	                else
	                	throw new RuntimeException("Database contains invalid boolean value '" + value + "' for atom " + this.toString());
	            }
        	}
            // if evidence dosn't contain this atom, there's no change
            return this;
        } 
        catch(Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
