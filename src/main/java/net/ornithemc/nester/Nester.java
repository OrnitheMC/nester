package net.ornithemc.nester;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import net.ornithemc.nester.jar.ClassNest;
import net.ornithemc.nester.jar.SourceJar;
import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.NestType;
import net.ornithemc.nester.nest.Nests;

public class Nester {

	public static class Options {

		private boolean silent = false;
		private boolean remap = true;

		/**
		 * Set whether the log progress through System.out.
		 */
		public Options silent(boolean silent) {
			this.silent = silent;
			return this;
		}

		/**
		 * Set whether to remap nested classes in addition
		 * to fixing their inner class attributes.
		 */
		public Options remap(boolean remap) {
			this.remap = remap;
			return this;
		}
	}

	/**
	 * Apply the given nests to the jar at the given source path and
	 * write it to the given destination path.
	 */
	public static void nestJar(Path src, Path dst, Path nests) {
		nestJar(new Options(), src, dst, Nests.of(nests));
	}

	/**
	 * Apply the given nests to the jar at the given source path and
	 * write it to the given destination path.
	 */
	public static void nestJar(Options options, Path src, Path dst, Path nests) {
		if (!Files.isReadable(nests) || !Files.isRegularFile(nests)) {
			throw new NesterException("invalid nests path");
		}

		nestJar(options, src, dst, Nests.of(nests));
	}

	/**
	 * Apply the given nests to the jar at the given source path and
	 * write it to the given destination path.
	 */
	public static void nestJar(Options options, Path src, Path dst, Nests nests) {
		if (!Files.isReadable(src) || !Files.isRegularFile(src)) {
			throw new NesterException("invalid source path: " + src);
		}
		if ((Files.exists(dst) && !Files.isWritable(dst))) {
			throw new NesterException("invalid destination path: " + dst);
		}
		if (nests == null) {
			throw new NesterException("no nests provided");
		}

		Nester nester = new Nester(options, src, dst);

		nester.accept(nests);
		nester.applyNests();
	}

	private final Options options;

	private final Path src;
	private final Path dst;
	private final SourceJar jar;

	private final Map<ClassNode, Map<ClassNode, ClassNest>> nests;
	private final Map<String, String> mappings;

	private Nester(Options options, Path src, Path dst) {
		this.options = options;

		this.src = src;
		this.dst = dst;
		this.jar = new SourceJar(this.src);

		this.nests = new LinkedHashMap<>();
		this.mappings = new HashMap<>();
	}

	private void accept(Nests nests) {
		int c = 0;

		for (Nest nest : nests) {
			NestType type = nest.type;

			ClassNode clazz = jar.getClass(nest.className);
			ClassNode enclClass = jar.getClass(nest.enclClassName);
			MethodNode enclMethod = jar.getMethod(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);

			if (clazz != null && enclClass == null) {
				enclClass = jar.newClass(nest.enclClassName);
			}

			String innerName = nest.innerName;
			int innerAccess = nest.access;

			if (accept(type, clazz, enclClass, enclMethod, innerName, innerAccess)) {
				c++;
			}
		}

		if (!options.silent) {
			System.out.println("Prepared " + c + " nests...");
		}
	}

	private boolean accept(NestType type, ClassNode clazz, ClassNode enclClass, MethodNode enclMethod, String innerName, int innerAccess) {
		if (clazz == null || enclClass == null) {
			return false;
		}
		if (innerName == null || innerAccess < 0) {
			return false;
		}
		// anonymous class may have an enclosing method, they may not
		// inner classes NEVER have an enclosing method
		// local classes ALWAYS have an enclosing method
		if (type == NestType.INNER && enclMethod != null) {
			return false;
		}
		if (type == NestType.LOCAL && enclMethod == null) {
			return false;
		}
		// for anonymous classes, the inner name is typically
		// a number: their anonymous class index
		if (type == NestType.ANONYMOUS) {
			int anonIndex = -1;

			try {
				anonIndex = Integer.parseInt(innerName);
			} catch (NumberFormatException e) {

			}

			if (anonIndex < 1) {
				return false;
			}
		}

		// only accept each class once
		// after all, one class cannot be nested into multiple places
		if (nests.containsKey(clazz)) {
			Map<ClassNode, ClassNest> referencedNests = nests.get(clazz);

			if (referencedNests.containsKey(clazz)) {
				return false;
			}
		}

		ClassNest nest = new ClassNest(type, clazz, enclClass, enclMethod, innerName, innerAccess);

		addNestReference(clazz, nest);
		addNestReference(enclClass, nest);

		return true;
	}

	private void addNestReference(ClassNode clazz, ClassNest nest) {
		nests.computeIfAbsent(clazz, key -> new LinkedHashMap<>()).put(nest.clazz, nest);
	}

	private void applyNests() {
		try {
			if (options.remap) {
				Path tmp1 = Files.createTempFile("tmp1", ".jar");
				Path tmp2 = Files.createTempFile("tmp2", ".jar");

				// TinyRemapper does not like it when the file already exists
				Files.delete(tmp2);

				applyNests(src, tmp1);
				remapJar(tmp1, tmp2);
				// TinyRemapper shuffles the classes
				sortJar(src, tmp2, dst); // also copies over non-class files

				Files.delete(tmp1);
				Files.delete(tmp2);
			} else {
				Path tmp = Files.createTempFile("tmp", ".jar");

				applyNests(src, tmp);
				sortJar(src, tmp, dst); // copy over non-class files
			}

			if (!options.silent) {
				System.out.println("Done!");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void applyNests(Path src, Path dst) {
		try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(dst.toFile()))) {
			for (ClassNode newClass : jar.getNewClasses()) {
				JarEntry entry = new JarEntry(newClass.name + ".class");
				ClassWriter writer = new ClassWriter(0);
				ClassVisitor visitor = new NestedClassAttributeClassVisitor(Opcodes.ASM9, writer);

				visitor.visit(
					newClass.version,
					newClass.access,
					newClass.name,
					null,
					newClass.superName,
					null
				);
				visitor.visitEnd();

				jos.putNextEntry(entry);
				jos.write(writer.toByteArray());
				jos.flush();
				jos.closeEntry();
			}

			try (JarInputStream jis = new JarInputStream(new FileInputStream(src.toFile()))) {
				for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
					if (entry.getName().endsWith(".class")) {
						ClassReader reader = new ClassReader(jis);
						ClassWriter writer = new ClassWriter(reader, 0);
						ClassVisitor visitor = new NestedClassAttributeClassVisitor(Opcodes.ASM9, writer);

						reader.accept(visitor, 0);

						jos.putNextEntry(new JarEntry(entry.getName()));
						jos.write(writer.toByteArray());
						jos.flush();
						jos.closeEntry();
					}
				}
			}

			if (!options.silent) {
				System.out.println("Applied nests...");
			}

			jos.finish();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void remapJar(Path src, Path dst) {
		mappings.clear();

		TinyRemapper remapper = TinyRemapper.newRemapper().withMappings(ma -> {
			for (ClassNode clazz : nests.keySet()) {
				String oldName = clazz.name;
				String newName = remap(clazz.name);

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

		if (!options.silent) {
			System.out.println("Remapped nested classes...");
		}
	}

	private String remap(String className) {
		String mapping = mappings.get(className);

		if (mapping == null) {
			mapping = map(className);
			mappings.put(className, mapping);
		}

		return mapping;
	}

	private String map(String className) {
		ClassNode clazz = jar.getClass(className);

		if (clazz == null) {
			return className;
		}

		Map<ClassNode, ClassNest> referencedNests = nests.get(clazz);

		if (referencedNests == null) {
			return className;
		}

		ClassNest nest = referencedNests.get(clazz);

		if (nest == null) {
			return className;
		}

		return remap(nest.enclClass.name) + "$" + nest.innerName;
	}

	private void sortJar(Path src, Path nestedSrc, Path dst) {
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

			if (!options.silent) {
				System.out.println("Moved over non-class files...");
			}

			JarFile nestedSrcJar = new JarFile(nestedSrc.toFile());

			for (ClassNode c : jar.getClasses()) {
				String entryName = remap(c.name) + ".class";
				JarEntry entry = nestedSrcJar.getJarEntry(entryName);

				try (InputStream jis = nestedSrcJar.getInputStream(entry)) {
					jos.putNextEntry(new JarEntry(entryName));

					while ((read = jis.read(buffer)) > 0) {
						jos.write(buffer, 0, read);
					}

					jos.flush();
					jos.closeEntry();
				}
			}

			nestedSrcJar.close();

			if (!options.silent) {
				System.out.println("Sorted class files...");
			}

			jos.finish();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private class NestedClassAttributeClassVisitor extends ClassVisitor {

		private ClassNode clazz;
		private Collection<ClassNest> nests;

		private NestedClassAttributeClassVisitor(int api) {
			super(api);
		}

		private NestedClassAttributeClassVisitor(int api, ClassVisitor classVisitor) {
			super(api, classVisitor);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			this.clazz = Nester.this.jar.getClass(name);

			if (this.clazz != null) {
				Map<ClassNode, ClassNest> referencedNests = Nester.this.nests.get(this.clazz);

				if (referencedNests != null) {
					this.nests = referencedNests.values();
				}
			}
			if (this.nests == null) {
				this.nests = Collections.emptySet();
			}

			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public void visitEnd() {
			for (ClassNest nest : nests) {
				visitNest(nest);
			}

			super.visitEnd();
		}

		private void visitNest(ClassNest nest) {
			if (nest.clazz.equals(clazz)) {
				visitOuterClass(nest);
			}

			visitInnerClass(nest);
		}

		private void visitOuterClass(ClassNest nest) {
			if (nest.isAnonymous() || nest.isLocal()) {
				visitOuterClass(
					nest.enclClass.name,
					nest.enclMethod == null ? null : nest.enclMethod.name,
					nest.enclMethod == null ? null : nest.enclMethod.desc
				);
			}
		}

		private void visitInnerClass(ClassNest nest) {
			visitInnerClass(
				nest.clazz.name,
				nest.isInner() ? nest.enclClass.name : null,
				nest.isInner() || nest.isLocal() ? stripLocalClassPrefix(nest.innerName) : null,
				nest.innerAccess
			);
		}

		private String stripLocalClassPrefix(String innerName) {
			int idx = 0;

			// local class names start with a number prefix
			while (idx < innerName.length() && Character.isDigit(innerName.charAt(idx))) {
				idx++;
			}
			// if entire inner name is a number, this class is anonymous, not local
			if (idx == innerName.length()) {
				idx = 0;
			}

			return innerName.substring(idx);
		}
	}
}
