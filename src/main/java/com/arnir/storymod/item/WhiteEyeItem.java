package com.arnir.storymod.item;

import com.arnir.storymod.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** A personal artifact. In the right hands it lets its owner step through space to where they look. */
public class WhiteEyeItem extends Item {
    public static final String OWNER_ROLE = "wanderer";
    private static final double RANGE = 24.0;
    private static final int COOLDOWN = 20 * 6;

    public WhiteEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!StoryState.get(sp.getServer()).role(sp.getUUID()).equals(OWNER_ROLE)) {
            sp.displayClientMessage(Component.literal("Око смотрит сквозь тебя. Оно ждёт не тебя.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return InteractionResultHolder.fail(stack);
        }
        StoryState state = StoryState.get(sp.getServer());
        double range = state.hasPersonal(sp.getUUID(), "eye_awakened") ? RANGE * 2 : RANGE;
        Vec3 eye = sp.getEyePosition();
        Vec3 end = eye.add(sp.getLookAngle().scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        BlockPos target = hit.getType() == HitResult.Type.MISS
                ? BlockPos.containing(end)
                : hit.getBlockPos().relative(hit.getDirection());
        BlockPos feet = findStandable(serverLevel, target);
        if (feet == null) {
            sp.displayClientMessage(Component.literal("Там нет места.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 40, 0.3, 0.8, 0.3, 0.05);
        level.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
        sp.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        sp.resetFallDistance();
        level.playSound(null, feet, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 40, 0.3, 0.8, 0.3, 0.05);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static BlockPos findStandable(ServerLevel level, BlockPos around) {
        for (int dy = 0; dy >= -4; dy--) {
            BlockPos feet = around.offset(0, dy, 0);
            if (level.getBlockState(feet.below()).isSolid()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return feet;
            }
        }
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos feet = around.offset(0, dy, 0);
            if (level.getBlockState(feet.below()).isSolid()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return feet;
            }
        }
        return null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.storymod.white_eye.desc").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
