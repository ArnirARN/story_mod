package com.arnir.storymod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A plain story item with a line of description (and optionally the enchanted glint). */
public class LoreItem extends Item {
    private final boolean foil;

    public LoreItem(Properties properties, boolean foil) {
        super(properties);
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
