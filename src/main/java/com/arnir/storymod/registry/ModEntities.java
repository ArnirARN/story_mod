package com.arnir.storymod.registry;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.entity.StoryNpc;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, StoryMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<StoryNpc>> STORY_NPC = ENTITIES.register("story_npc",
            () -> EntityType.Builder.<StoryNpc>of(StoryNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("story_npc"));

    private ModEntities() {
    }
}
