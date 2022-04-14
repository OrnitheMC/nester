package net.ornithemc.nester;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.ornithemc.nester.jar.node.MethodNode;
import net.ornithemc.nester.jar.node.Node;
import net.ornithemc.nester.jar.node.proto.ProtoClassNode;
import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;

public class Nester {

	public static void run(Path src, Path dst) {
		Nester nester = new Nester(src, dst);

		nester.findNestedClasses();
		nester.fixNestedClasses();
	}

	private final Path src;
	private final Path dst;
	private final SourceJar jar;

	private Nester(Path src, Path dst) {
		this.src = src;
		this.dst = dst;
		this.jar = new SourceJar(this.src);
	}

	private void findNestedClasses() {
		int found = 0;

		for (ClassNode clazz : jar.getClasses()) {
			if (nestClass(clazz)) {
				found++;
			}
		}

		System.out.println("Found " + found + " nested classes!");
	}

	private boolean nestClass(ClassNode clazz) {
		if (clazz.isNested() || !clazz.isNestable()) {
			return false;
		}

		ClassNode superClass = clazz.getSuperClass();

		if (clazz.isEnum() && !superClass.getName().equals("java/lang/Enum")) {
			return superClass.addAnonymousClass(clazz);
		}

		return false;
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

									for (Node node : clazz.getChildren()) {
										if (node.isClass()) {
											addReference(node.asClass());
										}
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

	private void writeFixedJar(Path src, Path tmp, Path dst) {
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

			try (JarInputStream jis = new JarInputStream(new FileInputStream(tmp.toFile()))) {
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
}
