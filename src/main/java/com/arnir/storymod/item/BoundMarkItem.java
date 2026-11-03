package com.arnir.storymod.item;

import com.arnir.storymod.story.PlayerRoles;
import com.arnir.storymod.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A personal mark bound to its owner: it cannot be thrown away and stays after death.
 * It gives and it takes.
 * <ul>
 *   <li>End mark (wanderer): stronger blows and lighter steps; water and rain sap strength and speed.</li>
 *   <li>Copper oath (keeper): armour and steady hands; heavier on her feet.</li>
 * </ul>
 * The attribute part lives in {@link com.arnir.storymod.story.RoleAbilities}; effects are kept up here.
 */
public class BoundMarkItem extends Item {
    private final String role;

    public BoundMarkItem(Properties properties, String role) {
        super(properties);
        this.role = role;
    }

    public String role() {
        return role;
    }

    public boolean isOwner(ServerPlayer player) {
        return StoryState.get(player.getServer()).role(player.getUUID()).equals(role);
    }

    /** Called every two seconds while carried or worn. */
    public void pulse(ServerPlayer player) {
        if (!isOwner(player)) {
            return;
        }
        if (role.equals(PlayerRoles.WANDERER)) {
            if (player.isInWaterOrRain()) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ArtifactItem.PULSE_TICKS, 0, true, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ArtifactItem.PULSE_TICKS, 0, true, false, true));
            }
        } else {
            MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
            if (haste == null || haste.getAmplifier() == 0 && haste.getDuration() <= ArtifactItem.PULSE_TICKS) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ArtifactItem.PULSE_TICKS, 0, true, false, true));
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".plus").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable(getDescriptionId() + ".minus").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.storymod.bound").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
