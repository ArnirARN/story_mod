package com.arnir.storymod.registry;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.block.FallenStarBlock;
import com.arnir.storymod.block.StarLanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StoryMod.MODID);

    /** What is left of a falling star: hot at first, cools down in water or with time. */
    public static final DeferredBlock<FallenStarBlock> FALLEN_STAR = BLOCKS.register("fallen_star",
            () -> new FallenStarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(6.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(FallenStarBlock.HOT) ? 15 : 9)));

    /** A ward made from star shards. Several of them around the old circle hold the sky together. */
    public static final DeferredBlock<StarLanternBlock> STAR_LANTERN = BLOCKS.register("star_lantern",
            () -> new StarLanternBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)));

    private ModBlocks() {
    }
}
