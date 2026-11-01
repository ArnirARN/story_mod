package com.arnir.storymod.dialogue;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.story.StoryEvents;
import com.arnir.storymod.story.StoryState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.ArrayList;
import java.util.List;

/**
 * Something a dialogue node or option does. Types:
 * set_flag, clear_flag, give_item, give_item_all, take_item, give_loot, spawn_npc, despawn_self,
 * despawn_character, become_boss, broadcast, message, effect, sound, lightning.
 */
public record Action(String type, JsonObject args) {

    public static List<Action> parseList(JsonElement element) {
        List<Action> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return out;
        }
        for (JsonElement e : element.getAsJsonArray()) {
            JsonObject obj = e.getAsJsonObject();
            out.add(new Action(obj.get("type").getAsString(), obj));
        }
        return out;
    }

    private String str(String key, String fallback) {
        return args.has(key) ? args.get(key).getAsString() : fallback;
    }

    private int num(String key, int fallback) {
        return args.has(key) ? args.get(key).getAsInt() : fallback;
    }

    public void run(ServerPlayer player, StoryNpc npc) {
        ServerLevel level = player.serverLevel();
        StoryState state = StoryState.get(level.getServer());
        switch (type) {
            case "set_flag" -> state.set(str("flag", ""));
            case "clear_flag" -> state.clear(str("flag", ""));
            case "give_item" -> give(player, new ItemStack(Inventories.item(str("item", "minecraft:air")), num("count", 1)));
            case "give_item_all" -> {
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    give(p, new ItemStack(Inventories.item(str("item", "minecraft:air")), num("count", 1)));
                }
            }
            case "take_item" -> Inventories.take(player, str("item", "minecraft:air"), num("count", 1));
            case "give_loot" -> {
                ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(str("table", "")));
                LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
                for (ItemStack stack : table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY))) {
                    give(player, stack);
                }
            }
            case "spawn_npc" -> {
                int distance = num("distance", 3);
                BlockPos pos = StoryEvents.groundNear(level, player.blockPosition(), distance);
                StoryNpc.spawn(level, str("character", "veld"), pos, player.getYRot() + 180.0F);
            }
            case "despawn_self" -> {
                if (npc != null) {
                    StoryState.get(level.getServer()).bumpGeneration(npc.getCharacter());
                    poof(level, npc);
                    npc.discard();
                }
            }
            case "despawn_character" -> {
                String character = str("character", "");
                state.bumpGeneration(character);
                for (ServerLevel l : level.getServer().getAllLevels()) {
                    for (StoryNpc other : l.getEntities(com.arnir.storymod.registry.ModEntities.STORY_NPC.get(),
                            e -> e.getCharacter().equals(character))) {
                        poof(l, other);
                        other.discard();
                    }
                }
            }
            case "become_boss" -> {
                if (npc != null) {
                    npc.becomeBoss();
                }
            }
            case "broadcast" -> level.getServer().getPlayerList().broadcastSystemMessage(text(player), false);
            case "message" -> player.sendSystemMessage(text(player));
            case "effect" -> BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(str("effect", "minecraft:glowing")))
                    .ifPresent(effect -> player.addEffect(new MobEffectInstance(effect, num("duration", 200), num("amplifier", 0))));
            case "sound" -> {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(str("sound", "minecraft:block.bell.use")));
                if (sound != null) {
                    level.playSound(null, player.blockPosition(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
                }
            }
            case "lightning" -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    BlockPos at = npc != null ? npc.blockPosition() : player.blockPosition();
                    bolt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
            default -> StoryMod.LOGGER.warn("Unknown dialogue action type '{}'", type);
        }
    }

    private Component text(ServerPlayer player) {
        String raw = str("text", "").replace("{player}", player.getGameProfile().getName());
        ChatFormatting color = ChatFormatting.getByName(str("color", "gray"));
        return Component.literal(raw).withStyle(color == null ? ChatFormatting.GRAY : color).withStyle(ChatFormatting.ITALIC);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void poof(ServerLevel level, StoryNpc npc) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, npc.getX(), npc.getY() + 1.0, npc.getZ(),
                80, 0.4, 0.9, 0.4, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, npc.getX(), npc.getY() + 1.0, npc.getZ(),
                20, 0.3, 0.6, 0.3, 0.01);
    }
}
