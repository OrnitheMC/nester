package net.ornithemc.nester;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

	public static void main(String... args) {
		if (args.length < 1) {
			System.out.println("No command given!");
			printUsage();

			System.exit(1);
		}

		String command = args[0];

		switch (command) {
		case "--fixJar":
			System.exit(fixJar(args));
		case "--generateMappings":
			System.exit(generateMappings(args));
		default:
			System.out.println("Unknown command!");
			printUsage();

			System.exit(1);
		}
	}

	private static void printUsage() {
		System.out.println("Correct usage:");
		System.out.println("  --fixJar <source jar> <destination jar>");
		System.out.println("   OR");
		System.out.println("  --fixJar <source jar> <destination jar> <mappings file>");
		System.out.println("   OR");
		System.out.println("  --generateMappings <source jar> <mappings file>");
	}

	private static int fixJar(String[] args) {
		if (args.length < 3 || args.length > 4) {
			System.out.println("Incorrect number of arguments! Expected 3 or 4, got " + args.length + "...");
			printUsage();

			return 1;
		}

		Path src = Paths.get(args[1]);

		if (!Files.isReadable(src) || !Files.isRegularFile(src)) {
			System.out.println("Cannot read source jar!");

			return 1;
		}

		Path dst = Paths.get(args[2]);

		if (Files.exists(dst) && !Files.isWritable(dst)) {
			System.out.println("Cannot write destination jar! " + args[1]);

			return 1;
		}

		if (args.length == 3) {
			Nester.fixJar(src, dst);

			return 0;
		}

		Path mappings = Paths.get(args[3]);

		if (!Files.isReadable(mappings) || !Files.isRegularFile(mappings)) {
			System.out.println("Cannot read mappings file!");

			return 1;
		}

		Nester.fixJar(src, dst, mappings);

		return 0;
	}

	private static int generateMappings(String[] args) {
		if (args.length != 3) {
			System.out.println("Incorrect number of arguments! Expected 3, got " + args.length + "...");
			printUsage();

			return 1;
		}

		Path src = Paths.get(args[1]);

		if (!Files.isReadable(src) || !Files.isRegularFile(src)) {
			System.out.println("Cannot read source jar!");

			return 1;
		}

		Path mappings = Paths.get(args[2]);

		if (Files.exists(mappings) && !Files.isWritable(mappings)) {
			System.out.println("Cannot write mappings file!");

			return 1;
		}

		Nester.generateMappings(src, mappings);

		return 0;
	}
}
