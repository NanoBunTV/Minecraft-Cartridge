package com.nanobuntv.cartridge.item;

import net.minecraft.world.phys.Vec3;
import com.nanobuntv.cartridge.util.Compat;
import com.nanobuntv.cartridge.util.Use;
import com.nanobuntv.cartridge.CartridgeSounds;
import com.nanobuntv.cartridge.block.ConsoleBlock;
import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.blockentity.TvBlockEntity;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.5 {
/*import net.minecraft.world.item.component.TooltipDisplay;
*///?}

/**
 * AV Cable (D18, D53, D61): right-click a console, then a display (any TV, or any panel of a video wall) within
 * {@code cableRange} blocks. Each link uses one
 * cable; a console feeds at most {@code maxTvsPerConsole} TVs. Sneak + right-click in the air cancels a started link.
 */
public class AvCableItem extends Item {
    public AvCableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockState(pos).getBlock() instanceof ConsoleBlock block) { // an arcade cabinet's upper half
            pos = block.anchor(level.getBlockState(pos), pos);
        }
        boolean console = level.getBlockEntity(pos) instanceof ConsoleBlockEntity;
        DisplayBlock display = DisplayBlock.of(level.getBlockState(pos));
        if (!console && display == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (console) {
            CartridgeComponents.CABLE_START.set(stack, GlobalPos.of(level.dimension(), pos.immutable()));
            message(player, Component.translatable("cartridge_mod.cable.started", range()));
            return InteractionResult.CONSUME;
        }
        // The display's anchor holds the link: a Large Flatscreen's main space, a video wall's linked panel (D129, D131).
        BlockPos anchor = display.anchor(level, pos, level.getBlockState(pos));
        if (level.getBlockEntity(anchor) instanceof TvBlockEntity tv) link(level, player, stack, anchor, tv);
        return InteractionResult.CONSUME;
    }

    private void link(Level level, Player player, ItemStack stack, BlockPos pos, TvBlockEntity tv) {
        GlobalPos start = CartridgeComponents.CABLE_START.get(stack);
        if (start == null) {
            message(player, Component.translatable("cartridge_mod.cable.no_start"));
            return;
        }
        if (!start.dimension().equals(level.dimension())
                || !(level.getBlockEntity(start.pos()) instanceof ConsoleBlockEntity console)) {
            CartridgeComponents.CABLE_START.remove(stack);
            message(player, Component.translatable("cartridge_mod.cable.console_gone"));
            return;
        }
        if (start.pos().equals(tv.console())) {
            message(player, Component.translatable("cartridge_mod.cable.already_linked"));
            return;
        }
        if (Vec3.atCenterOf(start.pos()).distanceTo(Vec3.atCenterOf(pos)) > range()) {
            message(player, Component.translatable("cartridge_mod.cable.too_far", range()));
            return;
        }
        if (console.tvs().size() >= maxTvs()) {
            message(player, Component.translatable("cartridge_mod.cable.too_many", maxTvs()));
            return;
        }
        if (tv.console() != null) {
            // Relinking a TV to another console: its old cable comes back (D53).
            DisplayBlock.unlink(level, pos, tv);
            ItemStack old = new ItemStack(this);
            if (player == null || !player.getInventory().add(old)) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), old);
            }
        }
        console.addTv(pos);
        tv.setConsole(start.pos());
        CartridgeComponents.CABLE_START.remove(stack);
        Compat.consume(stack, 1, player);
        CartridgeSounds.play(level, pos, CartridgeSounds.CABLE_LINK, 0.8F);
        message(player, Component.translatable("cartridge_mod.cable.linked"));
    }

    //? if >=1.21.2 {
    /*@Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return onUse(level, player, hand).held(player.getItemInHand(hand), level);
    }
    *///?} else {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return onUse(level, player, hand).held(player.getItemInHand(hand), level);
    }
    //?}

    private Use onUse(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && CartridgeComponents.CABLE_START.has(stack)) {
            if (!level.isClientSide) {
                CartridgeComponents.CABLE_START.remove(stack);
                message(player, Component.translatable("cartridge_mod.cable.cancelled"));
            }
            return Use.SIDED;
        }
        return Use.PASS; // as Item#use does for a plain item
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return CartridgeComponents.CABLE_START.has(stack) || super.isFoil(stack);
    }

    //? if >=1.21.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip(stack, tooltip, flag);
    }
    *///?} else if >=1.20.5 {
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip(stack, tooltip::add, flag);
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip(stack, tooltip::add, flag);
    }
    *///?}

    private void tooltip(ItemStack stack, Consumer<Component> tooltip, TooltipFlag flag) {
        GlobalPos start = CartridgeComponents.CABLE_START.get(stack);
        if (start != null) {
            BlockPos p = start.pos();
            tooltip.accept(Component.translatable("cartridge_mod.cable.tooltip_linking", p.getX(), p.getY(), p.getZ())
                    .withStyle(ChatFormatting.GRAY));
        }
        ItemHelp.append(stack, tooltip);
    }

    private static void message(Player player, Component text) {
        if (player != null) Compat.overlay(player, text);
    }

    private static int range() {
        return CartridgeConfig.server().cableRange;
    }

    private static int maxTvs() {
        return CartridgeConfig.server().maxTvsPerConsole;
    }
}
