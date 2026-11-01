package com.arnir.storymod.registry;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.item.DawnStarItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StoryMod.MODID);

    public static final DeferredItem<Item> CORE_DEPTHS = core("core_depths");
    public static final DeferredItem<Item> CORE_FLAME = core("core_flame");
    public static final DeferredItem<Item> CORE_TIDES = core("core_tides");
    public static final DeferredItem<Item> CORE_VOID = core("core_void");

    public static final DeferredItem<DawnStarItem> DAWN_STAR = ITEMS.register("dawn_star",
            () -> new DawnStarItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** Ids of the four cores, used for story flags ("found_core_depths", ...). */
    public static final List<String> CORE_IDS = List.of("core_depths", "core_flame", "core_tides", "core_void");

    private static DeferredItem<Item> core(String name) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    private ModItems() {
    }
}
