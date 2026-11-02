package com.arnir.storymod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.Predicate;

/**
 * A trinket that works while it is anywhere in the player's inventory: when its condition holds,
 * it keeps a short effect topped up. Several copies do not stack.
 */
public class ArtifactItem extends Item {
    /** Effect length; refreshed every two seconds while the condition holds. */
    public static final int PULSE_TICKS = 20 * 6;

    private final Holder<MobEffect> effect;
    private final int amplifier;
    private final Predicate<ServerPlayer> condition;

    public ArtifactItem(Properties properties, Holder<MobEffect> effect, int amplifier, Predicate<ServerPlayer> condition) {
        super(properties);
        this.effect = effect;
        this.amplifier = amplifier;
        this.condition = condition;
    }

    public void pulse(ServerPlayer player) {
        if (!condition.test(player)) {
            return;
        }
        MobEffectInstance current = player.getEffect(effect);
        // Never shorten or weaken a stronger effect from a potion or beacon.
        if (current != null && (current.getAmplifier() > amplifier || current.getDuration() > PULSE_TICKS)) {
            return;
        }
        // Night vision flickers below 10 seconds, so it gets a longer pulse.
        int duration = effect.is(net.minecraft.world.effect.MobEffects.NIGHT_VISION) ? 20 * 15 : PULSE_TICKS;
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(this.getDescriptionId() + ".effect").withStyle(ChatFormatting.BLUE));
    }
}
