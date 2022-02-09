package net.ornithemc.nester;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.objectweb.asm.Type;

public class MappingReader
{
    private final BiConsumer<String, String> mappingConsumer;
    private final BiConsumer<String, NestedClassReference> referenceConsumer;

    private final Map<String, NestedClassReference> unmappedReferences;
    private final Map<String, String> classMappings;

    private final Map<String, Integer> anonymousClassCounters;

    public MappingReader(BiConsumer<String, String> mappingConsumer, BiConsumer<String, NestedClassReference> referenceConsumer) {
        this.mappingConsumer = mappingConsumer;
        this.referenceConsumer = referenceConsumer;

        this.unmappedReferences = new HashMap<>();
        this.classMappings = new HashMap<>();

        this.anonymousClassCounters = new HashMap<>();
    }

    public void read(Path mappings) {
        unmappedReferences.clear();
        classMappings.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(mappings.toFile()))) {
            String line;

            while ((line = br.readLine()) != null) {
                parseLine(line);
            }
        } catch (IOException e) {

        }

        unpackReferences();
    }

    private void parseLine(String line) {
        String[] args = line.split("\\s");

        if (args.length != 6) {
            System.out.println("Incorrect number of arguments for mapping \'" + line + "\' - expected 6, got " + args.length + "...");
            return;
        }

        String className = args[0];
        String enclClassName = args[1];
        String enclMethodName = args[2];
        String enclMethodDesc = args[3];
        String innerName = args[4];
        String rawAccess = args[5];

        if (className == null || className.isEmpty()) {
            System.out.println("Invalid mapping \'" + line + "\': missing class name argument!");
            return;
        }
        if (enclClassName == null || enclClassName.isEmpty()) {
            System.out.println("Invalid mapping \'" + line + "\': missing enclosing class name argument!");
            return;
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

        Integer accessFlags = null;

        try {
            accessFlags = Integer.parseInt(rawAccess);
        } catch (NumberFormatException e) {
            
        }

        if (accessFlags == null || accessFlags < 0) {
            System.out.println("Invalid mapping \'" + line + "\': invalid access flags!");
            return;
        }

        NestedClassReference ref = new NestedClassReference(
                className,
                enclClassName,
                enclMethodName,
                enclMethodDesc,
                innerName,
                accessFlags
        );

        unmappedReferences.put(className, ref);
    }

    private void unpackReferences() {
        for (NestedClassReference unmappedRef : unmappedReferences.values()) {
            String className = unmappedRef.className;
            String classMapping = getMapping(className);

            mappingConsumer.accept(className, classMapping);

            String enclClassName = unmappedRef.enclosingClassName;
            String enclClassMapping = getMapping(enclClassName);

            String enclMethodName = unmappedRef.enclosingMethodName;
            String enclMethodDesc = null;

            if (enclMethodName != null) {
                enclMethodDesc = fixMethodDesc(unmappedRef.enclosingMethodDesc);
            }

            String innerName = unmappedRef.innerName;
            int accessFlags = unmappedRef.accessFlags;

            NestedClassReference ref = new NestedClassReference(
                    classMapping,
                    enclClassMapping,
                    enclMethodName,
                    enclMethodDesc,
                    innerName,
                    accessFlags
            );

            referenceConsumer.accept(classMapping, ref);
            referenceConsumer.accept(enclClassMapping, ref);
        }
    }

    private String getMapping(String className) {
        String mapping = classMappings.get(className);

        if (mapping != null) {
            return mapping;
        }

        NestedClassReference ref = unmappedReferences.get(className);

        if (ref == null) {
            return className;
        }

        String outer = getMapping(ref.enclosingClassName);
        String inner = ref.innerName;

        if (inner == null) {
            int i = anonymousClassCounters.compute(outer, (k, v) -> {
                if (v == null) {
                    v = 0;
                }

                return v + 1;
            });
            inner = String.valueOf(i);
        }

        classMappings.put(className, (mapping = outer + "$" + inner));

        return mapping;
    }

    private String fixMethodDesc(String methodDesc) {
        Type desc = Type.getMethodType(methodDesc);

        Type[] args = desc.getArgumentTypes();
        Type ret = desc.getReturnType();

        for (int i = 0; i < args.length; i++) {
            args[i] = fixType(args[i]);
        }
        ret = fixType(ret);

        return Type.getMethodDescriptor(ret, args);
    }

    private Type fixType(Type type) {
        switch (type.getSort()) {
        case Type.OBJECT:
            String className = type.getInternalName();
            className = getMapping(className);

            return Type.getObjectType(className);
        case Type.ARRAY:
            int dim = type.getDimensions();
            Type elementType = type.getElementType();
            elementType = fixType(elementType);

            String desc = "";

            for (int i = 0; i < dim; i++) {
                desc += "[";
            }

            desc += elementType.getDescriptor();

            return Type.getType(desc);
        }

        return type;
    }
}
