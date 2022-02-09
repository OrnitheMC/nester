package net.ornithemc.nester;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main
{
    public static void main(String... args) {
        if (args.length != 3) {
            System.out.println("Incorrect number of arguments! Expected 3, got " + args.length + "...");
            System.out.println("Correct usage: <source jar> <destination jar> <mappings>");

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

        Path mappings = Paths.get(args[2]);

        if (!Files.isReadable(mappings) || !Files.isRegularFile(mappings)) {
            System.out.println("Cannot read mappings!");
            System.exit(1);
        }

        Nester nester = new Nester(src, dst);

        nester.readMappings(mappings);
        nester.fixJar();

        System.exit(0);
    }
}
