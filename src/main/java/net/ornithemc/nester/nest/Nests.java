package net.ornithemc.nester.nest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.ornithemc.nester.NesterException;

public class Nests implements Iterable<Nest> {

	public static Nests of(Path mappings) {
		Nests nests;

		try (BufferedReader br = new BufferedReader(new FileReader(mappings.toFile()))) {
			nests = of(br);
		} catch (IOException e) {
			throw new NesterException("unable to read nests", e);
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
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Nests)) {
			return false;
		}

		return all.equals(((Nests)obj).all);
	}

	@Override
	public int hashCode() {
		return all.hashCode();
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

	public boolean isEmpty() {
		return all.isEmpty();
	}
}
