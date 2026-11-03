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
 */
public class TriggerManager extends SimpleJsonResourceReloadListener {
    public static final TriggerManager INSTANCE = new TriggerManager();

    private record Trigger(String id, boolean perPlayer, Condition condition, List<Action> actions) {
    }

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
                        Action.parseList(obj.get("actions"))));
            } catch (Exception e) {
                StoryMod.LOGGER.error("Could not load story trigger {}", id, e);
            }
        });
        triggers = List.copyOf(loaded);
        StoryMod.LOGGER.info("Loaded {} story triggers", loaded.size());
    }

    public static void check(ServerPlayer player, StoryState state) {
        for (Trigger t : triggers) {
            String flag = "trigger_" + t.id();
            boolean done = t.perPlayer() ? state.hasPersonal(player.getUUID(), flag) : state.has(flag);
            if (done || !t.condition().test(player, state)) {
                continue;
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
}
