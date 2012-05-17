package probcog.srl;

// TODO doesn't consider Prolog
public class SoftDatabase extends GenericDatabase<SoftVariable, ValueDistribution> {

	public SoftDatabase(RelationalModel model) throws Exception {
		super(model);
	}

	@Override
	public ValueDistribution getVariableValue(String varName, boolean closedWorld) throws Exception {
		return this.getVariable(varName).value;
	}

	@Override
	public void fillDomain(String domName, SoftVariable var) throws Exception {
		for(String v : var.value.getDomainElements()) {
			fillDomain(domName, v);
		}
	}

	@Override
	protected SoftVariable makeVar(String functionName, String[] args, String value) {
		ValueDistribution vd = new ValueDistribution();
		vd.setValue(value, 1.0);
		return new SoftVariable(functionName, args, vd);
	}

	@Override
	protected SoftVariable readEntry(String line) throws Exception {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getSingleVariableValue(String varName, boolean closedWorld) throws Exception {
		ValueDistribution vd = getVariableValue(varName, false);
		return vd.getSingleValue();
	}
}
