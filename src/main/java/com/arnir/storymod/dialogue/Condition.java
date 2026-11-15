package com.arnir.storymod.dialogue;

import com.arnir.storymod.story.StoryState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A requirement for a dialogue entry, option, trigger or journal line. Every listed part must pass.
 * <pre>
 * {
 *   "flags": ["a"], "not_flags": ["b"],          // shared story flags
 *   "pflags": ["c"], "not_pflags": ["d"],        // the player's own flags
 *   "role": "wanderer", "not_role": "keeper",    // story role of the player
 *   "items": [{"item": "minecraft:diamond", "count": 2}],
 *   "carry_pages": ["letter"],                   // written story notes the player carries
 *   "flag_count": {"prefix": "page_", "min": 3},
 *   "days_since": {"flag": "x", "days": 3},      // whole in-game days since flag x was set
 *   "time": {"from": 13000, "to": 23000},        // overworld time of day window (may wrap past 24000)
 *   "moon_phase": 0,                             // 0 = full moon
 *   "dimension": "minecraft:overworld",
 *   "near_anchor": 6,                            // within N blocks of the old stone circle
 *   "advancement": "minecraft:end/root",
 *   "mods": ["create"],                          // all these mods are installed
 *   "role_near": {"role": "keeper", "radius": 12},  // a player with this role stands nearby (or is the player)
 *   "min_y": 200, "max_y": 0,                    // the player's height
 *   "sneaking": true,
 *   "structure": "minecraft:monument",           // standing inside a structure (or "#tag")
 *   "near_entities": {"type": "minecraft:enderman", "count": 3, "radius": 16},
 *   "role_taken": "keeper", "not_role_taken": "keeper",   // somebody on the server has (never had) this role
 *   "pflag_count": {"prefix": "lullaby_", "min": 3},     // the player's own flags with a prefix
 *   "counter": {"name": "stars_fallen", "min": 2},       // a story counter
 *   "lanterns": 4,                               // star lanterns standing around the old circle
 *   "chance": 0.25,                              // passes only sometimes
 *   "not": {...},                                // the nested condition fails
 *   "any": [ {...}, {...} ]                      // at least one nested condition passes
 * }
 * </pre>
 */
public final class Condition {
    public static final Condition ALWAYS = new Condition(List.of());

    private final List<BiPredicate<ServerPlayer, StoryState>> parts;

    private Condition(List<BiPredicate<ServerPlayer, StoryState>> parts) {
        this.parts = parts;
    }

    public static Condition parse(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return ALWAYS;
        }
        JsonObject obj = element.getAsJsonObject();
        List<BiPredicate<ServerPlayer, StoryState>> parts = new ArrayList<>();

        for (String f : strings(obj, "flags")) {
            parts.add((p, s) -> s.has(f));
        }
        for (String f : strings(obj, "not_flags")) {
            parts.add((p, s) -> !s.has(f));
        }
        for (String f : strings(obj, "pflags")) {
            parts.add((p, s) -> s.hasPersonal(p.getUUID(), f));
        }
        for (String f : strings(obj, "not_pflags")) {
            parts.add((p, s) -> !s.hasPersonal(p.getUUID(), f));
        }
        if (obj.has("role")) {
            String role = obj.get("role").getAsString();
            parts.add((p, s) -> s.role(p.getUUID()).equals(role));
        }
        if (obj.has("not_role")) {
            String role = obj.get("not_role").getAsString();
            parts.add((p, s) -> !s.role(p.getUUID()).equals(role));
        }
        if (obj.has("items")) {
            for (JsonElement e : obj.getAsJsonArray("items")) {
                JsonObject o = e.getAsJsonObject();
                String item = o.get("item").getAsString();
                Inventories.REFERENCED.add(item);
                int count = o.has("count") ? o.get("count").getAsInt() : 1;
                parts.add((p, s) -> Inventories.count(p, item) >= count);
            }
        }
        for (String page : strings(obj, "carry_pages")) {
            parts.add((p, s) -> Inventories.carriesPage(p, page));
        }
        if (obj.has("flag_count")) {
            JsonObject fc = obj.getAsJsonObject("flag_count");
            String prefix = fc.get("prefix").getAsString();
            int min = fc.get("min").getAsInt();
            parts.add((p, s) -> s.countWithPrefix(prefix) >= min);
        }
        if (obj.has("days_since")) {
            JsonObject ds = obj.getAsJsonObject("days_since");
            String flag = ds.get("flag").getAsString();
            int days = ds.get("days").getAsInt();
            parts.add((p, s) -> s.daysSince(flag) >= days);
        }
        if (obj.has("time")) {
            JsonObject t = obj.getAsJsonObject("time");
            long from = t.get("from").getAsLong();
            long to = t.get("to").getAsLong();
            parts.add((p, s) -> {
                long day = p.getServer().overworld().getDayTime() % 24000L;
                return from <= to ? (day >= from && day <= to) : (day >= from || day <= to);
            });
        }
        if (obj.has("moon_phase")) {
            int phase = obj.get("moon_phase").getAsInt();
            parts.add((p, s) -> p.getServer().overworld().getMoonPhase() == phase);
        }
        if (obj.has("dimension")) {
            String dim = obj.get("dimension").getAsString();
            if (dim.equals(com.arnir.storymod.story.Underside.KEY.location().toString())) {
                parts.add((p, s) -> com.arnir.storymod.story.Underside.isHere(p));
            } else {
                parts.add((p, s) -> p.level().dimension().location().toString().equals(dim));
            }
        }
        if (obj.has("near_anchor")) {
            double radius = obj.get("near_anchor").getAsDouble();
            parts.add((p, s) -> {
                BlockPos anchor = s.anchor();
                return anchor != null && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                        && p.blockPosition().distSqr(anchor) <= radius * radius;
            });
        }
        for (String mod : strings(obj, "mods")) {
            parts.add((p, s) -> net.neoforged.fml.ModList.get().isLoaded(mod));
        }
        if (obj.has("advancement")) {
            ResourceLocation id = ResourceLocation.parse(obj.get("advancement").getAsString());
            parts.add((p, s) -> {
                AdvancementHolder adv = p.getServer().getAdvancements().get(id);
                return adv != null && p.getAdvancements().getOrStartProgress(adv).isDone();
            });
        }
        if (obj.has("role_near")) {
            JsonObject rn = obj.getAsJsonObject("role_near");
            String role = rn.get("role").getAsString();
            double radius = rn.has("radius") ? rn.get("radius").getAsDouble() : 12.0;
            parts.add((p, s) -> {
                for (ServerPlayer other : p.serverLevel().players()) {
                    if (s.role(other.getUUID()).equals(role) && other.distanceToSqr(p) <= radius * radius) {
                        return true;
                    }
                }
                return false;
            });
        }
        if (obj.has("min_y")) {
            double y = obj.get("min_y").getAsDouble();
            parts.add((p, s) -> p.getY() >= y);
        }
        if (obj.has("max_y")) {
            double y = obj.get("max_y").getAsDouble();
            parts.add((p, s) -> p.getY() <= y);
        }
        if (obj.has("sneaking")) {
            boolean want = obj.get("sneaking").getAsBoolean();
            parts.add((p, s) -> p.isShiftKeyDown() == want);
        }
        if (obj.has("structure")) {
            String id = obj.get("structure").getAsString();
            parts.add((p, s) -> com.arnir.storymod.story.ActTwo.insideStructure(p, id));
        }
        if (obj.has("near_entities")) {
            JsonObject ne = obj.getAsJsonObject("near_entities");
            ResourceLocation type = ResourceLocation.parse(ne.get("type").getAsString());
            int count = ne.has("count") ? ne.get("count").getAsInt() : 1;
            double radius = ne.has("radius") ? ne.get("radius").getAsDouble() : 16.0;
            parts.add((p, s) -> {
                var entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(type);
                return p.serverLevel().getEntities((net.minecraft.world.entity.Entity) null,
                        p.getBoundingBox().inflate(radius), e -> e.getType() == entityType && e.isAlive()).size() >= count;
            });
        }
        if (obj.has("role_taken")) {
            String role = obj.get("role_taken").getAsString();
            parts.add((p, s) -> s.roleTaken(role));
        }
        if (obj.has("not_role_taken")) {
            String role = obj.get("not_role_taken").getAsString();
            parts.add((p, s) -> !s.roleTaken(role));
        }
        if (obj.has("pflag_count")) {
            JsonObject fc = obj.getAsJsonObject("pflag_count");
            String prefix = fc.get("prefix").getAsString();
            int min = fc.get("min").getAsInt();
            parts.add((p, s) -> s.personalFlags(p.getUUID()).stream().filter(f -> f.startsWith(prefix)).count() >= min);
        }
        if (obj.has("counter")) {
            JsonObject c = obj.getAsJsonObject("counter");
            String name = c.get("name").getAsString();
            int min = c.has("min") ? c.get("min").getAsInt() : 1;
            parts.add((p, s) -> s.counter(name) >= min);
        }
        if (obj.has("lanterns")) {
            int min = obj.get("lanterns").getAsInt();
            parts.add((p, s) -> com.arnir.storymod.story.ActTwo.lanternsAroundCircle(s) >= min);
        }
        if (obj.has("chance")) {
            double chance = obj.get("chance").getAsDouble();
            parts.add((p, s) -> p.getRandom().nextDouble() < chance);
        }
        if (obj.has("not")) {
            Condition inner = parse(obj.get("not"));
            parts.add((p, s) -> !inner.test(p, s));
        }
        if (obj.has("any")) {
            List<Condition> any = new ArrayList<>();
            for (JsonElement e : obj.getAsJsonArray("any")) {
                any.add(parse(e));
            }
            parts.add((p, s) -> {
                for (Condition c : any) {
                    if (c.test(p, s)) {
                        return true;
                    }
                }
                return false;
            });
        }
        return new Condition(List.copyOf(parts));
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
        for (BiPredicate<ServerPlayer, StoryState> part : parts) {
            if (!part.test(player, state)) {
                return false;
            }
        }
        return true;
    }

}
