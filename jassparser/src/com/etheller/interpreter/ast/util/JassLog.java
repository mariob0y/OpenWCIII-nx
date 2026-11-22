package com.etheller.interpreter.ast.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class JassLog {
	public static PrintStream jassLogWriter;

	public static PrintStream getWriter() {
		if (jassLogWriter == null) {
			new File("Logs").mkdir();
			try {
				jassLogWriter = new PrintStream(
						new FileOutputStream(new File("Logs/" + System.currentTimeMillis() + ".jass.log")));
			}
			catch (final FileNotFoundException e) {
				e.printStackTrace();
				jassLogWriter = System.err;
			}
		}
		return jassLogWriter;
	}

	private static final java.util.Map<String, Integer> RECOVERED = new java.util.HashMap<>();

	/**
	 * warsmash-nx: an error the script survived (see NativeJassFunction.call, GlobalScope.runOneThreadLooop).
	 * Each kind (where + exception + message) goes to the jass log and stderr in full the first time, then
	 * as a one-line count at 10, 100, 1000... occurrences.
	 */
	public static void reportRecovered(final String what, final Throwable t) {
		final String key = what + ": " + t;
		final Integer seen = RECOVERED.get(key);
		final int count = (seen == null ? 0 : seen) + 1;
		RECOVERED.put(key, count);
		if (count == 1) {
			System.err.println("JASS error, carrying on: " + key);
			report(t);
		}
		else if ((count == 10) || (count == 100) || (count == 1000) || (count == 10000)) {
			System.err.println("JASS error, carrying on (x" + count + "): " + key);
		}
	}

	public static void report(final Throwable t) {
		final PrintStream writer = getWriter();
		t.printStackTrace(writer);
		writer.flush();
		if (writer != System.err) {
			t.printStackTrace(System.err);
		}
	}
}
