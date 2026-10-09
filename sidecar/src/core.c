#include "core.h"

#include <stdarg.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libretro.h"
#include "platform.h"
#include "protocol.h"

/* ---- the core's exported API ---- */

static struct {
    void *lib;
    void (*init)(void);
    void (*deinit)(void);
    unsigned (*api_version)(void);
    void (*get_system_info)(struct retro_system_info *);
    void (*get_system_av_info)(struct retro_system_av_info *);
    void (*set_environment)(retro_environment_t);
    void (*set_video_refresh)(retro_video_refresh_t);
    void (*set_audio_sample)(retro_audio_sample_t);
    void (*set_audio_sample_batch)(retro_audio_sample_batch_t);
    void (*set_input_poll)(retro_input_poll_t);
    void (*set_input_state)(retro_input_state_t);
    void (*set_controller_port_device)(unsigned, unsigned);
    void (*reset)(void);
    void (*run)(void);
    size_t (*serialize_size)(void);
    bool (*serialize)(void *, size_t);
    bool (*unserialize)(const void *, size_t);
    bool (*load_game)(const struct retro_game_info *);
    bool (*load_game_special)(unsigned, const struct retro_game_info *, size_t);
    void (*unload_game)(void);
    unsigned (*get_region)(void);
    void *(*get_memory_data)(unsigned);
    size_t (*get_memory_size)(unsigned);
} C;

static bool initialized, game_loaded;
static void *rom_copy; /* kept until unload: libretro lets the core keep pointing into the ROM data */

/* ---- frontend state ---- */

static unsigned char *shm;
static const char *system_dir = ".";
static enum retro_pixel_format pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
static uint64_t quirks;
static uint64_t frame_count;
static uint64_t audio_pos;
static bool video_enabled = true, audio_enabled = true;
static bool picture_hash_on;
static uint64_t picture_hash;
static double output_rate(double rate);
static uint32_t cur_ports;
static const uint32_t *cur_inputs;

/* What each port holds (CH_DEVICE_*), and the core's own device ids for a light gun and a mouse on each port, from
 * SET_CONTROLLER_INFO (FCEUmm calls its gun "Zapper", Snes9x its mouse "SNES Mouse"). A pad is the plain RetroPad,
 * unless the core offers a 6-button pad: Genesis Plus GX's "MD Joypad 6 Button" (left on auto it plays as a 3-button
 * pad, so games never see X, Y, Z and Mode). */
#define MAX_PORTS_INFO 4
static unsigned port_kind[MAX_PORTS_INFO];
static unsigned gun_device[MAX_PORTS_INFO];
static unsigned mouse_device[MAX_PORTS_INFO];
static unsigned pad_device[MAX_PORTS_INFO];

static unsigned pad(unsigned port) {
    return port < MAX_PORTS_INFO && pad_device[port] ? pad_device[port] : RETRO_DEVICE_JOYPAD;
}

/* Per-extension content overrides (SET_CONTENT_INFO_OVERRIDE): current cores such as FCEUmm report
 * need_fullpath = true globally and only accept in-memory ROMs for the extensions listed here. */
#define MAX_OVERRIDES 16
static struct {
    char extensions[128];
    bool need_fullpath;
} overrides[MAX_OVERRIDES];
static int override_count;
/* The core's subsystems (RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO), by name. */
#define MAX_SUBSYSTEMS 8
static struct {
    char ident[64];
    unsigned id;
} subsystems[MAX_SUBSYSTEMS];
static int subsystem_count;
/* The second game's copy, for a linked load. */
static void *rom_copy_2;
static char game_path_2[512];
static int loaded(core_av_info *av);
static char game_path[512], game_name[256], game_ext[16];
/* A game a core loads from a path (MAME): written to its own folder under the system directory, removed on shutdown. */
static char content_dir[512];
static bool content_written;
static struct retro_game_info_ext game_info_ext;
static bool game_info_ext_valid;

#define MAX_OPTIONS 256
static struct option {
    char *key, *value, *def;
} options[MAX_OPTIONS];
static int option_count;
static bool options_dirty;

static void put_u32(size_t off, uint32_t v) { memcpy(shm + off, &v, sizeof v); }
static void put_u64(size_t off, uint64_t v) { memcpy(shm + off, &v, sizeof v); }
static void put_f64(size_t off, double v) { memcpy(shm + off, &v, sizeof v); }
static _Atomic uint64_t *shm_atomic(size_t off) { return (_Atomic uint64_t *)(void *)(shm + off); }

static char *dup_string(const char *s) {
    size_t n = strlen(s) + 1;
    char *d = malloc(n);
    if (d) memcpy(d, s, n);
    return d;
}

static struct option *find_option(const char *key) {
    for (int i = 0; i < option_count; i++)
        if (strcmp(options[i].key, key) == 0) return &options[i];
    return NULL;
}

static struct option *add_option(const char *key) {
    struct option *o = find_option(key);
    if (o || option_count == MAX_OPTIONS) return o;
    o = &options[option_count++];
    o->key = dup_string(key);
    return o;
}

/* ---- callbacks ---- */

static void RETRO_CALLCONV log_cb(enum retro_log_level level, const char *fmt, ...) {
    static const char *names[] = {"debug", "info", "warn", "error"};
    if (level == RETRO_LOG_DEBUG) return;
    fprintf(stderr, "[core %s] ", (unsigned)level < 4 ? names[level] : "?");
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
}

/* Legacy option declarations: "Description; first|second|...", the first value being the default. */
static void declare_variables(const struct retro_variable *vars) {
    for (; vars && vars->key; vars++) {
        struct option *o = add_option(vars->key);
        if (!o || o->def) continue;
        const char *values = vars->value ? strstr(vars->value, "; ") : NULL;
        if (!values) continue;
        values += 2;
        size_t n = strcspn(values, "|");
        o->def = malloc(n + 1);
        if (!o->def) continue;
        memcpy(o->def, values, n);
        o->def[n] = '\0';
    }
}

static bool environment(unsigned cmd, void *data);

/* Set CARTRIDGE_HOST_TRACE=1 to log every environment call and its answer to stderr. */
static bool RETRO_CALLCONV environment_cb(unsigned cmd, void *data) {
    static int trace = -1;
    if (trace < 0) trace = getenv("CARTRIDGE_HOST_TRACE") != NULL;
    if (trace) fprintf(stderr, "[env] %u ...\n", cmd), fflush(stderr);
    bool handled = environment(cmd, data);
    if (trace) fprintf(stderr, "[env] %u -> %d\n", cmd, handled), fflush(stderr);
    return handled;
}

static bool environment(unsigned cmd, void *data) {
    switch (cmd & ~(unsigned)RETRO_ENVIRONMENT_EXPERIMENTAL) {
    case RETRO_ENVIRONMENT_GET_CAN_DUPE:
        *(bool *)data = true;
        return true;
    case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
        enum retro_pixel_format f = *(const enum retro_pixel_format *)data;
        if (f != RETRO_PIXEL_FORMAT_0RGB1555 && f != RETRO_PIXEL_FORMAT_XRGB8888 && f != RETRO_PIXEL_FORMAT_RGB565)
            return false;
        pixel_format = f;
        return true;
    }
    case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
    case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
        *(const char **)data = system_dir;
        return true;
    case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
        ((struct retro_log_callback *)data)->log = log_cb;
        return true;
    case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
        *(unsigned *)data = 0; /* cores then declare options with SET_VARIABLES */
        return true;
    case RETRO_ENVIRONMENT_SET_VARIABLES:
        declare_variables(data);
        return true;
    case RETRO_ENVIRONMENT_GET_VARIABLE: {
        struct retro_variable *var = data;
        struct option *o = var->key ? find_option(var->key) : NULL;
        var->value = o ? (o->value ? o->value : o->def) : NULL;
        return var->value != NULL;
    }
    case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
        *(bool *)data = options_dirty;
        options_dirty = false;
        return true;
    case RETRO_ENVIRONMENT_GET_LANGUAGE:
        *(unsigned *)data = RETRO_LANGUAGE_ENGLISH;
        return true;
    case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
    case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME: /* data is a bool (MAME sends it), not controller info */
        return true;
    case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO: {
        const struct retro_controller_info *info = data;
        for (unsigned port = 0; info && info[port].types && port < MAX_PORTS_INFO; port++) {
            for (unsigned i = 0; i < info[port].num_types; i++) {
                const struct retro_controller_description *d = &info[port].types[i];
                if (d->desc && strstr(d->desc, "Zapper")) gun_device[port] = d->id;
                if (d->desc && strstr(d->desc, "Mouse") && (d->id & RETRO_DEVICE_MASK) == RETRO_DEVICE_MOUSE)
                    mouse_device[port] = d->id;
                if (!pad_device[port] && d->desc && strstr(d->desc, "6 Button") && !strchr(d->desc, '+')
                        && (d->id & RETRO_DEVICE_MASK) == RETRO_DEVICE_JOYPAD)
                    pad_device[port] = d->id; /* the plain pad, not "... + Teamplayer" and other multitaps */
            }
        }
        return true;
    }
    case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
    case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
    case RETRO_ENVIRONMENT_SET_GEOMETRY:
        return true;
    case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
        const struct retro_system_av_info *av = data;
        if (shm) {
            put_f64(72, av->timing.fps);
            put_f64(80, output_rate(av->timing.sample_rate));
        }
        return true;
    }
    case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO: {
        const struct retro_subsystem_info *s = data;
        for (; s && s->ident && subsystem_count < MAX_SUBSYSTEMS; s++, subsystem_count++) {
            snprintf(subsystems[subsystem_count].ident, sizeof subsystems[0].ident, "%s", s->ident);
            subsystems[subsystem_count].id = s->id;
        }
        return true;
    }
    case RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE: {
        const struct retro_system_content_info_override *o = data;
        for (; o && o->extensions && override_count < MAX_OVERRIDES; o++, override_count++) {
            snprintf(overrides[override_count].extensions, sizeof overrides[0].extensions, "%s", o->extensions);
            overrides[override_count].need_fullpath = o->need_fullpath;
        }
        return true;
    }
    case RETRO_ENVIRONMENT_GET_GAME_INFO_EXT:
        if (!game_info_ext_valid) return false;
        *(const struct retro_game_info_ext **)data = &game_info_ext;
        return true;
    case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
        quirks = *(const uint64_t *)data;
        return true;
    case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
        /* Always "both on": a core told to skip audio or video may skip emulating it, which changes its state
         * and breaks rollback (D12). Unwanted output is dropped in the callbacks instead. */
        *(int *)data = 1 | 2;
        return true;
    case RETRO_ENVIRONMENT_GET_FASTFORWARDING:
        *(bool *)data = false;
        return true;
    case RETRO_ENVIRONMENT_SET_MESSAGE:
        fprintf(stderr, "[core message] %s\n", ((const struct retro_message *)data)->msg);
        return true;
    default:
        return false;
    }
}

static uint8_t expand5(unsigned v) { return (uint8_t)((v << 3) | (v >> 2)); }
static uint8_t expand6(unsigned v) { return (uint8_t)((v << 2) | (v >> 4)); }

void core_set_picture_hash(int on) {
    picture_hash_on = on != 0;
    picture_hash = 0;
}

uint64_t core_picture_hash(void) {
    return picture_hash;
}

/* The picture's pixels as the core drew them (row padding left out), with its size: eight bytes at a time. */
static uint64_t hash_picture(const uint8_t *data, unsigned width, unsigned height, size_t pitch) {
    size_t row = (size_t)width * (pixel_format == RETRO_PIXEL_FORMAT_XRGB8888 ? 4 : 2);
    uint64_t h = 0x9e3779b97f4a7c15ull ^ ((uint64_t)width << 32 | height);
    for (unsigned y = 0; y < height; y++) {
        const uint8_t *p = data + (size_t)y * pitch;
        size_t x = 0;
        for (; x + 8 <= row; x += 8) {
            uint64_t v;
            memcpy(&v, p + x, 8);
            h = (h ^ v) * 0xff51afd7ed558ccdull;
            h ^= h >> 32;
        }
        for (; x < row; x++) h = (h ^ p[x]) * 0x100000001b3ull;
    }
    return h;
}

static void RETRO_CALLCONV video_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (picture_hash_on && data) picture_hash = hash_picture(data, width, height, pitch);
    if (!shm || !data || !video_enabled) return; /* NULL data = duplicate frame: keep the last one */
    if (width > CH_SHM_MAX_WIDTH) width = CH_SHM_MAX_WIDTH;
    if (height > CH_SHM_MAX_HEIGHT) height = CH_SHM_MAX_HEIGHT;

    _Atomic uint64_t *seq = shm_atomic(32);
    uint64_t s = atomic_load_explicit(seq, memory_order_relaxed);
    atomic_store_explicit(seq, s + 1, memory_order_relaxed); /* odd: writing */
    atomic_thread_fence(memory_order_release);

    uint8_t *dst = shm + CH_SHM_HEADER_SIZE;
    for (unsigned y = 0; y < height; y++) {
        const uint8_t *row = (const uint8_t *)data + (size_t)y * pitch;
        uint8_t *out = dst + (size_t)y * width * 4;
        for (unsigned x = 0; x < width; x++, out += 4) {
            if (pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
                uint32_t p;
                memcpy(&p, row + x * 4, 4);
                out[0] = (uint8_t)(p >> 16), out[1] = (uint8_t)(p >> 8), out[2] = (uint8_t)p;
            } else {
                uint16_t p;
                memcpy(&p, row + x * 2, 2);
                if (pixel_format == RETRO_PIXEL_FORMAT_RGB565)
                    out[0] = expand5(p >> 11), out[1] = expand6((p >> 5) & 63), out[2] = expand5(p & 31);
                else
                    out[0] = expand5((p >> 10) & 31), out[1] = expand5((p >> 5) & 31), out[2] = expand5(p & 31);
            }
            out[3] = 255;
        }
    }
    put_u32(48, width);
    put_u32(52, height);
    put_u32(56, width * 4);

    atomic_store_explicit(seq, s + 2, memory_order_release); /* even: complete */
}

/*
 * Cores with a very high sample rate (SameBoy gives one sample per two CPU clocks, about 2 MHz) are averaged down by a
 * whole factor to near 48 kHz, so the ring and OpenAL get an ordinary rate. Others pass through (decimate == 1).
 */
static unsigned decimate = 1, decimate_count;
static int32_t decimate_sum[2];

static double output_rate(double rate) {
    decimate = rate > 96000.0 ? (unsigned)(rate / 48000.0 + 0.5) : 1;
    decimate_count = 0;
    decimate_sum[0] = decimate_sum[1] = 0;
    return rate / decimate;
}

static void push_frame(int16_t *ring, int16_t left, int16_t right) {
    size_t slot = (size_t)(audio_pos & (CH_SHM_AUDIO_FRAMES - 1)) * 2;
    ring[slot] = left;
    ring[slot + 1] = right;
    audio_pos++;
}

static void push_audio(const int16_t *frames, size_t count) {
    if (!shm || !audio_enabled) return;
    int16_t *ring = (int16_t *)(void *)(shm + CH_SHM_AUDIO_OFFSET);
    for (size_t i = 0; i < count; i++) {
        if (decimate == 1) {
            push_frame(ring, frames[i * 2], frames[i * 2 + 1]);
            continue;
        }
        decimate_sum[0] += frames[i * 2];
        decimate_sum[1] += frames[i * 2 + 1];
        if (++decimate_count < decimate) continue;
        push_frame(ring, (int16_t)(decimate_sum[0] / (int32_t)decimate), (int16_t)(decimate_sum[1] / (int32_t)decimate));
        decimate_count = 0;
        decimate_sum[0] = decimate_sum[1] = 0;
    }
    atomic_store_explicit(shm_atomic(64), audio_pos, memory_order_release);
}

static void RETRO_CALLCONV audio_sample_cb(int16_t left, int16_t right) {
    int16_t frame[2] = {left, right};
    push_audio(frame, 1);
}

static size_t RETRO_CALLCONV audio_batch_cb(const int16_t *data, size_t frames) {
    push_audio(data, frames);
    return frames;
}

static void RETRO_CALLCONV input_poll_cb(void) {}

/* A light gun's aim (protocol.h) as libretro screen coordinates, -0x7fff..0x7fff across the picture. */
static int16_t gun_state(uint16_t aim, unsigned device, unsigned id) {
    unsigned x = aim & 0xFF, half_y = (aim >> 8) & 0x7F;
    bool trigger = (aim & 0x8000) != 0, offscreen = half_y == CH_GUN_OFFSCREEN || half_y >= 120;
    int16_t sx = (int16_t)((x * 2 + 1) * 0xFFFE / 512 - 0x7FFF);
    int16_t sy = (int16_t)((half_y * 4 + 2) * 0xFFFE / 480 - 0x7FFF);
    if (device == RETRO_DEVICE_LIGHTGUN) {
        switch (id) {
        case RETRO_DEVICE_ID_LIGHTGUN_SCREEN_X: return offscreen ? -0x8000 : sx;
        case RETRO_DEVICE_ID_LIGHTGUN_SCREEN_Y: return offscreen ? -0x8000 : sy;
        case RETRO_DEVICE_ID_LIGHTGUN_IS_OFFSCREEN: return offscreen;
        case RETRO_DEVICE_ID_LIGHTGUN_TRIGGER: return trigger;
        default: return 0;
        }
    }
    if (device == RETRO_DEVICE_POINTER) {
        switch (id) {
        case RETRO_DEVICE_ID_POINTER_X: return offscreen ? -0x8000 : sx;
        case RETRO_DEVICE_ID_POINTER_Y: return offscreen ? -0x8000 : sy;
        case RETRO_DEVICE_ID_POINTER_PRESSED: return trigger;
        default: return 0;
        }
    }
    return 0;
}

/* A mouse's frame (protocol.h): the movement, signed, and its buttons. */
static int16_t mouse_state(uint16_t bits, unsigned device, unsigned id) {
    if (device != RETRO_DEVICE_MOUSE) return 0;
    switch (id) {
    case RETRO_DEVICE_ID_MOUSE_X: return (int16_t)((int)(bits & 0x7F) - ((bits & 0x40) ? 0x80 : 0));
    case RETRO_DEVICE_ID_MOUSE_Y: return (int16_t)((int)((bits >> 7) & 0x7F) - ((bits & 0x2000) ? 0x80 : 0));
    case RETRO_DEVICE_ID_MOUSE_LEFT: return (bits >> 14) & 1;
    case RETRO_DEVICE_ID_MOUSE_RIGHT: return (bits >> 15) & 1;
    default: return 0;
    }
}

static int16_t RETRO_CALLCONV input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    static int trace = -1; /* CARTRIDGE_HOST_TRACE=2: every input query too */
    if (trace < 0) trace = getenv("CARTRIDGE_HOST_TRACE") && strcmp(getenv("CARTRIDGE_HOST_TRACE"), "2") == 0;
    if (trace) fprintf(stderr, "[input] port %u device %u index %u id %u\n", port, device, index, id);
    if (port >= cur_ports || index != 0 || !cur_inputs) return 0;
    if (port < MAX_PORTS_INFO && port_kind[port] == CH_DEVICE_LIGHT_GUN)
        return gun_state((uint16_t)cur_inputs[port], device & RETRO_DEVICE_MASK, id);
    if (port < MAX_PORTS_INFO && port_kind[port] == CH_DEVICE_MOUSE)
        return mouse_state((uint16_t)cur_inputs[port], device & RETRO_DEVICE_MASK, id);
    if ((device & RETRO_DEVICE_MASK) == RETRO_DEVICE_ANALOG) {
        /* The left stick (protocol.h): -128..127 scaled to libretro's -32767..32767. */
        if (index != RETRO_DEVICE_INDEX_ANALOG_LEFT || id > RETRO_DEVICE_ID_ANALOG_Y) return 0;
        int v = (int8_t)(cur_inputs[port] >> (id == RETRO_DEVICE_ID_ANALOG_X ? 16 : 24));
        return (int16_t)(v < 0 ? v * 32767 / 128 : v * 32767 / 127);
    }
    if ((device & RETRO_DEVICE_MASK) != RETRO_DEVICE_JOYPAD) return 0;
    uint16_t mask = (uint16_t)cur_inputs[port];
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t)mask;
    return id < 16 ? (int16_t)((mask >> id) & 1) : 0;
}

/* ---- public API ---- */

void core_attach_shm(void *base) {
    shm = base;
    memset(shm, 0, CH_SHM_HEADER_SIZE);
    put_u32(0, CH_SHM_MAGIC);
    put_u32(4, CH_SHM_VERSION);
    put_u32(8, CH_SHM_HEADER_SIZE);
    put_u32(12, CH_SHM_HEADER_SIZE);
    put_u32(16, CH_SHM_VIDEO_CAPACITY);
    put_u32(20, CH_SHM_AUDIO_OFFSET);
    put_u32(24, CH_SHM_AUDIO_FRAMES);
}

void core_set_dirs(const char *dir) { system_dir = dir; }

static int fail(char *err, size_t err_size, const char *msg) {
    snprintf(err, err_size, "%s", msg);
    return -1;
}

#define LOAD_SYM(field, name)                                                     \
    do {                                                                          \
        void *p_ = plat_lib_symbol(C.lib, name);                                  \
        if (!p_) {                                                                \
            snprintf(err, err_size, "the core does not export %s", name);         \
            plat_lib_close(C.lib);                                                \
            memset(&C, 0, sizeof C);                                              \
            return -1;                                                            \
        }                                                                         \
        memcpy(&C.field, &p_, sizeof p_);                                         \
    } while (0)

int core_load(const char *path, char *info, size_t info_size, char *err, size_t err_size) {
    if (C.lib) return fail(err, err_size, "a core is already loaded");
    C.lib = plat_lib_open(path);
    if (!C.lib) return fail(err, err_size, "cannot load the core library");
    LOAD_SYM(init, "retro_init");
    LOAD_SYM(deinit, "retro_deinit");
    LOAD_SYM(api_version, "retro_api_version");
    LOAD_SYM(get_system_info, "retro_get_system_info");
    LOAD_SYM(get_system_av_info, "retro_get_system_av_info");
    LOAD_SYM(set_environment, "retro_set_environment");
    LOAD_SYM(set_video_refresh, "retro_set_video_refresh");
    LOAD_SYM(set_audio_sample, "retro_set_audio_sample");
    LOAD_SYM(set_audio_sample_batch, "retro_set_audio_sample_batch");
    LOAD_SYM(set_input_poll, "retro_set_input_poll");
    LOAD_SYM(set_input_state, "retro_set_input_state");
    LOAD_SYM(set_controller_port_device, "retro_set_controller_port_device");
    LOAD_SYM(reset, "retro_reset");
    LOAD_SYM(run, "retro_run");
    LOAD_SYM(serialize_size, "retro_serialize_size");
    LOAD_SYM(serialize, "retro_serialize");
    LOAD_SYM(unserialize, "retro_unserialize");
    LOAD_SYM(load_game, "retro_load_game");
    LOAD_SYM(load_game_special, "retro_load_game_special");
    LOAD_SYM(unload_game, "retro_unload_game");
    LOAD_SYM(get_region, "retro_get_region");
    LOAD_SYM(get_memory_data, "retro_get_memory_data");
    LOAD_SYM(get_memory_size, "retro_get_memory_size");

    if (C.api_version() != RETRO_API_VERSION) {
        plat_lib_close(C.lib);
        memset(&C, 0, sizeof C);
        return fail(err, err_size, "the core uses a different libretro API version");
    }
    C.set_environment(environment_cb);
    C.init();
    initialized = true;
    C.set_video_refresh(video_cb);
    C.set_audio_sample(audio_sample_cb);
    C.set_audio_sample_batch(audio_batch_cb);
    C.set_input_poll(input_poll_cb);
    C.set_input_state(input_state_cb);

    struct retro_system_info si = {0};
    C.get_system_info(&si);
    snprintf(info, info_size, "name=%s\nversion=%s\nextensions=%s\nneed_fullpath=%d\n",
             si.library_name ? si.library_name : "", si.library_version ? si.library_version : "",
             si.valid_extensions ? si.valid_extensions : "", si.need_fullpath ? 1 : 0);
    return 0;
}

int core_set_option(const char *key_value, char *err, size_t err_size) {
    const char *eq = strchr(key_value, '=');
    if (!eq || eq == key_value) return fail(err, err_size, "expected key=value");
    char key[256];
    size_t n = (size_t)(eq - key_value);
    if (n >= sizeof key) return fail(err, err_size, "option key too long");
    memcpy(key, key_value, n);
    key[n] = '\0';
    struct option *o = add_option(key);
    if (!o) return fail(err, err_size, "too many options");
    free(o->value);
    o->value = dup_string(eq + 1);
    options_dirty = true;
    return 0;
}

/* Whether `ext` appears in a '|'-separated extension list. */
static bool has_extension(const char *list, const char *ext) {
    size_t n = strlen(ext);
    for (const char *p = list; *p;) {
        size_t len = strcspn(p, "|");
        if (len == n && strncmp(p, ext, n) == 0) return true;
        p += len + (p[len] == '|');
    }
    return false;
}

/* Splits "dir/Name.NES" into the name without extension and the lower-case extension. */
static void split_name(const char *path) {
    const char *base = path;
    for (const char *p = path; *p; p++)
        if (*p == '/' || *p == '\\') base = p + 1;
    const char *dot = strrchr(base, '.');
    size_t n = dot ? (size_t)(dot - base) : strlen(base);
    if (n >= sizeof game_name) n = sizeof game_name - 1;
    memcpy(game_name, base, n);
    game_name[n] = '\0';
    game_ext[0] = '\0';
    if (dot) {
        size_t i = 0;
        for (const char *p = dot + 1; *p && i + 1 < sizeof game_ext; p++, i++)
            game_ext[i] = (char)(*p >= 'A' && *p <= 'Z' ? *p - 'A' + 'a' : *p);
        game_ext[i] = '\0';
    }
}

int core_load_game(const char *name, const void *data, size_t size, core_av_info *av, char *err, size_t err_size) {
    if (!C.lib) return fail(err, err_size, "no core loaded");
    if (game_loaded) return fail(err, err_size, "a game is already loaded");
    struct retro_system_info si = {0};
    C.get_system_info(&si);
    snprintf(game_path, sizeof game_path, "%s", name); /* the core may keep pointers into these */
    split_name(game_path);
    bool need_fullpath = si.need_fullpath;
    for (int i = 0; i < override_count; i++)
        if (has_extension(overrides[i].extensions, game_ext)) need_fullpath = overrides[i].need_fullpath;
    if (need_fullpath) {
        /* The file keeps the client's name (MAME knows a game by its zip's name), in a folder of this process's own. */
        snprintf(content_dir, sizeof content_dir, "%s%ccontent-%lu", system_dir, PLAT_SEP, plat_pid());
        snprintf(game_path, sizeof game_path, "%s%c%s.%s", content_dir, PLAT_SEP, game_name, game_ext);
        if (plat_make_dir(content_dir) != 0 || plat_write_file(game_path, data, size) != 0)
            return fail(err, err_size, "cannot write the game for the core to read");
        content_written = true;
        struct retro_game_info game = {.path = game_path, .data = NULL, .size = 0, .meta = NULL};
        if (!C.load_game(&game)) return fail(err, err_size, "the core rejected the game");
        return loaded(av);
    }

    rom_copy = malloc(size ? size : 1);
    if (!rom_copy) return fail(err, err_size, "out of memory");
    memcpy(rom_copy, data, size);
    /* The copy lives until core_shutdown, after retro_deinit, so the data is persistent. libretro.h requires
     * full_path when the content isn't in an archive (FCEUmm dereferences it); the client's name stands in. */
    game_info_ext = (struct retro_game_info_ext){
        .full_path = game_path, .dir = system_dir, .name = game_name, .ext = game_ext, .data = rom_copy, .size = size,
        .persistent_data = true};
    game_info_ext_valid = true;
    struct retro_game_info game = {.path = game_path, .data = rom_copy, .size = size, .meta = NULL};
    if (!C.load_game(&game)) {
        game_info_ext_valid = false;
        free(rom_copy);
        rom_copy = NULL;
        return fail(err, err_size, "the core rejected the game");
    }
    return loaded(av);
}

int core_load_game_linked(const char *ident, unsigned count, const char *const *names, const void *const *datas,
                          const size_t *sizes, core_av_info *av, char *err, size_t err_size) {
    if (!C.lib) return fail(err, err_size, "no core loaded");
    if (game_loaded) return fail(err, err_size, "a game is already loaded");
    if (count != 2) return fail(err, err_size, "linked games come in twos");
    int found = -1;
    for (int i = 0; i < subsystem_count; i++)
        if (strcmp(subsystems[i].ident, ident) == 0) found = i;
    if (found < 0) return fail(err, err_size, "the core has no such subsystem");
    snprintf(game_path, sizeof game_path, "%s", names[0]);
    snprintf(game_path_2, sizeof game_path_2, "%s", names[1]);
    split_name(game_path);
    rom_copy = malloc(sizes[0] ? sizes[0] : 1);
    rom_copy_2 = malloc(sizes[1] ? sizes[1] : 1);
    if (!rom_copy || !rom_copy_2) return fail(err, err_size, "out of memory");
    memcpy(rom_copy, datas[0], sizes[0]);
    memcpy(rom_copy_2, datas[1], sizes[1]);
    struct retro_game_info games[2] = {
        {.path = game_path, .data = rom_copy, .size = sizes[0], .meta = NULL},
        {.path = game_path_2, .data = rom_copy_2, .size = sizes[1], .meta = NULL}};
    if (!C.load_game_special(subsystems[found].id, games, 2)) {
        free(rom_copy);
        free(rom_copy_2);
        rom_copy = rom_copy_2 = NULL;
        return fail(err, err_size, "the core rejected the linked games");
    }
    return loaded(av);
}

/* After a game (or a linked pair) loaded: pads in, a first frame if the core needs one, the timing. */
static int loaded(core_av_info *av) {
    game_loaded = true;
    for (unsigned port = 0; port < 2; port++) {
        C.set_controller_port_device(port, pad(port));
        port_kind[port] = CH_DEVICE_PAD;
    }

    /*
     * Some cores (SameBoy) can't make a savestate until they've run a frame, but the networked game starts from one.
     * Such a core runs its first frame here, silently and with nothing pressed, the same on every player's host.
     */
    size_t probe_size = C.serialize_size();
    void *probe = probe_size ? malloc(probe_size) : NULL;
    if (probe && !C.serialize(probe, probe_size)) {
        cur_ports = 0;
        cur_inputs = NULL;
        audio_enabled = false;
        C.run();
        audio_enabled = true;
    }
    free(probe);

    struct retro_system_av_info info = {0};
    C.get_system_av_info(&info);
    av->base_width = info.geometry.base_width;
    av->base_height = info.geometry.base_height;
    av->max_width = info.geometry.max_width;
    av->max_height = info.geometry.max_height;
    av->fps = info.timing.fps;
    av->sample_rate = output_rate(info.timing.sample_rate);
    av->serialize_size = (uint32_t)C.serialize_size();
    av->region = C.get_region();
    av->quirks = quirks;
    if (shm) {
        put_f64(72, av->fps);
        put_f64(80, av->sample_rate);
    }
    frame_count = 0;
    return 0;
}

uint64_t core_run(uint32_t frames, uint32_t flags, uint32_t ports, const uint32_t *inputs) {
    if (!game_loaded) return frame_count;
    for (uint32_t i = 0; i < frames; i++) {
        cur_ports = ports;
        cur_inputs = inputs + (size_t)i * ports;
        video_enabled = !(flags & CH_RUN_NO_VIDEO) && (!(flags & CH_RUN_RENDER_LAST_ONLY) || i + 1 == frames);
        audio_enabled = !(flags & CH_RUN_MUTE);
        C.run();
        frame_count++;
    }
    cur_inputs = NULL;
    video_enabled = audio_enabled = true;
    if (shm) put_u64(40, frame_count);
    return frame_count;
}

size_t core_serialize_size(void) { return game_loaded ? C.serialize_size() : 0; }

int core_serialize(void *buf, size_t size) { return game_loaded && C.serialize(buf, size) ? 0 : -1; }

int core_unserialize(const void *buf, size_t size) { return game_loaded && C.unserialize(buf, size) ? 0 : -1; }

void *core_memory(unsigned id, size_t *size) {
    void *data = game_loaded ? C.get_memory_data(id) : NULL;
    *size = data ? C.get_memory_size(id) : 0;
    return *size ? data : NULL;
}

void core_reset(void) {
    if (game_loaded) C.reset();
}

int core_set_port_device(unsigned port, unsigned kind, char *err, size_t err_size) {
    if (!game_loaded) return fail(err, err_size, "no game loaded");
    if (port >= MAX_PORTS_INFO) return fail(err, err_size, "no such port");
    if (kind == CH_DEVICE_LIGHT_GUN) {
        if (!gun_device[port]) return fail(err, err_size, "the core has no light gun for this port");
        C.set_controller_port_device(port, gun_device[port]);
    } else if (kind == CH_DEVICE_MOUSE) {
        if (!mouse_device[port]) return fail(err, err_size, "the core has no mouse for this port");
        C.set_controller_port_device(port, mouse_device[port]);
    } else if (kind == CH_DEVICE_PAD) {
        C.set_controller_port_device(port, pad(port));
    } else {
        return fail(err, err_size, "unknown device");
    }
    port_kind[port] = kind;
    return 0;
}

void core_shutdown(void) {
    if (game_loaded) C.unload_game();
    if (initialized) C.deinit();
    plat_lib_close(C.lib);
    free(rom_copy);
    free(rom_copy_2);
    rom_copy_2 = NULL;
    memset(&C, 0, sizeof C);
    rom_copy = NULL;
    game_loaded = initialized = game_info_ext_valid = false;
    if (content_written) {
        plat_remove(game_path);
        plat_remove(content_dir);
        content_written = false;
    }
}
