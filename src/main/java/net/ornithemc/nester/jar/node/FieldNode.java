package net.ornithemc.nester.jar.node;

import net.ornithemc.nester.jar.node.proto.ProtoFieldNode;

public class FieldNode extends Node {

	public FieldNode(ProtoFieldNode proto) {
		super(proto);
	}

	@Override
	public ProtoFieldNode proto() {
		return proto.asField();
	}

	@Override
	public boolean isField() {
		return true;
	}

	@Override
	public FieldNode asField() {
		return this;
	}

	@Override
	public ClassNode getParent() {
		return super.getParent().asClass();
	}

	@Override
	public boolean isValidChild(Node node) {
		return false;
	}
}
