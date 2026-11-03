package com.arnir.storymod.item;

import com.arnir.storymod.story.PlayerRoles;
import com.arnir.storymod.story.RoleAbilities;
import com.arnir.storymod.story.StoryState;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A personal weapon. In its owner's hands it is a masterwork with its own technique; anyone else finds it
 * heavy and dull (less than half the damage, and it slows them down).
 * <ul>
 *   <li>Greatsword (wanderer): swung like a one-handed blade. Use: step through space behind the target
 *   you look at (up to 16 blocks) and strike.</li>
 *   <li>Hammer (keeper): blows stun. Use: slam the ground, hurting and throwing back everything around.</li>
 * </ul>
 */
public class RoleWeaponItem extends SwordItem {
    public static final float FOREIGN_DAMAGE = 0.4F;
    public static final int GREATSWORD_COOLDOWN = 20 * 6;
    public static final int HAMMER_COOLDOWN = 20 * 10;

    private final String role;

    public RoleWeaponItem(Tier tier, Properties properties, String role) {
        super(tier, properties);
        this.role = role;
    }

    public String role() {
        return role;
    }

    public boolean isOwner(Player player) {
        return player instanceof ServerPlayer sp && StoryState.get(sp.getServer()).role(sp.getUUID()).equals(role);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (role.equals(PlayerRoles.KEEPER) && attacker instanceof Player p && isOwner(p)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3), attacker);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1), attacker);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!isOwner(sp)) {
            sp.displayClientMessage(Component.literal("Оружие тяжелеет в руках и не слушается.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean done = role.equals(PlayerRoles.WANDERER) ? phaseStrike(sp, serverLevel) : groundSlam(sp, serverLevel);
        if (done) {
            sp.getCooldowns().addCooldown(this, role.equals(PlayerRoles.WANDERER) ? GREATSWORD_COOLDOWN : HAMMER_COOLDOWN);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static float weaponDamage(ServerPlayer player) {
        return (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** Wanderer: appear behind the creature you look at and strike it. */
    public static boolean phaseStrike(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(16));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end,
                new AABB(eye, end).inflate(1.5), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
                        && !(e instanceof com.arnir.storymod.entity.StoryNpc) && !(e instanceof Player), 0.6F);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            return false;
        }
        if (!RoleAbilities.blinkBehind(player, target)) {
            return false;
        }
        target.hurt(player.damageSources().playerAttack(player), weaponDamage(player) * 1.25F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    /** Keeper: slam the ground; everything hostile nearby takes a blow and is thrown back. */
    public static boolean groundSlam(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4.0),
                e -> e != player && e.isAlive() && !(e instanceof Player) && !(e instanceof com.arnir.storymod.entity.StoryNpc));
        for (LivingEntity e : around) {
            e.hurt(player.damageSources().playerAttack(player), weaponDamage(player) * 0.6F);
            Vec3 push = e.position().subtract(player.position()).multiply(1, 0, 1);
            if (push.lengthSqr() > 1.0E-4) {
                push = push.normalize();
                e.knockback(1.2, -push.x, -push.z);
            }
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), player);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.2, player.getZ(), 3, 1.5, 0.1, 1.5, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);
        return true;
    }

    /** Called every two seconds while held: the weapon weighs on anyone but its owner. */
    public void pulse(ServerPlayer player) {
        if (!isOwner(player) && player.getMainHandItem().getItem() == this) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ArtifactItem.PULSE_TICKS, 0, true, false, true));
        }
    }

    /** Damage dealt with someone else's weapon is cut down. */
    public static float scaleDamage(Entity attacker, float amount) {
        if (attacker instanceof ServerPlayer p && p.getMainHandItem().getItem() instanceof RoleWeaponItem w && !w.isOwner(p)) {
            return amount * FOREIGN_DAMAGE;
        }
        return amount;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".effect").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.storymod.role_weapon.foreign").withStyle(ChatFormatting.DARK_GRAY));
    }
}
