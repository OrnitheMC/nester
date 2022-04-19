package net.ornithemc.nester.jar.node.proto;

import net.ornithemc.nester.jar.SourceJar;
import net.ornithemc.nester.jar.node.MethodNode;

public class ProtoMethodNode extends ProtoNode {

	private final String desc;
	private final String[] exceptions;

	public ProtoMethodNode(SourceJar jar, int access, String name, String desc, String signature, String[] exceptions) {
		super(jar, access, name, signature);

		this.desc = desc;
		this.exceptions = exceptions;
	}

	@Override
	public boolean isMethod() {
		return true;
	}

	@Override
	public ProtoMethodNode asMethod() {
		return this;
	}

	@Override
	public MethodNode node() {
		return super.node().asMethod();
	}

	@Override
	protected MethodNode construct() {
		return new MethodNode(this);
	}

	public String getDescriptor() {
		return desc;
	}

	public String[] getExceptions() {
		return exceptions;
	}
}
