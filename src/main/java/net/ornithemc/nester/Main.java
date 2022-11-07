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
			case "--nestJar":
				nestJar(args);
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
		System.out.println("  --nestJar <source jar> <destination jar> <nests file>");
	}

	private static void nestJar(String[] args) {
		if (args.length == 4) {
			Path src = Paths.get(args[1]);
			Path dst = Paths.get(args[2]);
			Path nests = Paths.get(args[3]);

			Nester.nestJar(src, dst, nests);
		} else {
			System.out.println("Incorrect number of arguments! Expected 4, got " + args.length + "...");
			printUsage();
		}
	}
}
