/*
 * cartridge-host: the sidecar process that hosts one libretro core for one running console (D6, D7), built with
 * zig cc for Windows x64 and Linux x64 (D5, D8).
 *
 *   cartridge-host --version                 prints the version
 *   cartridge-host --about                   prints the version and platform
 *   cartridge-host [--shm <file>] [--system-dir <dir>]
 *                                            serves the protocol in protocol.h on stdin/stdout until SHUTDOWN or
 *                                            until stdin closes; without --shm, video and audio are discarded
 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "core.h"
#include "libretro.h"
#include "platform.h"
#include "protocol.h"

#ifndef CARTRIDGE_HOST_VERSION
#define CARTRIDGE_HOST_VERSION "dev"
#endif

#if defined(_WIN32)
#define HOST_OS "windows"
#elif defined(__linux__)
#define HOST_OS "linux"
#else
#error "cartridge-host supports Windows and Linux only (D5)"
#endif

#if defined(__x86_64__) || defined(_M_X64)
#define HOST_ARCH "x86_64"
#else
#error "cartridge-host supports x86_64 only (D5)"
#endif

#define MAX_PAYLOAD (256u * 1024u * 1024u + 4096u) /* the server config caps ROMs at 256 MB at most (D16, D72) */
#define MAX_PORTS 8u

/* The protocol's replies (plat_private_stdout); stdout itself goes to stderr, for whatever a core prints. */
static FILE *replies;

static int read_exact(void *buf, size_t n) {
    return fread(buf, 1, n, stdin) == n ? 0 : -1;
}

static void reply(uint32_t status, const void *data, size_t len) {
    uint32_t head[2] = {status, (uint32_t)len};
    fwrite(head, sizeof head, 1, replies);
    if (len) fwrite(data, 1, len, replies);
    fflush(replies);
}

static void reply_error(const char *msg) {
    reply(1, msg, strlen(msg));
}

static uint32_t get_u32(const uint8_t *p) {
    uint32_t v;
    memcpy(&v, p, sizeof v);
    return v;
}

static uint64_t fnv1a64(uint64_t h, const uint8_t *p, size_t n) {
    for (size_t i = 0; i < n; i++) h = (h ^ p[i]) * 0x100000001b3ull;
    return h;
}

/* The desync checksum (D12): system, battery and video RAM, not the savestate. Savestates can carry bookkeeping
 * that a load doesn't restore (FCEUmm's MMC1 keeps the cycle of the last mapper write, which reads back as 0 after
 * a load) without any effect on the game, and a full-state hash would flag every client that rolled back or
 * joined late as desynced. Game logic lives in RAM, so RAM is where a real desync shows. */
static int checksum_kind = CH_CHECKSUM_RAM;

static void handle_checksum(void) {
    if (checksum_kind == CH_CHECKSUM_PICTURE) {
        uint64_t h = core_picture_hash();
        reply(0, &h, sizeof h);
        return;
    }
    static const unsigned regions[] = {RETRO_MEMORY_SYSTEM_RAM, RETRO_MEMORY_SAVE_RAM, RETRO_MEMORY_VIDEO_RAM};
    uint64_t h = 0xcbf29ce484222325ull;
    size_t system_size;
    if (!core_memory(RETRO_MEMORY_SYSTEM_RAM, &system_size)) {
        /* No RAM to look at (SameBoy's linked Game Boys): the savestate instead, which is deterministic there. */
        size_t size = core_serialize_size();
        uint8_t *buf = size ? malloc(size) : NULL;
        if (buf && core_serialize(buf, size) == 0) h = fnv1a64(h, buf, size);
        free(buf);
        reply(0, &h, sizeof h);
        return;
    }
    for (size_t i = 0; i < sizeof regions / sizeof regions[0]; i++) {
        size_t size;
        const uint8_t *mem = core_memory(regions[i], &size);
        h = fnv1a64(h, (const uint8_t *)&size, sizeof size);
        if (mem) h = fnv1a64(h, mem, size);
    }
    reply(0, &h, sizeof h);
}

/* Copies a payload into a NUL-terminated string; the caller frees. */
static char *payload_string(const uint8_t *p, uint32_t len) {
    char *s = malloc((size_t)len + 1);
    if (!s) return NULL;
    memcpy(s, p, len);
    s[len] = '\0';
    return s;
}

static void reply_av(const core_av_info *av) {
    uint8_t out[48], *o = out;
    memcpy(o, &av->base_width, 4), o += 4;
    memcpy(o, &av->base_height, 4), o += 4;
    memcpy(o, &av->max_width, 4), o += 4;
    memcpy(o, &av->max_height, 4), o += 4;
    memcpy(o, &av->fps, 8), o += 8;
    memcpy(o, &av->sample_rate, 8), o += 8;
    memcpy(o, &av->serialize_size, 4), o += 4;
    memcpy(o, &av->region, 4), o += 4;
    memcpy(o, &av->quirks, 8), o += 8;
    reply(0, out, (size_t)(o - out));
}

static void handle_load_game(const uint8_t *p, uint32_t len, char *err, size_t err_size) {
    if (len < 4 || get_u32(p) > len - 4) {
        reply_error("malformed LOAD_GAME");
        return;
    }
    uint32_t name_len = get_u32(p);
    char *name = payload_string(p + 4, name_len);
    core_av_info av;
    int rc = name ? core_load_game(name, p + 4 + name_len, len - 4 - name_len, &av, err, err_size) : -1;
    free(name);
    if (rc != 0) {
        reply_error(name ? err : "out of memory");
        return;
    }
    reply_av(&av);
}

/* LOAD_GAME_LINKED: an ident, then two (name, data) pairs. */
static void handle_load_game_linked(const uint8_t *p, uint32_t len, char *err, size_t err_size) {
    const uint8_t *end = p + len;
    char *ident = NULL, *names[2] = {NULL, NULL};
    const void *datas[2];
    size_t sizes[2];
    int ok = 0;
    if (len >= 4 && get_u32(p) <= len - 4) {
        uint32_t n = get_u32(p);
        ident = payload_string(p + 4, n);
        p += 4 + n;
        ok = ident != NULL;
        for (int i = 0; i < 2 && ok; i++) {
            if (end - p < 4 || get_u32(p) > (uint32_t)(end - p - 4)) { ok = 0; break; }
            n = get_u32(p);
            names[i] = payload_string(p + 4, n);
            p += 4 + n;
            if (!names[i] || end - p < 4 || get_u32(p) > (uint32_t)(end - p - 4)) { ok = 0; break; }
            sizes[i] = get_u32(p);
            datas[i] = p + 4;
            p += 4 + sizes[i];
        }
    }
    core_av_info av;
    int rc = ok ? core_load_game_linked(ident, 2, (const char *const *)names, datas, sizes, &av, err, err_size) : -1;
    free(ident);
    free(names[0]);
    free(names[1]);
    if (rc != 0) {
        reply_error(ok ? err : "malformed LOAD_GAME_LINKED");
        return;
    }
    reply_av(&av);
}

static void handle_run(const uint8_t *p, uint32_t len) {
    if (len < 12) {
        reply_error("malformed RUN");
        return;
    }
    uint32_t frames = get_u32(p), flags = get_u32(p + 4), ports = get_u32(p + 8);
    if (ports > MAX_PORTS || (uint64_t)frames * ports * 4 != (uint64_t)len - 12) {
        reply_error("malformed RUN");
        return;
    }
    uint32_t *inputs = malloc((size_t)frames * ports * 4 + 4);
    if (!inputs) {
        reply_error("out of memory");
        return;
    }
    memcpy(inputs, p + 12, (size_t)frames * ports * 4);
    uint64_t count = core_run(frames, flags, ports, inputs);
    free(inputs);
    reply(0, &count, sizeof count);
}

static void handle_serialize(void) {
    size_t size = core_serialize_size();
    uint8_t *buf = size ? malloc(size) : NULL;
    if (!buf || core_serialize(buf, size) != 0) {
        free(buf);
        reply_error("serialize failed");
        return;
    }
    reply(0, buf, size);
    free(buf);
}

static int serve(void) {
    static char err[512], info[1024];
    for (;;) {
        uint32_t head[2];
        if (read_exact(head, sizeof head) != 0) return 0; /* stdin closed: the game is gone */
        uint32_t cmd = head[0], len = head[1];
        if (len > MAX_PAYLOAD) {
            fprintf(stderr, "cartridge-host: payload of %u bytes is too large\n", len);
            return 1;
        }
        uint8_t *p = malloc(len ? len : 1);
        if (!p || (len && read_exact(p, len) != 0)) {
            free(p);
            return 1;
        }

        switch (cmd) {
        case CH_HELLO: {
            uint8_t out[64];
            uint32_t v = CH_PROTOCOL_VERSION;
            size_t n = strlen(CARTRIDGE_HOST_VERSION);
            memcpy(out, &v, 4);
            memcpy(out + 4, CARTRIDGE_HOST_VERSION, n);
            reply(0, out, 4 + n);
            break;
        }
        case CH_LOAD_CORE: {
            char *path = payload_string(p, len);
            if (path && core_load(path, info, sizeof info, err, sizeof err) == 0)
                reply(0, info, strlen(info));
            else
                reply_error(path ? err : "out of memory");
            free(path);
            break;
        }
        case CH_SET_OPTION: {
            char *kv = payload_string(p, len);
            if (kv && core_set_option(kv, err, sizeof err) == 0)
                reply(0, NULL, 0);
            else
                reply_error(kv ? err : "out of memory");
            free(kv);
            break;
        }
        case CH_LOAD_GAME:
            handle_load_game(p, len, err, sizeof err);
            break;
        case CH_LOAD_GAME_LINKED:
            handle_load_game_linked(p, len, err, sizeof err);
            break;
        case CH_GET_MEMORY: {
            size_t size = 0;
            void *mem = len == 4 ? core_memory(get_u32(p), &size) : NULL;
            if (len == 4) reply(0, mem, mem ? size : 0);
            else reply_error("malformed GET_MEMORY");
            break;
        }
        case CH_SET_MEMORY: {
            size_t size = 0;
            void *mem = len >= 4 ? core_memory(get_u32(p), &size) : NULL;
            if (mem && len - 4 == size) {
                memcpy(mem, p + 4, size);
                reply(0, NULL, 0);
            } else {
                reply_error("memory size mismatch");
            }
            break;
        }
        case CH_RUN:
            handle_run(p, len);
            break;
        case CH_SERIALIZE:
            handle_serialize();
            break;
        case CH_CHECKSUM:
            handle_checksum();
            break;
        case CH_SET_CHECKSUM:
            if (len == 4 && get_u32(p) <= CH_CHECKSUM_PICTURE) {
                checksum_kind = (int)get_u32(p);
                core_set_picture_hash(checksum_kind == CH_CHECKSUM_PICTURE);
                reply(0, NULL, 0);
            } else {
                reply_error("malformed SET_CHECKSUM");
            }
            break;
        case CH_UNSERIALIZE:
            if (core_unserialize(p, len) == 0)
                reply(0, NULL, 0);
            else
                reply_error("unserialize failed");
            break;
        case CH_GET_SRAM: {
            size_t size;
            void *sram = core_memory(RETRO_MEMORY_SAVE_RAM, &size);
            reply(0, sram, size);
            break;
        }
        case CH_SET_SRAM: {
            size_t size;
            void *sram = core_memory(RETRO_MEMORY_SAVE_RAM, &size);
            if (sram && len == size) {
                memcpy(sram, p, size);
                reply(0, NULL, 0);
            } else {
                reply_error("battery RAM size mismatch");
            }
            break;
        }
        case CH_RESET:
            core_reset();
            reply(0, NULL, 0);
            break;
        case CH_SET_PORT_DEVICE:
            if (len == 8 && core_set_port_device(get_u32(p), get_u32(p + 4), err, sizeof err) == 0)
                reply(0, NULL, 0);
            else
                reply_error(len == 8 ? err : "malformed SET_PORT_DEVICE");
            break;
        case CH_SHUTDOWN:
            free(p);
            reply(0, NULL, 0);
            return 0;
        default:
            reply_error("unknown command");
            break;
        }
        free(p);
    }
}

int main(int argc, char **argv) {
    const char *shm_path = NULL;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--version") == 0) {
            printf("%s\n", CARTRIDGE_HOST_VERSION);
            return 0;
        } else if (strcmp(argv[i], "--about") == 0) {
            printf("cartridge-host %s (%s-%s), protocol %u\n", CARTRIDGE_HOST_VERSION, HOST_OS, HOST_ARCH,
                   CH_PROTOCOL_VERSION);
            return 0;
        } else if (strcmp(argv[i], "--shm") == 0 && i + 1 < argc) {
            shm_path = argv[++i];
        } else if (strcmp(argv[i], "--system-dir") == 0 && i + 1 < argc) {
            core_set_dirs(argv[++i]);
        } else {
            fprintf(stderr, "usage: cartridge-host [--version | --about | --shm <file> --system-dir <dir>]\n");
            return 2;
        }
    }

    plat_binary_stdio();
    replies = plat_private_stdout();
    if (!replies) {
        fprintf(stderr, "cartridge-host: can't set up the reply stream\n");
        return 1;
    }
    void *shm = NULL;
    if (shm_path) {
        shm = plat_map_file(shm_path, CH_SHM_SIZE);
        if (!shm) return 1;
        core_attach_shm(shm);
    }
    int rc = serve();
    core_shutdown();
    plat_unmap_file(shm, CH_SHM_SIZE);
    return rc;
}
