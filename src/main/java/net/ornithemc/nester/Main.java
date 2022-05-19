package net.ornithemc.nester;

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

		try {
			switch (command) {
			case "--fixJar":
				tryFixJar(args);
				break;
			case "--generateMappings":
				tryGenerateMappings(args);
				break;
			default:
				System.out.println("Unknown command: " + command);
				printUsage();

				System.exit(1);
			}
		} catch (NesterException e) {
			System.out.println("Something went wrong...");
			e.printStackTrace();

			System.exit(1);
		}

		System.exit(0);
	}

	private static void printUsage() {
		System.out.println("Correct usage:");
		System.out.println("  --fixJar <source jar> <destination jar>");
		System.out.println("   OR");
		System.out.println("  --fixJar <source jar> <destination jar> <mappings file>");
		System.out.println("   OR");
		System.out.println("  --generateMappings <source jar> <mappings file>");
	}

	private static void tryFixJar(String[] args) {
		switch (args.length) {
		case 3:
			fixJar(args);
			break;
		case 4:
			fixJarWithMappings(args);
			break;
		default:
			System.out.println("Incorrect number of arguments! Expected 3 or 4, got " + args.length + "...");
			printUsage();
		}
	}

	private static void fixJar(String[] args) {
		Path src = Paths.get(args[1]);
		Path dst = Paths.get(args[2]);

		Nester.fixJar(src, dst);
	}

	private static void fixJarWithMappings(String[] args) {
		Path src = Paths.get(args[1]);
		Path dst = Paths.get(args[2]);
		Path mappings = Paths.get(args[3]);

		Nester.fixJar(src, dst, mappings);
	}

	private static void tryGenerateMappings(String[] args) {
		if (args.length == 3) {
			generateMappings(args);
		} else {
			System.out.println("Incorrect number of arguments! Expected 3, got " + args.length + "...");
			printUsage();

			return;
		}
	}

	private static void generateMappings(String[] args) {
		Path src = Paths.get(args[1]);
		Path mappings = Paths.get(args[2]);

		Nester.generateMappings(src, mappings);
	}
}
