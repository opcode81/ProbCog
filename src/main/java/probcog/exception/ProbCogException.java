package probcog.exception;

public class ProbCogException extends Exception {
	private static final long serialVersionUID = 1L;

	public ProbCogException(String message) {
		super(message);
	}

	public ProbCogException(Throwable e) {
		super(e);
	}

	public ProbCogException(String message, Throwable e) {
		super(message, e);
	}
}
