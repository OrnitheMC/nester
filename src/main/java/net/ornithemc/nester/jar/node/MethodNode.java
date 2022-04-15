package net.ornithemc.nester.jar.node;

import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;

public class MethodNode extends Node {

	private final boolean isInstanceConstructor;
	private final boolean isClassConstructor;

	public MethodNode(ProtoMethodNode proto) {
		super(proto);

		String name = getName();

		this.isInstanceConstructor = name.equals("<init>");
		this.isClassConstructor = name.equals("<clinit>");
	}

	@Override
	public ProtoMethodNode proto() {
		return proto.asMethod();
	}

	@Override
	public boolean isMethod() {
		return true;
	}

	@Override
	public MethodNode asMethod() {
		return this;
	}

	@Override
	public ClassNode getParent() {
		return super.getParent().asClass();
	}

	@Override
	public boolean isValidChild(Node node) {
		return super.isValidChild(node) && node.isClass() && node.asClass().isNestable();
	}

	public boolean isInstanceConstructor() {
		return isInstanceConstructor;
	}

	public boolean isClassConstructor() {
		return isClassConstructor;
	}
}
