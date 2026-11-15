package com.arnir.storymod.story;

import com.arnir.storymod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Stories that belong to places, not to chapters: each starts when somebody finds the right corner of the world,
 * in any order and at any time. Their content is data (triggers "op_*", dialogues, journal/outposts.json);
 * this class only counts what was finished and hands out the sign for it.
 */
public final class Outposts {
    public static final List<String> IDS = List.of("desert", "swamp", "peaks", "cherry", "mushroom", "ship", "mansion");
    public static final String DONE_PREFIX = "outpost_done_";

    private Outposts() {
    }

    public static int done(StoryState s) {
        int n = 0;
        for (String id : IDS) {
            if (s.has(DONE_PREFIX + id)) {
                n++;
            }
        }
        return n;
    }

    public static boolean isDone(StoryState s, String id) {
        return s.has(DONE_PREFIX + id);
    }

    public static void complete(ServerPlayer player, String id) {
        StoryState s = StoryState.get(player.getServer());
        if (!IDS.contains(id) || s.has(DONE_PREFIX + id)) {
            return;
        }
        s.set(DONE_PREFIX + id);
        ItemStack sign = new ItemStack(ModItems.OUTPOST_SIGN.get());
        if (!player.getInventory().add(sign)) {
            player.drop(sign, false);
        }
        int n = done(s);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.literal("Знак заставы: " + n + " из " + IDS.size() + ".")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        if (n == IDS.size()) {
            s.set("outposts_all");
        }
    }
}
