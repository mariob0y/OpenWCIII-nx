package wsnx.warsmash;

import com.etheller.warsmash.datasources.FolderDataSource;

/**
 * Lists a game data folder for FolderDataSource with readdir, which reports each entry's type.
 * The Java walk asked Files.isDirectory and Files.isRegularFile of every entry: two calls to the
 * system's file service per file, about 100 s for war3.w3mod + enus.w3mod (36,600 files) at every
 * boot (2026-11-22). readdir's entries come in batches from one directory read.
 */
final class NxFolderLister implements FolderDataSource.FastLister {
	private NxFolderLister() {
	}

	static void install() {
		FolderDataSource.setFastLister(new NxFolderLister());
	}

	@Override
	public String list(final String folder) {
		return listFiles(folder);
	}

	/*JNI
	#include <dirent.h>
	#include <sys/stat.h>
	#include <string>

	namespace {
	// Appends every regular file below root/rel to out, as "rel/path\n" with '/' separators.
	void nxListFolder(const std::string &root, const std::string &rel, std::string &out) {
		const std::string dirPath = rel.empty() ? root : root + "/" + rel;
		DIR *dir = opendir(dirPath.c_str());
		if (!dir)
			return;
		while (struct dirent *entry = readdir(dir)) {
			const char *name = entry->d_name;
			if (name[0] == '.' && (name[1] == 0 || (name[1] == '.' && name[2] == 0)))
				continue;
			const std::string child = rel.empty() ? std::string(name) : rel + "/" + name;
			int type = entry->d_type;
			if (type == DT_UNKNOWN) {
				struct stat st;
				if (stat((root + "/" + child).c_str(), &st) != 0)
					continue;
				type = S_ISDIR(st.st_mode) ? DT_DIR : (S_ISREG(st.st_mode) ? DT_REG : DT_UNKNOWN);
			}
			if (type == DT_DIR) {
				nxListFolder(root, child, out);
			} else if (type == DT_REG) {
				out += child;
				out += '\n';
			}
		}
		closedir(dir);
	}
	}
	*/

	/** Every regular file below the folder, relative to it, one per line; null if it cannot be read. */
	private static native String listFiles(String folder); /*
		// Clearwing has already made `folder` a C string (a String parameter of a native arrives
		// converted); converting it again read garbage and crashed (2026-11-22, 22:34).
		if (!folder)
			return nullptr;
		std::string root(folder);
		while (root.size() > 1 && root.back() == '/')
			root.pop_back();
		DIR *probe = opendir(root.c_str());
		if (!probe)
			return nullptr;
		closedir(probe);
		std::string out;
		out.reserve(2 << 20);
		nxListFolder(root, "", out);
		return (jobject) stringFromNative(ctx, out.c_str());
	*/
}
