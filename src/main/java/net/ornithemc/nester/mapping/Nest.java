package net.ornithemc.nester.mapping;

import net.ornithemc.nester.jar.node.ClassNode;
import net.ornithemc.nester.jar.node.MethodNode;
import net.ornithemc.nester.jar.node.proto.ProtoClassNode;
import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;

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

	static Nest of(NestType type, ClassNode clazz, ClassNode enclClass, MethodNode enclMethod) {
		ProtoClassNode protoClass = clazz.proto();
		ProtoClassNode protoEnclClass = enclClass.proto();

		String className = protoClass.getName();
		String enclClassName = protoEnclClass.getName();
		String enclMethodName = null;
		String enclMethodDesc = null;
		String innerName = null;
		int access = clazz.getAccess();

		if (type == NestType.ANONYMOUS && enclMethod != null) {
			ProtoMethodNode protoMethod = enclMethod.proto();

			enclMethodName = protoMethod.getName();
			enclMethodDesc = protoMethod.getDescriptor();
		}
		if (type == NestType.INNER) {
			innerName = clazz.getSimpleName();
		}

		return new Nest(type, className, enclClassName, enclMethodName, enclMethodDesc, innerName, access);
	}

	public static Nest anonymous(ClassNode clazz, ClassNode enclClass, MethodNode enclMethod) {
		return of(NestType.ANONYMOUS, clazz, enclClass, enclMethod);
	}

	public static Nest inner(ClassNode clazz, ClassNode enclClass) {
		return of(NestType.INNER, clazz, enclClass, null);
	}
}
