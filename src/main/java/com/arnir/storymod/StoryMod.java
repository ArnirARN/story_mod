package com.arnir.storymod;

import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.network.ModNetwork;
import com.arnir.storymod.registry.ModEntities;
import com.arnir.storymod.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(StoryMod.MODID)
public class StoryMod {
    public static final String MODID = "storymod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StoryMod(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        com.arnir.storymod.loot.ModChestCondition.TYPES.register(modBus);
        modBus.addListener(StoryMod::onAttributes);
        modBus.addListener(ModNetwork::register);
        com.arnir.storymod.sync.PackSync.start();
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.STORY_NPC.get(), StoryNpc.createAttributes().build());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
