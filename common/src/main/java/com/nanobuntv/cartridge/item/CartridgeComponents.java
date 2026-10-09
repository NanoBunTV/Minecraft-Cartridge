package com.nanobuntv.cartridge.item;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.5 {
import com.nanobuntv.cartridge.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.ItemContainerContents;
//?} else {
/*import com.nanobuntv.cartridge.Cartridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
*///?}

/**
 * The mod's item data: data components from 1.20.5, before that an entry in the stack's NBT under the component's id
 * (Porting.md). Read and written through {@link Type}, the same on every version.
 */
public final class CartridgeComponents {
    /** On a burned cartridge: its game. */
    public static final Type<CartridgeContents> CARTRIDGE =
            new Type<>("cartridge", CartridgeContents.CODEC, CartridgeContents.STREAM_CODEC);

    /** On a Memory Card holding a savestate (D91, D98). */
    public static final Type<MemoryCardContents> MEMORY_CARD =
            new Type<>("memory_card", MemoryCardContents.CODEC, MemoryCardContents.STREAM_CODEC);

    /** On an AV Cable: the console a link was started from, until a TV is clicked (D18). */
    public static final Type<GlobalPos> CABLE_START = new Type<>("cable_start", GlobalPos.CODEC, GlobalPos.STREAM_CODEC);

    /** On a Pocketron or Pocketron Color (D158, D164): its id, power and power-on. */
    public static final Type<HandheldState> HANDHELD =
            new Type<>("handheld", HandheldState.CODEC, HandheldState.STREAM_CODEC);

    /** On a linked Pocketron (D176): its link cable to the other. */
    public static final Type<HandheldLink> HANDHELD_LINK =
            new Type<>("handheld_link", HandheldLink.CODEC, HandheldLink.STREAM_CODEC);

    private CartridgeComponents() {}

    public static void init() {}

    //? if <1.20.5 {
    /*private static final String CONTENTS = Cartridge.id("contents").toString();
    *///?}

    /**
     * The items inside an item (a handheld's or an adapter's slots, D163, D166), {@code size} of them: vanilla's
     * container component, before 1.20.5 an item list in the stack's NBT as a shulker box keeps it.
     */
    public static List<ItemStack> contents(ItemStack holder, int size) {
        NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
        //? if >=1.20.5 {
        holder.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        //?} else {
        /*CompoundTag tag = holder.getTagElement(CONTENTS);
        if (tag != null) ContainerHelper.loadAllItems(tag, items);
        *///?}
        return new ArrayList<>(items);
    }

    public static void setContents(ItemStack holder, List<ItemStack> items) {
        //? if >=1.20.5 {
        holder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        //?} else {
        /*NonNullList<ItemStack> list = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) list.set(i, items.get(i));
        ContainerHelper.saveAllItems(holder.getOrCreateTagElement(CONTENTS), list);
        *///?}
    }

    /** Something equal for equal contents, to notice a change made elsewhere. */
    @Nullable
    public static Object contentsState(ItemStack holder) {
        //? if >=1.20.5 {
        return holder.get(DataComponents.CONTAINER);
        //?} else {
        /*CompoundTag tag = holder.getTagElement(CONTENTS);
        return tag == null ? null : tag.copy();
        *///?}
    }

    /** One kind of item data. */
    public static final class Type<T> {
        //? if >=1.20.5 {
        private final Supplier<DataComponentType<T>> type;

        Type(String name, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
            type = Services.REGISTRY.register(BuiltInRegistries.DATA_COMPONENT_TYPE, name,
                    () -> DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build());
        }

        @Nullable
        public T get(ItemStack stack) {
            return stack.get(type.get());
        }

        public void set(ItemStack stack, T value) {
            stack.set(type.get(), value);
        }

        public boolean has(ItemStack stack) {
            return stack.has(type.get());
        }

        public void remove(ItemStack stack) {
            stack.remove(type.get());
        }
        //?} else {
        /*private final String key;
        private final Codec<T> codec;

        Type(String name, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
            this.key = Cartridge.id(name).toString();
            this.codec = codec;
        }

        @Nullable
        public T get(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            Tag value = tag == null ? null : tag.get(key);
            return value == null ? null : codec.parse(NbtOps.INSTANCE, value).result().orElse(null);
        }

        public void set(ItemStack stack, T value) {
            codec.encodeStart(NbtOps.INSTANCE, value).result().ifPresent(tag -> stack.getOrCreateTag().put(key, tag));
        }

        public boolean has(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            return tag != null && tag.contains(key);
        }

        public void remove(ItemStack stack) {
            stack.removeTagKey(key);
        }
        *///?}
    }
}
