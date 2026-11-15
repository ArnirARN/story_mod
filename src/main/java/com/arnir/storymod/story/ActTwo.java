package com.arnir.storymod.story;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.block.FallenStarBlock;
import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.registry.ModBlocks;
import com.arnir.storymod.registry.ModEntities;
import com.arnir.storymod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The second season. It starts by itself a day after any ending of the first one and runs on world events:
 * the sky slowly comes apart ("seam" stages 1..5, one stage every few days unless the star lanterns hold it),
 * stars fall at night, a silence marks people who die in the dark, an eclipse tests the old circle,
 * and at the end a way opens to the other side.
 * <p>
 * Everything story-specific (who says what, quests, choices, endings) is in data files; this class only runs
 * the clockwork the data relies on. Flags it sets: act2_start, seam_1..seam_5, first_star, rift_open,
 * eclipse_active, eclipse_done, lanterns_lit. Flags it reads: ending, act2_over, ending_open, loom_ready,
 * nel_cured / nel_gone / nel_lost.
 */
@EventBusSubscriber(modid = StoryMod.MODID)
public final class ActTwo {
    public static final int SEAM_MAX = 5;
    /** In-game days between seam stages when nothing holds the sky. */
    public static final int SEAM_DAYS = 5;
    public static final int LANTERNS_NEEDED = 4;
    public static final double LANTERN_RADIUS = 96.0;
    /** Lanterns closer than this to each other count as one. */
    public static final double LANTERN_SPACING = 12.0;
    public static final String ECHO_TAG = "storymod_echo";
    private static final String CALM_KEY = "storymod_calm_until";
    private static final String RIFT_COOLDOWN_KEY = "storymod_rift_cd";
    private static final int ECLIPSE_WAVES = 3;

    private ActTwo() {
    }

    // ------------------------------------------------------------------ state helpers

    public static boolean started(StoryState s) {
        return s.has("act2_start");
    }

    public static boolean over(StoryState s) {
        return s.has("act2_over");
    }

    /** Stars keep falling during the season, and forever if the sky was left open. */
    public static boolean starsActive(StoryState s) {
        return started(s) && (!over(s) || s.has("ending_open"));
    }

    public static String seamName(StoryState s) {
        String[] stage = {"цел", "тонкая нить", "трещина", "прореха", "рана", "открыт"};
        return stage[seam(s)];
    }

    public static int seam(StoryState s) {
        for (int i = SEAM_MAX; i >= 1; i--) {
            if (s.has("seam_" + i)) {
                return i;
            }
        }
        return 0;
    }

    private static boolean isNight(long dayTime) {
        long t = dayTime % 24000L;
        return t >= 13000L && t <= 23000L;
    }

    // ------------------------------------------------------------------ main clock

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 == 0) {
            tick(server);
        }
    }

    public static void tick(MinecraftServer server) {
        StoryState s = StoryState.get(server);
        if (!started(s)) {
            if (s.has("ending") && s.daysSince("ending") >= 1) {
                start(server);
            }
            return;
        }
        if (!over(s)) {
            escalate(server, s);
            maybeOpenRift(server, s);
            eclipseTick(server, s);
            if (server.getTickCount() % 200 == 0) {
                sightings(server, s);
            }
            if (server.getTickCount() % (20 * 60 * 6) == 0 && server.getPlayerCount() > 0) {
                ambient(server, s);
            }
        }
        if (starsActive(s)) {
            starfallTick(server, s);
        }
        if (server.getTickCount() % 1200 == 0) {
            validateLanterns(server, s);
        }
    }

    public static void start(MinecraftServer server) {
        StoryState s = StoryState.get(server);
        if (started(s)) {
            return;
        }
        s.set("act2_start");
        s.set("seam_1");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            title(p, "Небо треснуло.", "Тихо, как лёд на реке.");
        }
        StoryEvents.broadcast(server, "Ночью над миром прошла тонкая светлая линия, от края до края неба. Утром она не исчезла.");
    }

    private static void escalate(MinecraftServer server, StoryState s) {
        int level = seam(s);
        if (level == 0 || level >= SEAM_MAX || holding(s)) {
            return;
        }
        if (s.daysSince("seam_" + level) >= SEAM_DAYS) {
            raiseSeam(server);
        }
    }

    /** The lanterns hold the sky while enough of them stand around the old circle. */
    public static boolean holding(StoryState s) {
        return lanternsAroundCircle(s) >= LANTERNS_NEEDED;
    }

    private static final List<String> SEAM_LINES = List.of(
            "",
            "",
            "Линия в небе стала шире. Ночью из неё сыплется мелкая светлая пыль, и в эти минуты пропадают все звуки.",
            "Шов в небе разошёлся ещё. Теперь в нём видно что-то чёрное, и оно смотрит вниз.",
            "Шов разошёлся так, что ночью в нём можно разглядеть другую землю, перевёрнутую, без единого огня.",
            "Небо больше не держится. Шов открыт.");

    public static void raiseSeam(MinecraftServer server) {
        StoryState s = StoryState.get(server);
        int level = seam(s);
        if (level >= SEAM_MAX || !started(s)) {
            return;
        }
        int next = level + 1;
        s.set("seam_" + next);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.AMBIENT, 0.6F, 0.5F);
        }
        StoryEvents.broadcast(server, SEAM_LINES.get(next));
        if (next >= SEAM_MAX) {
            openRift(server);
        }
    }

    private static void maybeOpenRift(MinecraftServer server, StoryState s) {
        if (s.has("rift_open")) {
            return;
        }
        boolean nelSettled = s.has("nel_cured") || s.has("nel_gone") || s.has("nel_lost");
        if (seam(s) >= SEAM_MAX || (s.has("loom_ready") && nelSettled)) {
            openRift(server);
        }
    }

    /** The great star falls next to the circle and the way to the other side opens at the lodestone. */
    public static void openRift(MinecraftServer server) {
        StoryState s = StoryState.get(server);
        if (s.has("rift_open") || s.anchor() == null) {
            return;
        }
        s.set("rift_open");
        BlockPos anchor = s.anchor();
        impact(server, anchor.offset(14, 0, 9), true);
        ServerLevel overworld = server.overworld();
        overworld.sendParticles(ParticleTypes.REVERSE_PORTAL, anchor.getX() + 0.5, anchor.getY() + 1.0, anchor.getZ() + 0.5,
                200, 0.6, 1.5, 0.6, 0.1);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            title(p, "Шов открыт.", "Лодестоун в круге стал холодным, как лёд.");
        }
        StoryEvents.broadcast(server, "Большая звезда упала у старого круга, и с ней пришла тишина. Над лодестоуном в центре круга воздух прорезан, как ткань.");
    }

    // ------------------------------------------------------------------ falling stars

    private static void starfallTick(MinecraftServer server, StoryState s) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (s.nextStarTime() > 0) {
            if (now >= s.nextStarTime()) {
                BlockPos pos = s.nextStarPos();
                s.planStar(0, null);
                if (pos != null) {
                    impact(server, pos, false);
                }
            }
            return;
        }
        long dayTime = overworld.getDayTime();
        long t = dayTime % 24000L;
        if (t < 13500L || t > 21000L) {
            return;
        }
        int night = (int) (dayTime / 24000L) + 1;
        if (s.counter("star_night") == night) {
            return;
        }
        List<ServerPlayer> here = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == Level.OVERWORLD && !p.isSpectator()) {
                here.add(p);
            }
        }
        if (here.isEmpty()) {
            return;
        }
        s.setCounter("star_night", night);
        double chance = s.has("ending_open") && over(s) ? 0.5 : 0.35 + 0.1 * seam(s);
        boolean first = !s.has("first_star");
        if (!first && overworld.random.nextDouble() >= chance) {
            return;
        }
        planStar(server, first ? null : here.get(overworld.random.nextInt(here.size())), 20 * 60);
    }

    /** Plans a star to fall in {@code delay} ticks next to a player, or next to the circle if player is null. */
    public static BlockPos planStar(MinecraftServer server, ServerPlayer near, int delay) {
        StoryState s = StoryState.get(server);
        ServerLevel overworld = server.overworld();
        BlockPos base = near != null ? near.blockPosition() : s.anchor();
        if (base == null) {
            return null;
        }
        double angle = overworld.random.nextDouble() * Math.PI * 2;
        int distance = near != null ? 30 + overworld.random.nextInt(50) : 26 + overworld.random.nextInt(14);
        BlockPos target = base.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
        s.planStar(overworld.getGameTime() + delay, target);
        String who = near != null ? near.getGameProfile().getName() : "старым кругом";
        StoryEvents.broadcast(server, "Небо над " + who + " светлеет, как перед рассветом, хотя до утра далеко. Что-то падает "
                + direction(base, target) + ".");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.AMBIENT, 0.6F, 0.4F);
        }
        return target;
    }

    private static String direction(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) {
            deg += 360;
        }
        String[] names = {"к северу", "к северо-востоку", "к востоку", "к юго-востоку", "к югу", "к юго-западу", "к западу", "к северо-западу"};
        return names[(int) Math.round(deg / 45.0) % 8];
    }

    /** Used by the "star_fall" action: drops a star right away. */
    public static void dropStarNow(MinecraftServer server, ServerPlayer player, String at, int distance, boolean big) {
        StoryState s = StoryState.get(server);
        BlockPos base = "anchor".equals(at) && s.anchor() != null ? s.anchor() : player.blockPosition();
        double angle = server.overworld().random.nextDouble() * Math.PI * 2;
        impact(server, base.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance)), big);
    }

    private static boolean craterable(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.CLAY)
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.CALCITE);
    }

    private static boolean clearable(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty()) || craterable(state);
    }

    /**
     * A star hits the ground: a crater (only natural ground is touched, never anything built), a hot star in the middle,
     * a blast that hurts but breaks nothing. The great star also leaves a chest.
     */
    public static BlockPos impact(MinecraftServer server, BlockPos at, boolean big) {
        ServerLevel level = server.overworld();
        StoryState s = StoryState.get(server);
        level.getChunkAt(at);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        BlockPos ground = top.below();
        int r = big ? 5 : 3;
        BlockState[] floor = {Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                Blocks.BASALT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState()};
        BlockPos star = null;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 1.2) {
                    continue;
                }
                BlockPos column = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.offset(dx, 0, dz)).below();
                if (Math.abs(column.getY() - ground.getY()) > 4) {
                    continue;
                }
                if (d > r) {
                    // Scorched rim.
                    if (craterable(level.getBlockState(column)) && level.random.nextInt(3) > 0) {
                        level.setBlock(column, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                    }
                    continue;
                }
                int depth = (int) Math.round(Math.sqrt(r * r - d * d) * 0.7);
                boolean blocked = false;
                for (int y = 0; y < depth; y++) {
                    BlockPos p = column.below(y);
                    if (!clearable(level.getBlockState(p))) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) {
                    if (dx == 0 && dz == 0) {
                        star = column.above();
                    }
                    continue;
                }
                for (int y = 0; y < depth; y++) {
                    level.setBlock(column.below(y), Blocks.AIR.defaultBlockState(), 3);
                }
                // Clear plants and snow left hanging above the hole.
                for (int y = 1; y <= 2; y++) {
                    BlockState above = level.getBlockState(column.above(y));
                    if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) {
                        level.setBlock(column.above(y), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                BlockPos bottom = column.below(depth);
                if (craterable(level.getBlockState(bottom)) || level.getBlockState(bottom).isAir()) {
                    level.setBlock(bottom, floor[level.random.nextInt(floor.length)], 3);
                }
                if (dx == 0 && dz == 0) {
                    star = depth > 0 ? column.below(depth - 1) : column.above();
                }
            }
        }
        if (star == null) {
            star = top;
        }
        level.setBlock(star, ModBlocks.FALLEN_STAR.get().defaultBlockState(), 3);
        if (big) {
            for (BlockPos extra : List.of(star.offset(1, 0, 0), star.offset(-1, 0, 1), star.offset(0, 0, -1))) {
                if (clearable(level.getBlockState(extra))) {
                    level.setBlock(extra, ModBlocks.FALLEN_STAR.get().defaultBlockState(), 3);
                }
            }
            BlockPos chest = star.offset(2, 0, 2);
            if (!level.getBlockState(chest).canBeReplaced()) {
                chest = star.above();
            }
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(chest);
            if (be instanceof ChestBlockEntity c) {
                ItemStack core = new ItemStack(ModItems.STAR_CORE.get());
                c.setItem(13, core);
                c.setItem(11, new ItemStack(ModItems.STAR_SHARD.get(), 6));
                c.setItem(15, new ItemStack(ModItems.STAR_SHARD.get(), 6));
            }
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(star.getX() + 0.5, star.getY() + 1, star.getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.explode(null, star.getX() + 0.5, star.getY() + 1.5, star.getZ() + 0.5, big ? 4.0F : 2.5F, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, star.getX() + 0.5, star.getY() + 1.5, star.getZ() + 0.5, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, star.getX() + 0.5, star.getY() + 2, star.getZ() + 0.5, 150, 2, 2, 2, 0.2);
        s.addCounter("stars_fallen", 1);
        s.set("first_star");
        if (seam(s) >= 3 && !over(s)) {
            spawnEchoes(level, star.above(), 1 + seam(s) - 2, 5);
        }
        return star;
    }

    public static Component forecast(ServerPlayer player) {
        StoryState s = StoryState.get(player.getServer());
        ServerLevel overworld = player.getServer().overworld();
        String text;
        if (!starsActive(s)) {
            text = "Небо как небо. Звёзды на своих местах.";
        } else if (s.nextStarTime() > 0 && s.nextStarPos() != null) {
            long secs = Math.max(0, (s.nextStarTime() - overworld.getGameTime()) / 20);
            BlockPos p = s.nextStarPos();
            text = "Сквозь окуляр видно, как светлеет точка у края Шва. Звезда упадёт около X " + p.getX() + ", Z " + p.getZ()
                    + " примерно через " + secs + " с.";
        } else if (isNight(overworld.getDayTime())) {
            text = "Шов сегодня спокоен. Если что-то упадёт, это будет видно заранее.";
        } else {
            text = "Днём Шва почти не видно. Звёзды падают ночью.";
        }
        if (started(s) && !over(s)) {
            text += "\nШов: " + seamName(s) + ". Фонарей у круга: " + lanternsAroundCircle(s) + " из " + LANTERNS_NEEDED + ".";
        }
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    // ------------------------------------------------------------------ echoes

    /** Silent, darkened walkers that come with the stars. Their touch can carry the silence. */
    public static void spawnEchoes(ServerLevel level, BlockPos near, int count, int distance) {
        for (int i = 0; i < count; i++) {
            Zombie echo = EntityType.ZOMBIE.create(level);
            if (echo == null) {
                continue;
            }
            BlockPos pos = StoryEvents.groundNear(level, near, distance);
            echo.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0);
            echo.setCustomName(Component.literal("Отзвук").withStyle(ChatFormatting.DARK_GRAY));
            echo.setSilent(true);
            echo.addTag(ECHO_TAG);
            echo.setCanPickUpLoot(false);
            echo.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET));
            echo.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
            echo.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS));
            echo.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS));
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                echo.setDropChance(slot, 0.0F);
            }
            echo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 10, 0, false, false));
            level.addFreshEntity(echo);
            level.sendParticles(ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.02);
        }
    }

    private static ItemStack dyed(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x141414, false));
        return stack;
    }

    public static int echoesNear(ServerLevel level, BlockPos pos, double radius) {
        return level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(pos).inflate(radius),
                e -> e.getTags().contains(ECHO_TAG) && e.isAlive()).size();
    }

    // ------------------------------------------------------------------ eclipse

    private static void eclipseTick(MinecraftServer server, StoryState s) {
        if (seam(s) < 4 || s.has("eclipse_done") || s.anchor() == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos anchor = s.anchor();
        boolean night = isNight(overworld.getDayTime());
        boolean someoneThere = false;
        for (ServerPlayer p : overworld.players()) {
            if (!p.isSpectator() && p.blockPosition().distSqr(anchor) <= 48 * 48) {
                someoneThere = true;
                break;
            }
        }
        if (!s.has("eclipse_active")) {
            if (!night || !someoneThere || s.counter("eclipse_tries") >= 1000) {
                return;
            }
            startEclipse(server);
        }
        if (!night || !someoneThere) {
            s.clear("eclipse_active");
            s.setCounter("eclipse_wave", 0);
            StoryEvents.broadcast(server, "Тьма над кругом отступает, не добившись своего. Она вернётся следующей ночью.");
            return;
        }
        int wave = s.counter("eclipse_wave");
        int alive = echoesNear(overworld, anchor, 40);
        if (alive > 0) {
            return;
        }
        if (wave >= ECLIPSE_WAVES) {
            finishEclipse(server);
            return;
        }
        s.setCounter("eclipse_wave", wave + 1);
        spawnEchoes(overworld, anchor, 3 + wave * 2, 10);
        StoryEvents.broadcast(server, wave == 0
                ? "Из темноты за кругом выходят безмолвные фигуры."
                : "Ещё одна волна тишины накатывает на круг.");
    }

    public static void startEclipse(MinecraftServer server) {
        StoryState s = StoryState.get(server);
        s.set("eclipse_active");
        s.setCounter("eclipse_wave", 0);
        s.addCounter("eclipse_tries", 1);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            title(p, "Затмение.", "Даже луна закрыла глаза.");
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 8, 0));
        }
    }

    private static void finishEclipse(MinecraftServer server) {
        StoryState s = StoryState.get(server);
        s.clear("eclipse_active");
        s.set("eclipse_done");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            title(p, "Круг выстоял.", "");
            ItemStack shards = new ItemStack(ModItems.STAR_SHARD.get(), 4);
            if (!p.getInventory().add(shards)) {
                p.drop(shards, false);
            }
        }
        StoryEvents.broadcast(server, "Последний отзвук рассыпается чёрной пылью. На камнях круга остались светлые осколки, будто кто-то бросил их вам в награду.");
    }

    // ------------------------------------------------------------------ the figure at the edge of sight

    private static void sightings(MinecraftServer server, StoryState s) {
        if (seam(s) < 2 || s.has("rift_open")) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (!isNight(overworld.getDayTime())) {
            return;
        }
        int night = (int) (overworld.getDayTime() / 24000L) + 1;
        if (s.counter("shade_night") == night || overworld.random.nextInt(10) != 0) {
            return;
        }
        List<ServerPlayer> here = new ArrayList<>(overworld.players());
        here.removeIf(Entity::isSpectator);
        if (here.isEmpty()) {
            return;
        }
        s.setCounter("shade_night", night);
        ServerPlayer p = here.get(overworld.random.nextInt(here.size()));
        double angle = overworld.random.nextDouble() * Math.PI * 2;
        BlockPos far = p.blockPosition().offset((int) (Math.cos(angle) * 22), 0, (int) (Math.sin(angle) * 22));
        BlockPos pos = StoryEvents.groundNear(overworld, far, 0);
        StoryNpc npc = StoryNpc.spawn(overworld, "shade", pos, 0.0F);
        if (npc != null) {
            npc.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
        }
        s.addCounter("shade_seen", 1);
    }

    // ------------------------------------------------------------------ ambient

    private static final List<List<String>> AMBIENT = List.of(
            List.of(),
            List.of("Светлая линия в небе видна даже днём, если знать, куда смотреть.",
                    "Где-то далеко падает звезда. Звука нет.",
                    "Ночью собаки воют на небо, а не на луну."),
            List.of("На миг пропадают все звуки: ни ветра, ни шагов. Потом всё возвращается.",
                    "Тебе кажется, что кто-то стоит на краю зрения. Стоит обернуться, и никого.",
                    "Звёзды у Шва мигают не вразнобой, а вместе. Как будто дышат."),
            List.of("Колокола в деревнях звонят сами по себе и не издают ни звука.",
                    "Из Шва тянет холодом. Не зимним, другим.",
                    "Кто-то оставил на земле следы. Они ведут к тебе, но не от тебя."),
            List.of("Ночь стала длиннее. Или так только кажется.",
                    "Ветер приносит обрывок песни. Ты почти узнаёшь мотив.",
                    "В Шве видно перевёрнутые деревья. Они белые."),
            List.of("Тишина стоит над миром, как вода.",
                    "Шов ждёт."));

    private static void ambient(MinecraftServer server, StoryState s) {
        List<String> lines = AMBIENT.get(Math.min(seam(s), AMBIENT.size() - 1));
        if (!lines.isEmpty()) {
            StoryEvents.broadcast(server, lines.get(server.overworld().random.nextInt(lines.size())));
        }
    }

    // ------------------------------------------------------------------ lanterns

    public static void lanternPlaced(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        StoryState s = StoryState.get(level.getServer());
        int before = lanternsAroundCircle(s);
        s.addLantern(pos);
        int after = lanternsAroundCircle(s);
        if (!started(s) || s.anchor() == null) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(pos) <= 16 * 16) {
                p.displayClientMessage(Component.literal(after > before
                        ? "Фонарь загорается ровным звёздным светом. Фонарей у круга: " + after + " из " + LANTERNS_NEEDED + "."
                        : "Фонарь горит, но Шов его не замечает: он слишком далеко от круга или слишком близко к другому.")
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        if (after >= LANTERNS_NEEDED && !s.has("lanterns_lit")) {
            s.set("lanterns_lit");
            StoryEvents.broadcast(level.getServer(), "Четыре звёздных огня встали вокруг старого круга. Шов в небе замер, будто его прижали ладонью.");
        }
    }

    public static void lanternRemoved(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        StoryState s = StoryState.get(level.getServer());
        s.removeLantern(pos);
        if (s.has("lanterns_lit") && lanternsAroundCircle(s) < LANTERNS_NEEDED) {
            s.clear("lanterns_lit");
            StoryEvents.broadcast(level.getServer(), "Один из звёздных фонарей погас. Шов в небе снова дрогнул.");
        }
    }

    /** Lanterns within reach of the circle, counting only ones at least a few steps apart. */
    public static int lanternsAroundCircle(StoryState s) {
        BlockPos anchor = s.anchor();
        if (anchor == null) {
            return 0;
        }
        List<BlockPos> counted = new ArrayList<>();
        List<BlockPos> all = new ArrayList<>(s.lanterns());
        all.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        for (BlockPos p : all) {
            double dx = p.getX() - anchor.getX();
            double dz = p.getZ() - anchor.getZ();
            if (dx * dx + dz * dz > LANTERN_RADIUS * LANTERN_RADIUS) {
                continue;
            }
            boolean apart = true;
            for (BlockPos c : counted) {
                if (c.distSqr(p) < LANTERN_SPACING * LANTERN_SPACING) {
                    apart = false;
                    break;
                }
            }
            if (apart) {
                counted.add(p);
            }
        }
        return counted.size();
    }

    /** Someone told the wrong ears where the lanterns are. */
    public static void breakRandomLantern(MinecraftServer server) {
        StoryState s = StoryState.get(server);
        List<BlockPos> all = new ArrayList<>(s.lanterns());
        if (all.isEmpty()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos pos = all.get(overworld.random.nextInt(all.size()));
        overworld.getChunkAt(pos);
        if (overworld.getBlockState(pos).is(ModBlocks.STAR_LANTERN.get())) {
            overworld.destroyBlock(pos, false);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(overworld);
            if (bolt != null) {
                bolt.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                bolt.setVisualOnly(true);
                overworld.addFreshEntity(bolt);
            }
        }
        s.removeLantern(pos);
        if (s.has("lanterns_lit") && lanternsAroundCircle(s) < LANTERNS_NEEDED) {
            s.clear("lanterns_lit");
        }
        StoryEvents.broadcast(server, "Ночью что-то разбило звёздный фонарь у круга. Вокруг ни следа. Как будто оно точно знало, где искать.");
    }

    private static void validateLanterns(MinecraftServer server, StoryState s) {
        ServerLevel overworld = server.overworld();
        for (BlockPos pos : List.copyOf(s.lanterns())) {
            if (overworld.isLoaded(pos) && !overworld.getBlockState(pos).is(ModBlocks.STAR_LANTERN.get())) {
                s.removeLantern(pos);
            }
        }
    }

    // ------------------------------------------------------------------ players: the silence, the rift, the thread

    /** Called every two seconds for each player. */
    public static void tickPlayer(ServerPlayer player) {
        StoryState s = StoryState.get(player.getServer());
        if (!started(s)) {
            return;
        }
        if (isMarked(player, s)) {
            tickSilence(player, s);
        }
        Underside.tickPlayer(player, s);
        if (s.has("rift_open") && player.level().dimension() == Level.OVERWORLD && s.anchor() != null
                && player.isShiftKeyDown() && (!over(s) || s.has("ending_open"))) {
            BlockPos a = s.anchor();
            double dx = player.getX() - (a.getX() + 0.5);
            double dz = player.getZ() - (a.getZ() + 0.5);
            long now = player.getServer().overworld().getGameTime();
            if (dx * dx + dz * dz <= 2.5 * 2.5 && Math.abs(player.getY() - a.getY()) <= 3
                    && now >= player.getPersistentData().getLong(RIFT_COOLDOWN_KEY)) {
                player.getPersistentData().putLong(RIFT_COOLDOWN_KEY, now + 100);
                Underside.enter(player);
            }
        }
    }

    public static boolean isMarked(ServerPlayer player, StoryState s) {
        return s.hasPersonal(player.getUUID(), "hollow_mark") && !s.hasPersonal(player.getUUID(), "hollow_cured");
    }

    public static boolean isCalm(ServerPlayer player) {
        return player.getServer().overworld().getGameTime() < player.getPersistentData().getLong(CALM_KEY);
    }

    private static final List<String> WHISPERS = List.of(
            "Кто-то шепчет твоё имя у самого уха. Голос без звука.",
            "Твои шаги на миг перестают звучать.",
            "Ты слышишь, как тишина дышит.",
            "Перед глазами на миг всё становится белым, как пепел.",
            "Где-то рядом закрывается дверь, которой нет.");

    private static void tickSilence(ServerPlayer player, StoryState s) {
        if (isCalm(player)) {
            return;
        }
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
            player.displayClientMessage(Component.literal("Стоит закрыть глаза, как тишина становится громкой. Уснуть не выходит.")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
        }
        boolean night = isNight(player.getServer().overworld().getDayTime());
        if (!night || player.getRandom().nextInt(12) != 0) {
            return;
        }
        if (player.getRandom().nextBoolean()) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 6, 0, false, false));
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 4, 0, false, false));
        }
        player.sendSystemMessage(Component.literal(WHISPERS.get(player.getRandom().nextInt(WHISPERS.size())))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (seam(s) >= 3 && player.level().dimension() == Level.OVERWORLD && player.getRandom().nextInt(4) == 0) {
            spawnEchoes(player.serverLevel(), player.blockPosition(), 1, 12);
        }
    }

    public static void mark(ServerPlayer player) {
        StoryState s = StoryState.get(player.getServer());
        if (s.hasPersonal(player.getUUID(), "hollow_cured") || s.hasPersonal(player.getUUID(), "hollow_mark")) {
            return;
        }
        s.setPersonal(player.getUUID(), "hollow_mark");
        player.sendSystemMessage(Component.literal("Что-то холодное и беззвучное осталось с тобой. Как будто в тебе стало меньше звука.")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    public static void calm(ServerPlayer player, int days) {
        long until = player.getServer().overworld().getGameTime() + days * 24000L;
        player.getPersistentData().putLong(CALM_KEY, until);
        player.removeEffect(MobEffects.CONFUSION);
        player.removeEffect(MobEffects.DARKNESS);
        player.sendSystemMessage(Component.literal(isMarked(player, StoryState.get(player.getServer()))
                        ? "Тёплый горький вкус. Тишина внутри отступает, ненадолго."
                        : "Тёплый горький вкус. Спокойно.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    public static void cure(ServerPlayer player) {
        StoryState s = StoryState.get(player.getServer());
        boolean was = isMarked(player, s);
        s.clearPersonal(player.getUUID(), "hollow_mark");
        s.setPersonal(player.getUUID(), "hollow_cured");
        player.removeEffect(MobEffects.CONFUSION);
        player.removeEffect(MobEffects.DARKNESS);
        player.sendSystemMessage(Component.literal(was
                        ? "Звук возвращается разом: дыхание, сердце, ветер. Тишина ушла и больше не найдёт к тебе дороги."
                        : "Горько и звонко. Теперь тишине к тебе не пристать.")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        StoryState s = StoryState.get(player.getServer());
        if (tryThread(player, s)) {
            event.setCanceled(true);
            return;
        }
        if (!started(s) || over(s) || seam(s) < 2) {
            return;
        }
        boolean dark = Underside.isHere(player)
                || (player.level().dimension() == Level.OVERWORLD && isNight(player.getServer().overworld().getDayTime()));
        if (dark) {
            mark(player);
        }
    }

    /** The wanderer's thread pulls its owner out of a deadly blow, then needs a long rest. */
    private static boolean tryThread(ServerPlayer player, StoryState s) {
        if (!s.role(player.getUUID()).equals(PlayerRoles.WANDERER)
                || player.getCooldowns().isOnCooldown(ModItems.ILVA_THREAD.get())) {
            return false;
        }
        boolean carried = false;
        for (ItemStack stack : PlayerItems.carried(player)) {
            if (stack.is(ModItems.ILVA_THREAD.get())) {
                carried = true;
                break;
            }
        }
        if (!carried) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 0.9, 0.4, 0.1);
        BlockPos to = StoryEvents.groundNear(level, player.blockPosition(), 8);
        player.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        player.setHealth(8.0F);
        player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 6, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 4, 2));
        level.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.6F);
        player.getCooldowns().addCooldown(ModItems.ILVA_THREAD.get(), 20 * 60 * 4);
        player.displayClientMessage(Component.literal("Нить натягивается и выдёргивает тебя из-под удара.")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC), true);
        return true;
    }

    @SubscribeEvent
    public static void onEchoHit(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() != null
                && event.getSource().getEntity().getTags().contains(ECHO_TAG) && player.getRandom().nextInt(7) == 0) {
            mark(player);
        }
    }

    // ------------------------------------------------------------------ helpers

    public static boolean insideStructure(ServerPlayer player, String id) {
        ServerLevel level = player.serverLevel();
        if (id.startsWith("#")) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(id.substring(1)));
            return level.structureManager().getStructureWithPieceAt(player.blockPosition(), tag).isValid();
        }
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(id));
        return structure != null && level.structureManager().getStructureWithPieceAt(player.blockPosition(), structure).isValid();
    }

    static void title(ServerPlayer p, String text, String subtitle) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(20, 80, 30));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.literal(subtitle).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    /** The figure was beaten: the way back to the story is through the one who waits at the seam. */
    public static void onShadeDefeated(ServerLevel level, StoryNpc shade) {
        StoryState s = StoryState.get(level.getServer());
        s.set("shade_defeated");
        StoryEvents.broadcast(level.getServer(), "Тень рассыпается, и на миг во всём мире становится слышно, как падает снег.");
        boolean ilvaHere = !level.getEntities(ModEntities.STORY_NPC.get(), e -> e.getCharacter().equals("ilva") && e.isAlive()).isEmpty();
        if (!ilvaHere) {
            StoryNpc.spawn(level, "ilva", StoryEvents.groundNear(level, shade.blockPosition(), 3), 0.0F);
        }
    }
}
