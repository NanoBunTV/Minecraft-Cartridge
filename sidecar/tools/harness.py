"""cartridge-host test harness (M2). Drives the host over its real protocol (src/protocol.h, version 5) and checks it.

    python sidecar/tools/harness.py              download what's missing, run every check
    python sidecar/tools/harness.py --offline    use only what's already cached

Downloads go to sidecar/.cache/ (git-ignored): public test ROMs (D59), never committed. FCEUmm is unpacked there
from the bundled cores/fceumm (D114). Frame dumps go to sidecar/.cache/out/. Needs Python 3.10+ and Pillow. Windows
only for now; Linux runs are deferred (D60).
"""
import argparse
import hashlib
import io
import mmap
import os
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, ".cache")
OUT = os.path.join(CACHE, "out")
HOST = os.path.join(ROOT, "zig-out", "natives", "windows-x86_64", "cartridge-host.exe")
CORE = os.path.join(CACHE, "cores", "windows-x86_64", "fceumm_libretro.dll")
CORE_ZIP = os.path.join(os.path.dirname(ROOT), "cores", "fceumm", "fceumm_libretro.dll.zip")
ROM_BASE = "https://raw.githubusercontent.com/christopherpow/nes-test-roms/master/"
# name: (path in the test-ROM repository, SHA-1, frames to run, frames holding START)
ROMS = {
    "nestest.nes": ("other/nestest.nes", "5b608f023b41399c34dfc6c847d8af084e0f7aeb", 600, range(60, 66)),
    "official_only.nes": ("instr_test-v5/official_only.nes", "bb9034b8694e51805914e2b8409dee587cbdc98a", 3000,
                          range(0)),
    "apu_test.nes": ("apu_test/apu_test.nes", "b2a9c85072092e910ed6fcc2545880d42ad2b5de", 1200, range(0)),
    "square.nes": ("apu_mixer/square.nes", "9e0d70dfb7717f922287617e4dc47372753c6ae8", 600, range(0)),
}
AUDIBLE = {"square.nes"}  # ROMs that must produce sound
BLANK_SCREEN = {"square.nes"}  # audio-only ROMs that draw nothing

PROTOCOL_VERSION = 6
HELLO, LOAD_CORE, SET_OPTION, LOAD_GAME, RUN, SERIALIZE, UNSERIALIZE, GET_SRAM, SET_SRAM, RESET, CHECKSUM, \
    SHUTDOWN = range(12)
SET_CHECKSUM = 16
CHECKSUM_PICTURE = 1
RENDER_LAST_ONLY, MUTE, NO_VIDEO = 1, 2, 4
START = 1 << 3
SHM_SIZE = 4096 + 640 * 480 * 4 + 16384 * 4  # the protocol's CH_SHM_SIZE; the offsets inside come from the header


class HostError(Exception):
    pass


class Host:
    """One cartridge-host process with its shared-memory file."""

    def __init__(self):
        fd, self.shm_path = tempfile.mkstemp(prefix="cartridge-", suffix=".shm")
        os.close(fd)
        self.proc = subprocess.Popen([HOST, "--shm", self.shm_path, "--system-dir", CACHE],
                                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        hello = self.call(HELLO)
        version = struct.unpack_from("<I", hello)[0]
        if version != PROTOCOL_VERSION:
            raise HostError(f"host speaks protocol {version}, this harness {PROTOCOL_VERSION}: rebuild the host")
        self.host_version = hello[4:].decode("utf-8", "replace")
        self.shm_file = open(self.shm_path, "r+b")
        self.shm = mmap.mmap(self.shm_file.fileno(), SHM_SIZE)
        assert self.shm[0:4] == b"CHSM", "shared-memory magic"
        self.video_offset, _, self.audio_offset, self.audio_capacity = struct.unpack_from("<IIII", self.shm, 12)

    def call(self, cmd, payload=b""):
        self.proc.stdin.write(struct.pack("<II", cmd, len(payload)) + payload)
        self.proc.stdin.flush()
        head = self.proc.stdout.read(8)
        if len(head) < 8:
            raise HostError(f"host exited (code {self.proc.poll()})")
        status, length = struct.unpack("<II", head)
        data = self.proc.stdout.read(length)
        if status:
            raise HostError(data.decode("utf-8", "replace"))
        return data

    def load(self, rom_name, rom):
        info = self.call(LOAD_CORE, CORE.encode())
        name = rom_name.encode()
        av = self.call(LOAD_GAME, struct.pack("<I", len(name)) + name + rom)
        bw, bh, mw, mh, fps, rate, ss, region, quirks = struct.unpack("<IIIIddIIQ", av)
        return info.decode(), dict(size=(bw, bh), fps=fps, rate=rate, state=ss, region=region, quirks=quirks)

    def run(self, inputs, flags=0, ports=2):
        """inputs: one tuple of per-port inputs per frame, 32 bits each (a pad's stick in the upper 16, protocol 5)."""
        payload = struct.pack("<III", len(inputs), flags, ports)
        payload += b"".join(struct.pack(f"<{ports}I", *frame) for frame in inputs)
        return struct.unpack("<Q", self.call(RUN, payload))[0]

    def checksum(self):
        return struct.unpack("<Q", self.call(CHECKSUM))[0]

    def set_checksum(self, kind):
        """What checksum() hashes from now on: the RAM (0) or every picture drawn (CHECKSUM_PICTURE, protocol 6)."""
        self.call(SET_CHECKSUM, struct.pack("<I", kind))

    def frame(self):
        """Reads a consistent video frame (seqlock): (width, height, RGBA bytes)."""
        for _ in range(1000):
            seq = struct.unpack_from("<Q", self.shm, 32)[0]
            if seq & 1:
                continue
            w, h = struct.unpack_from("<II", self.shm, 48)
            pixels = self.shm[self.video_offset:self.video_offset + w * h * 4]
            if struct.unpack_from("<Q", self.shm, 32)[0] == seq:
                return w, h, pixels
        raise HostError("no consistent frame")

    def audio_position(self):
        return struct.unpack_from("<Q", self.shm, 64)[0]

    def audio(self, count):
        """The last `count` stereo frames from the ring, as raw int16 bytes."""
        end = self.audio_position()
        out = bytearray()
        for pos in range(max(0, end - count), end):
            slot = self.audio_offset + (pos & (self.audio_capacity - 1)) * 4
            out += self.shm[slot:slot + 4]
        return bytes(out)

    def close(self):
        try:
            self.call(SHUTDOWN)
        except (HostError, OSError):
            pass
        self.proc.wait(timeout=10)
        self.shm.close()
        self.shm_file.close()
        os.remove(self.shm_path)


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read()


def prepare(offline):
    os.makedirs(os.path.dirname(CORE), exist_ok=True)
    os.makedirs(os.path.join(CACHE, "roms"), exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    if not os.path.exists(HOST):
        sys.exit("build the host first: zig build (in sidecar/)")
    with zipfile.ZipFile(CORE_ZIP) as z:  # always the bundled, pinned build (D114)
        z.extract("fceumm_libretro.dll", os.path.dirname(CORE))
    for name, (path, sha1, _, _) in ROMS.items():
        file = os.path.join(CACHE, "roms", name)
        if not os.path.exists(file):
            if offline:
                sys.exit(f"{name} is not cached; run without --offline")
            with open(file, "wb") as f:
                f.write(fetch(ROM_BASE + path))
        if hashlib.sha1(open(file, "rb").read()).hexdigest() != sha1:
            sys.exit(f"{name}: unexpected SHA-1, delete it from {CACHE}/roms and retry")


def script(frames, start_frames):
    return [((START if i in start_frames else 0), 0) for i in range(frames)]


def save_png(name, frame):
    from PIL import Image
    w, h, pixels = frame
    Image.frombytes("RGBA", (w, h), pixels).save(os.path.join(OUT, name))


def check(label, ok, detail=""):
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}{(': ' + detail) if detail else ''}")
    return ok


def test_rom(name):
    _, _, frames, start = ROMS[name]
    rom = open(os.path.join(CACHE, "roms", name), "rb").read()
    inputs = script(frames, start)
    print(f"{name} ({frames} frames)")
    ok = True

    a = Host()
    info, av = a.load(name, rom)
    if name == "nestest.nes":
        core_info = dict(line.split("=", 1) for line in info.strip().splitlines())
        print(f"  host: {a.host_version}; core: {core_info['name']} {core_info['version']}; video {av['size'][0]}x{av['size'][1]} at "
              f"{av['fps']:.4f} fps, audio {av['rate']:.0f} Hz, savestate {av['state']} bytes, quirks {av['quirks']}")
    half = frames // 2
    peak = 0
    for chunk in range(0, half, 60):
        a.run(inputs[chunk:min(chunk + 60, half)])
        recent = a.audio(800)
        peak = max([peak] + [abs(s) for s in struct.unpack(f"<{len(recent) // 2}h", recent)])
    state = a.call(SERIALIZE)
    count = a.run(inputs[half:])
    ok &= check("frame count", count == frames, str(count))
    final = a.frame()
    final_sum = a.checksum()
    save_png(name.replace(".nes", ".png"), final)
    drawn = name in BLANK_SCREEN or len(set(final[2][::4])) > 1
    ok &= check("video frame", final[0] > 0 and final[1] > 0 and drawn, f"{final[0]}x{final[1]}, saved to .cache/out/")
    expected_audio = frames / av["fps"] * av["rate"]
    produced = a.audio_position()
    ok &= check("audio produced", abs(produced - expected_audio) / expected_audio < 0.02,
                f"{produced} stereo frames, expected about {expected_audio:.0f}")

    # Savestate round trip: restore the mid-point state and replay the second half. Compared by the host's RAM
    # checksum and the video frame (a loaded state may differ in core bookkeeping, see CHECKSUM in main.c).
    a.call(UNSERIALIZE, state)
    a.run(inputs[half:], flags=RENDER_LAST_ONLY | MUTE)
    ok &= check("savestate round trip", a.checksum() == final_sum and a.frame() == final)

    if name in AUDIBLE:
        ok &= check("audio not silent", peak > 0, f"peak {peak}")
    a.close()

    # Determinism: two fresh, independent processes fed the same inputs have byte-identical savestates at every
    # checkpoint (no state is loaded here, so the whole state must match), and match the first run at the end.
    b, c = Host(), Host()
    b.load(name, rom)
    c.load(name, rom)
    states_b, states_c, sums_b = [], [], []
    for chunk in range(0, frames, 60):
        b.run(inputs[chunk:chunk + 60], flags=RENDER_LAST_ONLY)
        c.run(inputs[chunk:chunk + 60], flags=RENDER_LAST_ONLY | MUTE)
        states_b.append(hashlib.sha1(b.call(SERIALIZE)).digest())
        states_c.append(hashlib.sha1(c.call(SERIALIZE)).digest())
        sums_b.append(b.checksum())
    ok &= check("determinism across processes", states_b == states_c and b.frame() == c.frame(),
                f"{len(states_b)} full-state checkpoints")
    ok &= check("same result as the first run", sums_b[-1] == final_sum and b.frame() == final)
    b.close()
    c.close()

    # The picture checksum (MAME's, D194): every picture is hashed, drawn to shared memory or not.
    d, e = Host(), Host()
    d.load(name, rom)
    e.load(name, rom)
    d.set_checksum(CHECKSUM_PICTURE)
    e.set_checksum(CHECKSUM_PICTURE)
    d.run(inputs, flags=NO_VIDEO | MUTE)
    e.run(inputs)
    ok &= check("picture checksum, video off or on", d.checksum() == e.checksum() != 0)
    d.close()
    e.close()
    return ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    if sys.platform != "win32":
        sys.exit("Windows only for now (D60)")
    prepare(args.offline)
    print(f"core: {CORE} (SHA-256 {hashlib.sha256(open(CORE, 'rb').read()).hexdigest()[:16]}...)")
    results = [test_rom(name) for name in ROMS]
    print("ALL PASSED" if all(results) else "FAILURES")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
