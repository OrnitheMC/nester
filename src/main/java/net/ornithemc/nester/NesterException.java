package net.ornithemc.nester;

public class NesterException extends RuntimeException {

	private static final long serialVersionUID = -8017397677712346879L;

	public NesterException() {
		super();
	}

	public NesterException(String message) {
		super(message);
	}

	public NesterException(String message, Throwable cause) {
		super(message, cause);
	}
}
