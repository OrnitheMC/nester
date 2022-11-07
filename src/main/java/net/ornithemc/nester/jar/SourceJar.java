package net.ornithemc.nester.jar;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class SourceJar {

	private static final Comparator<String> CLASS_NAME_COMPARATOR = (c1, c2) -> {
		int l1 = c1.length();
		int l2 = c2.length();

		return l1 == l2 ? c1.compareTo(c2) : l1 - l2;
	};

	private final Path src;

	private final Map<String, ClassNode> classes;
	private final Map<String, ClassNode> newClasses;

	private int classVersion = -1;

	public SourceJar(Path src) {
		this.src = src;

		this.classes = new TreeMap<>(CLASS_NAME_COMPARATOR);
		this.newClasses = new TreeMap<>(CLASS_NAME_COMPARATOR);

		this.read();
	}

	private void read() {
		try (JarInputStream js = new JarInputStream(new FileInputStream(src.toFile()))) {
			for (JarEntry entry; (entry = js.getNextJarEntry()) != null; ) {
				if (entry.getName().endsWith("class")) {
					ClassReader reader = new ClassReader(js);
					ClassNode visitor = new ClassNodeWrapper(Opcodes.ASM9);

					reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

					if (classVersion < 0 || visitor.version < classVersion) {
						classVersion = visitor.version;
					}

					classes.put(visitor.name, visitor);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Collection<ClassNode> getClasses() {
		return Collections.unmodifiableCollection(classes.values());
	}

	/**
	 * Returns a class given its name.
	 */
	public ClassNode getClass(String name) {
		return classes.get(name);
	}

	/**
	 * Check if the given class is contained in the jar or if it is
	 * merely a reference to a class in a library or the JRE.
	 */
	public boolean hasClass(String name) {
		return classes.containsKey(name);
	}

	/**
	 * Check if the given class is contained in the jar or if it is
	 * merely a reference to a class in a library or the JRE.
	 */
	public boolean hasClass(ClassNode clazz) {
		return hasClass(clazz.name);
	}

	public MethodNode getMethod(String className, String methodName, String methodDesc) {
		ClassNode clazz = getClass(className);

		if (clazz == null) {
			return null;
		}

		return ((ClassNodeWrapper)clazz).methods.get(methodName + methodDesc);
	}

	public Collection<ClassNode> getNewClasses() {
		return Collections.unmodifiableCollection(newClasses.values());
	}

	public ClassNode newClass(String name) {
		if (classes.containsKey(name)) {
			throw new IllegalStateException("cannot generate class " + name + " as it already exists!");
		}

		ClassNode clazz = newClasses.computeIfAbsent(name, key -> {
			ClassNode c = new ClassNodeWrapper(Opcodes.ASM9);
			c.visit(
				classVersion,
				Opcodes.ACC_PUBLIC,
				name,
				null,
				"java/lang/Object",
				null
			);

			return c;
		});
		classes.put(name, clazz);

		return clazz;
	}

	private static class ClassNodeWrapper extends ClassNode {

		private final Map<String, MethodNode> methods;

		public ClassNodeWrapper(int api) {
			super(api);

			this.methods = new HashMap<>();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof ClassNode)) {
				return false;
			}

			return name.equals(((ClassNode)obj).name);
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodNode method = new MethodNode(access, name, descriptor, signature, exceptions);
			methods.put(method.name + method.desc, method);
			return method;
		}
	}
}
