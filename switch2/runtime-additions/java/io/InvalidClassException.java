package java.io;

/** warsmash-nx: missing from Clearwing 3.1.2. As the JDK, the class name prefixes the message. */
public class InvalidClassException extends ObjectStreamException {
	public String classname;

	public InvalidClassException(String reason) {
		super(reason);
	}

	public InvalidClassException(String cname, String reason) {
		super(reason);
		classname = cname;
	}

	@Override
	public String getMessage() {
		if (classname == null)
			return super.getMessage();
		else
			return classname + "; " + super.getMessage();
	}
}
