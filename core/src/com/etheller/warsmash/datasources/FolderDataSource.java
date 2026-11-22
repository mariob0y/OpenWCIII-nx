package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import com.etheller.warsmash.util.LoadingStatus;

public class FolderDataSource implements DataSource {

	private final Path folderPath;
	private final Set<String> listfile;
	/** warsmash-nx: the listfile, lower case with '/' separators, for has() (see key()). */
	private final Set<String> lookup;

	/**
	 * warsmash-nx: the listing of each folder, by its absolute path. The menu builds its data sources
	 * again for every map it starts (MenuUI.internalStartMap -> parseDataSources), and on the console
	 * the walk takes about 2 minutes, with nothing drawn meanwhile. The game does not change its own
	 * data folders while it runs, so the first listing is reused.
	 */
	private static final Map<String, Set<String>> LISTFILES = new HashMap<>();
	private static final Map<String, Set<String>> LOOKUPS = new HashMap<>();

	/**
	 * warsmash-nx: a faster way to list a folder, set by the console launcher (a native readdir walk;
	 * the Java walk below costs two file-service calls per file there, ~100 s per boot).
	 */
	public interface FastLister {
		/** Every regular file below the folder, relative to it with '/' separators, one per line; null to fall back. */
		String list(String folder);
	}

	private static FastLister fastLister;

	public static void setFastLister(final FastLister lister) {
		fastLister = lister;
	}

	public FolderDataSource(final Path folderPath) {
		this.folderPath = folderPath;
		final String key = folderPath.toString();
		synchronized (LISTFILES) {
			final Set<String> known = LISTFILES.get(key);
			if (known != null) {
				this.listfile = known;
				this.lookup = LOOKUPS.get(key);
				return;
			}
		}
		this.listfile = new HashSet<>();
		final Path name = folderPath.getFileName();
		final String step = "Reading game files (" + (name == null ? folderPath.toString() : name.toString()) + ")";
		LoadingStatus.report(step);
		final String fast = (fastLister == null) ? null : fastLister.list(folderPath.toString());
		if (fast != null) {
			int start = 0;
			int end;
			while ((end = fast.indexOf('\n', start)) >= 0) {
				if (end > start) {
					this.listfile.add(fast.substring(start, end));
				}
				start = end + 1;
			}
		}
		else {
			try {
				walk(folderPath, folderPath, this.listfile, step);
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}
		LoadingStatus.report(step, this.listfile.size() + " files");
		this.lookup = new HashSet<>(this.listfile.size() * 2);
		for (final String file : this.listfile) {
			this.lookup.add(key(file));
		}
		synchronized (LISTFILES) {
			LISTFILES.put(key, this.listfile);
			LOOKUPS.put(key, this.lookup);
		}
	}

	/**
	 * warsmash-nx: was Files.walk(...).filter(Files::isRegularFile); the console's Java runtime has no
	 * java.util.stream. Same result: every regular file below the folder, relative to it.
	 */
	private static void walk(final Path folderPath, final Path directory, final Set<String> listfile,
			final String step) throws IOException {
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
			for (final Path entry : entries) {
				if (Files.isDirectory(entry)) {
					walk(folderPath, entry, listfile, step);
				}
				else if (Files.isRegularFile(entry)) {
					listfile.add(folderPath.relativize(entry).toString());
					if ((listfile.size() & 255) == 0) {
						LoadingStatus.report(step, listfile.size() + " files");
					}
				}
			}
		}
	}

	@Override
	public InputStream getResourceAsStream(String filepath) throws IOException {
		filepath = fixFilepath(filepath);
		if (!has(filepath)) {
			return null;
		}
		return Files.newInputStream(this.folderPath.resolve(filepath), StandardOpenOption.READ);
	}

	@Override
	public File getFile(String filepath) throws IOException {
		filepath = fixFilepath(filepath);
		if (!has(filepath)) {
			return null;
		}
		return new File(this.folderPath.toString() + File.separatorChar + filepath);
	}

	@Override
	public File getDirectory(String filepath) throws IOException {
		filepath = fixFilepath(filepath);
		File file = new File(this.folderPath.toString() + File.separatorChar + filepath);
		if(!file.exists() || !file.isDirectory()) {
			return null;
		}
		return file;
	}

	@Override
	public ByteBuffer read(String path) throws IOException {
		path = fixFilepath(path);
		if (!has(path)) {
			return null;
		}
		return ByteBuffer.wrap(Files.readAllBytes(Paths.get(this.folderPath.toString(), path)));
	}

	/**
	 * warsmash-nx: answered from the listing taken when the source was made, not by asking the file
	 * system. On the console each Files.exists is a call to the system's file service, and one asset
	 * lookup tries every data folder (and the tileset sub-folders of each): loading a campaign map's
	 * doodads spent minutes there (boot 9). Ignores case, as the SD card's FAT/exFAT and Windows do.
	 * Files added to a data folder after it was listed are not seen (the game does not write there).
	 */
	@Override
	public boolean has(final String filepath) {
		final String key = key(filepath);
		if (key.isEmpty()) {
			return false; // special case for folder data source, dont do this
		}
		return this.lookup.contains(key);
	}

	/** Lower case, '/' separators (':' and '\\' as in fixFilepath), no repeated or leading '/'. */
	private static String key(final String filepath) {
		final int n = filepath.length();
		final StringBuilder key = new StringBuilder(n);
		for (int i = 0; i < n; i++) {
			char c = filepath.charAt(i);
			if ((c == '\\') || (c == ':') || (c == '/')) {
				if ((key.length() == 0) || (key.charAt(key.length() - 1) == '/')) {
					continue;
				}
				c = '/';
			}
			else if ((c >= 'A') && (c <= 'Z')) {
				c = (char) (c + ('a' - 'A'));
			}
			key.append(c);
		}
		return key.toString();
	}

	@Override
	public Collection<String> getListfile() {
		return this.listfile;
	}

	@Override
	public void close() {
	}

	private static String fixFilepath(final String filepath) {
		return filepath.replace('\\', File.separatorChar).replace('/', File.separatorChar).replace(':',
				File.separatorChar);
	}
}
