package com.etheller.warsmash.parsers.fdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.fdfparser.FDFLexer;
import com.etheller.warsmash.fdfparser.FDFParser;
import com.etheller.warsmash.fdfparser.FDFParserBuilder;

public class DataSourceFDFParserBuilder implements FDFParserBuilder {
	private final DataSource dataSource;

	public DataSourceFDFParserBuilder(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public FDFParser build(final String path) {
		FDFLexer lexer;
		try {
			// warsmash-nx: was CharStreams.fromStream, which needs nio's CharsetDecoder and heap
			// CharBuffer/IntBuffer (missing on the console's Java runtime). The same characters: the
			// file decoded as UTF-8 with malformed input replaced (as fromStream does), into ANTLR's
			// char[]-based stream. Differs only in counting a supplementary character as two chars.
			lexer = new FDFLexer(new ANTLRInputStream(readUtf8(this.dataSource.getResourceAsStream(path))));
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		final FDFParser fdfParser = new FDFParser(new CommonTokenStream(lexer));
		final BaseErrorListener errorListener = new BaseErrorListener() {
			@Override
			public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
					final int charPositionInLine, final String msg, final RecognitionException e) {
				String sourceName = path;
				if (!sourceName.isEmpty()) {
					sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine);
				}

				System.err.println(sourceName + "line " + line + ":" + charPositionInLine + " " + msg);
			}
		};
		fdfParser.addErrorListener(errorListener);
		return fdfParser;
	}

	private static String readUtf8(final InputStream in) throws IOException {
		try (InputStream stream = in) {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			int read;
			while ((read = stream.read(buffer)) != -1) {
				bytes.write(buffer, 0, read);
			}
			return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
		}
	}
}