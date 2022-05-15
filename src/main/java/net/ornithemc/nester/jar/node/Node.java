package net.ornithemc.nester.jar.node;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
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

	public boolean isTopLevel() {
		return parent == null;
	}

	public boolean hasParent() {
		return parent != null;
	}

	public Node getParent() {
		return parent;
	}

	public boolean setParent(Node node) {
		return isValidParent(node) && setParentNode(node);
	}

	protected boolean setParentNode(Node node) {
		if (parent == node) {
			return false;
		}

		if (parent != null) {
			parent.removeChildNode(this);
		}

		parent = node;

		if (parent != null) {
			parent.addChildNode(this);
		}

		return true;
	}

	public boolean isValidParent(Node node) {
		return node == null ? !mustHaveParent() : node.isValidChild(this);
	}

	public boolean mustHaveParent() {
		return true;
	}

	public Node getClosestCommonAncestor(Node node) {
		for (Node p = this; p != null; p = p.parent) {
			if (p == node || p.encloses(node)) {
				return p;
			}
		}

		return null;
	}

	public ClassNode getClosestCommonParentClass(Node node) {
		for (Node p = getClosestCommonAncestor(node); p != null; p = p.parent) {
			if (p.isClass()) {
				return p.asClass();
			}
		}

		return null;
	}

	public static Node getClosestCommonAncestor(Collection<Node> nodes) {
		if (nodes.isEmpty()) {
			return null;
		}

		Iterator<Node> it = nodes.iterator();
		Node ancestor = it.next();

		while (it.hasNext() && ancestor != null) {
			ancestor = it.next().getClosestCommonAncestor(ancestor);
		}

		return ancestor;
	}

	public static ClassNode getClosestCommonParentClass(Collection<Node> nodes) {
		for (Node p = getClosestCommonAncestor(nodes); p != null; p = p.parent) {
			if (p.isClass()) {
				return p.asClass();
			}
		}

		return null;
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

	public boolean isValidChild(Node node) {
		return getClosestCommonAncestor(node) == null;
	}

	public boolean encloses(Node node) {
		for (Node p = node.parent; p != null; p = p.parent) {
			if (this == p) {
				return true;
			}
		}

		return false;
	}

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

	public void enableAccess(int... opcodes) {
		for (int opcode : opcodes) {
			setAccess(access | opcode);
		}
	}

	public void disableAccess(int... opcodes) {
		for (int opcode : opcodes) {
			setAccess(access & ~opcode);
		}
	}

	public void setAccess(int access) {
		this.access = access;
	}

	public String getName() {
		return name;
	}

	public boolean setName(String name) {
		return canRename() && isValidName(name) && setNodeName(name);
	}

	protected boolean setNodeName(String name) {
		boolean changed = !this.name.equals(name);

		if (changed) {
			this.name = name;
		}

		return changed;
	}

	public boolean canRename() {
		return false;
	}

	public boolean isValidName(String name) {
		return true; // TODO: test for keywords and invalid characters
	}

	public Comparator<Node> getNameComparator() {
		return (n1, n2) -> {
			String name1 = n1.name;
			String name2 = n2.name;

			int l1 = name1.length();
			int l2 = name2.length();

			if (l1 == l2) {
				return name1.compareTo(name2);
			}

			return l1 - l2;
		};
	}
}
