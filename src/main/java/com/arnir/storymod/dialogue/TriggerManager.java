package com.arnir.storymod.dialogue;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.story.StoryState;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Story events that fire on their own when a player meets a condition (be somewhere, at some time, carrying something).
 * Files: data/storymod/triggers/&lt;id&gt;.json
 * <pre>
 * { "once": "player" | "world", "if": {condition}, "actions": [...] }
 * </pre>
 * "world" triggers fire once for the whole server; "player" triggers once per player.
 * Optional "pace": "discovery" | "ambient" keeps unprompted story moments apart, so the world speaks often enough
 * to be felt but never in a stream: at most one paced trigger per check, a gap after each discovery,
 * a long gap between ambient moments, none during a fight or a conversation, and no ambient moment
 * in the first minutes after joining.
 */
public class TriggerManager extends SimpleJsonResourceReloadListener {
    public static final TriggerManager INSTANCE = new TriggerManager();

    private record Trigger(String id, boolean perPlayer, Condition condition, List<Action> actions, String pace) {
    }

    public static final long DISCOVERY_GAP = 20L * 60 * 2;
    public static final long AMBIENT_GAP = 20L * 60 * 15;
    public static final int AMBIENT_WARMUP = 20 * 60 * 4;
    private static final String PACE_KEY = "storymod_pace_";

    private static List<Trigger> triggers = List.of();

    private TriggerManager() {
        super(new Gson(), "triggers");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Trigger> loaded = new ArrayList<>();
        files.forEach((id, json) -> {
            if (!id.getNamespace().equals(StoryMod.MODID)) {
                return;
            }
            try {
                JsonObject obj = json.getAsJsonObject();
                boolean perPlayer = !obj.has("once") || obj.get("once").getAsString().equals("player");
                loaded.add(new Trigger(id.getPath(), perPlayer, Condition.parse(obj.get("if")),
                        Action.parseList(obj.get("actions")), obj.has("pace") ? obj.get("pace").getAsString() : null));
            } catch (Exception e) {
                StoryMod.LOGGER.error("Could not load story trigger {}", id, e);
            }
        });
        triggers = List.copyOf(loaded);
        StoryMod.LOGGER.info("Loaded {} story triggers", loaded.size());
    }

    public static void check(ServerPlayer player, StoryState state) {
        boolean pacedFired = false;
        for (Trigger t : triggers) {
            String flag = "trigger_" + t.id();
            boolean done = t.perPlayer() ? state.hasPersonal(player.getUUID(), flag) : state.has(flag);
            if (done || t.pace() != null && (pacedFired || !paceReady(player, t.pace())) || !t.condition().test(player, state)) {
                continue;
            }
            if (t.pace() != null) {
                pacedFired = true;
                markPace(player, t.pace());
            }
            if (t.perPlayer()) {
                state.setPersonal(player.getUUID(), flag);
            } else {
                state.set(flag);
            }
            for (Action action : t.actions()) {
                action.run(player, null);
            }
        }
    }

    private static long now(ServerPlayer player) {
        return player.getServer().overworld().getGameTime();
    }

    private static boolean paceReady(ServerPlayer player, String pace) {
        if (player.hurtTime > 0 || player.getLastHurtByMob() != null || DialogueSessions.view(player) != null) {
            return false;
        }
        var data = player.getPersistentData();
        long now = now(player);
        // Any paced moment holds back the next discovery a little; ambient ones also wait for each other.
        if (data.contains(PACE_KEY + "any") && now - data.getLong(PACE_KEY + "any") < DISCOVERY_GAP) {
            return false;
        }
        if (pace.equals("ambient")) {
            if (player.tickCount < AMBIENT_WARMUP) {
                return false;
            }
            return !data.contains(PACE_KEY + "ambient") || now - data.getLong(PACE_KEY + "ambient") >= AMBIENT_GAP;
        }
        return true;
    }

    private static void markPace(ServerPlayer player, String pace) {
        long now = now(player);
        player.getPersistentData().putLong(PACE_KEY + "any", now);
        player.getPersistentData().putLong(PACE_KEY + pace, now);
    }

    /** Lets the next paced moment come right away (tests and admin commands). */
    public static void resetPace(ServerPlayer player) {
        for (String k : List.of("any", "ambient", "discovery")) {
            player.getPersistentData().remove(PACE_KEY + k);
        }
    }
}
