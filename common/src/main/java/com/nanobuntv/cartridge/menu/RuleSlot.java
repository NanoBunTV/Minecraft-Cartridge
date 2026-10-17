package com.nanobuntv.cartridge.menu;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A slot whose rules are passed in: what it takes, how many, when it shows and when its item can be taken. A named
 * class with its own constructor rather than anonymous {@code new Slot(...) {}}: javac names an anonymous class's
 * constructor parameters after the superclass's, and on 1.19.4's Minecraft as built in CI those names clash
 * ("variable o is already defined").
 */
final class RuleSlot extends Slot {
    private final Predicate<ItemStack> takes;
    private final int maxStackSize;
    private BooleanSupplier active = () -> true;
    private BooleanSupplier unlocked = () -> true;

    /** {@code maxStackSize} 0: the container's own limit. */
    RuleSlot(Container container, int index, int x, int y, Predicate<ItemStack> takes, int maxStackSize) {
        super(container, index, x, y);
        this.takes = takes;
        this.maxStackSize = maxStackSize;
    }

    /** A slot for one item that {@code takes} accepts. */
    static RuleSlot single(Container container, int index, int x, int y, Predicate<ItemStack> takes) {
        return new RuleSlot(container, index, x, y, takes, 1);
    }

    /** Shown and usable only while {@code active} says so. */
    RuleSlot activeWhen(BooleanSupplier active) {
        this.active = active;
        return this;
    }

    /** Nothing goes in or comes out unless {@code unlocked} says so. */
    RuleSlot unlockedWhen(BooleanSupplier unlocked) {
        this.unlocked = unlocked;
        return this;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return unlocked.getAsBoolean() && takes.test(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return unlocked.getAsBoolean() && super.mayPickup(player);
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }

    @Override
    public int getMaxStackSize() {
        return maxStackSize > 0 ? maxStackSize : super.getMaxStackSize();
    }
}
