package com.nanobuntv.cartridge.util;

import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
//? if >=1.20.5 && <1.21.2 {
import net.minecraft.world.ItemInteractionResult;
//?}
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}

/**
 * The result of a right-click, written once for every Minecraft version (Porting.md). Blocks and items return one of
 * these from their own version-neutral methods; their thin overrides convert it for the version's API: 1.20.5 split a
 * block's {@code use} into {@code useItemOn} (with its own {@code ItemInteractionResult}) and {@code useWithoutItem},
 * and 1.21.2 merged those results back into {@code InteractionResult} and dropped {@code InteractionResultHolder}.
 */
public enum Use {
    /** Nothing happened. */
    PASS,
    FAIL,
    /** Done, with an arm swing, on both sides. */
    SUCCESS,
    /** Done: the client swings, the server doesn't (vanilla's {@code sidedSuccess}). */
    SIDED,
    /** Done, no swing. */
    CONSUME,
    /** A block's item use: leave it to the held item ({@code SKIP_DEFAULT_BLOCK_INTERACTION}). */
    TO_ITEM,
    /** A block's item use: go on as an empty-handed click ({@code PASS_TO_DEFAULT_BLOCK_INTERACTION}). */
    EMPTY_HAND;

    /** For {@code Block#useWithoutItem}, {@code Item#useOn} and the old {@code Block#use}. */
    public InteractionResult result(Level level) {
        return switch (this) {
            case PASS, TO_ITEM, EMPTY_HAND -> InteractionResult.PASS;
            case FAIL -> InteractionResult.FAIL;
            case SUCCESS -> InteractionResult.SUCCESS;
            //? if >=1.21.2 {
            /*case SIDED -> InteractionResult.SUCCESS;
            *///?} else {
            case SIDED -> InteractionResult.sidedSuccess(level.isClientSide);
            //?}
            case CONSUME -> InteractionResult.CONSUME;
        };
    }

    //? if >=1.21.2 {
    /*/^* For {@code Block#useItemOn}. ^/
    public InteractionResult itemOn(Level level) {
        return switch (this) {
            case TO_ITEM -> InteractionResult.PASS;
            case EMPTY_HAND -> InteractionResult.TRY_WITH_EMPTY_HAND;
            default -> result(level);
        };
    }

    /^* For {@code Item#use}: {@code held} is what the hand holds afterwards. ^/
    public InteractionResult held(ItemStack held, Level level) {
        return this == SUCCESS || this == SIDED ? InteractionResult.SUCCESS.heldItemTransformedTo(held) : result(level);
    }
    *///?} else {
    //? if >=1.20.5 {
    /** For {@code Block#useItemOn}. */
    public ItemInteractionResult itemOn(Level level) {
        return switch (this) {
            case TO_ITEM -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            case EMPTY_HAND -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            case FAIL -> ItemInteractionResult.FAIL;
            case SUCCESS -> ItemInteractionResult.SUCCESS;
            case SIDED -> ItemInteractionResult.sidedSuccess(level.isClientSide);
            case CONSUME -> ItemInteractionResult.CONSUME;
        };
    }
    //?}

    /** For {@code Item#use}: {@code held} is what the hand holds afterwards. */
    public InteractionResultHolder<ItemStack> held(ItemStack held, Level level) {
        return switch (this) {
            case PASS, TO_ITEM, EMPTY_HAND -> InteractionResultHolder.pass(held);
            case FAIL -> InteractionResultHolder.fail(held);
            case SUCCESS -> InteractionResultHolder.success(held);
            case SIDED -> InteractionResultHolder.sidedSuccess(held, level.isClientSide);
            case CONSUME -> InteractionResultHolder.consume(held);
        };
    }
    //?}

    /**
     * Before 1.20.5 a block had one {@code use} for both: the item use first, then, from the main hand, the empty-handed
     * one, as 1.20.5's {@code ServerPlayerGameMode} does.
     */
    public static InteractionResult legacy(Use itemUse, InteractionHand hand, Level level, Supplier<Use> emptyUse) {
        if (itemUse == EMPTY_HAND || itemUse == PASS) {
            return hand == InteractionHand.MAIN_HAND ? emptyUse.get().result(level) : InteractionResult.PASS;
        }
        return itemUse.result(level);
    }
}
