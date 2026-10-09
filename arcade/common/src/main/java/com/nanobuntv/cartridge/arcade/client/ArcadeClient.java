package com.nanobuntv.cartridge.arcade.client;

import com.nanobuntv.cartridge.arcade.ArcadeContent;
import com.nanobuntv.cartridge.arcade.block.ArcadeCabinetBlock;
import com.nanobuntv.cartridge.arcade.block.ArcadeCabinetBlockEntity;
import com.nanobuntv.cartridge.client.GamePreparer;
import com.nanobuntv.cartridge.platform.ClientEvents;
import com.nanobuntv.cartridge.platform.ClientServices;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.util.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Cartridge: Arcade's client side, after Cartridge's: the cabinets' renderer and colours, and the Writer's check. */
public final class ArcadeClient {
    private ArcadeClient() {}

    public static void init() {
        ClientEvents events = ClientServices.EVENTS;
        events.<ArcadeCabinetBlockEntity>registerBlockEntityRenderer(ArcadeContent.CABINET_ENTITY, CabinetRenderer::new);
        events.registerBlockColor(ArcadeClient::cabinetColor, ArcadeContent.CABINET, ArcadeContent.CABINET_2P);
        events.registerItemColor(ArcadeClient::itemColor, ArcadeContent.CABINET_ITEM, ArcadeContent.CABINET_2P_ITEM);
        GamePreparer.register(ArcadeContent.BOARDS.id(), new MameCheck(Services.PLATFORM.gameDir())); // D206
        events.onTickEnd(ArcadeSelfTest::tick);
    }

    /** The body's dye (tint 0, D204), from the anchor's block entity. */
    private static int cabinetColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tint) {
        if (tint != 0) return -1;
        int color = ArcadeCabinetBlock.DEFAULT_COLOR;
        if (level != null && pos != null && state.getBlock() instanceof ArcadeCabinetBlock block
                && level.getBlockEntity(block.anchor(state, pos)) instanceof ArcadeCabinetBlockEntity cabinet) {
            color = cabinet.color();
        }
        return Compat.opaque(color);
    }

    private static int itemColor(ItemStack stack, int tint) {
        return tint == 0 ? Compat.opaque(Compat.dye(stack, ArcadeCabinetBlock.DEFAULT_COLOR)) : -1;
    }
}
