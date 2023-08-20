package cn.nukkit.level.generator.template;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class IdMapper<T> implements Iterable<T> {
	private final HashMap<T, Integer> tToId;
	private final List<T> idToT;
	private int nextId;

	public IdMapper() {
		this(1 << 4);
	}

	public IdMapper(final int expectedSize) {
		idToT = Lists.newArrayListWithExpectedSize(expectedSize);
		tToId = Maps.newHashMapWithExpectedSize(expectedSize);
	}

	@Override
	public Iterator<T> iterator() {
		return Iterators.filter(idToT.iterator(), Objects::nonNull);
	}

	public void addMapping(final T t, final int id) {
		tToId.put(t, id);
		while (idToT.size() <= id) {
			idToT.add(null);
		}
		idToT.set(id, t);
		if (nextId <= id) {
			nextId = id + 1;
		}
	}

	public void add(final T t) {
		addMapping(t, nextId);
	}

	public int getId(final T t) {
		final Integer id = tToId.get(t);
		return id == null ? -1 : id;
	}

	public final T byId(final int id) {
		if (id >= 0 && id < idToT.size()) {
			return idToT.get(id);
		}
		return null;
	}

	public int size() {
		return tToId.size();
	}
}
