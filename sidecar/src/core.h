/* One libretro core instance (D6, D7): loading, the frontend callbacks, and the shared-memory outputs. */
#ifndef CARTRIDGE_CORE_H
#define CARTRIDGE_CORE_H

#include <stddef.h>
#include <stdint.h>

typedef struct {
    uint32_t base_width, base_height, max_width, max_height;
    double fps, sample_rate;
    uint32_t serialize_size, region;
    uint64_t quirks;
} core_av_info;

/* Attaches the shared-memory file (already mapped and CH_SHM_SIZE bytes long) and writes its header. */
void core_attach_shm(void *shm);
/* Directory handed to the core as its system and save directory. */
void core_set_dirs(const char *system_dir);

/* Loads the library and calls retro_init. Fills `info` with "key=value\n" lines. 0 on success. */
int core_load(const char *path, char *info, size_t info_size, char *err, size_t err_size);
/* Sets "key=value"; the core reads it on its next GET_VARIABLE. */
int core_set_option(const char *key_value, char *err, size_t err_size);
int core_load_game(const char *name, const void *data, size_t size, core_av_info *av, char *err, size_t err_size);
/* Loads `count` games through the subsystem named `ident` (retro_load_game_special). 0 on success. */
int core_load_game_linked(const char *ident, unsigned count, const char *const *names, const void *const *datas,
                          const size_t *sizes, core_av_info *av, char *err, size_t err_size);
/* Runs `frames` frames; `inputs` holds `ports` inputs per frame (protocol.h). Returns the total frame count. */
uint64_t core_run(uint32_t frames, uint32_t flags, uint32_t ports, const uint32_t *inputs);
size_t core_serialize_size(void);
int core_serialize(void *buf, size_t size);
int core_unserialize(const void *buf, size_t size);
/* A libretro memory region (RETRO_MEMORY_SAVE_RAM, _SYSTEM_RAM, _VIDEO_RAM); NULL with size 0 if absent. */
void *core_memory(unsigned id, size_t *size);
void core_reset(void);
/* What's plugged into `port` (CH_DEVICE_*). 0 on success; -1 if the core offers no such device there. */
int core_set_port_device(unsigned port, unsigned kind, char *err, size_t err_size);
void core_shutdown(void);
/* Hash every picture the core draws from now on (CH_CHECKSUM_PICTURE), or stop. */
void core_set_picture_hash(int on);
/* The hash of the last picture drawn, 0 before the first; only while core_set_picture_hash is on. */
uint64_t core_picture_hash(void);

#endif
