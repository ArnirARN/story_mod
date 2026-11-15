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
 * Something a dialogue node, option or trigger does. Types:
 * set_flag, clear_flag, set_pflag, clear_pflag, title, give_structure_compass, give_item, give_item_all, take_item, give_loot, spawn_npc, despawn_self,
 * despawn_character, become_boss, broadcast, message, effect, sound, lightning,
 * give_item_role, counter_add, star_fall, break_lantern, spawn_echoes, seam_up, teleport_underside, leave_underside,
 * set_time, effect_all, make_boss, mark_place, calm, outpost_done, silence_mark, silence_cure.
 * spawn_npc takes "at": "anchor" | "place" (with "place": name) | player (default).
 */
public record Action(String type, JsonObject args) {

    public static List<Action> parseList(JsonElement element) {
        List<Action> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return out;
        }
        for (JsonElement e : element.getAsJsonArray()) {
            JsonObject obj = e.getAsJsonObject();
            if (obj.has("item")) {
                Inventories.REFERENCED.add(obj.get("item").getAsString());
            }
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
            case "set_pflag" -> state.setPersonal(player.getUUID(), str("flag", ""));
            case "clear_pflag" -> state.clearPersonal(player.getUUID(), str("flag", ""));
            case "give_structure_compass" -> giveStructureCompass(player, level);
            case "title" -> {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(20, 80, 30));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                        Component.literal(str("subtitle", "")).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal(str("text", "")).withStyle(ChatFormatting.WHITE)));
            }
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
                net.minecraft.core.GlobalPos place = "place".equals(str("at", "")) ? state.place(str("place", "")) : null;
                ServerLevel placeLevel = place == null ? null : level.getServer().getLevel(place.dimension());
                if (placeLevel != null) {
                    StoryNpc.spawn(placeLevel, str("character", "veld"), StoryEvents.groundNear(placeLevel, place.pos(), distance),
                            (float) num("yaw", 0));
                } else if ("anchor".equals(str("at", "")) && state.anchor() != null) {
                    ServerLevel overworld = level.getServer().overworld();
                    BlockPos base = state.anchor().offset(num("dx", 0), 0, num("dz", 0));
                    StoryNpc.spawn(overworld, str("character", "veld"), StoryEvents.groundNear(overworld, base, distance),
                            (float) num("yaw", 0));
                } else {
                    BlockPos pos = StoryEvents.groundNear(level, player.blockPosition(), distance);
                    StoryNpc.spawn(level, str("character", "veld"), pos, player.getYRot() + 180.0F);
                }
            }
            case "make_boss" -> {
                String character = str("character", "");
                for (StoryNpc other : level.getEntities(com.arnir.storymod.registry.ModEntities.STORY_NPC.get(),
                        e -> e.getCharacter().equals(character) && e.isAlive())) {
                    other.becomeBoss();
                }
            }
            case "give_item_role" -> {
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    if (state.role(p.getUUID()).equals(str("role", ""))) {
                        give(p, new ItemStack(Inventories.item(str("item", "minecraft:air")), num("count", 1)));
                    }
                }
            }
            case "mark_place" -> state.setPlace(str("name", ""), net.minecraft.core.GlobalPos.of(level.dimension(),
                    npc != null ? npc.blockPosition() : player.blockPosition()));
            case "silence_mark" -> com.arnir.storymod.story.ActTwo.mark(player);
            case "silence_cure" -> com.arnir.storymod.story.ActTwo.cure(player);
            case "calm" -> com.arnir.storymod.story.ActTwo.calm(player, num("days", 2));
            case "outpost_done" -> com.arnir.storymod.story.Outposts.complete(player, str("outpost", ""));
            case "counter_add" -> state.addCounter(str("counter", ""), num("amount", 1));
            case "star_fall" -> com.arnir.storymod.story.ActTwo.dropStarNow(level.getServer(), player,
                    str("at", "player"), num("distance", 24), args.has("big") && args.get("big").getAsBoolean());
            case "break_lantern" -> com.arnir.storymod.story.ActTwo.breakRandomLantern(level.getServer());
            case "spawn_echoes" -> com.arnir.storymod.story.ActTwo.spawnEchoes(level, player.blockPosition(), num("count", 3), num("distance", 8));
            case "seam_up" -> com.arnir.storymod.story.ActTwo.raiseSeam(level.getServer());
            case "teleport_underside" -> com.arnir.storymod.story.Underside.enter(player);
            case "leave_underside" -> com.arnir.storymod.story.Underside.leave(player);
            case "set_time" -> level.getServer().overworld().setDayTime(
                    level.getServer().overworld().getDayTime() / 24000L * 24000L + num("time", 13000));
            case "effect_all" -> BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(str("effect", "minecraft:glowing")))
                    .ifPresent(effect -> {
                        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                            p.addEffect(new MobEffectInstance(effect, num("duration", 200), num("amplifier", 0)));
                        }
                    });
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

    /**
     * A compass pointing at the nearest structure from a structure tag, e.g.
     * {"type": "give_structure_compass", "structure": "storymod:ancient_cities", "name": "..."}
     */
    private void giveStructureCompass(ServerPlayer player, ServerLevel level) {
        net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag =
                net.minecraft.tags.TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(str("structure", "")));
        BlockPos found = level.findNearestMapStructure(tag, player.blockPosition(), num("radius", 64), false);
        if (found == null) {
            player.sendSystemMessage(Component.literal(str("fail_text", "Стрелка кружится и не может выбрать направление."))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        ItemStack compass = new ItemStack(net.minecraft.world.item.Items.COMPASS);
        compass.set(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER,
                new net.minecraft.world.item.component.LodestoneTracker(
                        java.util.Optional.of(net.minecraft.core.GlobalPos.of(level.dimension(), found)), false));
        compass.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(str("name", "Компас")).withStyle(ChatFormatting.AQUA));
        give(player, compass);
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
