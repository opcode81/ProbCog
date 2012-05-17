package probcog.srldb.datadict.domain;

public class BooleanDomain extends OrderedStringDomain {
	private static final long serialVersionUID = 1L;
	protected static BooleanDomain singleton = null;
	
	public static BooleanDomain getInstance() {
		if(singleton == null)
			singleton = new BooleanDomain();
		return singleton;
	}
	
	protected BooleanDomain() {
		super("bool", new String[]{"true", "false"});		
	}
	
	public boolean isTrue(String value) {
		return value.equalsIgnoreCase(this.values[0]);
	}
	
	public boolean isFalse(String value) {
		return !isTrue(value);
	}
	
	public boolean containsString(String s) {
		return containsIgnoreCase(s);
	}
}
