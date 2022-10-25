package net.ornithemc.nester.mapping;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class Nests implements Iterable<Nest> {

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

	private final Map<String, Nest> all;

	private Nests() {
		this.all = new LinkedHashMap<>();
	}

	@Override
	public Iterator<Nest> iterator() {
		return all.values().iterator();
	}

	public Nest get(String className) {
		return all.get(className);
	}

	public void add(Nest nest) {
		all.put(nest.className, nest);
	}
}
