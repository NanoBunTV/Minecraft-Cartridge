package com.nanobuntv.cartridge.blockentity;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.6 {
/*import com.mojang.serialization.Codec;
import java.util.stream.LongStream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?} else {
import net.minecraft.nbt.Tag;
//?}

/**
 * A block entity's saved fields, written once for every Minecraft version (Porting.md): over a {@code CompoundTag}
 * before 1.21.6, over {@code ValueOutput} / {@code ValueInput} since. The block entities' save and load overrides
 * (whose signatures differ) only wrap their own {@code save(Out)} and {@code load(In)}.
 */
public final class Saved {
    private Saved() {}

    /** A tag of fields, for a block entity's update tag. */
    public static CompoundTag tag(HolderLookup.Provider registries, Consumer<Out> fields) {
        //? if >=1.21.6 {
        /*TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        fields.accept(new Out(output));
        return output.buildResult();
        *///?} else {
        CompoundTag tag = new CompoundTag();
        fields.accept(new Out(tag, registries));
        return tag;
        //?}
    }

    /** A block entity's own fields, without its id and position (for its update tag). */
    public static CompoundTag customOnly(BlockEntity blockEntity, HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        return blockEntity.saveCustomOnly(registries);
        //?} else {
        /*return blockEntity.saveWithoutMetadata();
        *///?}
    }

    public static final class Out {
        //? if >=1.21.6 {
        /*private final ValueOutput out;

        public Out(ValueOutput out) {
            this.out = out;
        }
        *///?} else {
        private final CompoundTag tag;
        private final HolderLookup.Provider registries;

        public Out(CompoundTag tag, HolderLookup.Provider registries) {
            this.tag = tag;
            this.registries = registries;
        }
        //?}

        public void putBoolean(String key, boolean value) {
            //? if >=1.21.6 {
            /*out.putBoolean(key, value);
            *///?} else {
            tag.putBoolean(key, value);
            //?}
        }

        public void putInt(String key, int value) {
            //? if >=1.21.6 {
            /*out.putInt(key, value);
            *///?} else {
            tag.putInt(key, value);
            //?}
        }

        public void putLong(String key, long value) {
            //? if >=1.21.6 {
            /*out.putLong(key, value);
            *///?} else {
            tag.putLong(key, value);
            //?}
        }

        public void putUUID(String key, UUID value) {
            //? if >=1.21.6 {
            /*out.store(key, UUIDUtil.CODEC, value);
            *///?} else {
            tag.putUUID(key, value);
            //?}
        }

        public void putLongArray(String key, long[] value) {
            //? if >=1.21.6 {
            /*out.store(key, Codec.LONG_STREAM, LongStream.of(value));
            *///?} else {
            tag.putLongArray(key, value);
            //?}
        }

        /** A stack; an empty one isn't written. */
        public void putStack(String key, ItemStack stack) {
            if (stack.isEmpty()) return;
            //? if >=1.21.6 {
            /*out.store(key, ItemStack.CODEC, stack);
            *///?} else if >=1.20.5 {
            tag.put(key, stack.save(registries));
            //?} else {
            /*tag.put(key, stack.save(new CompoundTag()));
            *///?}
        }

        /** A container's slots, as {@code ContainerHelper} writes them; {@code withEmpty}: even when all are empty. */
        public void putItems(NonNullList<ItemStack> items, boolean withEmpty) {
            //? if >=1.21.6 {
            /*ContainerHelper.saveAllItems(out, items, withEmpty);
            *///?} else if >=1.20.5 {
            ContainerHelper.saveAllItems(tag, items, withEmpty, registries);
            //?} else {
            /*ContainerHelper.saveAllItems(tag, items, withEmpty);
            *///?}
        }
    }

    public static final class In {
        //? if >=1.21.6 {
        /*private final ValueInput in;

        public In(ValueInput in) {
            this.in = in;
        }
        *///?} else {
        private final CompoundTag tag;
        private final HolderLookup.Provider registries;

        public In(CompoundTag tag, HolderLookup.Provider registries) {
            this.tag = tag;
            this.registries = registries;
        }
        //?}

        public boolean getBoolean(String key) {
            //? if >=1.21.6 {
            /*return in.getBooleanOr(key, false);
            *///?} else {
            return tag.getBoolean(key);
            //?}
        }

        public int getInt(String key, int fallback) {
            //? if >=1.21.6 {
            /*return in.getIntOr(key, fallback);
            *///?} else {
            return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;
            //?}
        }

        @Nullable
        public Long getLong(String key) {
            //? if >=1.21.6 {
            /*return in.getLong(key).orElse(null);
            *///?} else {
            return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : null;
            //?}
        }

        @Nullable
        public UUID getUUID(String key) {
            //? if >=1.21.6 {
            /*return in.read(key, UUIDUtil.CODEC).orElse(null);
            *///?} else {
            return tag.hasUUID(key) ? tag.getUUID(key) : null;
            //?}
        }

        public long[] getLongArray(String key) {
            //? if >=1.21.6 {
            /*return in.read(key, Codec.LONG_STREAM).map(LongStream::toArray).orElse(new long[0]);
            *///?} else {
            return tag.getLongArray(key);
            //?}
        }

        public ItemStack getStack(String key) {
            //? if >=1.21.6 {
            /*return in.read(key, ItemStack.CODEC).orElse(ItemStack.EMPTY);
            *///?} else if >=1.20.5 {
            return tag.contains(key, Tag.TAG_COMPOUND) ? ItemStack.parseOptional(registries, tag.getCompound(key)) : ItemStack.EMPTY;
            //?} else {
            /*return tag.contains(key, Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound(key)) : ItemStack.EMPTY;
            *///?}
        }

        public void getItems(NonNullList<ItemStack> items) {
            //? if >=1.21.6 {
            /*ContainerHelper.loadAllItems(in, items);
            *///?} else if >=1.20.5 {
            ContainerHelper.loadAllItems(tag, items, registries);
            //?} else {
            /*ContainerHelper.loadAllItems(tag, items);
            *///?}
        }
    }
}
