package com.arnir.storymod.dialogue;

import com.arnir.storymod.StoryMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/** Loads every dialogue file from data/storymod/dialogues/ on server start and /reload. */
public class DialogueManager extends SimpleJsonResourceReloadListener {
    public static final DialogueManager INSTANCE = new DialogueManager();
    private static Map<String, Dialogue> dialogues = Map.of();

    private DialogueManager() {
        super(new Gson(), "dialogues");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Dialogue> loaded = new HashMap<>();
        files.forEach((id, json) -> {
            if (!id.getNamespace().equals(StoryMod.MODID)) {
                return;
            }
            try {
                loaded.put(id.getPath(), Dialogue.parse(json.getAsJsonObject()));
            } catch (Exception e) {
                StoryMod.LOGGER.error("Could not load dialogue {}", id, e);
            }
        });
        dialogues = loaded;
        StoryMod.LOGGER.info("Loaded {} story dialogues", loaded.size());
    }

    public static Dialogue get(String character) {
        return dialogues.get(character);
    }

    public static String speakerFor(String character) {
        Dialogue d = dialogues.get(character);
        return d != null ? d.speaker() : character;
    }
}
