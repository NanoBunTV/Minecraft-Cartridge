package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.item.HandheldItem;
import com.nanobuntv.cartridge.net.LinkRequestPayload;
import com.nanobuntv.cartridge.platform.Services;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * "Link with..." (D176): the players within the cable's length (D178) holding a Pocketron, nearest first. Picking one
 * asks them; their answer comes in chat. Opened from the handheld's GUI, which it goes back to.
 */
public class LinkScreen extends CartridgeScreen {
    private static final int ROWS = 8;
    private final Screen parent;

    LinkScreen(Screen parent) {
        super(Component.translatable("cartridge_mod.link.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<Player> players = new ArrayList<>();
        double range = CartridgeConfig.synced().linkCableRange;
        for (Player p : minecraft.level.players()) {
            if (p == minecraft.player || p.isSpectator() || p.distanceTo(minecraft.player) > range) continue;
            for (InteractionHand hand : InteractionHand.values()) {
                if (HandheldItem.isLinkable(p.getItemInHand(hand))) { // a Pocketron: the Advances don't link (D184)
                    players.add(p);
                    break;
                }
            }
        }
        players.sort(Comparator.comparingDouble(p -> p.distanceTo(minecraft.player)));
        int y = height / 2 - ROWS * 12;
        for (Player p : players.subList(0, Math.min(ROWS, players.size()))) {
            addRenderableWidget(Button.builder(p.getName(), b -> ask(p)).bounds(width / 2 - 80, y, 160, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 80, Math.max(y + 8, height / 2 + 20), 160, 20).build());
    }

    private void ask(Player player) {
        Services.NETWORK.sendToServer(new LinkRequestPayload(player.getUUID()));
        minecraft.player.closeContainer(); // the handheld's GUI too: the answer comes in chat
    }

    @Override
    protected void drawOver(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiDraw.centered(g, font, title, width / 2, height / 2 - ROWS * 12 - 30, 0xFFFFFFFF);
        if (children().size() == 1) {
            GuiDraw.centered(g, font, Component.translatable("cartridge_mod.link.nobody", CartridgeConfig.synced().linkCableRange),
                    width / 2, height / 2 - 10, 0xFFA0A0A0);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
