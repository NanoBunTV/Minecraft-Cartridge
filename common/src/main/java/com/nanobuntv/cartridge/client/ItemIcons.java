package com.nanobuntv.cartridge.client;

// Up to 1.21.3 only: from 1.21.4 the item model definitions (assets/cartridge_mod/items/) pick these models.
//? if <1.21.4 {
import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.CartridgeItem;
import com.nanobuntv.cartridge.item.CartridgeItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Extra item models, swapped in by {@code ItemRendererMixin}:
 * <ul>
 *   <li>flat inventory icons for the items that are 3D in hand, on the ground and in item frames (D43): each such
 *   item's {@code models/item/<id>.json} is the 3D model, {@code models/item/<id>_icon.json} the flat sprite;</li>
 *   <li>the cartridge's shape per console (D143): a burned SNES game is a Super Pixelon cartridge,
 *   {@code models/item/cartridge_16bit.json} and its icon, a Genesis game a Vortex Drive cartridge,
 *   {@code cartridge_vortex.json}, Game Boy games Pocketron cartridges, grey, black or clear (D170), Game Boy Advance games
 *   Pocketron Advance cartridges (D185), Nintendo 64 games Pixelon 64 cartridges (D189).</li>
 * </ul>
 * The loaders bake these as extra models and supply the lookup.
 */
public final class ItemIcons {
    private static final Set<String> ITEMS = Set.of("blank_cartridge", "cartridge", "controller_8bit", "light_gun",
            "av_cable", "memory_card", "controller_16bit", "mouse_16bit", "controller_vortex", "pocketron",
            "pocketron_color", "pocketron_adapter", "pocketron_advance", "pocketron_advance_sp", "controller_64");
    /** Cartridge shapes other than the item's own, by system (D143, D170). */
    private static final Map<String, String> SHAPES = CartridgeItem.SHAPES;

    private static Function<ResourceLocation, BakedModel> lookup = id -> null;

    private ItemIcons() {}

    /** The models the loader must bake as extra models. */
    public static List<ResourceLocation> models() {
        List<ResourceLocation> models = new ArrayList<>();
        ITEMS.forEach(item -> models.add(model(item + "_icon")));
        SHAPES.values().forEach(shape -> {
            models.add(model(shape));
            models.add(model(shape + "_icon"));
        });
        return models;
    }

    /** Set by the loader: fetches a baked extra model by its id. */
    public static void setLookup(Function<ResourceLocation, BakedModel> modelLookup) {
        lookup = modelLookup;
    }

    /**
     * The model to draw this stack with instead of its own, or null to keep its own: in inventories its flat icon, and
     * a cartridge in its console's shape.
     */
    @Nullable
    public static BakedModel modelFor(ItemStack stack, boolean gui) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals(Cartridge.MOD_ID)) return null;
        String name = id.getPath();
        if (stack.is(CartridgeItems.CARTRIDGE.get())) {
            CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(stack);
            if (contents != null) name = SHAPES.getOrDefault(contents.system(), name);
        }
        boolean shape = SHAPES.containsValue(name);
        if (gui && (ITEMS.contains(name) || shape)) return baked(name + "_icon");
        return shape ? baked(name) : null;
    }

    @Nullable
    private static BakedModel baked(String name) {
        BakedModel model = lookup.apply(model(name));
        return model == null || model == Minecraft.getInstance().getModelManager().getMissingModel() ? null : model;
    }

    private static ResourceLocation model(String name) {
        return Cartridge.id("item/" + name);
    }
}
//?}
