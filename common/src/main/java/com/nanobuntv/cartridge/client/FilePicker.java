package com.nanobuntv.cartridge.client;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
//? if >=26.3 {
/*import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
*///?} else {
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
//?}

/**
 * The operating system's own "open file" and "save as" dialogs (D77, D97), through what Minecraft's LWJGL already
 * ships: tinyfd up to 26.2, SDL's dialogs from 26.3. A tinyfd dialog blocks its thread until the player picks or
 * cancels, so it runs on its own thread; SDL's is started on the render thread and answers on its own. Either way the
 * result comes back on the render thread. One dialog at a time.
 */
final class FilePicker {
    private static volatile boolean open;
    private static volatile String lastDirectory;

    private FilePicker() {}

    static boolean isOpen() {
        return open;
    }

    /** {@code patterns} like "*.nes"; {@code onPicked} runs on the render thread, and not at all on cancel. */
    static void open(String title, String[] patterns, String description, Consumer<Path> onPicked) {
        show(title, patterns, description, null, onPicked);
    }

    /** The "save as" dialog, starting with {@code fileName} (D97). */
    static void save(String title, String fileName, String[] patterns, String description, Consumer<Path> onPicked) {
        show(title, patterns, description, fileName, onPicked);
    }

    /** Hands a picked file to {@code onPicked} on the render thread, remembering its folder for the next dialog. */
    private static void picked(String result, Consumer<Path> onPicked) {
        Path path = Path.of(result);
        if (path.getParent() != null) lastDirectory = path.getParent().toString() + File.separator;
        Minecraft.getInstance().execute(() -> onPicked.accept(path));
    }

    //? if >=26.3 {
    /*/^* The dialog's native memory and callback, alive until it answers. ^/
    private static final List<ByteBuffer> STRINGS = new ArrayList<>();
    private static SDL_DialogFileFilter.Buffer filters;
    private static SDL_DialogFileCallback callback;

    private static void show(String title, String[] patterns, String description, String saveAs, Consumer<Path> onPicked) {
        if (open) return;
        open = true;
        // SDL's patterns are extensions, ";" between them: "*.nes" becomes "nes".
        StringBuilder extensions = new StringBuilder();
        for (String pattern : patterns) {
            if (extensions.length() > 0) extensions.append(';');
            extensions.append(pattern.startsWith("*.") ? pattern.substring(2) : pattern);
        }
        filters = SDL_DialogFileFilter.calloc(1);
        filters.get(0).name(string(description)).pattern(string(extensions.toString()));
        callback = SDL_DialogFileCallback.create((userdata, files, filter) -> {
            try {
                // A null list is an error, an empty one a cancel.
                String result = files == 0 ? null : MemoryUtil.memUTF8Safe(MemoryUtil.memGetAddress(files));
                if (result != null) picked(result, onPicked);
            } finally {
                Minecraft.getInstance().execute(FilePicker::free);
            }
        });
        String location = saveAs == null ? lastDirectory : (lastDirectory != null ? lastDirectory : "") + saveAs;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (saveAs == null) {
            SDLDialog.SDL_ShowOpenFileDialog(callback, 0, window, filters, location, false);
        } else {
            SDLDialog.SDL_ShowSaveFileDialog(callback, 0, window, filters, location);
        }
    }

    private static ByteBuffer string(String text) {
        ByteBuffer buffer = MemoryUtil.memUTF8(text);
        STRINGS.add(buffer);
        return buffer;
    }

    private static void free() {
        STRINGS.forEach(MemoryUtil::memFree);
        STRINGS.clear();
        if (filters != null) filters.free();
        if (callback != null) callback.free();
        filters = null;
        callback = null;
        open = false;
    }
    *///?} else {
    private static void show(String title, String[] patterns, String description, String saveAs, Consumer<Path> onPicked) {
        if (open) return;
        open = true;
        Thread thread = new Thread(() -> {
            try {
                String result;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer filters = stack.mallocPointer(patterns.length);
                    for (String pattern : patterns) filters.put(stack.UTF8(pattern));
                    filters.flip();
                    result = saveAs == null
                            ? TinyFileDialogs.tinyfd_openFileDialog(title, lastDirectory, filters, description, false)
                            : TinyFileDialogs.tinyfd_saveFileDialog(title, (lastDirectory != null ? lastDirectory : "") + saveAs,
                                    filters, description);
                }
                if (result != null) picked(result, onPicked);
            } finally {
                open = false;
            }
        }, "Cartridge file picker");
        thread.setDaemon(true);
        thread.start();
    }
    //?}
}
