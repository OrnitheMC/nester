package net.ornithemc.nester.jar.node.proto;

import net.ornithemc.nester.jar.SourceJar;
import net.ornithemc.nester.jar.node.ClassNode;

public class ProtoClassNode extends ProtoNode {

	private final int version;
	private final String superName;
	private final String[] interfaces;

	public ProtoClassNode(SourceJar jar, int version, int access, String name, String signature, String superName, String[] interfaces) {
		super(jar, access, name, signature);

		this.version = version;
		this.superName = superName;
		this.interfaces = interfaces;
	}

	@Override
	public boolean isClass() {
		return true;
	}

	@Override
	public ProtoClassNode asClass() {
		return this;
	}

	@Override
	public ClassNode node() {
		return super.node().asClass();
	}

	@Override
	protected ClassNode construct() {
		if (superName == null) {
			return new ClassNode(this, null, null);
		}

		String[] interfaceNames = interfaces;
		int interfaceCount = (interfaceNames == null) ? 0 : interfaceNames.length;

		ClassNode superClass = getClass(superName);
		ClassNode[] interfaces = new ClassNode[interfaceCount];

		for (int i = 0; i < interfaceCount; i++) {
			interfaces[i] = getClass(interfaceNames[i]);
		}

		return new ClassNode(this, superClass, interfaces);
	}

	public int getVersion() {
		return version;
	}

	public String getSuperName() {
		return superName;
	}

	public String[] getInterfaces() {
		return interfaces;
	}

	private ClassNode getClass(String name) {
		ProtoClassNode protoClass = jar.getProtoClass(name);

		if (protoClass == null) {
			protoClass = new ProtoClassNode(jar, 0, 0, name, null, null, null);
		}

		return protoClass.node();
	}
}
