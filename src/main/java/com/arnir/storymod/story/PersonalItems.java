package com.arnir.storymod.story;

import com.arnir.storymod.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Personal things are never lost: they stay with the player through death, and bound ones cannot be thrown away.
 */
public final class PersonalItems {
    private static final Map<UUID, List<ItemStack>> KEPT = new HashMap<>();

    private PersonalItems() {
    }

    public static Set<Item> personal() {
        return Set.of(ModItems.WHITE_PEARL.get(), ModItems.WHITE_GREATSWORD.get(), ModItems.KEEPER_HAMMER.get(),
                ModItems.KEEPER_WRENCH.get(), ModItems.WHITE_EYE.get(), ModItems.JOURNAL.get(),
                ModItems.END_MARK.get(), ModItems.COPPER_OATH.get());
    }

    /** Bound items cannot be thrown away at all. */
    public static boolean isBound(ItemStack stack) {
        return stack.is(ModItems.WHITE_PEARL.get()) || stack.is(ModItems.END_MARK.get()) || stack.is(ModItems.COPPER_OATH.get());
    }

    public static boolean isPersonal(ItemStack stack) {
        return personal().contains(stack.getItem());
    }

    /** On death: take personal things out of the drops and keep them for the respawn. */
    public static void keepFromDrops(ServerPlayer player, Collection<ItemEntity> drops) {
        List<ItemStack> kept = new ArrayList<>();
        drops.removeIf(entity -> {
            if (isPersonal(entity.getItem())) {
                kept.add(entity.getItem().copy());
                entity.discard();
                return true;
            }
            return false;
        });
        if (!kept.isEmpty()) {
            KEPT.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).addAll(kept);
        }
    }

    /** After respawn: hand them back. */
    public static void restore(ServerPlayer player) {
        List<ItemStack> kept = KEPT.remove(player.getUUID());
        if (kept == null) {
            return;
        }
        for (ItemStack stack : kept) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}
