package com.arnir.storymod.story;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/** Everything a player carries: the inventory plus Curios slots when Curios is installed. */
public final class PlayerItems {
    private static Boolean curios;

    private PlayerItems() {
    }

    public static boolean curiosLoaded() {
        if (curios == null) {
            curios = ModList.get() != null && ModList.get().isLoaded("curios");
        }
        return curios;
    }

    public static List<ItemStack> carried(Player player) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        if (curiosLoaded()) {
            com.arnir.storymod.compat.CuriosCompat.collect(player, out);
        }
        return out;
    }

    public static boolean has(Player player, Item item) {
        for (ItemStack stack : carried(player)) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }
}
