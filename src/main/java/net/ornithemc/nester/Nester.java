package net.ornithemc.nester;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class Nester
{
    private final Path src;
    private final Path dst;

    private final Map<String, String> classMappings;
    private final Map<String, Set<NestedClassReference>> nestedClassReferences;

    public Nester(Path src, Path dst) {
        this.src = src;
        this.dst = dst;

        this.classMappings = new HashMap<>();
        this.nestedClassReferences = new HashMap<>();
    }

    public void readMappings(Path mappings) {
        classMappings.clear();
        nestedClassReferences.clear();

        MappingReader reader = new MappingReader((src, dst) -> classMappings.put(src, dst), (cls, ref) -> {
            nestedClassReferences.computeIfAbsent(cls, key -> new HashSet<>()).add(ref);
        });
        reader.read(mappings);

        if (nestedClassReferences.isEmpty()) {
            System.out.println("The provided mappings are empty or invalid!");
        }
    }

    public void fixJar() {
        try {
            Path tmp = Files.createTempFile("tmp", ".jar");

            Files.delete(tmp);

            remapJar(tmp);
            writeFixedJar(tmp);

            Files.delete(tmp);

            System.out.println("Done!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void remapJar(Path tmp) {
        TinyRemapper remapper = TinyRemapper.newRemapper().withMappings(ma -> {
            for (Entry<String, String> mapping : classMappings.entrySet()) {
                ma.acceptClass(mapping.getKey(), mapping.getValue());
            }
        }).build();

        try (OutputConsumerPath oc = new OutputConsumerPath.Builder(tmp).build()) {
            remapper.readInputs(src);
            remapper.apply(oc);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            remapper.finish();
        }

        System.out.println("Remapped nested class names...");
    }

    private void writeFixedJar(Path tmp) {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(dst.toFile()))) {
            try (JarInputStream jis = new JarInputStream(new FileInputStream(tmp.toFile()))) {
                for (JarEntry entry; (entry = jis.getNextJarEntry()) != null; ) {
                    if (entry.getName().endsWith(".class")) {
                        ClassReader reader = new ClassReader(jis);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {

                            private String name;
                            private Set<NestedClassReference> references;

                            @Override
                            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                                this.name = name;
                                this.references = nestedClassReferences.get(this.name);

                                super.visit(version, access, name, signature, superName, interfaces);
                            }

                            @Override
                            public void visitEnd() {
                                if (references != null) {
                                	for (NestedClassReference ref : references) {
                                		if (ref.className.equals(name) && ref.innerName == null) {
                                			visitOuterClass(ref.enclosingClassName, ref.enclosingMethodName, ref.enclosingMethodDesc);
                                		}

                                		addReference(ref);
                                	}
                                }

                                super.visitEnd();
                            }

                            private void addReference(NestedClassReference ref) {
                                String name = ref.className;
                                String outerName = ref.enclosingClassName;
                                String innerName = ref.innerName;
                                int access = ref.accessFlags;

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

            try (JarInputStream jis = new JarInputStream(new FileInputStream(src.toFile()))) {
                for (ZipEntry entry; (entry = jis.getNextEntry()) != null; ) {
                    if (!entry.getName().endsWith(".class")) {
                        jos.putNextEntry(new ZipEntry(entry.getName()));

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

            jos.finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
