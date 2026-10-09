package com.nanobuntv.cartridge.config;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.nanobuntv.cartridge.Cartridge;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * A settings class's JSON5 file (D122): its {@link SerialEntry} fields, each with its comment above it. Read leniently
 * (comments, trailing commas); a key the file lacks keeps its default. Plain Gson, which Minecraft ships, so it's the
 * same on every Minecraft version whatever YetAnotherConfigLib offers there (YACL draws the screen only).
 */
final class SettingsFile<T> {
    private final Path path;
    private final Class<T> type;
    private final Supplier<T> defaults;
    private final Gson gson;
    private T instance;

    SettingsFile(Path path, Class<T> type, Supplier<T> defaults) {
        this.path = path;
        this.type = type;
        this.defaults = defaults;
        this.instance = defaults.get();
        // Only the marked fields of the settings class itself; nested objects (control profiles) keep all theirs.
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                .setExclusionStrategies(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes field) {
                        return field.getDeclaringClass() == type && field.getAnnotation(SerialEntry.class) == null;
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> c) {
                        return false;
                    }
                })
                .create();
    }

    T instance() {
        return instance;
    }

    T defaults() {
        return defaults.get();
    }

    /** Reads the file; without one (or with a broken one) the defaults. */
    void load() {
        if (!Files.exists(path)) {
            instance = defaults.get();
            return;
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(Files.readString(path, StandardCharsets.UTF_8)));
            reader.setLenient(true);
            T read = gson.fromJson(reader, type);
            instance = read != null ? read : defaults.get();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Cartridge.LOGGER.error("Couldn't read {}; using the defaults", path, e);
            instance = defaults.get();
        }
    }

    /** Writes the file, each field's comment above it. */
    void save() {
        String json = gson.toJson(instance, type);
        StringBuilder out = new StringBuilder();
        for (String line : json.split("\n", -1)) {
            String comment = commentFor(line);
            if (comment != null) {
                for (String part : comment.split("\n")) out.append("  // ").append(part).append('\n');
            }
            out.append(line).append('\n');
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, out.toString().stripTrailing() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            Cartridge.LOGGER.error("Couldn't write {}", path, e);
        }
    }

    /** The comment for a top-level key's line ({@code   "name": ...}), or null. */
    private String commentFor(String line) {
        if (!line.startsWith("  \"") || line.startsWith("   ")) return null;
        int end = line.indexOf('"', 3);
        if (end < 0) return null;
        try {
            Field field = type.getField(line.substring(3, end));
            SerialEntry entry = field.getAnnotation(SerialEntry.class);
            return entry == null || entry.comment().isEmpty() ? null : entry.comment();
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
