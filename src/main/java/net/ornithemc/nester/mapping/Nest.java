package net.ornithemc.nester.mapping;

public class Nest {

	public final NestType type;

	public final String className;
	public final String enclClassName;
	public final String enclMethodName;
	public final String enclMethodDesc;
	public final String innerName;
	public final int access;

	Nest(NestType type, String className, String enclClassName, String enclMethodName, String enclMethodDesc, String innerName, int access) {
		this.type = type;

		this.className = className;
		this.enclClassName = enclClassName;
		this.enclMethodName = enclMethodName;
		this.enclMethodDesc = enclMethodDesc;
		this.innerName = innerName;
		this.access = access;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Nest)) {
			return false;
		}

		return className.equals(((Nest)obj).className);
	}

	public boolean isAnonymous() {
		return type == NestType.ANONYMOUS;
	}

	public boolean isInner() {
		return type == NestType.INNER;
	}
}
