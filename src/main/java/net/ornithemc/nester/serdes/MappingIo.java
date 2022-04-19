package net.ornithemc.nester.serdes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import net.ornithemc.nester.jar.SourceJar;
import net.ornithemc.nester.jar.node.ClassNode;
import net.ornithemc.nester.jar.node.MethodNode;
import net.ornithemc.nester.jar.node.Node;
import net.ornithemc.nester.jar.node.proto.ProtoClassNode;
import net.ornithemc.nester.jar.node.proto.ProtoMethodNode;

public class MappingIo {

	public static void read(SourceJar jar, Path mappings) {
		try (BufferedReader br = new BufferedReader(new FileReader(mappings.toFile()))) {
			String line;

			while ((line = br.readLine()) != null) {
				String[] args = line.split("\\s");

				if (args.length != 6) {
					System.out.println("Incorrect number of arguments for mapping \'" + line + "\' - expected 6, got " + args.length + "...");
					continue;
				}

				String className = args[0];
				String enclClassName = args[1];
				String enclMethodName = args[2];
				String enclMethodDesc = args[3];
				String innerName = args[4];
				String accessString = args[5];

				if (className == null || className.isEmpty()) {
					System.out.println("Invalid mapping \'" + line + "\': missing class name argument!");
					continue;
				}
				if (enclClassName == null || enclClassName.isEmpty()) {
					System.out.println("Invalid mapping \'" + line + "\': missing enclosing class name argument!");
					continue;
				}

				boolean emptyName = (enclMethodName == null) || enclMethodName.isEmpty();
				boolean emptyDesc = (enclMethodDesc == null) || enclMethodDesc.isEmpty();

				if (emptyName || emptyDesc) {
					enclMethodName = null;
					enclMethodDesc = null;
				}

				if (innerName != null && innerName.isEmpty()) {
					innerName = null;
				}

				Integer access = null;

				try {
					access = Integer.parseInt(accessString);
				} catch (NumberFormatException e) {

				}

				if (access == null || access < 0) {
					System.out.println("Invalid mapping \'" + line + "\': invalid access flags!");
					continue;
				}

				nestClass(jar, className, enclClassName, enclMethodName, enclMethodDesc, innerName, access);
			}
		} catch (IOException e) {

		}
	}

	private static void nestClass(SourceJar jar, String className, String enclClassName, String enclMethodName, String enclMethodDesc, String innerName, int access) {
		ClassNode clazz = jar.getClass(className);

		if (clazz == null) {
			System.out.println("ignoring mapping for class \'" + className + "\': class does not exist!");
			return;
		}

		ClassNode enclClass = jar.getClass(enclClassName);

		if (enclClass == null) {
			System.out.println("ignoring mapping for class \'" + className + "\': enclosing class \'" + enclClassName + "\' does not exist!");
			return;
		}

		MethodNode method = null;

		if (enclMethodName != null) {
			method = enclClass.getMethod(enclMethodName, enclMethodDesc);

			if (method == null) {
				System.out.println("ignoring mapping for class \'" + className + "\': enclosing method \'" + enclMethodName + "\' does not exist!");
				return;
			}
		}

		clazz.enableAccess(access);

		if (innerName == null) {
			enclClass.addAnonymousClass(method, clazz);
		} else {
			enclClass.addInnerClass(clazz);
		}
	}

	public static void write(SourceJar jar, Path mappings) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(mappings.toFile()))) {
			for (ClassNode clazz : jar.getClasses()) {
				if (!clazz.hasParent()) {
					continue;
				}

				Node parent = clazz.getParent();

				ClassNode enclClass = null;
				MethodNode enclMethod = null;

				if (parent.isClass()) {
					enclClass = parent.asClass();
				} else if (parent.isMethod()) {
					enclMethod = parent.asMethod();
					enclClass = enclMethod.getParent();
				} else {
					System.out.println("Unable to write mapping for class \'" + clazz.getName() + "\': parent class \'" + parent.getName() + "\' is neither a class nor a method!");
					continue;
				}

				nestClass(bw, clazz, enclClass, enclMethod);
			}
		} catch (IOException e) {

		}
	}

	private static void nestClass(BufferedWriter bw, ClassNode clazz, ClassNode enclClass, MethodNode enclMethod) throws IOException {
		// Remapping class names happens after fixing inner class attributes
		// so these nester mappings must use the proto names.
		ProtoClassNode protoClass = clazz.proto();
		ProtoClassNode protoEnclClass = enclClass.proto();

		String className = protoClass.getName();
		String enclClassName = protoEnclClass.getName();
		String enclMethodName = "";
		String enclMethodDesc = "";
		String innerName = clazz.getSimpleName();
		String access = String.valueOf(clazz.getAccess());

		if (enclMethod != null) {
			ProtoMethodNode protoEnclMethod = enclMethod.proto();

			enclMethodName = protoEnclMethod.getName();
			enclMethodDesc = protoEnclMethod.getDescriptor();
		}
		if (innerName == null) {
			innerName = "";
		}

		bw.write(className);
		bw.write("\t");
		bw.write(enclClassName);
		bw.write("\t");
		bw.write(enclMethodName);
		bw.write("\t");
		bw.write(enclMethodDesc);
		bw.write("\t");
		bw.write(innerName);
		bw.write("\t");
		bw.write(access);

		bw.newLine();
	}
}
