/*
 * cartridge-host wire protocol and shared-memory layout (D7). Everything is little-endian.
 *
 * Commands travel over the host's stdin, replies over its stdout; logs go to stderr. When stdin closes, the host
 * shuts down, so it never outlives the game that started it.
 *
 *   request:  u32 command, u32 payload length, payload
 *   reply:    u32 status (0 = ok, 1 = error), u32 payload length, payload (the error text when status is 1)
 *
 * The video frame and the audio ring live in a memory-mapped file whose path the client passes as `--shm <path>`.
 * The host creates it; the client maps it after HELLO and deletes it once the host has exited.
 */
#ifndef CARTRIDGE_PROTOCOL_H
#define CARTRIDGE_PROTOCOL_H

#define CH_PROTOCOL_VERSION 6u

enum ch_command {
    /* -> u32 protocol version, then the host version (UTF-8) */
    CH_HELLO = 0,
    /* UTF-8 path of the core library -> "key=value\n" lines: name, version, extensions, need_fullpath */
    CH_LOAD_CORE = 1,
    /* "key=value" (UTF-8). Set options before LOAD_GAME; netplay pins them (Reference-Libretro.md). */
    CH_SET_OPTION = 2,
    /* u32 name length, name (UTF-8, e.g. "game.nes"), ROM bytes ->
     * u32 base_width, u32 base_height, u32 max_width, u32 max_height, f64 fps, f64 sample_rate,
     * u32 serialize_size, u32 region, u64 serialization_quirks */
    CH_LOAD_GAME = 3,
    /* u32 frames, u32 flags, u32 ports, u32 input[frames * ports] -> u64 frame count */
    CH_RUN = 4,
    /* -> savestate bytes */
    CH_SERIALIZE = 5,
    /* savestate bytes */
    CH_UNSERIALIZE = 6,
    /* -> battery RAM bytes (empty if the game has none) */
    CH_GET_SRAM = 7,
    /* battery RAM bytes */
    CH_SET_SRAM = 8,
    CH_RESET = 9,
    /* -> u64 hash for desync checks (D12): of system, battery and video RAM (not the savestate, see main.c), or with
     * CH_CHECKSUM_PICTURE of the last picture the core drew */
    CH_CHECKSUM = 10,
    CH_SHUTDOWN = 11,
    /* u32 port, u32 kind (CH_DEVICE_*): what is plugged into a port. After LOAD_GAME; kept across savestates. */
    CH_SET_PORT_DEVICE = 12,
    /* Two games as one, through a core subsystem (SameBoy's linked Game Boys, D177): u32 ident length, ident (e.g.
     * "gb_link_2p"), then per game u32 name length, name, u32 data length, data -> as LOAD_GAME */
    CH_LOAD_GAME_LINKED = 13,
    /* u32 libretro memory id (RETRO_MEMORY_SAVE_RAM, or a subsystem's own, e.g. 0x101 for the first Game Boy's)
     * -> its bytes (empty if absent) */
    CH_GET_MEMORY = 14,
    /* u32 memory id, then its bytes (the size must match) */
    CH_SET_MEMORY = 15,
    /* u32 kind (CH_CHECKSUM_*): what CHECKSUM hashes from now on. Set after LOAD_GAME, before the first RUN. */
    CH_SET_CHECKSUM = 16,
};

/* CH_SET_CHECKSUM kinds */
#define CH_CHECKSUM_RAM 0u     /* the default */
#define CH_CHECKSUM_PICTURE 1u /* every picture the core draws, video off or not: MAME, which shows no RAM (D194) */

/* CH_SET_PORT_DEVICE kinds */
#define CH_DEVICE_PAD 0u
#define CH_DEVICE_LIGHT_GUN 1u /* the core's Zapper (D25, D99) */
#define CH_DEVICE_MOUSE 2u     /* the core's mouse, e.g. the SNES Mouse (D144) */

/* CH_RUN flags */
#define CH_RUN_RENDER_LAST_ONLY 1u /* only the last frame reaches shared memory (rollback re-simulation) */
#define CH_RUN_MUTE 2u             /* drop the audio of these frames (re-simulated audio was already played) */
#define CH_RUN_NO_VIDEO 4u         /* none of these frames reaches shared memory (rollback steps before the last) */

/* Joypad bitmask: bit n = RETRO_DEVICE_ID_JOYPAD_n (B=0, Y=1, SELECT=2, START=3, UP=4, DOWN=5, LEFT=6, RIGHT=7, A=8).
 * A light-gun port's u16 is the aim instead: bits 0-7 x (0-255), bits 8-14 y / 2 (0-119; 127 = off the screen),
 * bit 15 the trigger. A mouse port's u16 is the frame's movement: bits 0-6 x, bits 7-13 y (each -64..63, two's
 * complement), bit 14 the left button, bit 15 the right. A port's upper 16 bits are its pad's left analog stick
 * (the Pixelon 64's, D189): bits 16-23 x, bits 24-31 y, each -128..127 (two's complement; y grows downwards). */
#define CH_GUN_OFFSCREEN 127u

/*
 * Shared-memory file layout. Byte offsets in the header:
 *    0 u32 magic 'CHSM'          4 u32 layout version     8 u32 header size (= video offset)
 *   12 u32 video offset         16 u32 video capacity (bytes)
 *   20 u32 audio offset         24 u32 audio capacity (stereo frames, a power of two)
 *   28 u32 reserved
 *   32 u64 video sequence: odd while the host writes a frame, even when it's complete (seqlock)
 *   40 u64 frames run since the game loaded
 *   48 u32 width   52 u32 height   56 u32 pitch (bytes; always width * 4)   60 u32 reserved
 *   64 u64 audio write position (stereo frames written so far; slot = position & (capacity - 1))
 *   72 f64 fps     80 f64 audio sample rate
 * Video pixels are RGBA8888 (bytes R, G, B, A). Audio is int16 stereo, interleaved.
 */
#define CH_SHM_MAGIC 0x4D534843u /* "CHSM" */
#define CH_SHM_VERSION 1u
#define CH_SHM_HEADER_SIZE 4096u
#define CH_SHM_MAX_WIDTH 640u
#define CH_SHM_MAX_HEIGHT 480u
#define CH_SHM_VIDEO_CAPACITY (CH_SHM_MAX_WIDTH * CH_SHM_MAX_HEIGHT * 4u)
#define CH_SHM_AUDIO_FRAMES 16384u
#define CH_SHM_AUDIO_OFFSET (CH_SHM_HEADER_SIZE + CH_SHM_VIDEO_CAPACITY)
#define CH_SHM_SIZE (CH_SHM_AUDIO_OFFSET + CH_SHM_AUDIO_FRAMES * 4u)

#endif
