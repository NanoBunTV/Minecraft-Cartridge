//! Builds cartridge-host, the libretro sidecar (D7, D8), for every supported platform (D5) in one go:
//!   zig build                -> zig-out/natives/<os>-<arch>/cartridge-host[.exe] for all targets
//!   zig build run            -> builds for this machine and runs it
//! The natives/<os>-<arch>/ layout matches where the mod jar carries them (TechnicalArchitecture.md).
const std = @import("std");

const version = "0.1.0";

/// D5: Windows x64 and Linux x64 only. glibc 2.28 (2018: Debian 10, RHEL 8, Ubuntu 18.10) keeps the Linux
/// binary loadable on older distros; zig links against that version's symbols.
const targets = [_]struct { triple: []const u8, dir: []const u8 }{
    .{ .triple = "x86_64-windows-gnu", .dir = "windows-x86_64" },
    .{ .triple = "x86_64-linux-gnu.2.28", .dir = "linux-x86_64" },
};

const c_flags = [_][]const u8{
    "-std=c11",
    "-Wall",
    "-Wextra",
    "-Werror",
    "-DCARTRIDGE_HOST_VERSION=\"" ++ version ++ "\"",
};

pub fn build(b: *std.Build) void {
    // Size-optimized by default: the host should stay tiny (D8). Pass -Doptimize=debug for sanitizers and symbols.
    const optimize = b.option(std.builtin.Optimize, "optimize", "Optimization mode (default: small)") orelse .small;

    for (targets) |t| {
        const query = std.Target.Query.parse(.{ .arch_os_abi = t.triple }) catch @panic("bad target triple");
        const exe = addHost(b, b.resolveTargetQuery(query), optimize);
        const install = b.addInstallArtifact(exe, .{
            .dest_dir = .{ .override = .{ .custom = b.fmt("natives/{s}", .{t.dir}) } },
            .pdb_dir = .disabled,
        });
        b.getInstallStep().dependOn(&install.step);
    }

    const run_step = b.step("run", "Build cartridge-host for this machine and run it");
    const run = b.addRunArtifact(addHost(b, b.graph.host, optimize));
    run_step.dependOn(&run.step);
}

fn addHost(b: *std.Build, target: std.Build.ResolvedTarget, optimize: std.builtin.Optimize) *std.Build.Step.Compile {
    const module = b.createModule(.{
        .target = target,
        .optimize = optimize,
        .link_libc = true,
        .strip = optimize != .debug,
    });
    module.addCSourceFiles(.{ .root = b.path("src"), .files = &.{ "main.c", "core.c", "platform.c" }, .flags = &c_flags });
    // libretro.h, vendored from libretro-common (MIT, licence in the file).
    module.addIncludePath(b.path("include"));
    // glibc 2.28 keeps dlopen in libdl.
    if (target.result.os.tag == .linux) module.linkSystemLibrary("dl", .{});
    return b.addExecutable(.{ .name = "cartridge-host", .root_module = module });
}
