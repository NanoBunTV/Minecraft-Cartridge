/* The few OS services cartridge-host needs, for Windows and Linux (D5). */
#ifndef CARTRIDGE_PLATFORM_H
#define CARTRIDGE_PLATFORM_H

#include <stddef.h>
#include <stdio.h>
#include <stdint.h>

/* Opens a shared library (UTF-8 path); NULL on failure (the reason goes to stderr). */
void *plat_lib_open(const char *path);
/* Looks up a symbol; NULL if missing. */
void *plat_lib_symbol(void *lib, const char *name);
void plat_lib_close(void *lib);

/* Creates (or truncates) the file at the UTF-8 path, sizes it and maps it read-write; NULL on failure. */
void *plat_map_file(const char *path, size_t size);
void plat_unmap_file(void *base, size_t size);

/* Files for cores that load their games from a path (MAME): a directory (fine if it exists), a whole file written
 * at once, and removal. UTF-8 paths; 0 on success. */
int plat_make_dir(const char *path);
int plat_write_file(const char *path, const void *data, size_t size);
void plat_remove(const char *path);
/* The path separator: MAME on Windows splits a game's path only at backslashes. */
#ifdef _WIN32
#define PLAT_SEP '\\'
#else
#define PLAT_SEP '/'
#endif
/* This process's id, to keep each host's files apart. */
unsigned long plat_pid(void);

/* Puts stdin and stdout into binary mode (a no-op on Linux). */
void plat_binary_stdio(void);

/* Moves the protocol's replies off stdout: returns a private binary stream on the original stdout, and points fd 1
 * at stderr, so anything a core prints can't corrupt the replies. NULL on failure. */
FILE *plat_private_stdout(void);

#endif
