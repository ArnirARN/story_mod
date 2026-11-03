package com.arnir.storymod.story;

import com.arnir.storymod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;
import java.util.Map;

/**
 * Personal story roles. Players get them by nickname on first login; admins can reassign with /story role.
 */
public final class PlayerRoles {
    public static final String WANDERER = "wanderer";
    public static final String KEEPER = "keeper";

    /** Default nickname → role. */
    private static final Map<String, String> DEFAULTS = Map.of(
            "arnira", WANDERER,
            "sserutto", KEEPER);

    private PlayerRoles() {
    }

    public static void onLogin(ServerPlayer player, StoryState state) {
        if (state.role(player.getUUID()).isEmpty()) {
            String role = DEFAULTS.get(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
            if (role != null && !state.roleTaken(role)) {
                state.setRole(player.getUUID(), role);
            }
        }
        if (!state.hasPersonal(player.getUUID(), "journal_given")) {
            state.setPersonal(player.getUUID(), "journal_given");
            ItemStack journal = new ItemStack(ModItems.JOURNAL.get());
            if (!player.getInventory().add(journal)) {
                player.drop(journal, false);
            }
        }
        if (!state.hasPersonal(player.getUUID(), "starter_kit")) {
            state.setPersonal(player.getUUID(), "starter_kit");
            state.setPersonal(player.getUUID(), "white_pearl_given");
            state.setPersonal(player.getUUID(), "role_weapon_given");
            giveStarterKit(player, state.role(player.getUUID()));
        }
        if (WANDERER.equals(state.role(player.getUUID())) && !state.hasPersonal(player.getUUID(), "white_pearl_given")) {
            state.setPersonal(player.getUUID(), "white_pearl_given");
            swapAmuletForPearl(player);
        }
        String role = state.role(player.getUUID());
        if ((WANDERER.equals(role) || KEEPER.equals(role)) && !state.hasPersonal(player.getUUID(), "role_weapon_given")) {
            state.setPersonal(player.getUUID(), "role_weapon_given");
            ItemStack weapon = roleWeapon(role);
            if (!player.getInventory().add(weapon)) {
                player.drop(weapon, false);
            }
            player.sendSystemMessage(Component.literal(WANDERER.equals(role)
                            ? "Рядом лежит клинок, слишком длинный для одной руки. Но рука помнит его вес, как свой."
                            : "Рядом лежит молот с медной оковкой. Рукоять стёрта ровно под твою ладонь.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (WANDERER.equals(state.role(player.getUUID())) && !state.hasPersonal(player.getUUID(), "wanderer_arrived")) {
            state.setPersonal(player.getUUID(), "wanderer_arrived");
            arriveWanderer(player);
        }
    }

    /** Personal starting things. Also handed out once to players who joined before kits existed. */
    public static void giveStarterKit(ServerPlayer player, String role) {
        java.util.List<ItemStack> kit = new java.util.ArrayList<>();
        switch (role) {
            case WANDERER -> {
                kit.add(new ItemStack(ModItems.WHITE_PEARL.get()));
                kit.add(roleWeapon(WANDERER));
                kit.add(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 4));
                kit.add(new ItemStack(net.minecraft.world.item.Items.COOKED_MUTTON, 12));
                kit.add(note(player, "storymod:notes/start_wanderer"));
            }
            case KEEPER -> {
                kit.add(new ItemStack(ModItems.KEEPER_WRENCH.get()));
                kit.add(roleWeapon(KEEPER));
                kit.add(new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 12));
                kit.add(new ItemStack(net.minecraft.world.item.Items.BREAD, 12));
                kit.add(note(player, "storymod:notes/start_keeper"));
            }
            default -> {
                kit.add(new ItemStack(net.minecraft.world.item.Items.BREAD, 8));
                kit.add(new ItemStack(net.minecraft.world.item.Items.TORCH, 16));
            }
        }
        for (ItemStack stack : kit) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    public static ItemStack roleWeapon(String role) {
        return new ItemStack(WANDERER.equals(role) ? ModItems.WHITE_GREATSWORD.get() : ModItems.KEEPER_HAMMER.get());
    }

    /** Players who started with the old amulet get the white pearl instead. */
    public static void swapAmuletForPearl(ServerPlayer player) {
        var inv = player.getInventory();
        boolean replaced = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.TWILIGHT_AMULET.get())) {
                inv.setItem(i, replaced ? ItemStack.EMPTY : new ItemStack(ModItems.WHITE_PEARL.get()));
                replaced = true;
            }
        }
        if (!replaced) {
            ItemStack pearl = new ItemStack(ModItems.WHITE_PEARL.get());
            if (!inv.add(pearl)) {
                player.drop(pearl, false);
            }
        }
        ItemStack note = note(player, "storymod:notes/start_wanderer");
        if (!note.isEmpty() && !inv.add(note)) {
            player.drop(note, false);
        }
        player.sendSystemMessage(Component.literal(replaced
                        ? "Амулет осыпается тёмной пылью. На ладони остаётся что-то белое, гладкое и тёплое."
                        : "В кармане что-то белое, гладкое и тёплое. Раньше его там не было.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static ItemStack note(ServerPlayer player, String table) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                net.minecraft.resources.ResourceLocation.parse(table));
        var items = level.getServer().reloadableRegistries().getLootTable(key).getRandomItems(
                new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY));
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0);
    }

    /** The wanderer does not arrive where everyone else does. */
    private static void arriveWanderer(ServerPlayer player) {
        ServerLevel level = player.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos center = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(-36, 0, 28));
        BlockPos floor = center.below();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) {
                    continue;
                }
                level.setBlockAndUpdate(floor.offset(dx, 0, dz), (dx + dz) % 2 == 0
                        ? Blocks.END_STONE_BRICKS.defaultBlockState() : Blocks.END_STONE.defaultBlockState());
                for (int y = 1; y <= 3; y++) {
                    BlockPos air = floor.offset(dx, y, dz);
                    if (!level.getBlockState(air).isAir() && level.getBlockState(air).canBeReplaced()) {
                        level.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        int[][] pillars = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int i = 0; i < pillars.length; i++) {
            int height = 2 + i % 2;
            for (int y = 0; y <= height; y++) {
                BlockPos p = floor.offset(pillars[i][0], y, pillars[i][1]);
                level.setBlockAndUpdate(p, y == height ? Blocks.END_ROD.defaultBlockState() : Blocks.PURPUR_PILLAR.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(floor.offset(1, 1, 2), Blocks.CHORUS_FLOWER.defaultBlockState());
        level.setBlockAndUpdate(floor, Blocks.CRYING_OBSIDIAN.defaultBlockState());

        player.teleportTo(level, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 180.0F, 10.0F);
        player.setRespawnPosition(Level.OVERWORLD, center, 180.0F, true, false);
        player.sendSystemMessage(Component.literal(
                        "Ты открываешь глаза. Свет здесь слишком яркий, а тишина слишком знакомая. Дорогу сюда память не сохранила.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
