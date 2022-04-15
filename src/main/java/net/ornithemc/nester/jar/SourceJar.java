package net.ornithemc.nester.jar;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import net.ornithemc.nester.jar.node.ClassNode;
import net.ornithemc.nester.jar.node.FieldNode;
import net.ornithemc.nester.jar.node.MethodNode;
import net.ornithemc.nester.jar.node.Node;
import net.ornithemc.nester.jar.node.proto.ProtoClassNode;
import net.ornithemc.nester.jar.node.proto.ProtoFieldNode;
import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;
import net.ornithemc.nester.jar.node.proto.ProtoNode;

public class SourceJar {

	private final Path src;
	private final Map<String, ProtoClassNode> protoClasses;
	private final Map<String, ClassNode> classes;
	private final Map<Node, Collection<Node>> references;

	public SourceJar(Path src) {
		this.src = src;
		this.protoClasses = new LinkedHashMap<>();
		this.classes = new LinkedHashMap<>();
		this.references = new LinkedHashMap<>();

		this.read();
		this.findReferences();
	}

	private void read() {
		Set<ProtoNode> protoNodes = new LinkedHashSet<>();

		try (JarInputStream js = new JarInputStream(new FileInputStream(src.toFile()))) {
			for (JarEntry entry; (entry = js.getNextJarEntry()) != null;) {
				if (entry.getName().endsWith(".class")) {
					ClassReader reader = new ClassReader(js);
					ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9) {

						private String className;
						private ProtoClassNode clazz;

						@Override
						public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
							if (clazz == null) {
								className = name;
								clazz = new ProtoClassNode(SourceJar.this, version, access, name, signature, superName, interfaces);

								protoNodes.add(clazz);
								protoClasses.put(name, clazz);
							}

							super.visit(version, access, name, signature, superName, interfaces);
						}

						@Override
						public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
							if (clazz != null) {
								ProtoFieldNode field = new ProtoFieldNode(SourceJar.this, access, name, desc, signature, value);

								field.setEnclosingClass(className);
								protoNodes.add(field);
							}

							return super.visitField(access, name, desc, signature, value);
						}

						@Override
						public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
							if (clazz != null) {
								ProtoMethodNode method = new ProtoMethodNode(SourceJar.this, access, name, desc, signature, exceptions);

								method.setEnclosingClass(className);
								protoNodes.add(method);
							}

							return super.visitMethod(access, name, desc, signature, exceptions);
						}

						@Override
						public void visitInnerClass(String name, String outerName, String innerName, int access) {
							if (clazz != null && clazz.getName().equals(name)) {
								if (outerName == null) {
									int i = name.lastIndexOf('$');
									outerName = name.substring(0, i);
								}

								clazz.setEnclosingClass(outerName, access);
							}

							super.visitInnerClass(name, outerName, innerName, access);
						}
					};

					reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (ProtoNode protoNode : protoNodes) {
			Node node = protoNode.node();

			if (node.isClass()) {
				ClassNode clazz = node.asClass();
				classes.put(clazz.getName(), clazz);
			}
		}
	}

	private void findReferences() {
		try (JarInputStream js = new JarInputStream(new FileInputStream(src.toFile()))) {
			for (JarEntry entry; (entry = js.getNextJarEntry()) != null;) {
				if (entry.getName().endsWith(".class")) {
					ClassReader reader = new ClassReader(js);
					ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9) {

						private ClassNode clazz;

						@Override
						public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
							if (clazz == null) {
								clazz = SourceJar.this.getClass(name);
							}

							super.visit(version, access, name, signature, superName, interfaces);
						}

						@Override
						public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
							FieldVisitor visitor = super.visitField(access, name, desc, signature, value);

							if (clazz == null) {
								return visitor;
							}

							FieldNode field = clazz.getField(name);

							if (field == null) {
								return visitor;
							}

							Type type = Type.getType(desc);
							int sort = type.getSort();

							if (sort == Type.OBJECT || sort == Type.ARRAY) {
								String className = type.getInternalName();
								ClassNode clazz = SourceJar.this.getClass(className);

								if (clazz != null) {
									SourceJar.this.addReference(clazz, field);
									clazz.markNotAnonymous();
								}
							}

							return visitor;
						}

						@Override
						public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
							MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);

							if (clazz == null) {
								return visitor;
							}

							MethodNode method = clazz.getMethod(name, desc);

							if (method == null) {
								return visitor;
							}

							return new MethodVisitor(Opcodes.ASM9, visitor) {

								@Override
								public void visitTypeInsn(int opcode, String type) {
									ClassNode m_clazz = SourceJar.this.getClass(type);

									if (m_clazz != null) {
										SourceJar.this.addReference(m_clazz, method);

										if (opcode != Opcodes.NEW) {
											m_clazz.markNotAnonymous();
										}
									}

									super.visitTypeInsn(opcode, type);
								}

								@Override
								public void visitMethodInsn(int opcodeAndSource, String owner, String name, String descriptor, boolean isInterface) {
									ClassNode m_clazz = SourceJar.this.getClass(owner);

									if (m_clazz != null) {
										SourceJar.this.addReference(m_clazz, method);
										MethodNode m_method = m_clazz.getMethod(name, descriptor);

										if (m_method != null) {
											SourceJar.this.addReference(m_method, method);
										}
									}

									super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface);
								}
							};
						}
					};

					reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Collection<ProtoClassNode> getProtoClasses() {
		return Collections.unmodifiableCollection(protoClasses.values());
	}

	public ProtoClassNode getProtoClass(String name) {
		return protoClasses.get(name);
	}

	public Collection<ClassNode> getClasses() {
		return Collections.unmodifiableCollection(classes.values());
	}

	public ClassNode getClass(String name) {
		return classes.get(name);
	}

	public Collection<Node> getReferences(Node node) {
		return references.getOrDefault(node, Collections.emptySet());
	}

	private void addReference(Node node, Node byNode) {
		if (node.isClass()) {
			// ignore references to classes by nodes inside those classes
			ClassNode clazz = node.asClass();
			ClassNode ancestor = byNode.getClosestCommonParentClass(clazz);

			if (clazz == ancestor) {
				return;
			}
		}

		references.computeIfAbsent(node, key -> new LinkedHashSet<>()).add(byNode);
	}
}
