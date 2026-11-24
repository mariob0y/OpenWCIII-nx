package com.etheller.warsmash.desktop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;

/**
 * PC test tool (env NX_GLCOUNT): counts the GL calls the game makes through Gdx.gl/gl20/gl30, per
 * function, and every 600 frames prints the per-frame averages of the busiest ones, with how many of
 * them only set a state that was already set (a uniform to its current value in the current program,
 * a capability already on, the bound buffer or texture again). The console runs every GL call through
 * its GL-to-Vulkan layer, so the number of calls per frame matters there.
 */
final class GlCallCounter {
	static final boolean ENABLED = System.getenv("NX_GLCOUNT") != null;
	private static final int REPORT_FRAMES = 600;
	private static final Map<String, long[]> COUNTS = new HashMap<>(); // name -> {calls, redundant}
	private static final Map<String, Object> STATE = new HashMap<>();
	private static int frames;
	private static int program;
	private static int activeUnit;

	private GlCallCounter() {
	}

	static void install() {
		final GL30 real = Gdx.gl30;
		if (real == null) {
			System.out.println("NX_GLCOUNT: no GL30, not counting");
			return;
		}
		final GL30 counting = (GL30) Proxy.newProxyInstance(GL30.class.getClassLoader(), new Class<?>[] { GL30.class },
				(proxy, method, args) -> {
					final String name = method.getName();
					long[] count = COUNTS.get(name);
					if (count == null) {
						count = new long[2];
						COUNTS.put(name, count);
					}
					count[0]++;
					if (redundant(name, args)) {
						count[1]++;
					}
					try {
						return method.invoke(real, args);
					}
					catch (final InvocationTargetException e) {
						throw e.getCause();
					}
				});
		Gdx.gl = counting;
		Gdx.gl20 = counting;
		Gdx.gl30 = counting;
		System.out.println("NX_GLCOUNT: counting GL calls");
	}

	/** Records the state the call sets; true if it was set to that already. */
	private static boolean redundant(final String name, final Object[] args) {
		final String key;
		final Object value;
		switch (name) {
		case "glUseProgram":
			key = "program";
			value = args[0];
			program = (Integer) args[0];
			break;
		case "glLinkProgram":
		case "glDeleteProgram":
			STATE.keySet().removeIf(k -> k.startsWith("u" + args[0] + ":"));
			return false;
		case "glEnable":
			key = "cap" + args[0];
			value = Boolean.TRUE;
			break;
		case "glDisable":
			key = "cap" + args[0];
			value = Boolean.FALSE;
			break;
		case "glDepthMask":
		case "glBlendFunc":
		case "glBlendFuncSeparate":
		case "glDepthFunc":
		case "glCullFace":
			key = "blend".equals(name) ? name : (name.startsWith("glBlendFunc") ? "blendfunc" : name);
			value = Arrays.asList(args);
			break;
		case "glActiveTexture":
			key = "activeTexture";
			value = args[0];
			activeUnit = (Integer) args[0];
			break;
		case "glBindTexture":
			key = "tex" + activeUnit + ":" + args[0];
			value = args[1];
			break;
		case "glBindBuffer":
			key = "buf" + args[0];
			value = args[1];
			break;
		case "glBindVertexArray":
			STATE.remove("buf" + GL20.GL_ELEMENT_ARRAY_BUFFER);
			key = "vao";
			value = args[0];
			break;
		case "glVertexAttribPointer": {
			final List<Object> v = new ArrayList<>(Arrays.asList(args));
			v.add(STATE.get("buf" + GL20.GL_ARRAY_BUFFER));
			key = "vap" + STATE.get("vao") + ":" + args[0];
			value = v;
			break;
		}
		case "glVertexAttribDivisor":
			key = "div" + STATE.get("vao") + ":" + args[0];
			value = args[1];
			break;
		case "glEnableVertexAttribArray":
		case "glDisableVertexAttribArray":
			key = "vaa" + STATE.get("vao") + ":" + args[0];
			value = name;
			break;
		default:
			if (name.startsWith("glUniform")) {
				key = "u" + program + ":" + args[0];
				value = uniformValue(name, args);
				break;
			}
			return false;
		}
		final Object old = STATE.put(key, value);
		return value.equals(old);
	}

	private static Object uniformValue(final String name, final Object[] args) {
		final List<Object> value = new ArrayList<>();
		value.add(name);
		for (int i = 1; i < args.length; i++) {
			final Object arg = args[i];
			if (arg instanceof float[]) {
				value.add(Arrays.toString((float[]) arg));
			}
			else if (arg instanceof int[]) {
				value.add(Arrays.toString((int[]) arg));
			}
			else if (arg instanceof FloatBuffer) {
				final FloatBuffer b = ((FloatBuffer) arg).duplicate();
				final float[] f = new float[b.remaining()];
				b.get(f);
				value.add(Arrays.toString(f));
			}
			else if (arg instanceof IntBuffer) {
				final IntBuffer b = ((IntBuffer) arg).duplicate();
				final int[] f = new int[b.remaining()];
				b.get(f);
				value.add(Arrays.toString(f));
			}
			else if (arg instanceof Buffer) {
				value.add(new Object()); // never equal
			}
			else {
				value.add(arg);
			}
		}
		return value;
	}

	static void endFrame() {
		if (++frames < REPORT_FRAMES) {
			return;
		}
		final List<Map.Entry<String, long[]>> entries = new ArrayList<>(COUNTS.entrySet());
		entries.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
		long total = 0;
		long totalRedundant = 0;
		for (final Map.Entry<String, long[]> entry : entries) {
			total += entry.getValue()[0];
			totalRedundant += entry.getValue()[1];
		}
		final StringBuilder line = new StringBuilder("NX_GLCOUNT per frame (calls/already set): total ")
				.append(String.format("%.0f/%.0f", total / (double) frames, totalRedundant / (double) frames));
		for (int i = 0; (i < entries.size()) && (i < 30); i++) {
			final long[] c = entries.get(i).getValue();
			line.append(", ").append(entries.get(i).getKey()).append(' ')
					.append(String.format("%.0f/%.0f", c[0] / (double) frames, c[1] / (double) frames));
		}
		System.out.println(line);
		COUNTS.clear();
		frames = 0;
	}
}
