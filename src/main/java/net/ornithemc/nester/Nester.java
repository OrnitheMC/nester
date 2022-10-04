package net.ornithemc.nester;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
import net.ornithemc.nester.mapping.Nest;
import net.ornithemc.nester.mapping.NestType;
import net.ornithemc.nester.mapping.Nests;

public class Nester {

	/**
	 * Fix the jar at the given source path with the given mappings and
	 * write it to the given destination path.
	 */
	public static void fixJar(Path src, Path dst, Path mappings) {
		if (!Files.isReadable(mappings) || !Files.isRegularFile(mappings)) {
			throw new NesterException("invalid mappings path");
		}

		fixJar(src, dst, Nests.of(mappings));
	}

	/**
	 * Fix the jar at the given source path with the given mappings and
	 * write it to the given destination path.
	 */
	public static void fixJar(Path src, Path dst, Nests nests) {
		if (!Files.isReadable(src) || !Files.isRegularFile(src)) {
			throw new NesterException("invalid source path: " + src);
		}
		if ((Files.exists(dst) && !Files.isWritable(dst))) {
			throw new NesterException("invalid destination path: " + dst);
		}
		if (nests == null) {
			throw new NesterException("no nests provided");
		}

		Nester nester = new Nester(src, dst, nests);

		nester.applyMappings();
		nester.fixNestedClasses();
	}

	private final Path src;
	private final Path dst;

	private final SourceJar jar;
	private final Nests nests;

	private Nester(Path src, Path dst, Nests nests) {
		this.src = src;
		this.dst = dst;

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
			if (nest.innerName == null) {
				continue;
			}

			MethodNode method = null;

			if (nest.enclMethodName != null) {
				method = enclClass.getMethod(nest.enclMethodName, nest.enclMethodDesc);

				if (method == null) {
					continue;
				}
			}

			if (nest.type == NestType.ANONYMOUS) {
				int anonIndex = -1;

				try {
					anonIndex = Integer.parseInt(nest.innerName);
				} catch (NumberFormatException e) {

				}

				if (anonIndex < 1) {
					continue;
				}
			}

			if (nest.type == NestType.ANONYMOUS) {
				enclClass.addAnonymousClass(method, clazz);
			} else {
				enclClass.addInnerClass(method, clazz);
			}

			clazz.setInnerName(nest.innerName);
			clazz.setInnerAccess(nest.access);

			applied++;
		}

		System.out.println("Applied " + applied + " nested class mappings...");
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
								this.nests = new LinkedHashSet<>();

								ClassNode c = jar.getClass(this.name);

								if (c != null) {
									prepareAttribute(c);

									for (ClassNode cc : c.getInnerClasses()) {
										prepareAttribute(cc);
									}
									for (ClassNode cc : c.getAnonymousClasses()) {
										prepareAttribute(cc);
									}
									for (Node n : jar.getReferencesBy(c)) {
										if (n.isClass()) {
											prepareAttribute(n.asClass());
										}
									}
								}

								super.visit(version, access, name, signature, superName, interfaces);
							}

							@Override
							public void visitEnd() {
								for (Nest nest : nests) {
									addAttribute(nest);
								}

								super.visitEnd();
							}

							private void prepareAttribute(ClassNode c) {
								Nest nest = Nester.this.nests.get(c.proto().getName());

								if (nest != null) {
									nests.add(nest);
								}
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
								visitInnerClass(nest.className, nest.isAnonymous() ? null : nest.enclClassName, nest.isAnonymous() ? null : nest.innerName, nest.access);
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
			byte[] buffer = new byte[4096];
			int read = 0;

			try (JarInputStream jis = new JarInputStream(new FileInputStream(src.toFile()))) {
				for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
					if (!entry.getName().endsWith(".class")) {
						jos.putNextEntry(new JarEntry(entry.getName()));

						while ((read = jis.read(buffer)) > 0) {
							jos.write(buffer, 0, read);
						}

						jos.flush();
						jos.closeEntry();
					}
				}
			}

			System.out.println("Moved over non-class files...");

			JarFile fixedSrcJar = new JarFile(fixedSrc.toFile());

			for (ClassNode c : jar.getClasses()) {
				String className = c.getName();
				String entryName = className + ".class";

				JarEntry entry = fixedSrcJar.getJarEntry(entryName);

				try (InputStream jis = fixedSrcJar.getInputStream(entry)) {
					jos.putNextEntry(new JarEntry(entryName));

					while ((read = jis.read(buffer)) > 0) {
						jos.write(buffer, 0, read);
					}

					jos.flush();
					jos.closeEntry();
				}
			}

			fixedSrcJar.close();

			System.out.println("Moved over class files...");

			jos.finish();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
