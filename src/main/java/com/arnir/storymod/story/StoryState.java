package com.arnir.storymod.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Story progress shared by everybody on the server (the story is cooperative).
 * Stored with the overworld save.
 */
public class StoryState extends SavedData {
    private static final String NAME = "storymod_state";

    private final Set<String> flags = new TreeSet<>();
    private final Map<String, Integer> npcGenerations = new HashMap<>();
    /** Game time (overworld ticks) at which each flag was first set. */
    private final Map<String, Long> flagTimes = new HashMap<>();
    /** Personal flags: each player's own quest progress. */
    private final Map<UUID, Set<String>> playerFlags = new HashMap<>();
    /** Story roles of players ("wanderer", "keeper"). */
    private final Map<UUID, String> roles = new HashMap<>();
    private BlockPos anchor;
    private transient MinecraftServer server;

    public static StoryState get(MinecraftServer server) {
        StoryState state = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StoryState::new, StoryState::load, null), NAME);
        state.server = server;
        return state;
    }

    private long now() {
        return server != null ? server.overworld().getGameTime() : 0L;
    }

    private static StoryState load(CompoundTag tag, HolderLookup.Provider registries) {
        StoryState state = new StoryState();
        ListTag list = tag.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            state.flags.add(list.getString(i));
        }
        CompoundTag gens = tag.getCompound("NpcGenerations");
        for (String key : gens.getAllKeys()) {
            state.npcGenerations.put(key, gens.getInt(key));
        }
        if (tag.contains("Anchor")) {
            state.anchor = NbtUtils.readBlockPos(tag, "Anchor").orElse(null);
        }
        CompoundTag times = tag.getCompound("FlagTimes");
        for (String key : times.getAllKeys()) {
            state.flagTimes.put(key, times.getLong(key));
        }
        CompoundTag players = tag.getCompound("PlayerFlags");
        for (String key : players.getAllKeys()) {
            Set<String> set = new TreeSet<>();
            ListTag pl = players.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < pl.size(); i++) {
                set.add(pl.getString(i));
            }
            state.playerFlags.put(UUID.fromString(key), set);
        }
        CompoundTag roleTag = tag.getCompound("Roles");
        for (String key : roleTag.getAllKeys()) {
            state.roles.put(UUID.fromString(key), roleTag.getString(key));
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String flag : flags) {
            list.add(StringTag.valueOf(flag));
        }
        tag.put("Flags", list);
        CompoundTag gens = new CompoundTag();
        npcGenerations.forEach(gens::putInt);
        tag.put("NpcGenerations", gens);
        if (anchor != null) {
            tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
        }
        CompoundTag times = new CompoundTag();
        flagTimes.forEach(times::putLong);
        tag.put("FlagTimes", times);
        CompoundTag players = new CompoundTag();
        playerFlags.forEach((uuid, set) -> {
            ListTag pl = new ListTag();
            set.forEach(f -> pl.add(StringTag.valueOf(f)));
            players.put(uuid.toString(), pl);
        });
        tag.put("PlayerFlags", players);
        CompoundTag roleTag = new CompoundTag();
        roles.forEach((uuid, role) -> roleTag.putString(uuid.toString(), role));
        tag.put("Roles", roleTag);
        return tag;
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    public void set(String flag) {
        if (flags.add(flag)) {
            flagTimes.put(flag, now());
            setDirty();
        }
    }

    public void clear(String flag) {
        if (flags.remove(flag)) {
            flagTimes.remove(flag);
            setDirty();
        }
    }

    /** Whole in-game days (24000 ticks) since the flag was set, or -1 if it is not set. */
    public long daysSince(String flag) {
        Long t = flagTimes.get(flag);
        if (t == null || !flags.contains(flag)) {
            return -1;
        }
        return (now() - t) / 24000L;
    }

    /** Pretends a flag was set {@code ticks} earlier than it was (used by tests and admin tools). */
    public void backdate(String flag, long ticks) {
        Long t = flagTimes.get(flag);
        if (t != null) {
            flagTimes.put(flag, t - ticks);
            setDirty();
        }
    }

    public boolean hasPersonal(UUID player, String flag) {
        Set<String> set = playerFlags.get(player);
        return set != null && set.contains(flag);
    }

    public void setPersonal(UUID player, String flag) {
        if (playerFlags.computeIfAbsent(player, k -> new TreeSet<>()).add(flag)) {
            setDirty();
        }
    }

    public void clearPersonal(UUID player, String flag) {
        Set<String> set = playerFlags.get(player);
        if (set != null && set.remove(flag)) {
            setDirty();
        }
    }

    public Set<String> personalFlags(UUID player) {
        return java.util.Collections.unmodifiableSet(playerFlags.getOrDefault(player, Set.of()));
    }

    /** The player's story role, or "" if none. */
    public String role(UUID player) {
        return roles.getOrDefault(player, "");
    }

    public boolean roleTaken(String role) {
        return roles.containsValue(role);
    }

    public void setRole(UUID player, String role) {
        if (role == null || role.isEmpty() || role.equals("none")) {
            roles.remove(player);
        } else {
            roles.values().removeIf(role::equals);
            roles.put(player, role);
        }
        setDirty();
    }

    public int countWithPrefix(String prefix) {
        int n = 0;
        for (String flag : flags) {
            if (flag.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    public Set<String> flags() {
        return java.util.Collections.unmodifiableSet(flags);
    }

    public int generation(String character) {
        return npcGenerations.getOrDefault(character, 0);
    }

    /** Invalidates every existing NPC of this character, loaded or not. Returns the new generation. */
    public int bumpGeneration(String character) {
        int next = generation(character) + 1;
        npcGenerations.put(character, next);
        setDirty();
        return next;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public void setAnchor(BlockPos pos) {
        this.anchor = pos;
        setDirty();
    }

    public void reset() {
        flags.clear();
        flagTimes.clear();
        playerFlags.clear();
        for (String key : npcGenerations.keySet().toArray(String[]::new)) {
            npcGenerations.put(key, npcGenerations.get(key) + 1);
        }
        anchor = null;
        setDirty();
    }
}
