package net.ornithemc.nester;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

	public static void main(String... args) {
		if (args.length != 2) {
			System.out.println("Incorrect number of arguments! Expected 2, got " + args.length + "...");
			System.out.println("Correct usage: <source jar> <destination jar>");

			System.exit(1);
		}

		Path src = Paths.get(args[0]);

		if (!Files.isReadable(src) || !Files.isRegularFile(src)) {
			System.out.println("Cannot read source jar!");
			System.exit(1);
		}

		Path dst = Paths.get(args[1]);

		if (Files.exists(dst) && !Files.isWritable(dst)) {
			System.out.println("Cannot write destination jar! " + args[1]);
			System.exit(1);
		}

		Nester.run(src, dst);

		System.exit(0);
	}
}
