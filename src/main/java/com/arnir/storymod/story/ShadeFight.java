package com.arnir.storymod.story;

import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The figure from the other side.
 * <ul>
 *   <li>Before the fight it only watches from afar and is gone the moment someone comes close.</li>
 *   <li>Phase 1: steps behind its target every few seconds.</li>
 *   <li>Phase 2 (below 2/3): calls the echoes and dims the light.</li>
 *   <li>Phase 3 (below 1/3): blows pass through it, except from the one the End hears,
 *       or from anybody standing next to starlight.</li>
 * </ul>
 */
public final class ShadeFight {
    public static final double LANTERN_REACH = 7.0;

    private ShadeFight() {
    }

    /** Not a fight yet: it stands still, watches, and disappears when somebody comes near or after a while. */
    public static void lurk(StoryNpc shade, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        boolean close = level.getNearestPlayer(shade, 9.0) != null;
        if (close || shade.tickCount > 20 * 45) {
            level.sendParticles(ParticleTypes.SQUID_INK, shade.getX(), shade.getY() + 1, shade.getZ(), 40, 0.3, 0.9, 0.3, 0.02);
            level.playSound(null, shade.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6F, 0.4F);
            shade.discard();
        }
    }

    public static void tick(StoryNpc shade, ServerLevel level) {
        LivingEntity target = shade.getTarget();
        if (target == null) {
            return;
        }
        float frac = shade.getHealth() / shade.getMaxHealth();
        int t = shade.tickCount;
        int blinkEvery = frac > 2.0F / 3.0F ? 100 : 70;
        if (t % blinkEvery == 0) {
            blinkBehind(shade, level, target);
        }
        if (frac <= 2.0F / 3.0F && t % 180 == 0) {
            ActTwo.spawnEchoes(level, shade.blockPosition(), frac <= 1.0F / 3.0F ? 3 : 2, 5);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(shade) <= 32 * 32) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 3, 0, false, false));
                }
            }
        }
        if (frac <= 1.0F / 3.0F && !shade.getTags().contains("storymod_phase3")) {
            shade.addTag("storymod_phase3");
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(shade) <= 48 * 48) {
                    ActTwo.title(p, "Тень почти прозрачна.", "Удары проходят сквозь неё. Ей нужен свет. Или тот, кого она узнаёт.");
                }
            }
        }
    }

    private static void blinkBehind(StoryNpc shade, ServerLevel level, LivingEntity target) {
        Vec3 look = target.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) {
            look = new Vec3(0, 0, 1);
        }
        Vec3 behind = target.position().subtract(look.normalize().scale(2.0));
        BlockPos feet = StoryEvents.groundNear(level, BlockPos.containing(behind), 0);
        if (!level.getBlockState(feet.below()).isSolid()) {
            return;
        }
        level.sendParticles(ParticleTypes.SQUID_INK, shade.getX(), shade.getY() + 1, shade.getZ(), 30, 0.3, 0.8, 0.3, 0.02);
        shade.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        shade.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        level.playSound(null, feet, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.5F);
    }

    /** In the last phase only the wanderer or someone close to a star lantern can hurt it. */
    public static boolean canHurt(StoryNpc shade, DamageSource source) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player)) {
            return false;
        }
        StoryState s = StoryState.get(player.getServer());
        if (s.role(player.getUUID()).equals(PlayerRoles.WANDERER)) {
            return true;
        }
        return nearLantern(player.serverLevel(), player.blockPosition()) || nearLantern(player.serverLevel(), shade.blockPosition());
    }

    public static boolean nearLantern(ServerLevel level, BlockPos center) {
        int r = (int) Math.ceil(LANTERN_REACH);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            if (p.distSqr(center) <= LANTERN_REACH * LANTERN_REACH && level.getBlockState(p).is(ModBlocks.STAR_LANTERN.get())) {
                return true;
            }
        }
        return false;
    }

    public static void deflect(StoryNpc shade, DamageSource source) {
        if (shade.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, shade.getX(), shade.getY() + 1, shade.getZ(), 12, 0.3, 0.6, 0.3, 0.01);
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("Удар проходит сквозь тень.")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
        }
    }
}
