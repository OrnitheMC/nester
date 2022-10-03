package net.ornithemc.nester.jar.node.proto;

import java.util.Objects;

import net.ornithemc.nester.jar.SourceJar;
import net.ornithemc.nester.jar.node.Node;

public abstract class ProtoNode {

	protected final SourceJar jar;

	protected final int access;
	protected final String name;
	protected final String signature;

	private String enclosingClassName;
	private int innerAccess;

	private Node node;

	protected ProtoNode(SourceJar jar, int access, String name, String signature) {
		this.jar = jar;

		this.access = access;
		this.name = name;
		this.signature = signature;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof ProtoNode) {
			ProtoNode node = (ProtoNode) obj;
			return Objects.equals(enclosingClassName, node.enclosingClassName) && Objects.equals(name, node.name) && Objects.equals(signature, node.signature);
		}

		return false;
	}

	public boolean isClass() {
		return false;
	}

	public ProtoClassNode asClass() {
		throw new UnsupportedOperationException();
	}

	public boolean isField() {
		return false;
	}

	public ProtoFieldNode asField() {
		throw new UnsupportedOperationException();
	}

	public boolean isMethod() {
		return false;
	}

	public ProtoMethodNode asMethod() {
		throw new UnsupportedOperationException();
	}

	public void setEnclosingClass(String name) {
		setEnclosingClass(name, 0);
	}

	public void setEnclosingClass(String name, int access) {
		enclosingClassName = name;
		innerAccess = access;
	}

	public int getAccess() {
		return access;
	}

	public String getName() {
		return name;
	}

	public String getSignature() {
		return signature;
	}

	public Node node() {
		if (node == null) {
			node = construct();

			if (innerAccess != 0 && node.isClass()) {
				node.asClass().setInnerAccess(innerAccess);
			}
			if (enclosingClassName != null) {
				ProtoClassNode clazz = jar.getProtoClass(enclosingClassName);

				if (clazz != null) {
					node.setParent(clazz.node());
				}
			}
		}

		return node;
	}

	protected abstract Node construct();

}
