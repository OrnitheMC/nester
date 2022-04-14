package net.ornithemc.nester.jar.node;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import net.ornithemc.nester.jar.node.proto.ProtoNode;

public abstract class Node {

	protected final ProtoNode proto;

	private Node parent;
	private Set<Node> children;

	private int access;
	private String name;

	protected Node(ProtoNode proto) {
		this.proto = proto;

		this.children = new LinkedHashSet<>();

		this.access = this.proto.getAccess();
		this.name = this.proto.getName();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && obj instanceof Node && proto.equals(((Node)obj).proto);
	}

	public ProtoNode proto() {
		return proto;
	}

	public boolean isClass() {
		return false;
	}

	public ClassNode asClass() {
		throw new UnsupportedOperationException();
	}

	public boolean isField() {
		return false;
	}

	public FieldNode asField() {
		throw new UnsupportedOperationException();
	}

	public boolean isMethod() {
		return false;
	}

	public MethodNode asMethod() {
		throw new UnsupportedOperationException();
	}

	public Node getParent() {
		return parent;
	}

	public boolean setParent(Node node) {
		if (isValidParent(node)) {
			if (parent != null) {
				parent.removeChildNode(this);
			}

			parent = node;

			if (parent != null) {
				parent.addChildNode(this);
			}

			return true;
		}

		return false;
	}

	public boolean isValidParent(Node node) {
		return node == null ? !mustHaveParent() : node.isValidChild(this);
	}

	public boolean mustHaveParent() {
		return true;
	}

	public Set<Node> getChildren() {
		return Collections.unmodifiableSet(children);
	}

	protected boolean addChildNode(Node node) {
		return children.add(node);
	}

	protected boolean removeChildNode(Node node) {
		return children.remove(node);
	}

	public abstract boolean isValidChild(Node node);

	public int getAccess() {
		return access;
	}

	public boolean isPackagePrivate() {
		return !isPublic() && !isPrivate() && !isProtected();
	}

	public boolean isPublic() {
		return (access & Opcodes.ACC_PUBLIC) != 0;
	}

	public boolean isPrivate() {
		return (access & Opcodes.ACC_PRIVATE) != 0;
	}

	public boolean isProtected() {
		return (access & Opcodes.ACC_PROTECTED) != 0;
	}

	public boolean isStatic() {
		return (access & Opcodes.ACC_STATIC) != 0;
	}

	public boolean isFinal() {
		return (access & Opcodes.ACC_FINAL) != 0;
	}

	public boolean isSuper() {
		return (access & Opcodes.ACC_SUPER) != 0;
	}

	public boolean isSynchronized() {
		return (access & Opcodes.ACC_SYNCHRONIZED) != 0;
	}

	public boolean isOpen() {
		return (access & Opcodes.ACC_OPEN) != 0;
	}

	public boolean isTransitive() {
		return (access & Opcodes.ACC_TRANSITIVE) != 0;
	}

	public boolean isVolatile() {
		return (access & Opcodes.ACC_VOLATILE) != 0;
	}

	public boolean isBridge() {
		return (access & Opcodes.ACC_BRIDGE) != 0;
	}

	public boolean isStaticPhase() {
		return (access & Opcodes.ACC_STATIC_PHASE) != 0;
	}

	public boolean isVarargs() {
		return (access & Opcodes.ACC_VARARGS) != 0;
	}

	public boolean isTransient() {
		return (access & Opcodes.ACC_TRANSIENT) != 0;
	}

	public boolean isNative() {
		return (access & Opcodes.ACC_NATIVE) != 0;
	}

	public boolean isInterface() {
		return (access & Opcodes.ACC_INTERFACE) != 0;
	}

	public boolean isAbstract() {
		return (access & Opcodes.ACC_ABSTRACT) != 0;
	}

	public boolean isStrict() {
		return (access & Opcodes.ACC_STRICT) != 0;
	}

	public boolean isSynthetic() {
		return (access & Opcodes.ACC_SYNTHETIC) != 0;
	}

	public boolean isAnnotation() {
		return (access & Opcodes.ACC_ANNOTATION) != 0;
	}

	public boolean isEnum() {
		return (access & Opcodes.ACC_ENUM) != 0;
	}

	public boolean isMandated() {
		return (access & Opcodes.ACC_MANDATED) != 0;
	}

	public boolean isModule() {
		return (access & Opcodes.ACC_MODULE) != 0;
	}

	public void setPackagePrivate() {
		disableAccess(Opcodes.ACC_PUBLIC, Opcodes.ACC_PRIVATE, Opcodes.ACC_PROTECTED);
	}

	public void setPublic() {
		enableAccess(Opcodes.ACC_PUBLIC);
		disableAccess(Opcodes.ACC_PRIVATE, Opcodes.ACC_PROTECTED);
	}

	public void setPrivate() {
		enableAccess(Opcodes.ACC_PRIVATE);
		disableAccess(Opcodes.ACC_PUBLIC, Opcodes.ACC_PROTECTED);
	}

	public void setProtected() {
		enableAccess(Opcodes.ACC_PROTECTED);
		disableAccess(Opcodes.ACC_PUBLIC, Opcodes.ACC_PRIVATE);
	}

	private void enableAccess(int... opcodes) {
		for (int opcode : opcodes) {
			access |= opcode;
		}
	}

	private void disableAccess(int... opcodes) {
		for (int opcode : opcodes) {
			access &= ~opcode;
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (isValidName(name)) {
			this.name = name;
		}
	}

	protected boolean isValidName(String name) {
		return true; // TODO: test for keywords and invalid characters
	}
}
