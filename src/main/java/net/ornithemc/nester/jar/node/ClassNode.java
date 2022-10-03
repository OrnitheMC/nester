package net.ornithemc.nester.jar.node;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.ornithemc.nester.jar.node.proto.ProtoClassNode;
import net.ornithemc.nester.jar.node.proto.ProtoFieldNode;
import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;

public class ClassNode extends Node {

	private final ClassNode superClass;
	private final ClassNode[] interfaces;

	private final Map<String, FieldNode> fields;
	private final Map<String, FieldNode> syntheticFields;
	private final Map<String, MethodNode> methods;
	private final Map<String, MethodNode> declaredMethods;
	private final Map<String, MethodNode> syntheticMethods;
	private final Map<String, MethodNode> constructors;

	private final Set<ClassNode> innerClasses;
	private final Set<ClassNode> anonymousClasses;

	private boolean hasSyntheticFields;
	private boolean hasSyntheticMethods;
	private boolean hasStaticMembers;

	private boolean canBeAnonymous;

	// used by nested classes
	private String innerName;
	private int innerAccess;

	public ClassNode(ProtoClassNode proto, ClassNode superClass, ClassNode[] interfaces) {
		super(proto);
	
		this.superClass = superClass;
		this.interfaces = interfaces;

		this.fields = new HashMap<>();
		this.syntheticFields = new HashMap<>();
		this.methods = new HashMap<>();
		this.declaredMethods = new HashMap<>();
		this.syntheticMethods = new HashMap<>();
		this.constructors = new HashMap<>();

		this.innerClasses = new TreeSet<>(ClassNode::compareByName);
		this.anonymousClasses = new TreeSet<>(ClassNode::compareByName);

		this.canBeAnonymous = true;

		if (this.superClass != null) {
			this.superClass.markNotAnonymous();
		}
	}

	public static int compareByName(ClassNode c1, ClassNode c2) {
		return compareByName(c1.proto().getName(), c2.proto().getName());
	}

	public static int compareByName(String name1, String name2) {
		int l1 = name1.length();
		int l2 = name2.length();

		if (l1 == l2) {
			return name1.compareTo(name2);
		}

		return l1 - l2;
	}

	@Override
	public ProtoClassNode proto() {
		return proto.asClass();
	}

	@Override
	public boolean isClass() {
		return true;
	}

	@Override
	public ClassNode asClass() {
		return this;
	}

	@Override
	public boolean isTopLevel() {
		return super.isTopLevel() && isNestable();
	}

	@Override
	public boolean mustHaveParent() {
		return false;
	}

	@Override
	protected boolean addChildNode(Node node) {
		if (super.addChildNode(node)) {
			if (node.isClass()) {
				ClassNode clazz = node.asClass();
				ProtoClassNode protoClass = clazz.proto();

				clazz.innerName = protoClass.getName();
				clazz.innerAccess = protoClass.getAccess();
			}
			if (node.isField()) {
				FieldNode field = node.asField();
				ProtoFieldNode protoField = field.proto();
				String name = protoField.getName();

				fields.put(name, field);

				if (field.isSynthetic()) {
					syntheticFields.put(name, field);
					hasSyntheticFields = true;
				}
			}
			if (node.isMethod()) {
				MethodNode method = node.asMethod();
				ProtoMethodNode protoMethod = method.proto();
				String fullName = protoMethod.getName() + protoMethod.getDescriptor();

				methods.put(fullName, method);

				if (!method.isClassConstructor() && !method.isInstanceConstructor() && !method.isSynthetic()) {
					declaredMethods.put(fullName, method);
				}
				if (method.isSynthetic() && !method.isBridge()) {
					syntheticMethods.put(fullName, method);
					hasSyntheticMethods = true;
				}
				if (method.isInstanceConstructor() && !method.isSynthetic()) {
					constructors.put(fullName, method);
				}
			}

			if (node.isStatic() && (!node.isField() || !node.isFinal())) {
				hasStaticMembers = true;
			}

			return true;
		}

		return false;
	}

	@Override
	public boolean isValidChild(Node node) {
		return super.isValidChild(node) && (!node.isClass() || node.asClass().isNestable());
	}

	@Override
	public boolean canRename() {
		return superClass != null;
	}

	@Override
	protected boolean setNodeName(String name) {
		if (super.setNodeName(name)) {
			fixNestedClassNames();

			return true;
		}

		return false;
	}

	private void fixNestedClassNames() {
		String base = getName() + "$";

		for (ClassNode clazz : innerClasses) {
			clazz.setName(base + clazz.innerName);
		}
		for (ClassNode clazz : anonymousClasses) {
			clazz.setName(base + clazz.innerName);
		}
	}

	public boolean isNestable() {
		return canRename() && !isNested();
	}

	public boolean isNested() {
		return hasParent();
	}

	public ClassNode getEnclosingClass() {
		Node p = getParent();

		if (p != null) {
			if (p.isClass()) {
				return p.asClass();
			}
			if (p.isMethod()) {
				return p.getParent().asClass();
			}
		}

		return null;
	}

	public ClassNode getSuperClass() {
		return superClass;
	}

	public ClassNode[] getInterfaces() {
		return interfaces;
	}

	public Collection<FieldNode> getFields() {
		return Collections.unmodifiableCollection(fields.values());
	}

	public Collection<FieldNode> getSyntheticFields() {
		return Collections.unmodifiableCollection(syntheticFields.values());
	}

	/**
	 * Retrieve a field node from the name of its proto field
	 */
	public FieldNode getField(String name) {
		return fields.get(name);
	}

	public Collection<MethodNode> getMethods() {
		return Collections.unmodifiableCollection(methods.values());
	}

	public Collection<MethodNode> getDeclaredMethods() {
		return Collections.unmodifiableCollection(declaredMethods.values());
	}

	public Collection<MethodNode> getSyntheticMethods() {
		return Collections.unmodifiableCollection(syntheticMethods.values());
	}

	public Collection<MethodNode> getConstructors() {
		return Collections.unmodifiableCollection(constructors.values());
	}

	/**
	 * Retrieve a method node from the name and descriptor of its proto method
	 */
	public MethodNode getMethod(String name, String desc) {
		return methods.get(name + desc);
	}

	public boolean hasSyntheticFields() {
		return hasSyntheticFields;
	}

	public boolean hasSyntheticMethods() {
		return hasSyntheticMethods;
	}

	public boolean hasSyntheticMembers() {
		return hasSyntheticFields || hasSyntheticMethods;
	}

	public boolean hasStaticMembers() {
		return hasStaticMembers;
	}

	public boolean canBeAnonymous() {
		return canBeAnonymous && !hasStaticMembers;
	}

	public void markNotAnonymous() {
		canBeAnonymous = false;
	}

	public boolean canBeInner() {
		return !isStatic() || !hasStaticMembers;
	}

	public boolean addInnerClass(ClassNode clazz) {
		if (!clazz.setParent(this)) {
			return false;
		}
		if (innerClasses.contains(clazz)) {
			return false;
		}

		innerClasses.add(clazz);
		fixNestedClassNames();

		return true;
	}

	public Collection<ClassNode> getInnerClasses() {
		return Collections.unmodifiableCollection(innerClasses);
	}

	public boolean addAnonymousClass(MethodNode enclMethod, ClassNode clazz) {
		Node parent = (enclMethod == null) ? this : enclMethod;

		if (!clazz.setParent(parent)) {
			return false;
		}
		if (anonymousClasses.contains(clazz)) {
			return false;
		}

		anonymousClasses.add(clazz);
		fixNestedClassNames();

		return true;
	}

	public Collection<ClassNode> getAnonymousClasses() {
		return Collections.unmodifiableCollection(anonymousClasses);
	}

	public String getInnerName() {
		return innerName;
	}

	public void setInnerName(String name) {
		if (isValidInnerName(name)) {
			innerName = name;

			if (isNested()) {
				getEnclosingClass().fixNestedClassNames();
			}
		}
	}

	private boolean isValidInnerName(String name) {
		return isNested() ? !name.contains("$") : name == null; // TODO: check against keywords and invalid characters
	}

	public int getInnerAccess() {
		return innerAccess;
	}

	public void setInnerAccess(int access) {
		this.innerAccess = access;
	}

	public void enableInnerAccess(int... opcodes) {
		for (int opcode : opcodes) {
			setInnerAccess(innerAccess | opcode);
		}
	}

	public void disableInnerAccess(int... opcodes) {
		for (int opcode : opcodes) {
			setInnerAccess(innerAccess & ~opcode);
		}
	}
}
