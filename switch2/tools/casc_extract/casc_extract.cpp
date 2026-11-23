// Extracts the files of a Warcraft III CASC storage (patches 1.30-1.32) whose names start with one
// of the given prefixes, into folders: "war3.w3mod:units\human\footman.mdx" becomes
// <out>\war3.w3mod\units\human\footman.mdx. Warsmash then reads them as "Folder" data sources
// (its own CASC reader handles 1.32.10+ only; see core/assets/warsmash_131.ini).
//
//   casc_extract <wc3 install dir> <out dir> [prefix ...]
//   default prefixes: war3.w3mod: and war3.w3mod:_locales\enus.w3mod: and war3.w3mod:_deprecated.w3mod:
//
// Reads the storage only; writes only under <out dir>. Skips files that already exist with the
// same size, so an interrupted run can be resumed.
#include <CascLib.h>

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <filesystem>

namespace fs = std::filesystem;

int main(int argc, char** argv) {
    if (argc < 3) {
        std::fprintf(stderr, "usage: casc_extract <wc3 dir> <out dir> [prefix ...]\n");
        return 2;
    }
    const std::string storage_dir = argv[1];
    const fs::path out_dir = argv[2];
    std::vector<std::string> prefixes;
    for (int i = 3; i < argc; ++i) prefixes.push_back(argv[i]);
    if (prefixes.empty()) prefixes = {"war3.w3mod:"};

    HANDLE storage = nullptr;
    if (!CascOpenStorage(storage_dir.c_str(), 0, &storage)) {
        std::fprintf(stderr, "CascOpenStorage(%s) failed: %u\n", storage_dir.c_str(), GetCascError());
        return 1;
    }

    CASC_FIND_DATA find{};
    HANDLE finder = CascFindFirstFile(storage, "*", &find, nullptr);
    if (finder == nullptr) {
        std::fprintf(stderr, "CascFindFirstFile failed: %u\n", GetCascError());
        return 1;
    }
    unsigned long long files = 0, skipped = 0, failed = 0, bytes = 0, seen = 0;
    std::vector<char> buffer;
    do {
        ++seen;
        const std::string name = find.szFileName;
        bool wanted = false;
        for (const auto& prefix : prefixes) {
            if (name.size() > prefix.size() && _strnicmp(name.c_str(), prefix.c_str(), prefix.size()) == 0) {
                wanted = true;
                break;
            }
        }
        // A name ending in ':' is a sub-mod's own marker entry ("war3.w3mod:_locales\enus.w3mod:"),
        // not a file.
        if (!wanted || !find.bFileAvailable || name.back() == ':') continue;

        // "war3.w3mod:units\human\x.mdx" -> war3.w3mod\units\human\x.mdx; a ':' inside a
        // sub-mod ("war3.w3mod:_locales\enus.w3mod:ui\...") becomes a folder too.
        std::string rel = name;
        for (char& c : rel) {
            if (c == ':' || c == '/') c = '\\';
        }
        if (rel.find("..") != std::string::npos) {
            std::fprintf(stderr, "skipping suspicious name %s\n", name.c_str());
            continue;
        }
        const fs::path target = out_dir / fs::path(rel);
        std::error_code ec;
        if (fs::exists(target, ec) && fs::file_size(target, ec) == find.FileSize) {
            ++skipped;
            continue;
        }

        HANDLE file = nullptr;
        if (!CascOpenFile(storage, name.c_str(), 0, CASC_OPEN_BY_NAME, &file)) {
            std::fprintf(stderr, "open %s: %u\n", name.c_str(), GetCascError());
            ++failed;
            continue;
        }
        ULONGLONG size = 0;
        CascGetFileSize64(file, &size);
        buffer.resize(size_t(size));
        DWORD read = 0;
        const bool ok = size == 0 || (CascReadFile(file, buffer.data(), DWORD(size), &read) && read == size);
        CascCloseFile(file);
        if (!ok) {
            std::fprintf(stderr, "read %s: %u (%u of %llu)\n", name.c_str(), GetCascError(), read,
                         (unsigned long long)size);
            ++failed;
            continue;
        }
        fs::create_directories(target.parent_path(), ec);
        if (std::FILE* f = _wfopen(target.wstring().c_str(), L"wb")) {
            std::fwrite(buffer.data(), 1, buffer.size(), f);
            std::fclose(f);
            ++files;
            bytes += size;
            if (files % 2000 == 0) std::printf("%llu files, %llu MB...\n", files, bytes >> 20);
        } else {
            std::fprintf(stderr, "write %s failed\n", target.string().c_str());
            ++failed;
        }
    } while (CascFindNextFile(finder, &find));
    CascFindClose(finder);
    CascCloseStorage(storage);

    std::printf("done: %llu names seen, %llu extracted (%llu MB), %llu already there, %llu failed\n", seen, files,
                bytes >> 20, skipped, failed);
    return failed ? 1 : 0;
}
