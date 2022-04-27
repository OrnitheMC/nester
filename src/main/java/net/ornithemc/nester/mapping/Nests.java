package net.ornithemc.nester.mapping;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class Nests {

	public static Nests of(Path mappings) {
		Nests nests;

		try (BufferedReader br = new BufferedReader(new FileReader(mappings.toFile()))) {
			nests = of(br);
		} catch (IOException e) {
			nests = empty();
		}

		return nests;
	}

	public static Nests of(BufferedReader reader) throws IOException {
		Nests nests = empty();
		NesterIo.read(nests, reader);
		return nests;
	}

	public static Nests empty() {
		return new Nests();
	}

	private final Collection<Nest> all;
	private final Map<String, Collection<Nest>> byClass;

	private Nests() {
		this.all = new LinkedHashSet<>();
		this.byClass = new LinkedHashMap<>();
	}

	public Collection<Nest> get() {
		return all;
	}

	public Collection<Nest> get(String className) {
		return byClass.get(className);
	}

	public void add(Nest nest) {
		all.add(nest);
		add(nest, nest.className);
		add(nest, nest.enclClassName);
	}

	private void add(Nest nest, String className) {
		byClass.computeIfAbsent(className, key -> new LinkedHashSet<>()).add(nest);
	}
}
