package com.arnir.storymod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;

/**
 * A fallen star. While hot it burns whoever touches it and gives nothing when broken;
 * water next to it (or a day of waiting) cools it, and then it breaks into star shards.
 */
public class FallenStarBlock extends Block {
    public static final BooleanProperty HOT = BooleanProperty.create("hot");

    public FallenStarBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(HOT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HOT);
    }

    public static boolean isHot(BlockState state) {
        return state.getBlock() instanceof FallenStarBlock && state.getValue(HOT);
    }

    /** Cools the star down: steam, a hiss and it becomes safe to mine. */
    public static void cool(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isHot(state)) {
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(HOT, false));
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    30, 0.4, 0.3, 0.4, 0.02);
        }
    }

    private static boolean touchesWater(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            FluidState fluid = level.getFluidState(pos.relative(d));
            if (fluid.is(FluidTags.WATER)) {
                return true;
            }
            BlockState near = level.getBlockState(pos.relative(d));
            if (near.is(net.minecraft.world.level.block.Blocks.ICE) || near.is(net.minecraft.world.level.block.Blocks.PACKED_ICE)
                    || near.is(net.minecraft.world.level.block.Blocks.BLUE_ICE) || near.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                    || near.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moved);
        if (!level.isClientSide && state.getValue(HOT) && touchesWater(level, pos)) {
            cool(level, pos);
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HOT);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // On its own it cools in about one in-game day.
        if (touchesWater(level, pos) || random.nextInt(24) == 0) {
            cool(level, pos);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(HOT) && entity instanceof LivingEntity living && !living.fireImmune()) {
            living.hurt(level.damageSources().hotFloor(), 2.0F);
            living.igniteForSeconds(3.0F);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (state.getValue(HOT) && !level.isClientSide && !player.fireImmune()) {
            player.igniteForSeconds(2.0F);
        }
        super.attack(state, level, pos, player);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(HOT)) {
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                        pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
            }
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                    pos.getZ() + random.nextDouble(), 0.0, 0.05, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FLAME, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                    pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
        }
    }
}
