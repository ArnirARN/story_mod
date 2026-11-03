package com.arnir.storymod.story;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.command.StoryCommand;
import com.arnir.storymod.dialogue.DialogueManager;
import com.arnir.storymod.dialogue.DialogueSessions;
import com.arnir.storymod.dialogue.Inventories;
import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Story triggers that do not come from a conversation: the opening, finding cores and notes,
 * characters appearing on their own, ambient lines and the boss.
 */
@EventBusSubscriber(modid = StoryMod.MODID)
public final class StoryEvents {
    private static final int SCAN_INTERVAL = 40;
    private static final int AMBIENT_INTERVAL = 20 * 60 * 7;

    private StoryEvents() {
    }

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(DialogueManager.INSTANCE);
        event.addListener(com.arnir.storymod.dialogue.TriggerManager.INSTANCE);
        event.addListener(com.arnir.storymod.dialogue.JournalManager.INSTANCE);
    }

    /** Reports typos in item ids used by story data (only for mods that are installed). */
    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        for (String id : Inventories.unknownReferencedItems()) {
            StoryMod.LOGGER.error("storymod: story data mentions an unknown item {}", id);
        }
        List<String> active = new java.util.ArrayList<>();
        for (String mod : List.of("create", "aether", "twilightforest", "deeperdarker", "ars_nouveau", "irons_spellbooks", "curios")) {
            if (net.neoforged.fml.ModList.get().isLoaded(mod)) {
                active.add(mod);
            }
        }
        StoryMod.LOGGER.info("Story integrations active: {}", active);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StoryCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            startStoryIfNeeded(player.getServer());
            StoryState state = StoryState.get(player.getServer());
            PlayerRoles.onLogin(player, state);
            if (!state.has("veld_met")) {
                player.sendSystemMessage(Component.literal(
                                "Тебе кажется, что кто-то тихо зовёт тебя по имени. Голос доносится оттуда, где этот мир начинается.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DialogueSessions.forget(player);
        }
    }

    /** The opening: the first characters and a small ruin appear next to world spawn. */
    public static void startStoryIfNeeded(MinecraftServer server) {
        StoryState state = StoryState.get(server);
        if (state.has("intro_spawned")) {
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        BlockPos center = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(6, 0, 6));
        buildRuin(overworld, center);
        state.setAnchor(center);
        StoryNpc.spawn(overworld, "veld", center.offset(0, 0, -2), 0.0F);
        StoryNpc.spawn(overworld, "os", center.offset(3, 0, 1), 90.0F);
        state.set("intro_spawned");
    }

    /** A ring of old stone around a lodestone. Only replaces natural surface blocks and plants. */
    private static void buildRuin(ServerLevel level, BlockPos center) {
        BlockState[] palette = {
                Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
        };
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                BlockPos column = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));
                BlockPos ground = column.below();
                if (dist <= 4.4 && isNatural(level.getBlockState(ground))) {
                    level.setBlockAndUpdate(ground, palette[Math.floorMod(dx * 31 + dz * 17, palette.length)]);
                }
                // Broken pillars on the ring
                if (dist > 3.6 && dist <= 4.4 && Math.floorMod(dx * 7 + dz * 13, 5) == 0) {
                    int h = 1 + Math.floorMod(dx + dz, 3);
                    for (int y = 0; y < h; y++) {
                        BlockPos p = column.above(y);
                        if (level.getBlockState(p).canBeReplaced()) {
                            level.setBlockAndUpdate(p, palette[y % 2]);
                        }
                    }
                }
            }
        }
        BlockPos lodestone = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center).below();
        level.setBlockAndUpdate(lodestone, Blocks.LODESTONE.defaultBlockState());
    }

    private static boolean isNatural(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.STONE)
                || state.is(Blocks.PODZOL) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.RED_SAND) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.TERRACOTTA) || state.is(Blocks.SANDSTONE);
    }

    /** A free spot on the ground roughly {@code distance} blocks away from {@code origin}. */
    public static BlockPos groundNear(ServerLevel level, BlockPos origin, int distance) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos feet = new BlockPos(x, origin.getY() + dy, z);
                if (level.getBlockState(feet.below()).isSolid()
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    return feet;
                }
            }
        }
        return origin;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SCAN_INTERVAL != 0) {
            return;
        }
        tickItems(player);
        tickStory(player);
    }

    /** Passive powers of carried trinkets. Works in any story state. */
    public static void tickItems(ServerPlayer player) {
        RoleAbilities.apply(player);
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (ItemStack stack : PlayerItems.carried(player)) {
            net.minecraft.world.item.Item item = stack.getItem();
            if (!seen.add(item)) {
                continue;
            }
            if (item instanceof com.arnir.storymod.item.ArtifactItem artifact) {
                artifact.pulse(player);
            } else if (item instanceof com.arnir.storymod.item.WhitePearlItem) {
                com.arnir.storymod.item.WhitePearlItem.pulse(player);
            } else if (item instanceof com.arnir.storymod.item.TwilightAmuletItem) {
                com.arnir.storymod.item.TwilightAmuletItem.pulse(player);
            } else if (item instanceof com.arnir.storymod.item.BoundMarkItem mark) {
                mark.pulse(player);
            } else if (item instanceof com.arnir.storymod.item.RoleWeaponItem weapon) {
                weapon.pulse(player);
            } else if (item instanceof com.arnir.storymod.item.KeeperWrenchItem) {
                com.arnir.storymod.item.KeeperWrenchItem.pulse(player);
            }
        }
    }

    /** Everything the story checks about a player every two seconds. */
    public static void tickStory(ServerPlayer player) {
        StoryState state = StoryState.get(player.getServer());
        if (!state.has("intro_spawned") || state.has("ending")) {
            return;
        }
        scanInventory(player, state);
        checkAppearances(player, state);
        com.arnir.storymod.dialogue.TriggerManager.check(player, state);
    }

    private static void scanInventory(ServerPlayer player, StoryState state) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (String core : ModItems.CORE_IDS) {
                if (stack.is(Inventories.item(StoryMod.MODID + ":" + core)) && !state.has("found_" + core)) {
                    state.set("found_" + core);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                            SoundSource.PLAYERS, 1.0F, 0.6F);
                    broadcast(player.getServer(), "Где-то далеко отозвался низкий гул: " + player.getGameProfile().getName()
                            + " держит в руках то, что не должно было найтись.");
                }
            }
            String page = Inventories.pageId(stack);
            if (page != null && !state.has("page_" + page)) {
                state.set("page_" + page);
                player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                        SoundSource.PLAYERS, 1.0F, 0.8F);
            }
        }
    }

    public static int coresFound(StoryState state) {
        int n = 0;
        for (String core : ModItems.CORE_IDS) {
            if (state.has("found_" + core)) {
                n++;
            }
        }
        return n;
    }

    private static void checkAppearances(ServerPlayer player, StoryState state) {
        ServerLevel level = player.serverLevel();
        int cores = coresFound(state);
        int pages = state.countWithPrefix("page_");

        if (cores >= 1 && !state.has("ash_spawn_1")) {
            state.set("ash_spawn_1");
            appear(level, player, "ash", "Воздух вокруг становится холодным. С неба, хотя оно чистое, падает пепел.");
        } else if (state.has("ash_met_1") && !state.has("ash_spawn_2") && (cores >= 2 || pages >= 4)
                && level.dimension() == Level.OVERWORLD && level.isNight()) {
            state.set("ash_spawn_2");
            appear(level, player, "ash", "Пепел снова кружится в воздухе. Она вернулась.");
        }

        // If a meeting was missed (the player walked away), she finds them again later.
        boolean missedFirst = state.has("ash_spawn_1") && !state.has("ash_met_1");
        boolean missedSecond = state.has("ash_spawn_2") && !state.has("ash_met_2");
        if ((missedFirst || missedSecond) && level.random.nextInt(600) == 0) {
            appear(level, player, "ash", "Ты снова чувствуешь запах пепла. Кто-то стоит совсем рядом.");
        }

        if (cores >= ModItems.CORE_IDS.size() && state.has("void_awakened") && !state.has("finale_ready")) {
            state.set("finale_ready");
            broadcast(player.getServer(), "Земля под ногами вздрагивает. Там, где начинается мир, кто-то ждёт тебя.");
        }
        BlockPos anchor = state.anchor();
        // The seal needs a few days to gather strength after the second meeting before she can come.
        if (state.has("finale_ready") && state.daysSince("ash_met_2") >= 3 && !state.has("ash_at_anchor") && anchor != null) {
            state.set("ash_at_anchor");
            StoryNpc.spawn(player.getServer().overworld(), "ash", anchor.offset(-3, 0, 1), -90.0F);
        }
    }

    private static void appear(ServerLevel level, ServerPlayer player, String character, String line) {
        BlockPos pos = groundNear(level, player.blockPosition(), 5);
        StoryNpc.spawn(level, character, pos, 0.0F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 1.0,
                pos.getZ() + 0.5, 120, 1.0, 1.2, 1.0, 0.02);
        player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static final List<List<String>> AMBIENT = List.of(
            List.of("Солнце сегодня кажется чуть бледнее, чем вчера.",
                    "Где-то далеко звонит колокол, хотя колоколов поблизости нет.",
                    "На миг тебе кажется, что тени двигаются чуть позже, чем ты."),
            List.of("Тени стали длиннее, чем должны быть в этот час.",
                    "Ветер приносит запах пепла.",
                    "Цветы у дороги закрылись посреди дня."),
            List.of("Птицы замолчали. Даже ветер говорит шёпотом.",
                    "Солнце мигнуло, как свеча на сквозняке.",
                    "Свет ложится на землю тонко, будто его стало меньше."),
            List.of("Свет стал серым и тяжёлым. Мир ждёт.",
                    "С ясного неба падает пепел.",
                    "В полдень видно звёзды. Это неправильно."),
            List.of("Всё замерло. Решение за тобой.",
                    "Мир задержал дыхание."));

    @SubscribeEvent
    public static void onServerTickMerchant(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 == 0) {
            updateMerchant(event.getServer());
        }
    }

    /** True on the days and hours the travelling merchant stands at the circle: every third day, morning to dusk. */
    public static boolean merchantHours(long dayTime) {
        long day = dayTime / 24000L;
        long time = dayTime % 24000L;
        return day % 3 == 0 && time >= 0 && time < 11500;
    }

    /** Brings the travelling merchant to the stone circle on her days and sends her away afterwards. */
    public static void updateMerchant(MinecraftServer server) {
        StoryState state = StoryState.get(server);
        BlockPos anchor = state.anchor();
        if (anchor == null || !state.has("veld_met")) {
            return;
        }
        boolean open = merchantHours(server.overworld().getDayTime());
        boolean here = state.has("mira_here");
        if (open && !here) {
            state.set("mira_here");
            ServerLevel overworld = server.overworld();
            BlockPos pos = groundNear(overworld, anchor.offset(6, 0, -5), 1);
            StoryNpc.spawn(overworld, "mira", pos, 225.0F);
            if (!state.has("mira_announced")) {
                state.set("mira_announced");
                broadcast(server, "К старому кругу камней свернула повозка. Похоже, у вас гостья.");
            }
        } else if (!open && here) {
            state.clear("mira_here");
            state.bumpGeneration("mira");
            for (StoryNpc npc : server.overworld().getEntities(com.arnir.storymod.registry.ModEntities.STORY_NPC.get(),
                    e -> e.getCharacter().equals("mira"))) {
                npc.discard();
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % AMBIENT_INTERVAL != 0 || server.getPlayerCount() == 0) {
            return;
        }
        StoryState state = StoryState.get(server);
        if (!state.has("veld_met") || state.has("ending")) {
            return;
        }
        List<String> lines = AMBIENT.get(Math.min(coresFound(state), AMBIENT.size() - 1));
        broadcast(server, lines.get(server.overworld().random.nextInt(lines.size())));
    }

    /** Ringing a bell at the old stone circle at dawn is remembered by the story. */
    @SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().getBlockState(event.getPos()).is(Blocks.BELL)) {
            return;
        }
        StoryState state = StoryState.get(player.getServer());
        BlockPos anchor = state.anchor();
        long time = player.getServer().overworld().getDayTime() % 24000L;
        boolean dawn = time >= 22500L || time <= 500L;
        if (anchor != null && dawn && player.level().dimension() == Level.OVERWORLD
                && event.getPos().distSqr(anchor) <= 12 * 12 && state.has("veld_met")) {
            state.set("bell_rung_dawn");
        }
    }

    /** Endermen do not take offence at the wanderer's gaze. */
    @SubscribeEvent
    public static void onEndermanAnger(net.neoforged.neoforge.event.entity.living.EnderManAngerEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && StoryState.get(player.getServer()).role(player.getUUID()).equals(PlayerRoles.WANDERER)) {
            event.setCanceled(true);
        }
    }

    /** The white pearl lets its owner slip away from projectiles. */
    @SubscribeEvent
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (event.getEntity() instanceof ServerPlayer player) {
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) && com.arnir.storymod.item.WhitePearlItem.tryDodge(player)) {
                event.setCanceled(true);
                return;
            }
            if (RoleAbilities.tryCounter(player, source)) {
                event.setCanceled(true);
                return;
            }
        }
        // Someone else's personal weapon hits weakly.
        if (source.getEntity() != null && source.getEntity() == source.getDirectEntity()) {
            event.setAmount(com.arnir.storymod.item.RoleWeaponItem.scaleDamage(source.getEntity(), event.getAmount()));
        }
    }

    /** Personal things survive death; lowest priority so worn trinkets are already in the drops. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PersonalItems.keepFromDrops(player, event.getDrops());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PersonalItems.restore(player);
        }
    }

    /** Bound things cannot be thrown away: they come straight back. */
    @SubscribeEvent
    public static void onToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (PersonalItems.isBound(stack) && event.getPlayer() instanceof ServerPlayer player) {
            event.setCanceled(true);
            if (!player.getInventory().add(stack.copy())) {
                player.getInventory().placeItemBackInInventory(stack.copy());
            }
            player.displayClientMessage(Component.literal("Это не отпускает тебя. Или ты не отпускаешь это.")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
        }
    }

    /** Thrown ender pearls do not hurt the white pearl's owner. */
    @SubscribeEvent
    public static void onPearlTeleport(net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderPearl event) {
        if (com.arnir.storymod.item.WhitePearlItem.protects(event.getPlayer())) {
            event.setAttackDamage(0.0F);
        }
    }

    public static void onBossDefeated(ServerLevel level, StoryNpc boss) {
        StoryState state = StoryState.get(level.getServer());
        state.set("boss_defeated");
        state.bumpGeneration(boss.getCharacter());
        broadcast(level.getServer(), "Пламя гаснет. На том месте, где он стоял, остался только тёплый пепел.");
        StoryNpc.spawn(level, "ash", groundNear(level, boss.blockPosition(), 3), 0.0F);
    }

    public static void broadcast(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
    }
}
