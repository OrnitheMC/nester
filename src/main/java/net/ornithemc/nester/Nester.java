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
import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;
import net.ornithemc.nester.serdes.MappingIo;

public class Nester {

	/**
	 * Fix the jar at the given source path with the given mappings and
	 * write it to the given destination path.
	 */
	public static void fixJar(Path src, Path dst, Path mappings) {
		Nester nester = new Nester(src, dst, mappings);

		nester.readNestedClasses();
		nester.fixNestedClasses();
	}

	/**
	 * Fix the jar at the given source path using Nester's automatic nested
	 * class detection and write it to the given destination path.
	 */
	public static void fixJar(Path src, Path dst) {
		Nester nester = new Nester(src, dst, null);

		nester.findNestedClasses();
		nester.fixNestedClasses();
	}

	/**
	 * Fix the jar at the given source path using Nester's automatic nested
	 * class detection and write it to the given destination path.
	 */
	public static void generateMappings(Path src, Path mappings) {
		Nester nester = new Nester(src, null, mappings);

		nester.findNestedClasses();
		nester.writeNestedClasses();
	}

	private final Path src;
	private final Path dst;
	private final Path mappings;

	private final SourceJar jar;

	private Nester(Path src, Path dst, Path mappings) {
		if (dst == null && mappings == null) {
			throw new IllegalArgumentException("must provide destination path and/or mappings path!");
		}

		this.src = src;
		this.dst = dst;
		this.mappings = mappings;

		this.jar = new SourceJar(this.src);
	}

	private void findNestedClasses() {
		int found = 0;

		for (ClassNode clazz : jar.getClasses()) {
			if (nestClass(clazz)) {
				found++;
			}
		}

		System.out.println("Found " + found + " nested classes...");
	}

	private void readNestedClasses() {
		MappingIo.read(jar, mappings);
		System.out.println("Read mappings from file...");
	}

	private boolean nestClass(ClassNode clazz) {
		return !clazz.isNested() && clazz.isNestable() && tryNestClass(clazz, false) != null;
	}

	private ClassNode tryNestClass(ClassNode clazz, boolean checkOnly) {
		ClassNode enclClass = tryEnum(clazz, checkOnly);

		if (enclClass == null) {
			enclClass = tryAnonymous(clazz, checkOnly);
		}
		if (enclClass == null) {
			enclClass = tryInner(clazz, checkOnly);
		}

		return enclClass;
	}

	private ClassNode tryEnum(ClassNode clazz, boolean checkOnly) {
		if (!clazz.isEnum()) {
			return null;
		}

		ClassNode superClass = clazz.getSuperClass();

		if (superClass.getName().equals("java/lang/Enum")) {
			return null;
		}

		return checkOrAddAnonymous(clazz, superClass, null, checkOnly);
	}

	private ClassNode tryAnonymous(ClassNode clazz, boolean checkOnly) {
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

	private ClassNode tryInner(ClassNode clazz, boolean checkOnly) {
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
		if (tryNestClass(enclClass, true) == clazz) {
			return null;
		}

		return checkOrAddInner(clazz, enclClass, checkOnly);
	}

	private ClassNode checkOrAddAnonymous(ClassNode clazz, ClassNode enclClass, MethodNode enclMethod, boolean checkOnly) {
		Node parent = (enclMethod == null) ? enclClass : enclMethod;

		if (checkOnly ? parent.isValidChild(clazz) : enclClass.addAnonymousClass(enclMethod, clazz)) {
			return enclClass;
		} else {
			return null;
		}
	}

	private ClassNode checkOrAddInner(ClassNode clazz, ClassNode enclClass, boolean checkOnly) {
		if (checkOnly ? enclClass.isValidChild(clazz) : enclClass.addInnerClass(clazz)) {
			return enclClass;
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

							private ProtoClassNode protoClass;
							private ClassNode clazz;

							@Override
							public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
								protoClass = jar.getProtoClass(name);

								if (protoClass != null) {
									clazz = protoClass.node();
								}

								super.visit(version, access, name, signature, superName, interfaces);
							}

							@Override
							public void visitEnd() {
								if (clazz != null) {
									addReference(clazz);

									for (ClassNode anonClass : clazz.getAnonymousClasses()) {
										addReference(anonClass);
									}
									for (ClassNode innerClass : clazz.getInnerClasses()) {
										addReference(innerClass);
									}
								}

								super.visitEnd();
							}

							private void addReference(ClassNode innerClass) {
								Node parent = innerClass.getParent();

								ClassNode enclClass = null;
								MethodNode enclMethod = null;

								if (parent == null) {
									return;
								} else if (parent.isClass()) {
									enclClass = parent.asClass();
								} else if (parent.isMethod()) {
									enclMethod = parent.asMethod();
									enclClass = enclMethod.getParent();
								} else {
									return;
								}

								if (innerClass == clazz) {
									addOuterReference(enclClass, enclMethod);
								}

								addInnerReference(enclClass, innerClass);
							}

							private void addOuterReference(ClassNode enclClass, MethodNode enclMethod) {
								// Remapping happens later so the proto names must be used
								ProtoClassNode protoEnclClass = enclClass.proto();

								String enclosingClassName = protoEnclClass.getName();
								String enclosingMethodName = null;
								String enclosingMethodDescriptor = null;

								if (enclMethod != null) {
									// Remapping happens later so the proto names must be used
									ProtoMethodNode protoEnclMethod = enclMethod.proto();

									enclosingMethodName = protoEnclMethod.getName();
									enclosingMethodDescriptor = protoEnclMethod.getDescriptor();
								}

								visitOuterClass(enclosingClassName, enclosingMethodName, enclosingMethodDescriptor);
							}

							private void addInnerReference(ClassNode enclClass, ClassNode innerClass) {
								// Remapping happens later so the proto names must be used
								ProtoClassNode protoEnclClass = enclClass.proto();
								ProtoClassNode protoInnerClass = innerClass.proto();

								String name = protoInnerClass.getName();
								String outerName = protoEnclClass.getName();
								String innerName = innerClass.getSimpleName();
								int access = protoInnerClass.getAccess();

								if (innerName == null) {
									outerName = null;
								}

								visitInnerClass(name, outerName, innerName, access);
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

				String protoName = protoClass.getName();
				String name = clazz.getName();

				if (!name.equals(protoName)) {
					ma.acceptClass(protoName, name);
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
		MappingIo.write(jar, mappings);
		System.out.println("Written mappings to file...");
		System.out.println("Done!");
	}
}
