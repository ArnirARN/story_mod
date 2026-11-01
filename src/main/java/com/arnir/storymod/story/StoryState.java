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

/**
 * Story progress shared by everybody on the server (the story is cooperative).
 * Stored with the overworld save.
 */
public class StoryState extends SavedData {
    private static final String NAME = "storymod_state";

    private final Set<String> flags = new TreeSet<>();
    private final Map<String, Integer> npcGenerations = new HashMap<>();
    private BlockPos anchor;

    public static StoryState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StoryState::new, StoryState::load, null), NAME);
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
        return tag;
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    public void set(String flag) {
        if (flags.add(flag)) {
            setDirty();
        }
    }

    public void clear(String flag) {
        if (flags.remove(flag)) {
            setDirty();
        }
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
        for (String key : npcGenerations.keySet().toArray(String[]::new)) {
            npcGenerations.put(key, npcGenerations.get(key) + 1);
        }
        anchor = null;
        setDirty();
    }
}
