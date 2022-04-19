package net.ornithemc.nester.jar.node.proto;

import org.objectweb.asm.Type;

import net.ornithemc.nester.jar.SourceJar;
import net.ornithemc.nester.jar.node.ClassNode;
import net.ornithemc.nester.jar.node.FieldNode;

public class ProtoFieldNode extends ProtoNode {

	private final String desc;
	private final Object value;

	public ProtoFieldNode(SourceJar jar, int access, String name, String desc, String signature, Object value) {
		super(jar, access, name, signature);

		this.desc = desc;
		this.value = value;
	}

	@Override
	public boolean isField() {
		return true;
	}

	@Override
	public ProtoFieldNode asField() {
		return this;
	}

	@Override
	public FieldNode node() {
		return super.node().asField();
	}

	@Override
	protected FieldNode construct() {
		return new FieldNode(this);
	}

	public String getDescriptor() {
		return desc;
	}

	public Object getValue() {
		return value;
	}

	public ClassNode findType() {
		Type typeDesc = Type.getType(desc);
		int sort = typeDesc.getSort();

		ClassNode type = null;

		if (sort == Type.OBJECT) {
			String className = typeDesc.getInternalName();
			type = jar.getClass(className);
		}
		if (type == null) {
			type = new ProtoClassNode(jar, 0, 0, desc, null, null, null).node();
		}

		return type;
	}
}
