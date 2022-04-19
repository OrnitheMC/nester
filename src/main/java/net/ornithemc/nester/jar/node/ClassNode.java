package net.ornithemc.nester.jar.node;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;

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

	private final Map<String, ClassNode> innerClasses;
	private final Set<ClassNode> anonymousClasses;

	private boolean hasSyntheticFields;
	private boolean hasSyntheticMethods;
	private boolean hasStaticMembers;

	private boolean canBeAnonymous;

	private String simpleName; // used by nested classes

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

		this.innerClasses = new HashMap<>();
		this.anonymousClasses = new HashSet<>();

		this.canBeAnonymous = true;
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
				if (method.isInstanceConstructor()) {
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

	public boolean isNestable() {
		return canRename() && !isNested();
	}

	public boolean isNested() {
		return hasParent();
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

		// Non-static inner class store a reference to an instance of
		// the enclosing class in a synthetic field.
		if (!clazz.hasSyntheticFields()) {
			clazz.enableAccess(Opcodes.ACC_STATIC);
		}

		String oldName = clazz.getName();
		String simpleName = getSimpleName(clazz);
		clazz.setSimpleName(simpleName);
		String name = getName() + "$" + simpleName;
		clazz.setName(name);
System.out.println(getName() + " add inner " + name + " (was " + oldName + ")");
		innerClasses.put(simpleName, clazz);

		return true;
	}

	private String getSimpleName(ClassNode clazz) {
		return clazz.getName(); // TODO: generate unique (within this class) simple names
	}

	public boolean addAnonymousClass(MethodNode enclMethod, ClassNode clazz) {
		Node parent = (enclMethod == null) ? this : enclMethod;

		if (!clazz.setParent(parent)) {
			return false;
		}

String oldName = clazz.getName();
		clazz.setSimpleName(null);
		String name = getName() + "$" + anonymousClasses.size();
		clazz.setName(name);
System.out.println(getName() + " add anon " + name + " (was " + oldName + ")" );
		anonymousClasses.add(clazz);

		return true;
	}

	public String getSimpleName() {
		return simpleName;
	}

	public void setSimpleName(String name) {
		if (isValidSimpleName(name)) {
			simpleName = name;
		}
	}

	private boolean isValidSimpleName(String name) {
		return name == null || !name.contains("$"); // TODO: check against keywords and invalid characters
	}
}
