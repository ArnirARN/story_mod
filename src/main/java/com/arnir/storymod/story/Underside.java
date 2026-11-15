package com.arnir.storymod.story;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The other side of the sky: a silent dimension (data: dimension/underside.json) with one island,
 * built by code the first time someone comes. The island has the seam in the middle, an exit stone,
 * and room for whoever the story keeps there.
 */
public final class Underside {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, StoryMod.id("underside"));
    public static final BlockPos CENTER = new BlockPos(0, 64, 0);
    public static final BlockPos EXIT = new BlockPos(0, 65, 19);
    public static final BlockPos ARRIVAL = new BlockPos(0, 65, 15);
    public static final BlockPos MIRA_CAGE = new BlockPos(15, 65, -6);
    public static final BlockPos NEL_SPOT = new BlockPos(-15, 65, -6);
    private static final int RADIUS = 22;

    private Underside() {
    }

    /**
     * The level that hosts the island. The game test server ignores datapack dimensions, so there the
     * island is built in the Nether instead; everywhere else it is storymod:underside.
     */
    public static ResourceKey<Level> key(MinecraftServer server) {
        if (server.getLevel(KEY) == null && server instanceof net.minecraft.gametest.framework.GameTestServer) {
            return Level.NETHER;
        }
        return KEY;
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(key(server));
    }

    public static boolean isHere(ServerPlayer player) {
        return player.getServer() != null && player.level().dimension() == key(player.getServer());
    }

    public static void enter(ServerPlayer player) {
        ServerLevel level = level(player.getServer());
        if (level == null) {
            player.sendSystemMessage(Component.literal("Шов не пускает. (Измерение storymod:underside не загружено.)")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ensureArena(level);
        StoryState s = StoryState.get(player.getServer());
        player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                80, 0.4, 1.0, 0.4, 0.1);
        player.teleportTo(level, ARRIVAL.getX() + 0.5, ARRIVAL.getY(), ARRIVAL.getZ() + 0.5, 180.0F, 0.0F);
        player.resetFallDistance();
        level.playSound(null, ARRIVAL, SoundEvents.PORTAL_TRAVEL, SoundSource.AMBIENT, 0.3F, 0.5F);
        if (!s.hasPersonal(player.getUUID(), "been_underside")) {
            s.setPersonal(player.getUUID(), "been_underside");
            ActTwo.title(player, "Изнанка.", "Здесь нет ни одного звука, кроме твоего дыхания.");
        }
        ensureCast(level, s);
    }

    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        StoryState s = StoryState.get(server);
        ServerLevel overworld = server.overworld();
        BlockPos anchor = s.anchor() != null ? s.anchor() : overworld.getSharedSpawnPos();
        BlockPos to = StoryEvents.groundNear(overworld, anchor.offset(0, 0, 4), 1);
        player.teleportTo(overworld, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        overworld.sendParticles(ParticleTypes.REVERSE_PORTAL, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, 60, 0.4, 1.0, 0.4, 0.1);
    }

    /** Exit stone and a safety net under the island. */
    static void tickPlayer(ServerPlayer player, StoryState s) {
        if (!isHere(player)) {
            return;
        }
        if (player.getY() < 20) {
            player.teleportTo(ARRIVAL.getX() + 0.5, ARRIVAL.getY() + 2, ARRIVAL.getZ() + 0.5);
            player.resetFallDistance();
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 4, 0));
            player.displayClientMessage(Component.literal("Пустота выталкивает тебя обратно. Ей ты не нужен.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return;
        }
        if (player.isShiftKeyDown() && player.blockPosition().distSqr(EXIT) <= 2 * 2) {
            leave(player);
        }
        ServerLevel level = player.serverLevel();
        if (level.getGameTime() % 200 == 0) {
            ensureCast(level, s);
        }
    }

    public static void ensureArena(ServerLevel level) {
        level.getChunkAt(CENTER);
        if (level.getBlockState(CENTER).is(Blocks.REINFORCED_DEEPSLATE)) {
            return;
        }
        build(level);
    }

    private static void build(ServerLevel level) {
        BlockState[] ground = {
                Blocks.BLACK_CONCRETE.defaultBlockState(),
                Blocks.BLACKSTONE.defaultBlockState(),
                Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                Blocks.BLACK_CONCRETE_POWDER.defaultBlockState(),
                Blocks.DEEPSLATE_TILES.defaultBlockState()
        };
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                level.getChunkAt(CENTER.offset(dx, 0, dz));
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > RADIUS + 0.5) {
                    continue;
                }
                // A floating island: thick in the middle, thin at the edge.
                int depth = 2 + (int) Math.round((RADIUS - d) * 0.6);
                for (int y = 0; y < depth; y++) {
                    BlockPos p = CENTER.offset(dx, -y, dz);
                    level.setBlock(p, y == 0 ? ground[Math.floorMod(dx * 31 + dz * 17 + dx * dz, ground.length)]
                            : Blocks.BLACKSTONE.defaultBlockState(), 2);
                }
                // White ash drifts.
                if (Math.floorMod(dx * 7 + dz * 11 + dx * dz * 3, 13) == 0 && d < RADIUS - 1) {
                    level.setBlock(CENTER.offset(dx, 0, dz), Blocks.WHITE_CONCRETE_POWDER.defaultBlockState(), 2);
                }
                // Crying obsidian rim.
                if (d > RADIUS - 0.8) {
                    level.setBlock(CENTER.offset(dx, 0, dz), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
                }
            }
        }
        // Pale dead trees.
        for (BlockPos t : List.of(new BlockPos(9, 0, 8), new BlockPos(-10, 0, 7), new BlockPos(12, 0, -12),
                new BlockPos(-8, 0, -14), new BlockPos(17, 0, 3), new BlockPos(-18, 0, -1))) {
            int h = 4 + Math.floorMod(t.getX() + t.getZ(), 3);
            for (int y = 1; y <= h; y++) {
                level.setBlock(CENTER.offset(t.getX(), y, t.getZ()), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState(), 2);
            }
            level.setBlock(CENTER.offset(t.getX() + 1, h, t.getZ()), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState(), 2);
            level.setBlock(CENTER.offset(t.getX(), h + 1, t.getZ() - 1), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState(), 2);
        }
        // The seam: a platform and a pale column of light.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 4) {
                    level.setBlock(CENTER.offset(dx, 0, dz), Blocks.END_STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        level.setBlock(CENTER, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 2);
        for (int y = 1; y <= 14; y++) {
            level.setBlock(CENTER.offset(0, y, 0), y % 3 == 0 ? Blocks.END_ROD.defaultBlockState()
                    : Blocks.WHITE_STAINED_GLASS.defaultBlockState(), 2);
        }
        // Exit stone.
        level.setBlock(EXIT.below(), Blocks.LODESTONE.defaultBlockState(), 2);
        for (BlockPos l : List.of(EXIT.offset(2, -1, 0), EXIT.offset(-2, -1, 0))) {
            level.setBlock(l, Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
        // Cage at the edge (empty until somebody is kept there).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 0; y <= 2; y++) {
                    BlockPos p = MIRA_CAGE.offset(dx, y, dz);
                    boolean wall = dx != 0 || dz != 0;
                    if (y == 2) {
                        level.setBlock(p, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), 2);
                    } else if (wall && !(dx == 0 && dz == 1 && y <= 1)) {
                        level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), 2);
                    }
                }
            }
        }
        // Soft light so nobody fights blind.
        for (BlockPos l : List.of(new BlockPos(8, 0, 0), new BlockPos(-8, 0, 0), new BlockPos(0, 0, 8), new BlockPos(0, 0, -8),
                new BlockPos(14, 0, 14), new BlockPos(-14, 0, 14), new BlockPos(14, 0, -14), new BlockPos(-14, 0, -14))) {
            level.setBlock(CENTER.offset(l.getX(), 0, l.getZ()), Blocks.SHROOMLIGHT.defaultBlockState(), 2);
        }
    }

    private static boolean present(ServerLevel level, String character) {
        return !level.getEntities(ModEntities.STORY_NPC.get(), e -> e.getCharacter().equals(character) && e.isAlive()).isEmpty();
    }

    /** Puts the people the story keeps here in their places (again, if they were lost). */
    public static void ensureCast(ServerLevel level, StoryState s) {
        if (ActTwo.over(s)) {
            return;
        }
        if (!present(level, "ilva")) {
            StoryNpc.spawn(level, "ilva", CENTER.offset(0, 1, -3), 0.0F);
        }
        if (s.has("mira_taken") && !s.has("mira_rescued") && !present(level, "mira")) {
            StoryNpc.spawn(level, "mira", MIRA_CAGE, 90.0F);
        }
        if (s.has("nel_lost") && !s.has("nel_defeated") && !s.has("nel_cured") && !present(level, "nel")) {
            StoryNpc.spawn(level, "nel", NEL_SPOT, -90.0F);
        }
        if (s.has("shade_fight") && !s.has("shade_defeated") && !present(level, "shade")) {
            StoryNpc shade = StoryNpc.spawn(level, "shade", CENTER.offset(0, 1, 5), 180.0F);
            if (shade != null) {
                shade.becomeBoss();
            }
        }
    }
}
