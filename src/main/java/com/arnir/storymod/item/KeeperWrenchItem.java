package com.arnir.storymod.item;

import com.arnir.storymod.story.PlayerRoles;
import com.arnir.storymod.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The keeper's starting tool. Use with a damaged item in the other hand: spends one copper ingot
 * to restore a quarter of its durability. Held: work goes a little faster.
 */
public class KeeperWrenchItem extends Item {
    public static final int COOLDOWN = 20 * 20;

    public KeeperWrenchItem(Properties properties) {
        super(properties);
    }

    public static boolean isOwner(ServerPlayer player) {
        return StoryState.get(player.getServer()).role(player.getUUID()).equals(PlayerRoles.KEEPER);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wrench = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(wrench, level.isClientSide);
        }
        if (!isOwner(sp)) {
            sp.displayClientMessage(Component.literal("Ключ не ложится в руку. Он сделан не для тебя.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return InteractionResultHolder.fail(wrench);
        }
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack target = sp.getItemInHand(other);
        if (!repair(sp, target)) {
            sp.displayClientMessage(Component.literal(
                            "Возьми повреждённый предмет в другую руку. Нужен медный слиток.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(wrench);
        }
        level.playSound(null, sp.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(wrench, false);
    }

    /** Restores a quarter of the item's durability for one copper ingot. */
    public static boolean repair(ServerPlayer player, ItemStack target) {
        if (target.isEmpty() || !target.isDamageableItem() || !target.isDamaged()) {
            return false;
        }
        int copperSlot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.COPPER_INGOT)) {
                copperSlot = i;
                break;
            }
        }
        if (copperSlot < 0) {
            return false;
        }
        player.getInventory().getItem(copperSlot).shrink(1);
        int restore = Math.max(1, target.getMaxDamage() / 4);
        target.setDamageValue(Math.max(0, target.getDamageValue() - restore));
        return true;
    }

    /** Called every two seconds while carried. */
    public static void pulse(ServerPlayer player) {
        if (isOwner(player) && player.getMainHandItem().getItem() instanceof KeeperWrenchItem) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ArtifactItem.PULSE_TICKS, 0, true, false, true));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.storymod.keeper_wrench.desc").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.storymod.keeper_wrench.effect").withStyle(ChatFormatting.BLUE));
    }
}
