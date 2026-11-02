package com.arnir.storymod.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * Reads items worn in Curios slots. Only touched when Curios is installed (see {@link com.arnir.storymod.story.PlayerItems}).
 */
public final class CuriosCompat {
    private CuriosCompat() {
    }

    public static void collect(LivingEntity entity, List<ItemStack> out) {
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            var stacks = handler.getEquippedCurios();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        });
    }

    /** Puts a stack into the first slot of the given Curios slot type. Returns false if there is no such slot. */
    public static boolean equip(LivingEntity entity, String slot, ItemStack stack) {
        var handler = CuriosApi.getCuriosInventory(entity);
        if (handler.isEmpty() || handler.get().getStacksHandler(slot).isEmpty()) {
            return false;
        }
        handler.get().setEquippedCurio(slot, 0, stack);
        return true;
    }
}
