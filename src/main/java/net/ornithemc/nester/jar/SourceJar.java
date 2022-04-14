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

import net.ornithemc.nester.jar.node.ClassNode;
import net.ornithemc.nester.jar.node.Node;
import net.ornithemc.nester.jar.node.proto.ProtoClassNode;
import net.ornithemc.nester.jar.node.proto.ProtoFieldNode;
import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;
import net.ornithemc.nester.jar.node.proto.ProtoNode;

public class SourceJar {

	private final Path src;
	private final Map<String, ProtoClassNode> protoClasses;
	private final Map<String, ClassNode> classes;

	public SourceJar(Path src) {
		this.src = src;
		this.protoClasses = new LinkedHashMap<>();
		this.classes = new LinkedHashMap<>();

		this.read();
	}

	private void read() {
		Set<ProtoNode> protoNodes = new LinkedHashSet<>();

		try (JarInputStream js = new JarInputStream(new FileInputStream(src.toFile()))) {
			for (JarEntry entry; (entry = js.getNextJarEntry()) != null;) {
				if (entry.getName().endsWith(".class")) {
					ClassReader reader = new ClassReader(js);
					ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9) {

						private ProtoClassNode clazz;

						@Override
						public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
							if (clazz == null) {
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

								field.setParent(clazz);
								protoNodes.add(field);
							}

							return super.visitField(access, name, desc, signature, value);
						}

						@Override
						public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
							if (clazz != null) {
								ProtoMethodNode method = new ProtoMethodNode(SourceJar.this, access, name, desc, signature, exceptions);

								method.setParent(clazz);
								protoNodes.add(method);
							}

							return super.visitMethod(access, name, desc, signature, exceptions);
						}

						@Override
						public void visitOuterClass(String owner, String name, String descriptor) {
							if (clazz != null) {
								clazz.markNested();
								System.out.println(clazz.getName() + " outer: " + owner + (name == null ? "" : ("." + name + descriptor)));
							}

							super.visitOuterClass(owner, name, descriptor);
						}

						@Override
						public void visitInnerClass(String name, String outerName, String innerName, int access) {
							if (clazz != null) {
								System.out.println(clazz.getName() + " inner: " + name + " (" + outerName + " - " + innerName + ")");
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
}
