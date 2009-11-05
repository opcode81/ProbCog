package edu.tum.cs.logic;

import java.util.Collection;
import java.util.Map;

import edu.tum.cs.srl.Database;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.util.StringTool;

public class Atom extends UngroundedFormula {

	public Collection<String> params;
	public String predName;

	public Atom(String predName, Collection<String> params) {
		this.predName = predName;
		this.params = params;
	}

	@Override
	public String toString() {
		return predName + "(" + StringTool.join(",", params) + ")";
	}

	@Override
	public void getVariables(Database db, Map<String, String> ret) throws Exception {
        Signature sig = db.getSignature(predName);
        if(sig == null)
        	throw new Exception("Unknown predicate '" + predName + "'");
		int i = 0;
		for(String param : params) {
			if(isVariable(param)) {
				String type;
				if(i < sig.argTypes.length)
					type = sig.argTypes[i];
				else
					type = sig.returnType;
				String oldval = ret.put(param, type);
				if(oldval != null && !type.equals(oldval))
					throw new Exception("The variable " + param + " is bound to more than one domain: " + oldval + " and " + type);
			}
			++i;
		}
	}

	public static boolean isVariable(String paramName) {
		return Character.isLowerCase(paramName.charAt(0));
	}

	@Override
	public Formula ground(Map<String, String> binding, WorldVariables vars, Database db) throws Exception {
		StringBuffer sb = new StringBuffer(predName + "(");
		int i = 0;
		for(String param : params) {
			if(i++ > 0)
				sb.append(',');
			String value = binding.get(param);
			if(value == null) { // if the binding contains no value for a parameter, it must be a constant
				if(isVariable(param))
					throw new Exception("Cannot ground " + toString() + " with binding "  + binding + " - variable " + param + " unbound.");
				value = param;
			}
			sb.append(value);
		}
		sb.append(')');
		String strGA = sb.toString();
		GroundAtom ga = vars.get(strGA);
		if(ga == null)
			throw new Exception("Could not find ground atom '" + strGA + "' in set of world variables " + vars);
		return ga;
	}

	@Override
	public Formula toCNF() {
		return this;
	}
}
