package com.arnir.storymod.loot;

import com.arnir.storymod.StoryMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Matches chest loot tables of other mods by namespace, so story finds can appear in their dungeons without
 * naming every table (and without breaking when a mod is not installed).
 * <pre>{"condition": "storymod:mod_chest", "namespaces": ["twilightforest", "aether"]}</pre>
 * A table matches when its namespace is listed and its path contains "chest".
 */
public record ModChestCondition(List<String> namespaces) implements LootItemCondition {
    public static final MapCodec<ModChestCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.listOf().fieldOf("namespaces").forGetter(ModChestCondition::namespaces)
    ).apply(i, ModChestCondition::new));

    public static final DeferredRegister<LootItemConditionType> TYPES = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, StoryMod.MODID);
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> TYPE =
            TYPES.register("mod_chest", () -> new LootItemConditionType(CODEC));

    public static boolean matches(List<String> namespaces, ResourceLocation table) {
        return table != null && namespaces.contains(table.getNamespace()) && table.getPath().contains("chest");
    }

    @Override
    public LootItemConditionType getType() {
        return TYPE.get();
    }

    @Override
    public boolean test(LootContext context) {
        return matches(namespaces, context.getQueriedLootTableId());
    }
}
