package com.arnir.storymod.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One character's dialogue tree, loaded from data/&lt;namespace&gt;/dialogues/&lt;character&gt;.json.
 * "entries" are checked top to bottom; the first whose condition passes picks the starting node.
 */
public record Dialogue(String speaker, List<Entry> entries, Map<String, Node> nodes) {

    public record Entry(Condition condition, String node) {
    }

    public record Node(String speaker, String text, List<Action> actions, List<Option> options) {
    }

    /** {@code next} is null when choosing the option ends the conversation. */
    public record Option(String text, Condition condition, List<Action> actions, String next) {
    }

    public static Dialogue parse(JsonObject json) {
        String speaker = json.has("speaker") ? json.get("speaker").getAsString() : "???";
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("entries")) {
            JsonObject o = e.getAsJsonObject();
            entries.add(new Entry(Condition.parse(o.get("if")), o.get("node").getAsString()));
        }
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("nodes").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            List<Option> options = new ArrayList<>();
            if (o.has("options")) {
                for (JsonElement oe : o.getAsJsonArray("options")) {
                    JsonObject oo = oe.getAsJsonObject();
                    String next = oo.has("next") && !oo.get("next").isJsonNull() ? oo.get("next").getAsString() : null;
                    options.add(new Option(oo.get("text").getAsString(), Condition.parse(oo.get("if")),
                            Action.parseList(oo.get("actions")), next));
                }
            }
            nodes.put(e.getKey(), new Node(
                    o.has("speaker") ? o.get("speaker").getAsString() : null,
                    joinText(o.get("text")),
                    Action.parseList(o.get("actions")),
                    options));
        }
        return new Dialogue(speaker, entries, nodes);
    }

    /** "text" may be a string or an array of paragraphs. */
    private static String joinText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement e : (JsonArray) element) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(e.getAsString());
            }
            return sb.toString();
        }
        return element.getAsString();
    }
}
