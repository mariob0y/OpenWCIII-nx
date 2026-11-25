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
	#include <algorithm>
	#include <cstdint>
	#include <cstdio>
	#include <dirent.h>
	#include <string>
	#include <sys/stat.h>
	#include <vector>

	namespace {
	// Appends every regular file below root/rel to out, as "rel/path\n" with '/' separators.
	void nxListFolder(const std::string &root, const std::string &rel, std::string &out, size_t &fileCount) {
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
				nxListFolder(root, child, out, fileCount);
			} else if (type == DT_REG) {
				out += child;
				out += '\n';
				fileCount++;
			}
		}
		closedir(dir);
	}

	uint64_t nxFnv1a64(const std::string &s) {
		uint64_t h = 14695981039346656037ULL;
		for (unsigned char c : s) {
			h ^= c;
			h *= 1099511628211ULL;
		}
		return h;
	}

	// Fast top-level signature of root (root mtime + direct children name/type/mtime/size).
	std::string nxFolderSignature(const std::string &root) {
		std::vector<std::string> entries;
		DIR *dir = opendir(root.c_str());
		if (!dir)
			return std::string();
		while (struct dirent *entry = readdir(dir)) {
			const char *name = entry->d_name;
			if (name[0] == '.' && (name[1] == 0 || (name[1] == '.' && name[2] == 0)))
				continue;
			struct stat st;
			long long mtime = 0;
			long long size = 0;
			int isDir = (entry->d_type == DT_DIR) ? 1 : 0;
			if (stat((root + "/" + name).c_str(), &st) == 0) {
				mtime = (long long) st.st_mtime;
				size = (long long) st.st_size;
				isDir = S_ISDIR(st.st_mode) ? 1 : 0;
			}
			char buf[64];
			snprintf(buf, sizeof(buf), "=%d:%lld:%lld", isDir, mtime, size);
			entries.push_back(std::string(name) + buf);
		}
		closedir(dir);
		std::sort(entries.begin(), entries.end());
		struct stat rootSt;
		long long rootMtime = (stat(root.c_str(), &rootSt) == 0) ? (long long) rootSt.st_mtime : 0;
		char hdr[64];
		snprintf(hdr, sizeof(hdr), "#v1|%lld|%zu|", rootMtime, entries.size());
		std::string sig = hdr + root;
		for (const auto &e : entries) {
			sig += '|';
			sig += e;
		}
		sig += '\n';
		return sig;
	}

	std::string nxCachePath(const std::string &root) {
		char hex[32];
		snprintf(hex, sizeof(hex), "%016llx", (unsigned long long) nxFnv1a64(root));
		return std::string("sdmc:/switch/warsmash/.nx_dir_cache/") + hex + ".idx";
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

		const std::string sig = nxFolderSignature(root);
		const std::string cacheFile = nxCachePath(root);
		if (!sig.empty()) {
			if (FILE *f = fopen(cacheFile.c_str(), "rb")) {
				if (fseek(f, 0, SEEK_END) == 0) {
					long sz = ftell(f);
					if (sz > (long) sig.size() && sz < (32 << 20) && fseek(f, 0, SEEK_SET) == 0) {
						std::string cached((size_t) sz, '\0');
						if (fread(&cached[0], 1, (size_t) sz, f) == (size_t) sz
								&& cached.compare(0, sig.size(), sig) == 0) {
							fclose(f);
							return (jobject) stringFromNative(ctx, cached.c_str() + sig.size());
						}
					}
				}
				fclose(f);
			}
		}

		std::string out;
		out.reserve(2 << 20);
		size_t fileCount = 0;
		nxListFolder(root, "", out, fileCount);

		if (fileCount >= 256 && !sig.empty()) {
			mkdir("sdmc:/switch/warsmash/.nx_dir_cache", 0755);
			if (FILE *f = fopen(cacheFile.c_str(), "wb")) {
				fwrite(sig.data(), 1, sig.size(), f);
				fwrite(out.data(), 1, out.size(), f);
				fclose(f);
			}
		}
		return (jobject) stringFromNative(ctx, out.c_str());
	*/
}
