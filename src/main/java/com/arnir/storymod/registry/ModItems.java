package com.arnir.storymod.registry;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.item.EffectItem;
import com.arnir.storymod.item.JournalItem;
import com.arnir.storymod.item.WhiteEyeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffects;
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

    public static final DeferredItem<EffectItem> DAWN_STAR = ITEMS.register("dawn_star",
            () -> new EffectItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), 300,
                    ChatFormatting.GOLD,
                    new EffectItem.Effect(MobEffects.REGENERATION, 30, 1),
                    new EffectItem.Effect(MobEffects.NIGHT_VISION, 120, 0),
                    new EffectItem.Effect(MobEffects.DAMAGE_RESISTANCE, 30, 0)));

    public static final DeferredItem<EffectItem> KEEPER_GEAR = ITEMS.register("keeper_gear",
            () -> new EffectItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant(), 180,
                    ChatFormatting.AQUA,
                    new EffectItem.Effect(MobEffects.DIG_SPEED, 120, 1),
                    new EffectItem.Effect(MobEffects.MOVEMENT_SPEED, 60, 0)));

    public static final DeferredItem<WhiteEyeItem> WHITE_EYE = ITEMS.register("white_eye",
            () -> new WhiteEyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<JournalItem> JOURNAL = ITEMS.register("journal",
            () -> new JournalItem(new Item.Properties().stacksTo(1)));

    /** Ids of the four cores, used for story flags ("found_core_depths", ...). */
    public static final List<String> CORE_IDS = List.of("core_depths", "core_flame", "core_tides", "core_void");

    private static DeferredItem<Item> core(String name) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    private ModItems() {
    }
}
