package com.arnir.storymod.story;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.item.RoleWeaponItem;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Permanent personal powers of the story roles, in the spirit of Origins.
 * <ul>
 *   <li>Wanderer: longer reach, faster at night, swings the greatsword like a one-handed blade,
 *   and sometimes vanishes from a melee blow to reappear behind the attacker.</li>
 *   <li>Keeper: sturdier (more health, toughness, steadier on her feet) and mines faster.</li>
 * </ul>
 */
public final class RoleAbilities {
    public static final int COUNTER_COOLDOWN = 20 * 6;
    /** Chance to slip behind a melee attacker. Tests set it to 1. */
    public static float counterChance = 0.25F;
    private static final String LAST_COUNTER = "storymod_last_counter";

    private record Mod(Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation op) {
        ResourceLocation key() {
            return StoryMod.id("role/" + id);
        }
    }

    private static final List<Mod> ALL = List.of(
            new Mod(Attributes.ENTITY_INTERACTION_RANGE, "wanderer_reach", 1.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.BLOCK_INTERACTION_RANGE, "wanderer_block_reach", 1.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.MOVEMENT_SPEED, "wanderer_night", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Mod(Attributes.ATTACK_SPEED, "wanderer_one_hand", 0.8, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.MAX_HEALTH, "keeper_health", 4.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.ARMOR_TOUGHNESS, "keeper_toughness", 2.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.KNOCKBACK_RESISTANCE, "keeper_steady", 0.25, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.BLOCK_BREAK_SPEED, "keeper_mining", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

    private RoleAbilities() {
    }

    /** Called every two seconds: keeps exactly the modifiers the player's role and situation call for. */
    public static void apply(ServerPlayer player) {
        String role = StoryState.get(player.getServer()).role(player.getUUID());
        List<String> active = new ArrayList<>();
        if (PlayerRoles.WANDERER.equals(role)) {
            active.add("wanderer_reach");
            active.add("wanderer_block_reach");
            Level level = player.level();
            long time = level.getDayTime() % 24000L;
            if (level.dimension() != Level.OVERWORLD || time >= 13000L && time <= 23000L) {
                active.add("wanderer_night");
            }
            if (player.getMainHandItem().getItem() instanceof RoleWeaponItem w && w.role().equals(PlayerRoles.WANDERER)) {
                active.add("wanderer_one_hand");
            }
        } else if (PlayerRoles.KEEPER.equals(role)) {
            active.add("keeper_health");
            active.add("keeper_toughness");
            active.add("keeper_steady");
            active.add("keeper_mining");
        }
        for (Mod mod : ALL) {
            AttributeInstance inst = player.getAttribute(mod.attribute());
            if (inst == null) {
                continue;
            }
            if (active.contains(mod.id())) {
                inst.addOrUpdateTransientModifier(new AttributeModifier(mod.key(), mod.amount(), mod.op()));
            } else if (inst.hasModifier(mod.key())) {
                inst.removeModifier(mod.key());
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static boolean has(ServerPlayer player, String id) {
        for (Mod mod : ALL) {
            if (mod.id().equals(id)) {
                AttributeInstance inst = player.getAttribute(mod.attribute());
                return inst != null && inst.hasModifier(mod.key());
            }
        }
        return false;
    }

    /** The wanderer may vanish from a melee blow and reappear behind the attacker. Returns true if the blow missed. */
    public static boolean tryCounter(ServerPlayer player, DamageSource source) {
        if (!PlayerRoles.WANDERER.equals(StoryState.get(player.getServer()).role(player.getUUID()))) {
            return false;
        }
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == player || !source.isDirect()) {
            return false;
        }
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(LAST_COUNTER);
        if (last != 0 && now >= last && now - last < COUNTER_COOLDOWN) {
            return false;
        }
        if (player.getRandom().nextFloat() >= counterChance) {
            return false;
        }
        if (!blinkBehind(player, attacker)) {
            return false;
        }
        player.getPersistentData().putLong(LAST_COUNTER, now);
        return true;
    }

    /** Teleports the player just behind the target, facing it. */
    public static boolean blinkBehind(ServerPlayer player, LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        flat = flat.normalize();
        Vec3 from = player.position();
        for (double dist : new double[]{1.6, 2.4, 1.0}) {
            Vec3 spot = target.position().subtract(flat.scale(dist + target.getBbWidth() / 2));
            if (player.level().noCollision(player, player.getBoundingBox().move(spot.subtract(from)))) {
                float yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
                player.teleportTo((ServerLevel) player.level(), spot.x, spot.y, spot.z, yaw, player.getXRot());
                ServerLevel level = (ServerLevel) player.level();
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 25, 0.3, 0.6, 0.3, 0.05);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.3F);
                return true;
            }
        }
        return false;
    }
}
