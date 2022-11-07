package net.ornithemc.nester.nest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class NesterIo {

	private static final String TAB = "\t";

	public static void read(Nests nests, Path mappings) {
		try (BufferedReader br = new BufferedReader(new FileReader(mappings.toFile()))) {
			read(nests, br);
		} catch (IOException e) {

		}
	}

	public static void read(Nests nests, BufferedReader br) throws IOException {
		String line;

		while ((line = br.readLine()) != null) {
			String[] args = line.split(TAB);

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
			if (innerName == null || innerName.isEmpty()) {
				System.out.println("Invalid mapping \'" + line + "\': missing inner class name argument!");
				continue;
			}

			boolean emptyName = (enclMethodName == null) || enclMethodName.isEmpty();
			boolean emptyDesc = (enclMethodDesc == null) || enclMethodDesc.isEmpty();

			if (emptyName || emptyDesc) {
				enclMethodName = null;
				enclMethodDesc = null;
			}

			int idx = 0;

			while (idx < innerName.length() && Character.isDigit(innerName.charAt(idx))) {
				idx++;
			}

			int access = -1;

			try {
				access = Integer.parseInt(accessString);

				if (access < 0) {
					System.out.println("Invalid mapping \'" + line + "\': invalid access flags!");
					continue;
				}
			} catch (NumberFormatException e) {

			}

			NestType type = (idx == innerName.length()) ? NestType.ANONYMOUS : ((idx == 0) ? NestType.INNER : NestType.LOCAL);
			Nest nest = new Nest(type, className, enclClassName, enclMethodName, enclMethodDesc, innerName, access);

			nests.add(nest);
		}
	}

	public static void write(Nests nests, Path mappings) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(mappings.toFile()))) {
			write(nests, bw);
		} catch (IOException e) {

		}
	}

	public static void write(Nests nests, BufferedWriter bw) throws IOException {
		for (Nest nest : nests) {
			String className = nest.className;
			String enclClassName = nest.enclClassName;
			String enclMethodName = nest.enclMethodName;
			String enclMethodDesc = nest.enclMethodDesc;
			String innerName = nest.innerName;
			String access = Integer.toString(nest.access);

			if (enclMethodName == null || enclMethodDesc == null) {
				enclMethodName = "";
				enclMethodDesc = "";
			}

			bw.write(className);
			bw.write(TAB);
			bw.write(enclClassName);
			bw.write(TAB);
			bw.write(enclMethodName);
			bw.write(TAB);
			bw.write(enclMethodDesc);
			bw.write(TAB);
			bw.write(innerName);
			bw.write(TAB);
			bw.write(access);

			bw.newLine();
		}
	}
}
