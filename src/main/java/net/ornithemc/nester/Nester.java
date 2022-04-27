package net.ornithemc.nester;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.ornithemc.nester.jar.SourceJar;
import net.ornithemc.nester.jar.node.ClassNode;
import net.ornithemc.nester.jar.node.FieldNode;
import net.ornithemc.nester.jar.node.MethodNode;
import net.ornithemc.nester.jar.node.Node;
import net.ornithemc.nester.jar.node.proto.ProtoClassNode;
import net.ornithemc.nester.mapping.Nest;
import net.ornithemc.nester.mapping.NestType;
import net.ornithemc.nester.mapping.NesterIo;
import net.ornithemc.nester.mapping.Nests;

public class Nester {

	/**
	 * Fix the jar at the given source path with the given mappings and
	 * write it to the given destination path.
	 */
	public static void fixJar(Path src, Path dst, Path mappings) {
		fixJar(src, dst, Nests.of(mappings));
	}

	/**
	 * Fix the jar at the given source path with the given mappings and
	 * write it to the given destination path.
	 */
	public static void fixJar(Path src, Path dst, Nests nests) {
		Nester nester = new Nester(src, dst, null, nests);

		nester.applyMappings();
		nester.fixNestedClasses();
	}

	/**
	 * Fix the jar at the given source path using Nester's automatic nested
	 * class detection and write it to the given destination path.
	 */
	public static void fixJar(Path src, Path dst) {
		Nester nester = new Nester(src, dst, null, Nests.empty());

		nester.findNestedClasses();
		nester.fixNestedClasses();
	}

	/**
	 * Fix the jar at the given source path using Nester's automatic nested
	 * class detection and write the mappings to the given destination path.
	 */
	public static void generateMappings(Path src, Path mappings) {
		Nester nester = new Nester(src, null, mappings, Nests.empty());

		nester.findNestedClasses();
		nester.writeNestedClasses();
	}

	private final Path src;
	private final Path dst;
	private final Path mappings;

	private final SourceJar jar;
	private final Nests nests;

	private Nester(Path src, Path dst, Path mappings, Nests nests) {
		if (dst == null && mappings == null) {
			throw new IllegalArgumentException("must provide destination path and/or mappings path!");
		}

		this.src = src;
		this.dst = dst;
		this.mappings = mappings;

		this.jar = new SourceJar(this.src);
		this.nests = nests;
	}

	private void applyMappings() {
		int applied = 0;

		for (Nest nest : nests.get()) {
			ClassNode clazz = jar.getClass(nest.className);

			if (clazz == null) {
				continue;
			}

			ClassNode enclClass = jar.getClass(nest.enclClassName);

			if (enclClass == null) {
				continue;
			}

			MethodNode method = null;

			if (nest.type == NestType.ANONYMOUS && nest.enclMethodName != null) {
				method = enclClass.getMethod(nest.enclMethodName, nest.enclMethodDesc);

				if (method == null) {
					continue;
				}
			}
			if (nest.type == NestType.INNER && nest.innerName == null) {
				continue;
			}

			clazz.enableAccess(nest.access);

			if (nest.type == NestType.ANONYMOUS) {
				enclClass.addAnonymousClass(method, clazz);
			} else {
				enclClass.addInnerClass(clazz);
				clazz.setSimpleName(nest.innerName);
			}

			applied++;
		}

		System.out.println("Applied " + applied + " nested class mappings...");
	}

	private void findNestedClasses() {
		int found = 0;

		for (ClassNode clazz : jar.getClasses()) {
			if (!clazz.isNestable() || clazz.isNested()) {
				continue;
			}

			Nest nest = tryNestClass(clazz, false);

			if (nest != null) {
				nests.add(nest);
				found++;
			}
		}

		System.out.println("Found " + found + " nested classes...");
	}

	private Nest tryNestClass(ClassNode clazz, boolean checkOnly) {
		Nest nest = tryEnum(clazz, checkOnly);

		if (nest == null) {
			nest = tryAnonymous(clazz, checkOnly);
		}
		if (nest == null) {
			nest = tryInner(clazz, checkOnly);
		}

		return nest;
	}

	private Nest tryEnum(ClassNode clazz, boolean checkOnly) {
		if (!clazz.isEnum()) {
			return null;
		}

		ClassNode superClass = clazz.getSuperClass();

		if (superClass.getName().equals("java/lang/Enum")) {
			return null;
		}

		return checkOrAddAnonymous(clazz, superClass, null, checkOnly);
	}

	private Nest tryAnonymous(ClassNode clazz, boolean checkOnly) {
		// anonymous classes are always package private
		if (!clazz.canBeAnonymous() || !clazz.isPackagePrivate()) {
			return null;
		}

		Collection<MethodNode> declaredMethods = clazz.getDeclaredMethods();

		// anonymous classes typically declare or override just 1 method
		if (declaredMethods.size() != 1) {
			return null;
		}

		Collection<MethodNode> constructors = clazz.getConstructors();

		// anonymous classes have only 1 constructor
		if (constructors.size() != 1) {
			return null;
		}

		MethodNode constr = constructors.iterator().next();
		Collection<Node> references = jar.getReferences(constr);

		// anonymous classes are only created once
		if (references.size() != 1) {
			return null;
		}

		Node refNode = references.iterator().next();

		if (!refNode.isMethod()) {
			return null;
		}

		MethodNode enclMethod = refNode.asMethod();
		ClassNode enclClass = enclMethod.getParent();

		return checkOrAddAnonymous(clazz, enclClass, enclMethod, checkOnly);
	}

	private Nest tryInner(ClassNode clazz, boolean checkOnly) {
		// Inner classes usually have a synthetic field to store a
		// reference to an instance of the enclosing class. This might
		// be missing if the inner class is static.
		// There might also be synthetic methods. These are used by
		// the enclosing class to access members of the inner class.
		// The enclosing class might also have synthetic methods, used
		// by the inner class to access members of the enclosing class.
		// However, these only exist if the inner class is not static,
		// since static inner classes cannot access instance members of
		// the enclosing class.
		if (!clazz.canBeInner() || !clazz.hasSyntheticMembers()) {
			return null;
		}

		// Inner classes have 1 synthetic field: a reference to
		// an instance of the enclosing class.
		if (clazz.hasSyntheticFields()) {
			Collection<FieldNode> syntheticFields = clazz.getSyntheticFields();

			// If there are more synthetic fields, this class is
			// probably an anonymous class...
			if (syntheticFields.size() > 1) {
				return null;
			}

			FieldNode field = syntheticFields.iterator().next();
			ClassNode type = field.getType();

			if (jar.hasClass(type)) {
				return checkOrAddInner(clazz, type, checkOnly);
			}
		}

		if (checkOnly) {
			return null;
		}

		// If the inner class does not reference the enclosing class instance
		// at all, that synthetic field might be missing. This might be because
		// the inner class is static, or because it was removed due to being
		// unused. So then we turn to looking for synthetic methods.
		Collection<ClassNode> references = new LinkedHashSet<>();
		Collection<MethodNode> syntheticMethods = clazz.getSyntheticMethods();

		for (MethodNode method : syntheticMethods) {
			for (Node ref : jar.getReferences(method)) {
				if (ref.isClass()) {
					references.add(ref.asClass());
				} else {
					Node parent = ref.getParent();

					if (parent.isClass()) {
						references.add(parent.asClass());
					}
				}
			}
		}

		// Things can get pretty complex, e.g. when there are multiple layers of
		// nested classes, or if one inner class is referenced from another. We
		// will not account for all of these scenarios, and will instead look for
		// the simple case where all references to the inner class come from the
		// enclosing class.
		if (references.size() != 1) {
			return null;
		}

		ClassNode enclClass = references.iterator().next();

		// Sometimes the enclosing class is mistaken for the inner class.
		// Usually enclosing classes do not have synthetic fields, unless
		// the nesting goes multiple levels deep...
		Nest enclNest = tryNestClass(enclClass, true);

		if (enclNest != null && clazz.getName().equals(enclNest.className)) {
			return null;
		}

		return checkOrAddInner(clazz, enclClass, checkOnly);
	}

	private Nest checkOrAddAnonymous(ClassNode clazz, ClassNode enclClass, MethodNode enclMethod, boolean checkOnly) {
		Node parent = (enclMethod == null) ? enclClass : enclMethod;

		if (checkOnly ? parent.isValidChild(clazz) : enclClass.addAnonymousClass(enclMethod, clazz)) {
			return Nest.anonymous(clazz, enclClass, enclMethod);
		} else {
			return null;
		}
	}

	private Nest checkOrAddInner(ClassNode clazz, ClassNode enclClass, boolean checkOnly) {
		if (checkOnly ? enclClass.isValidChild(clazz) : enclClass.addInnerClass(clazz)) {
			return Nest.inner(clazz, enclClass);
		} else {
			return null;
		}
	}

	private void fixNestedClasses() {
		try {
			Path tmp1 = Files.createTempFile("tmp1", ".jar");
			Path tmp2 = Files.createTempFile("tmp2", ".jar");

			// tiny remapper does not like it when the file already exists...
			Files.delete(tmp2);

			addAttributes(src, tmp1);
			remapNestedClasses(tmp1, tmp2);
			writeFixedJar(src, tmp2, dst);

			Files.delete(tmp1);
			Files.delete(tmp2);

			System.out.println("Done!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void addAttributes(Path src, Path dst) {
		try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(dst.toFile()))) {
			try (JarInputStream jis = new JarInputStream(new FileInputStream(src.toFile()))) {
				for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
					if (entry.getName().endsWith(".class")) {
						ClassReader reader = new ClassReader(jis);
						ClassWriter writer = new ClassWriter(reader, 0);
						ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {

							private String name;
							private Collection<Nest> nests;

							@Override
							public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
								this.name = name;
								this.nests = Nester.this.nests.get(this.name);

								super.visit(version, access, name, signature, superName, interfaces);
							}

							@Override
							public void visitEnd() {
								if (nests != null) {
									for (Nest nest : nests) {
										addAttribute(nest);
									}
								}

								super.visitEnd();
							}

							private void addAttribute(Nest nest) {
								if (nest.className.equals(name)) {
									visitOuterClass(nest);
								}

								visitInnerClass(nest);
							}

							private void visitOuterClass(Nest nest) {
								visitOuterClass(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
							}

							private void visitInnerClass(Nest nest) {
								visitInnerClass(nest.className, nest.innerName == null ? null : nest.enclClassName, nest.innerName, nest.access);
							}
						};

						reader.accept(visitor, 0);

						jos.putNextEntry(new JarEntry(entry.getName()));
						jos.write(writer.toByteArray());
						jos.flush();
						jos.closeEntry();
					}
				}
			}

			System.out.println("Fixed inner class attributes...");

			jos.finish();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void remapNestedClasses(Path src, Path dst) {
		TinyRemapper remapper = TinyRemapper.newRemapper().keepInputData(true).withMappings(ma -> {
			for (ClassNode clazz : jar.getClasses()) {
				ProtoClassNode protoClass = clazz.proto();

				String oldName = protoClass.getName();
				String newName = clazz.getName();

				if (!newName.equals(oldName)) {
					ma.acceptClass(oldName, newName);
				}
			}
		}).build();

		try (OutputConsumerPath oc = new OutputConsumerPath.Builder(dst).build()) {
			remapper.readInputs(src);
			remapper.apply(oc);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			remapper.finish();
		}

		System.out.println("Remapped nested class names...");
	}

	private void writeFixedJar(Path src, Path fixedSrc, Path dst) {
		try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(dst.toFile()))) {
			try (JarInputStream jis = new JarInputStream(new FileInputStream(src.toFile()))) {
				for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
					if (!entry.getName().endsWith(".class")) {
						jos.putNextEntry(new JarEntry(entry.getName()));

						byte[] buffer = new byte[4096];
						int read = 0;

						while ((read = jis.read(buffer)) > 0) {
							jos.write(buffer, 0, read);
						}

						jos.flush();
						jos.closeEntry();
					}
				}
			}

			System.out.println("Moved over non-class files...");

			try (JarInputStream jis = new JarInputStream(new FileInputStream(fixedSrc.toFile()))) {
				for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
					if (entry.getName().endsWith(".class")) {
						jos.putNextEntry(new JarEntry(entry.getName()));

						byte[] buffer = new byte[4096];
						int read = 0;

						while ((read = jis.read(buffer)) > 0) {
							jos.write(buffer, 0, read);
						}

						jos.flush();
						jos.closeEntry();
					}
				}
			}

			System.out.println("Moved over class files...");

			jos.finish();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeNestedClasses() {
		NesterIo.write(nests, mappings);
		System.out.println("Written mappings to file...");
		System.out.println("Done!");
	}
}
