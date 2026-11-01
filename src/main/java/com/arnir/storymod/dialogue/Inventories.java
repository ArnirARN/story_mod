package com.arnir.storymod.dialogue;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Inventory helpers used by conditions, actions and story triggers. */
public final class Inventories {
    /** Custom data key that marks a written book as a story note. */
    public static final String PAGE_KEY = "storymod_page";

    private Inventories() {
    }

    public static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    public static int count(ServerPlayer player, String itemId) {
        Item item = item(itemId);
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Removes up to {@code count} items; returns how many were removed. */
    public static int take(ServerPlayer player, String itemId, int count) {
        Item item = item(itemId);
        Inventory inv = player.getInventory();
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int n = Math.min(left, stack.getCount());
                stack.shrink(n);
                left -= n;
            }
        }
        inv.setChanged();
        return count - left;
    }

    /** The story note id of a stack, or null if it is not a story note. */
    public static String pageId(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(PAGE_KEY) ? tag.getString(PAGE_KEY) : null;
    }

    public static boolean carriesPage(ServerPlayer player, String page) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (page.equals(pageId(inv.getItem(i)))) {
                return true;
            }
        }
        return false;
    }
}
