package com.arnir.storymod.entity;

import com.arnir.storymod.dialogue.DialogueManager;
import com.arnir.storymod.dialogue.DialogueSessions;
import com.arnir.storymod.registry.ModEntities;
import com.arnir.storymod.story.StoryEvents;
import com.arnir.storymod.story.StoryState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * A story character. Which character it is (and therefore its dialogue and skin) is a synced string id.
 * Peaceful and invulnerable by default; can be turned into a boss by a dialogue action.
 */
public class StoryNpc extends PathfinderMob {
    private static final EntityDataAccessor<String> CHARACTER =
            SynchedEntityData.defineId(StoryNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> HOSTILE =
            SynchedEntityData.defineId(StoryNpc.class, EntityDataSerializers.BOOLEAN);

    public static final float BOSS_HEALTH = 300.0F;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.empty(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int generation;
    private boolean hostileGoalsAdded;

    public StoryNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.bossEvent.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    /** Spawns a fresh copy of a character, removing every older copy of it. */
    public static StoryNpc spawn(ServerLevel level, String character, BlockPos pos, float yaw) {
        StoryState state = StoryState.get(level.getServer());
        int gen = state.bumpGeneration(character);
        StoryNpc npc = ModEntities.STORY_NPC.get().create(level);
        if (npc == null) {
            return null;
        }
        npc.setCharacter(character);
        npc.generation = gen;
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setCustomName(Component.literal(DialogueManager.speakerFor(character)));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
        return npc;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARACTER, "veld");
        builder.define(HOSTILE, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    private void addHostileGoals() {
        if (hostileGoalsAdded) {
            return;
        }
        hostileGoalsAdded = true;
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public String getCharacter() {
        return this.entityData.get(CHARACTER);
    }

    public void setCharacter(String character) {
        String clean = character.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        this.entityData.set(CHARACTER, clean);
    }

    public boolean isHostile() {
        return this.entityData.get(HOSTILE);
    }

    public void becomeBoss() {
        if (isHostile()) {
            return;
        }
        this.entityData.set(HOSTILE, true);
        var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(BOSS_HEALTH);
        }
        this.setHealth(BOSS_HEALTH);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setVisible(true);
        addHostileGoals();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isHostile() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            DialogueSessions.start(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!isHostile() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return isHostile();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount % 100 == 0) {
            StoryState state = StoryState.get(((ServerLevel) this.level()).getServer());
            if (state.generation(getCharacter()) != this.generation) {
                // An older copy of a character that has since been respawned or removed by the story.
                this.discard();
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!isHostile()) {
            return;
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 240 == 0 && this.getTarget() != null && this.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 2; i++) {
                Vex vex = EntityType.VEX.create(serverLevel);
                if (vex == null) {
                    continue;
                }
                vex.moveTo(this.getX() + (this.random.nextDouble() - 0.5) * 3, this.getY() + 1.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 3, 0.0F, 0.0F);
                vex.setOwner(this);
                vex.setLimitedLife(20 * 30);
                vex.setTarget(this.getTarget());
                serverLevel.addFreshEntity(vex);
            }
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        boolean wasBoss = isHostile();
        super.die(source);
        if (wasBoss && this.level() instanceof ServerLevel serverLevel) {
            StoryEvents.onBossDefeated(serverLevel, this);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Character", getCharacter());
        tag.putBoolean("Hostile", isHostile());
        tag.putInt("Generation", generation);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Character")) {
            setCharacter(tag.getString("Character"));
        }
        this.generation = tag.getInt("Generation");
        if (tag.getBoolean("Hostile")) {
            this.entityData.set(HOSTILE, true);
            this.bossEvent.setName(this.getDisplayName());
            this.bossEvent.setVisible(true);
            addHostileGoals();
        }
    }
}
