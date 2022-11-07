package net.ornithemc.nester.jar;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.ornithemc.nester.nest.NestType;

public class ClassNest {

	public final NestType type;

	public final ClassNode clazz;
	public final ClassNode enclClass;
	public final MethodNode enclMethod;

	public final String innerName;
	public final int innerAccess;

	public ClassNest(NestType type, ClassNode clazz, ClassNode enclClass, MethodNode enclMethod, String innerName, int innerAccess) {
		this.type = type;

		this.clazz = clazz;
		this.enclClass = enclClass;
		this.enclMethod = enclMethod;

		this.innerName = innerName;
		this.innerAccess = innerAccess;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ClassNest)) {
			return false;
		}

		return clazz.equals(((ClassNest)obj).clazz);
	}

	public boolean isAnonymous() {
		return type == NestType.ANONYMOUS;
	}

	public boolean isInner() {
		return type == NestType.INNER;
	}

	public boolean isLocal() {
		return type == NestType.LOCAL;
	}
}
