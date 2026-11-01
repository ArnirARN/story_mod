package com.arnir.storymod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A keepsake that grants a few effects on use, then rests for a while. */
public class EffectItem extends Item {
    public record Effect(Holder<MobEffect> effect, int seconds, int amplifier) {
    }

    private final List<Effect> effects;
    private final int cooldownSeconds;
    private final ChatFormatting color;

    public EffectItem(Properties properties, int cooldownSeconds, ChatFormatting color, Effect... effects) {
        super(properties);
        this.effects = List.of(effects);
        this.cooldownSeconds = cooldownSeconds;
        this.color = color;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            for (Effect e : effects) {
                player.addEffect(new MobEffectInstance(e.effect(), e.seconds() * 20, e.amplifier()));
            }
            player.getCooldowns().addCooldown(this, cooldownSeconds * 20);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.getDescriptionId() + ".desc").withStyle(color));
    }
}
