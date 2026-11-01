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
import java.util.TreeMap;

/**
 * Lines of the per-player quest journal. Files: data/storymod/journal/&lt;name&gt;.json
 * <pre>
 * { "entries": [ { "if": {condition}, "title": "...", "text": "..." } ] }
 * </pre>
 * The journal shows every entry whose condition passes for the reading player, in file order.
 */
public class JournalManager extends SimpleJsonResourceReloadListener {
    public static final JournalManager INSTANCE = new JournalManager();

    public record Entry(Condition condition, String title, String text) {
    }

    private static List<Entry> entries = List.of();

    private JournalManager() {
        super(new Gson(), "journal");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Entry> loaded = new ArrayList<>();
        new TreeMap<>(files).forEach((id, json) -> {
            if (!id.getNamespace().equals(StoryMod.MODID)) {
                return;
            }
            try {
                for (JsonElement e : json.getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject o = e.getAsJsonObject();
                    loaded.add(new Entry(Condition.parse(o.get("if")),
                            o.has("title") ? o.get("title").getAsString() : "",
                            o.get("text").getAsString()));
                }
            } catch (Exception e) {
                StoryMod.LOGGER.error("Could not load story journal {}", id, e);
            }
        });
        entries = List.copyOf(loaded);
        StoryMod.LOGGER.info("Loaded {} story journal entries", loaded.size());
    }

    /** One book page per active entry. */
    public static List<String> pagesFor(ServerPlayer player) {
        StoryState state = StoryState.get(player.getServer());
        List<String> pages = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.condition().test(player, state)) {
                String text = entry.text()
                        .replace("{player}", player.getGameProfile().getName())
                        .replace("{cores}", String.valueOf(com.arnir.storymod.story.StoryEvents.coresFound(state)))
                        .replace("{pages}", String.valueOf(state.countWithPrefix("page_")));
                pages.add(entry.title().isEmpty() ? text : "§l" + entry.title() + "§r\n\n" + text);
            }
        }
        if (pages.isEmpty()) {
            pages.add("Страницы пусты. Пока.");
        }
        return pages;
    }
}
