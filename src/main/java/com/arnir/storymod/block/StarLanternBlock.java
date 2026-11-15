package com.arnir.storymod.block;

import com.arnir.storymod.story.ActTwo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A ward of star shards. The story keeps track of every one standing in the overworld. */
public class StarLanternBlock extends Block {
    public StarLanternBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof ServerLevel serverLevel && !old.is(this)) {
            ActTwo.lanternPlaced(serverLevel, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (level instanceof ServerLevel serverLevel && !replacement.is(this)) {
            ActTwo.lanternRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    pos.getY() + 1.1, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.03, 0.0);
        }
    }
}
