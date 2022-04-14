package net.ornithemc.nester.jar.node;

import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;

public class MethodNode extends Node {

	public MethodNode(ProtoMethodNode proto) {
		super(proto);
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
		return node.isClass() && node.asClass().isNestable();
	}
}
