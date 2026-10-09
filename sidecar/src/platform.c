#ifndef _WIN32
#define _POSIX_C_SOURCE 200809L
#endif

#include "platform.h"

#include <stdio.h>

#ifdef _WIN32

#define WIN32_LEAN_AND_MEAN
#include <fcntl.h>
#include <io.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>

/* UTF-8 -> UTF-16 for the W APIs; the caller frees. */
static wchar_t *widen(const char *utf8) {
    int n = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, NULL, 0);
    if (n <= 0) return NULL;
    wchar_t *w = malloc((size_t)n * sizeof *w);
    if (w) MultiByteToWideChar(CP_UTF8, 0, utf8, -1, w, n);
    return w;
}

void *plat_lib_open(const char *path) {
    wchar_t *w = widen(path);
    if (!w) return NULL;
    HMODULE lib = LoadLibraryW(w);
    free(w);
    if (!lib) fprintf(stderr, "cartridge-host: LoadLibrary failed (error %lu)\n", GetLastError());
    return (void *)lib;
}

void *plat_lib_symbol(void *lib, const char *name) {
    FARPROC sym = GetProcAddress((HMODULE)lib, name);
    void *p;
    _Static_assert(sizeof p == sizeof sym, "function and data pointers differ in size");
    memcpy(&p, &sym, sizeof p);
    return p;
}

void plat_lib_close(void *lib) {
    if (lib) FreeLibrary((HMODULE)lib);
}

void *plat_map_file(const char *path, size_t size) {
    wchar_t *w = widen(path);
    if (!w) return NULL;
    /* TEMPORARY keeps the pages in memory instead of flushing them to disk. */
    HANDLE file = CreateFileW(w, GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                              NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_TEMPORARY, NULL);
    free(w);
    if (file == INVALID_HANDLE_VALUE) {
        fprintf(stderr, "cartridge-host: cannot create the shared-memory file (error %lu)\n", GetLastError());
        return NULL;
    }
    HANDLE mapping = CreateFileMappingW(file, NULL, PAGE_READWRITE, (DWORD)((uint64_t)size >> 32), (DWORD)size, NULL);
    CloseHandle(file);
    if (!mapping) {
        fprintf(stderr, "cartridge-host: CreateFileMapping failed (error %lu)\n", GetLastError());
        return NULL;
    }
    void *base = MapViewOfFile(mapping, FILE_MAP_ALL_ACCESS, 0, 0, size);
    CloseHandle(mapping);
    if (!base) fprintf(stderr, "cartridge-host: MapViewOfFile failed (error %lu)\n", GetLastError());
    return base;
}

void plat_unmap_file(void *base, size_t size) {
    (void)size;
    if (base) UnmapViewOfFile(base);
}

int plat_make_dir(const char *path) {
    wchar_t *w = widen(path);
    if (!w) return -1;
    BOOL ok = CreateDirectoryW(w, NULL) || GetLastError() == ERROR_ALREADY_EXISTS;
    free(w);
    return ok ? 0 : -1;
}

int plat_write_file(const char *path, const void *data, size_t size) {
    wchar_t *w = widen(path);
    if (!w) return -1;
    FILE *f = _wfopen(w, L"wb");
    free(w);
    if (!f) return -1;
    size_t n = fwrite(data, 1, size, f);
    return fclose(f) == 0 && n == size ? 0 : -1;
}

void plat_remove(const char *path) {
    wchar_t *w = widen(path);
    if (!w) return;
    if (!DeleteFileW(w)) RemoveDirectoryW(w);
    free(w);
}

unsigned long plat_pid(void) { return GetCurrentProcessId(); }

void plat_binary_stdio(void) {
    _setmode(_fileno(stdin), _O_BINARY);
    _setmode(_fileno(stdout), _O_BINARY);
}

FILE *plat_private_stdout(void) {
    fflush(stdout);
    int fd = _dup(_fileno(stdout));
    if (fd < 0) return NULL;
    _setmode(fd, _O_BINARY);
    FILE *out = _fdopen(fd, "wb");
    if (!out) return NULL;
    _dup2(_fileno(stderr), _fileno(stdout));
    return out;
}

#else

#include <dlfcn.h>
#include <fcntl.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

int plat_make_dir(const char *path) {
    struct stat st;
    return mkdir(path, 0700) == 0 || (stat(path, &st) == 0 && S_ISDIR(st.st_mode)) ? 0 : -1;
}

int plat_write_file(const char *path, const void *data, size_t size) {
    FILE *f = fopen(path, "wb");
    if (!f) return -1;
    size_t n = fwrite(data, 1, size, f);
    return fclose(f) == 0 && n == size ? 0 : -1;
}

void plat_remove(const char *path) {
    if (unlink(path) != 0) rmdir(path);
}

unsigned long plat_pid(void) { return (unsigned long)getpid(); }

FILE *plat_private_stdout(void) {
    fflush(stdout);
    int fd = dup(STDOUT_FILENO);
    if (fd < 0) return NULL;
    FILE *out = fdopen(fd, "wb");
    if (!out) return NULL;
    dup2(STDERR_FILENO, STDOUT_FILENO);
    return out;
}

void *plat_lib_open(const char *path) {
    void *lib = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!lib) fprintf(stderr, "cartridge-host: dlopen failed: %s\n", dlerror());
    return lib;
}

void *plat_lib_symbol(void *lib, const char *name) {
    return dlsym(lib, name);
}

void plat_lib_close(void *lib) {
    if (lib) dlclose(lib);
}

void *plat_map_file(const char *path, size_t size) {
    int fd = open(path, O_RDWR | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) {
        perror("cartridge-host: cannot create the shared-memory file");
        return NULL;
    }
    if (ftruncate(fd, (off_t)size) != 0) {
        perror("cartridge-host: ftruncate");
        close(fd);
        return NULL;
    }
    void *base = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    close(fd);
    if (base == MAP_FAILED) {
        perror("cartridge-host: mmap");
        return NULL;
    }
    return base;
}

void plat_unmap_file(void *base, size_t size) {
    if (base) munmap(base, size);
}

void plat_binary_stdio(void) {}

#endif
