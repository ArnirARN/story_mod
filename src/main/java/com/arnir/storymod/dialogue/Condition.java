package com.arnir.storymod.dialogue;

import com.arnir.storymod.story.StoryState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * A requirement for a dialogue entry or option. Every listed part must pass.
 * <pre>
 * {
 *   "flags": ["a", "b"],          // all story flags set
 *   "not_flags": ["c"],           // none of these set
 *   "items": [{"item": "minecraft:diamond", "count": 2}],  // the talking player carries them
 *   "carry_pages": ["letter"],    // the talking player carries these written notes
 *   "flag_count": {"prefix": "page_", "min": 3},
 *   "any": [ {...}, {...} ]       // at least one nested condition passes
 * }
 * </pre>
 */
public record Condition(List<String> flags, List<String> notFlags, List<ItemReq> items, List<String> carryPages,
                        String countPrefix, int countMin, List<Condition> any) {

    public static final Condition ALWAYS = new Condition(List.of(), List.of(), List.of(), List.of(), null, 0, List.of());

    public record ItemReq(String item, int count) {
    }

    public static Condition parse(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return ALWAYS;
        }
        JsonObject obj = element.getAsJsonObject();
        List<ItemReq> items = new ArrayList<>();
        if (obj.has("items")) {
            for (JsonElement e : obj.getAsJsonArray("items")) {
                JsonObject o = e.getAsJsonObject();
                items.add(new ItemReq(o.get("item").getAsString(), o.has("count") ? o.get("count").getAsInt() : 1));
            }
        }
        String prefix = null;
        int min = 0;
        if (obj.has("flag_count")) {
            JsonObject fc = obj.getAsJsonObject("flag_count");
            prefix = fc.get("prefix").getAsString();
            min = fc.get("min").getAsInt();
        }
        List<Condition> any = new ArrayList<>();
        if (obj.has("any")) {
            for (JsonElement e : obj.getAsJsonArray("any")) {
                any.add(parse(e));
            }
        }
        return new Condition(strings(obj, "flags"), strings(obj, "not_flags"), items, strings(obj, "carry_pages"),
                prefix, min, any);
    }

    static List<String> strings(JsonObject obj, String key) {
        List<String> out = new ArrayList<>();
        if (obj.has(key)) {
            JsonElement el = obj.get(key);
            if (el.isJsonArray()) {
                for (JsonElement e : (JsonArray) el) {
                    out.add(e.getAsString());
                }
            } else {
                out.add(el.getAsString());
            }
        }
        return out;
    }

    public boolean test(ServerPlayer player, StoryState state) {
        for (String f : flags) {
            if (!state.has(f)) {
                return false;
            }
        }
        for (String f : notFlags) {
            if (state.has(f)) {
                return false;
            }
        }
        for (ItemReq req : items) {
            if (Inventories.count(player, req.item()) < req.count()) {
                return false;
            }
        }
        for (String page : carryPages) {
            if (!Inventories.carriesPage(player, page)) {
                return false;
            }
        }
        if (countPrefix != null && state.countWithPrefix(countPrefix) < countMin) {
            return false;
        }
        if (!any.isEmpty()) {
            for (Condition c : any) {
                if (c.test(player, state)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
