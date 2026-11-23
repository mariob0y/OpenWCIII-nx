package wsnx.probeb.gson;

import java.util.List;
import java.util.Map;

/**
 * Probe B step "w2": a small model of how Warsmash's ability builder uses Gson
 * (AbilityBuilderGsonBuilder: plain fields, enums, an abstract base resolved through
 * RuntimeTypeAdapterFactory by a "type" label, and List<Base> fields). Kept whole by config.json
 * (nonOptimized wsnx.probeb.gson.**): Gson reaches every field and constructor by reflection.
 */
public final class GsonModel {
	private GsonModel() {
	}

	public enum Kind {
		ATTACK, MOVE, CAST
	}

	public static class Plain {
		String name;
		int count;
		float speed;
		double ratio;
		boolean flag;
		long big;
		int[] ints;
		String missing;
		Plain child;
		transient int skipped = 7;
	}

	public abstract static class Shape {
	}

	public static class Circle extends Shape {
		float radius;
	}

	public static class Square extends Shape {
		int side;
		String label;
	}

	public static class Holder {
		public Shape main;
		public Shape other;
	}

	public static class WithEnum {
		public Kind kind;
		public Kind[] kinds;
	}

	public static class WithList {
		public List<String> tags;
	}

	public static class WithShapes {
		public List<Shape> shapes;
	}

	public static class WithMap {
		public Map<String, Integer> counts;
	}

	public static String describe(final Plain p) {
		if (p == null) {
			return "null";
		}
		final StringBuilder s = new StringBuilder();
		s.append(p.name).append('/').append(p.count).append('/').append(p.speed).append('/').append(p.ratio).append('/')
				.append(p.flag).append('/').append(p.big).append('/');
		if (p.ints == null) {
			s.append("null");
		}
		else {
			for (final int i : p.ints) {
				s.append(i).append(',');
			}
		}
		s.append('/').append(p.missing).append('/').append(p.skipped).append("/child{").append(describe(p.child)).append('}');
		return s.toString();
	}

	public static String describe(final Shape shape) {
		if (shape instanceof Circle) {
			return "Circle(" + ((Circle) shape).radius + ")";
		}
		if (shape instanceof Square) {
			return "Square(" + ((Square) shape).side + "," + ((Square) shape).label + ")";
		}
		return shape == null ? "null" : shape.getClass().getName();
	}

	public static String describeShapes(final List<Shape> shapes) {
		if (shapes == null) {
			return "null";
		}
		final StringBuilder s = new StringBuilder(shapes.getClass().getName()).append('[');
		for (final Object o : shapes) { // Object: a wrong element type must print, not throw
			s.append(o instanceof Shape ? describe((Shape) o) : o == null ? "null" : o.getClass().getName() + ":" + o).append(' ');
		}
		return s.append(']').toString();
	}

	public static String describeList(final List<String> list) {
		if (list == null) {
			return "null";
		}
		final StringBuilder s = new StringBuilder(list.getClass().getName()).append('[');
		for (final Object o : list) {
			s.append(o == null ? "null" : o.getClass().getSimpleName() + ":" + o).append(' ');
		}
		return s.append(']').toString();
	}

	public static String describeMap(final Map<String, Integer> map) {
		if (map == null) {
			return "null";
		}
		final StringBuilder s = new StringBuilder(map.getClass().getName()).append('{');
		for (final Map.Entry<String, Integer> e : map.entrySet()) {
			final Object v = e.getValue();
			s.append(e.getKey()).append('=').append(v == null ? "null" : v.getClass().getSimpleName() + ":" + v).append(' ');
		}
		return s.append('}').toString();
	}
}
