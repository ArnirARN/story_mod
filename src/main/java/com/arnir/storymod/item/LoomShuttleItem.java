package com.arnir.storymod.item;

import com.arnir.storymod.block.FallenStarBlock;
import com.arnir.storymod.registry.ModItems;
import com.arnir.storymod.story.PlayerRoles;
import com.arnir.storymod.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** The keeper's shuttle from the old loom: it draws the heat out of a fallen star and pulls a shard loose. */
public class LoomShuttleItem extends Item {
    public LoomShuttleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(context.getPlayer() instanceof ServerPlayer player) || !FallenStarBlock.isHot(level.getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        if (!StoryState.get(player.getServer()).role(player.getUUID()).equals(PlayerRoles.KEEPER)) {
            player.displayClientMessage(Component.literal("Челнок не поддаётся. Он помнит другие руки.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return InteractionResult.FAIL;
        }
        FallenStarBlock.cool(level, pos);
        ItemStack shard = new ItemStack(ModItems.STAR_SHARD.get());
        if (!player.getInventory().add(shard)) {
            player.drop(shard, false);
        }
        player.getCooldowns().addCooldown(this, 20 * 10);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
