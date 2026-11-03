package com.arnir.storymod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.List;

/**
 * A keepsake found in old ruins. Use: step into the shadows for a few seconds.
 * Carried in darkness: moves a little faster.
 */
public class TwilightAmuletItem extends Item {
    public static final int COOLDOWN = 20 * 45;

    public TwilightAmuletItem(Properties properties) {
        super(properties);
    }

    /** Anyone who finds it can use it. */
    public static boolean isOwner(ServerPlayer player) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!isOwner(sp)) {
            sp.displayClientMessage(Component.literal("Амулет холодный и молчит.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return InteractionResultHolder.fail(stack);
        }
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 8, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 8, 1, false, false, true));
        serverLevel.sendParticles(ParticleTypes.PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 50, 0.4, 0.8, 0.4, 0.4);
        level.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.6F);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** Called every two seconds while carried. */
    public static void pulse(ServerPlayer player) {
        if (!isOwner(player)) {
            return;
        }
        int light = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
        int sky = player.level().getBrightness(LightLayer.SKY, player.blockPosition()) - player.level().getSkyDarken();
        if (Math.max(light, sky) <= 5) {
            MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SPEED);
            if (current == null || current.getDuration() <= ArtifactItem.PULSE_TICKS) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ArtifactItem.PULSE_TICKS, 0, true, false, true));
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.storymod.twilight_amulet.desc").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.storymod.twilight_amulet.effect").withStyle(ChatFormatting.BLUE));
    }
}
