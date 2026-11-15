package com.arnir.storymod.test;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.dialogue.DialogueManager;
import com.arnir.storymod.dialogue.DialogueSessions;
import com.arnir.storymod.dialogue.Inventories;
import com.arnir.storymod.dialogue.JournalManager;
import com.arnir.storymod.dialogue.TriggerManager;
import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.registry.ModItems;
import com.arnir.storymod.story.PlayerRoles;
import com.arnir.storymod.story.StoryState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game tests that walk through the story with mock players. Run with ./gradlew runGameTestServer.
 * Every test uses its own batch so they run one after another (the story state is shared by the server).
 */
@GameTestHolder(StoryMod.MODID)
@PrefixGameTestTemplate(false)
public final class StoryGameTests {
    private static final String T = "empty";

    private StoryGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static void fail(String message) {
        throw new GameTestAssertException(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    private static StoryState freshState(GameTestHelper helper) {
        StoryState state = StoryState.get(helper.getLevel().getServer());
        // Players left behind by a failed test must not leak into the next one.
        var players = helper.getLevel().getServer().getPlayerList();
        for (ServerPlayer old : List.copyOf(players.getPlayers())) {
            DialogueSessions.forget(old);
            players.remove(old);
        }
        for (StoryNpc npc : helper.getLevel().getEntities(com.arnir.storymod.registry.ModEntities.STORY_NPC.get(), e -> true)) {
            npc.discard();
        }
        state.reset();
        state.set("intro_spawned");
        state.setAnchor(helper.absolutePos(new BlockPos(2, 1, 2)));
        return state;
    }

    private static ServerPlayer player(GameTestHelper helper, String role) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 1, 3.5));
        p.moveTo(pos.x, pos.y, pos.z, 0, 0);
        p.getInventory().clearContent();
        StoryState.get(helper.getLevel().getServer()).setRole(p.getUUID(), role);
        return p;
    }

    private static void leave(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) {
            DialogueSessions.forget(p);
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    private static StoryNpc npc(GameTestHelper helper, String character) {
        StoryNpc npc = StoryNpc.spawn(helper.getLevel(), character, helper.absolutePos(new BlockPos(2, 1, 1)), 0);
        check(npc != null, "could not spawn " + character);
        return npc;
    }

    private static String talk(ServerPlayer p, StoryNpc npc) {
        DialogueSessions.start(p, npc);
        DialogueSessions.View v = DialogueSessions.view(p);
        check(v != null, "no dialogue opened with " + npc.getCharacter());
        return v.node();
    }

    /** Picks the visible option whose label contains {@code text}; returns the new node or null if closed. */
    private static String say(ServerPlayer p, String text) {
        DialogueSessions.View v = DialogueSessions.view(p);
        check(v != null, "not in a dialogue when trying to say '" + text + "'");
        for (int i = 0; i < v.labels().size(); i++) {
            if (v.labels().get(i).contains(text)) {
                DialogueSessions.choose(p, v.npcId(), v.ids().get(i));
                DialogueSessions.View after = DialogueSessions.view(p);
                return after == null ? null : after.node();
            }
        }
        fail("no option '" + text + "' at " + v.character() + "/" + v.node() + ", visible: " + v.labels());
        return null;
    }

    private static boolean hasOption(ServerPlayer p, String text) {
        DialogueSessions.View v = DialogueSessions.view(p);
        return v != null && v.labels().stream().anyMatch(l -> l.contains(text));
    }

    private static void expectNode(String actual, String expected) {
        check(expected.equals(actual), "expected node " + expected + " but was " + actual);
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        p.getInventory().add(stack);
    }

    private static void giveCores(ServerPlayer p) {
        give(p, new ItemStack(ModItems.CORE_DEPTHS.get()));
        give(p, new ItemStack(ModItems.CORE_FLAME.get()));
        give(p, new ItemStack(ModItems.CORE_TIDES.get()));
        give(p, new ItemStack(ModItems.CORE_VOID.get()));
    }

    private static ItemStack page(String id) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = new CompoundTag();
        tag.putString(Inventories.PAGE_KEY, id);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return book;
    }

    private static int count(ServerPlayer p, String item) {
        return Inventories.count(p, item);
    }

    // ------------------------------------------------------------------ data

    @GameTest(template = T, batch = "story_01")
    public static void dataLoads(GameTestHelper helper) {
        for (String c : com.arnir.storymod.command.StoryCommand.CHARACTERS) {
            check(DialogueManager.get(c) != null, "dialogue missing: " + c);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_02", timeoutTicks = 200)
    public static void lootInjections(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        checkLoot(level, "chests/stronghold_library", s -> "letter".equals(Inventories.pageId(s)), "letter");
        checkLoot(level, "chests/ancient_city", s -> s.is(ModItems.CORE_DEPTHS.get()), "core_depths");
        checkLoot(level, "chests/bastion_treasure", s -> s.is(ModItems.CORE_FLAME.get()), "core_flame");
        checkLoot(level, "chests/end_city_treasure", s -> s.is(ModItems.CORE_VOID.get()), "core_void");
        checkLoot(level, "chests/desert_pyramid", s -> "founding".equals(Inventories.pageId(s)), "founding page");
        // Written books must actually have pages and a title.
        checkLoot(level, "chests/jungle_temple", s -> "roster".equals(Inventories.pageId(s))
                && s.get(DataComponents.WRITTEN_BOOK_CONTENT) != null
                && !s.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().isEmpty(), "roster book with pages");
        helper.succeed();
    }

    private static void checkLoot(ServerLevel level, String table, java.util.function.Predicate<ItemStack> wanted, String what) {
        LootTable loot = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(table)));
        for (int i = 0; i < 400; i++) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .create(LootContextParamSets.CHEST);
            for (ItemStack s : loot.getRandomItems(params)) {
                if (wanted.test(s)) {
                    return;
                }
            }
        }
        fail(what + " never dropped from " + table + " in 400 rolls");
    }

    // ------------------------------------------------------------------ opening

    @GameTest(template = T, batch = "story_03")
    public static void introAndHints(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        StoryNpc veld = npc(helper, "veld");
        expectNode(talk(p, veld), "intro");
        expectNode(say(p, "Что такое Угасание"), "intro_fading");
        expectNode(say(p, "Сердечники нужно собрать"), "intro_task");
        say(p, "Я найду их");
        check(state.has("veld_met"), "veld_met not set");
        expectNode(talk(p, veld), "hub");
        check(!hasOption(p, "Кто такая Лира"), "Lira option visible too early");
        say(p, "Мне пора");

        StoryNpc os = npc(helper, "os");
        expectNode(talk(p, os), "intro");
        expectNode(say(p, "Где искать"), "hints");
        check(hasOption(p, "Сердечник Глубин"), "depths hint missing");
        check(!hasOption(p, "Сердечник Пустоты"), "void hint should need repair");
        expectNode(say(p, "А остальные"), "h_damaged");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_04")
    public static void journalShowsPersonalGoals(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        state.set("veld_met");
        state.set("os_met");
        String wPages = String.join("|", JournalManager.pagesFor(w));
        String kPages = String.join("|", JournalManager.pagesFor(k));
        check(wPages.contains("Сон"), "wanderer journal lacks dream: " + wPages);
        check(!kPages.contains("Сон"), "keeper sees wanderer's dream");
        check(kPages.contains("Ось"), "keeper journal lacks repair goal: " + kPages);
        check(wPages.contains("0 из 4"), "core counter missing: " + wPages);
        leave(helper, w, k);
        helper.succeed();
    }

    // ------------------------------------------------------------------ keeper line

    @GameTest(template = T, batch = "story_05")
    public static void keeperRepairsOsAfterTwoNights(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        ServerPlayer other = player(helper, "");
        state.set("veld_met");
        state.set("os_met");
        StoryNpc os = npc(helper, "os");

        talk(other, os);
        check(!hasOption(other, "Дай посмотрю"), "non-keeper can repair Os");
        say(other, "Ничего");

        talk(k, os);
        expectNode(say(k, "Дай посмотрю"), "repair1");
        check(!hasOption(k, "Вот всё"), "repair accepted without parts");
        say(k, "Соберу");
        give(k, new ItemStack(Items.COPPER_INGOT, 16));
        give(k, new ItemStack(Items.REDSTONE, 8));
        give(k, new ItemStack(Items.AMETHYST_SHARD, 4));
        give(k, new ItemStack(Items.CLOCK));
        talk(k, os);
        say(k, "Дай посмотрю");
        expectNode(say(k, "Вот всё"), "repair2");
        check(state.has("os_repair_started"), "repair not started");
        check(count(k, "minecraft:copper_ingot") == 0, "copper not taken");

        helper.getLevel().setDayTime(14000);
        expectNode(talk(k, os), "rebooting");
        state.backdate("os_repair_started", 24000L * 2);
        helper.getLevel().setDayTime(6000);
        expectNode(talk(k, os), "rebooting");
        helper.getLevel().setDayTime(24000L * 2 + 14000);
        expectNode(talk(other, os), "rebooting");
        expectNode(talk(k, os), "reboot_done");
        say(k, "Рада");
        check(state.has("os_repaired"), "os not repaired");
        check(count(k, "storymod:keeper_gear") == 1, "keeper gear not given");
        talk(k, os);
        say(k, "Где искать");
        check(hasOption(k, "Сердечник Пустоты"), "void hint still hidden after repair");
        leave(helper, k, other);
        helper.succeed();
    }

    // ------------------------------------------------------------------ wanderer line

    @GameTest(template = T, batch = "story_06")
    public static void wandererEyeAndVoidAwakening(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer other = player(helper, "");
        state.set("veld_met");
        // Full moon midnight, but too early: nothing happens.
        helper.getLevel().setDayTime(18000);
        TriggerManager.check(w, state);
        check(count(w, "storymod:white_eye") == 0, "eye given before three days passed");
        state.backdate("veld_met", 24000L * 3);
        // Wrong moon phase.
        helper.getLevel().setDayTime(24000L + 18000);
        TriggerManager.check(w, state);
        check(count(w, "storymod:white_eye") == 0, "eye given without full moon");
        // Full moon, midnight, at the circle.
        helper.getLevel().setDayTime(24000L * 8 + 18000);
        TriggerManager.check(other, state);
        check(count(other, "storymod:white_eye") == 0, "eye given to the wrong player");
        TriggerManager.check(w, state);
        check(count(w, "storymod:white_eye") == 1, "eye not given");
        TriggerManager.check(w, state);
        check(count(w, "storymod:white_eye") == 1, "eye given twice");

        give(w, new ItemStack(ModItems.CORE_VOID.get()));
        helper.getLevel().setDayTime(24000L * 9 + 6000);
        TriggerManager.check(w, state);
        check(!state.has("void_awakened"), "void awakened at noon");
        helper.getLevel().setDayTime(24000L * 9 + 18000);
        TriggerManager.check(w, state);
        check(state.has("void_awakened"), "void did not awaken");
        leave(helper, w, other);
        helper.succeed();
    }

    // ------------------------------------------------------------------ Ash

    @GameTest(template = T, batch = "story_07")
    public static void ashMeetings(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        state.set("veld_met");
        StoryNpc ash = npc(helper, "ash");
        expectNode(talk(w, ash), "meet1");
        expectNode(say(w, "будто знаешь"), "meet1_w");
        expectNode(say(w, "Кому"), "meet1_veld");
        expectNode(say(w, "Хорошо"), "meet1_end");
        check(state.has("ash_met_1"), "ash_met_1 not set");
        check(Inventories.carriesPage(w, "ash_note"), "ash note not given");
        check(ash.isRemoved(), "ash did not vanish");

        state.set("ash_spawn_2");
        StoryNpc ash2 = npc(helper, "ash");
        expectNode(talk(w, ash2), "meet2");
        say(w, "Кто построил");
        say(w, "Кто его разбил");
        say(w, "Почему Угасание");
        expectNode(say(w, "Что тогда делать"), "meet2_what");
        check(state.has("ash_met_2"), "ash_met_2 not set");
        leave(helper, w);
        helper.succeed();
    }

    // ------------------------------------------------------------------ endings

    private static void finaleState(StoryState state) {
        for (String f : List.of("veld_met", "os_met", "os_repaired", "ash_met_1", "ash_met_2", "void_awakened",
                "found_core_depths", "found_core_flame", "found_core_tides", "found_core_void", "finale_ready")) {
            state.set(f);
        }
    }

    @GameTest(template = T, batch = "story_08")
    public static void dawnEndingNeedsBothPlayers(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        finaleState(state);
        state.set("os_memory");
        state.set("ash_at_anchor");
        giveCores(w);
        give(w, page("letter"));
        StoryNpc veld = npc(helper, "veld");
        npc(helper, "ash");
        expectNode(talk(w, veld), "finale");
        expectNode(say(w, "Письмо от Лиры"), "letter1");
        say(w, "Она здесь");
        say(w, "…");
        say(w, "другой путь");
        expectNode(say(w, "…"), "letter5");
        check(!hasOption(w, "Возьмите"), "dawn ending possible without the keeper");
        say(w, "позову");
        check(!state.has("ending"), "ending set too early");

        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        expectNode(talk(w, veld), "finale");
        say(w, "Письмо от Лиры");
        say(w, "Она здесь");
        say(w, "…");
        say(w, "другой путь");
        say(w, "…");
        check(say(w, "Возьмите") == null, "dialogue should close after the ending");
        check(state.has("ending_dawn") && state.has("ending"), "dawn ending flags missing");
        check(count(w, "storymod:core_void") == 0, "cores not taken");
        check(count(w, "storymod:dawn_star") == 1 && count(k, "storymod:dawn_star") == 1, "dawn star not given to both");
        check(veld.isRemoved(), "veld still here after dawn ending");
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_09")
    public static void sealEnding(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, PlayerRoles.KEEPER);
        finaleState(state);
        state.set("ash_at_anchor");
        StoryNpc veld = npc(helper, "veld");
        StoryNpc ash = npc(helper, "ash");
        expectNode(talk(p, ash), "final");
        check(!hasOption(p, "Запечатай"), "seal possible without cores");
        say(p, "подумать");
        giveCores(p);
        talk(p, ash);
        expectNode(say(p, "Запечатай"), "seal");
        check(state.has("ending_seal"), "seal ending flag missing");
        check(veld.isRemoved(), "veld still here after seal ending");
        check(ash.isRemoved(), "ash still here after seal ending");
        check(count(p, "storymod:dawn_star") == 1, "no reward for seal ending");
        StoryNpc os = npc(helper, "os");
        expectNode(talk(p, os), "epi_seal");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_10")
    public static void bossEnding(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        finaleState(state);
        giveCores(p);
        StoryNpc veld = npc(helper, "veld");
        expectNode(talk(p, veld), "finale");
        expectNode(say(p, "Бери"), "betrayal");
        check(count(p, "storymod:core_flame") == 0, "cores not taken by veld");
        // Closing the window here must not lose the betrayal.
        DialogueSessions.forget(p);
        expectNode(talk(p, veld), "betrayal");
        say(p, "остановись");
        check(veld.isHostile(), "veld did not become a boss");
        check(veld.getHealth() > 100, "boss health too low");
        veld.kill();
        check(state.has("boss_defeated"), "boss_defeated not set");
        List<StoryNpc> ashes = new ArrayList<>(helper.getLevel().getEntities(
                com.arnir.storymod.registry.ModEntities.STORY_NPC.get(), e -> e.getCharacter().equals("ash") && !e.isRemoved()));
        check(ashes.size() == 1, "expected ash after the boss, found " + ashes.size());
        expectNode(talk(p, ashes.get(0)), "after_boss");
        say(p, "Прощай");
        check(state.has("ending_fight"), "fight ending flag missing");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_11")
    public static void refusingVeldKeepsOtherPathsOpen(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        finaleState(state);
        giveCores(p);
        StoryNpc veld = npc(helper, "veld");
        talk(p, veld);
        say(p, "Нет. Я не отдам");
        check(state.has("veld_refused"), "refusal not remembered");
        expectNode(talk(p, veld), "refused");
        expectNode(say(p, "поговорим ещё раз"), "finale");
        check(!hasOption(p, "Нет. Я не отдам"), "refuse offered twice");
        check(count(p, "storymod:core_tides") == 1, "cores lost after refusing");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_12")
    public static void voidMustAwakenBeforeFinale(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        state.set("veld_met");
        for (String c : ModItems.CORE_IDS) {
            state.set("found_" + c);
        }
        StoryNpc veld = npc(helper, "veld");
        expectNode(talk(p, veld), "void_sleeps");
        leave(helper, p);
        helper.succeed();
    }

    // ------------------------------------------------------------------ side quests

    @GameTest(template = T, batch = "story_13")
    public static void bellSummonsIrmenAndReadingQuest(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, PlayerRoles.KEEPER);
        state.set("veld_met");
        TriggerManager.check(p, state);
        check(findNpc(helper, "irmen") == null, "irmen came without the bell");
        state.set("bell_rung_dawn");
        TriggerManager.check(p, state);
        StoryNpc irmen = findNpc(helper, "irmen");
        check(irmen != null, "irmen did not come after the bell");
        expectNode(talk(p, irmen), "first");
        say(p, "Ты призрак");
        expectNode(say(p, "велели не записывать"), "secret");
        expectNode(say(p, "Хорошо"), "hub");
        check(state.has("irmen_met"), "irmen_met not set");
        check(!hasOption(p, "набралось достаточно"), "reward offered without five notes");
        check(hasOption(p, "Сколько записей"), "no progress option");
        check(!hasOption(p, "руки как у"), "keeper option shown before Os is repaired");
        for (String id : List.of("founding", "roster", "forge_truth", "breaking", "seal")) {
            state.set("page_" + id);
        }
        talk(p, irmen);
        expectNode(say(p, "набралось достаточно"), "reward");
        check(Inventories.carriesPage(p, "irmen_testament"), "testament not given");
        check(count(p, "minecraft:enchanted_book") == 1, "reward book not given");
        talk(p, irmen);
        check(!hasOption(p, "набралось достаточно"), "reward offered twice");
        leave(helper, p);
        helper.succeed();
    }

    private static StoryNpc findNpc(GameTestHelper helper, String character) {
        for (StoryNpc npc : helper.getLevel().getEntities(com.arnir.storymod.registry.ModEntities.STORY_NPC.get(),
                e -> e.getCharacter().equals(character) && !e.isRemoved())) {
            return npc;
        }
        return null;
    }

    @GameTest(template = T, batch = "story_14")
    public static void keeperLampTakesANight(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        state.set("veld_met");
        state.set("os_met");
        state.set("os_repaired");
        StoryNpc os = npc(helper, "os");
        talk(w, os);
        check(!hasOption(w, "помочь с поисками"), "wanderer can start the keeper lamp");
        say(w, "Ничего");
        give(k, new ItemStack(Items.ECHO_SHARD, 2));
        give(k, new ItemStack(Items.PRISMARINE_CRYSTALS, 8));
        give(k, new ItemStack(Items.GLOWSTONE_DUST, 4));
        talk(k, os);
        expectNode(say(k, "помочь с поисками"), "lamp1");
        expectNode(say(k, "Вот детали"), "lamp2");
        check(count(k, "minecraft:echo_shard") == 0, "lamp parts not taken");
        helper.getLevel().setDayTime(15000);
        expectNode(talk(k, os), "hub");
        expectNode(say(k, "Как там лампа"), "lamp_wait");
        state.backdate("os_lamp_started", 24000L);
        helper.getLevel().setDayTime(24000L + 15000);
        expectNode(talk(w, os), "hub");
        say(w, "Ничего");
        expectNode(talk(k, os), "lamp_done");
        expectNode(say(k, "Покажи"), "lamp_menu");
        check(state.has("os_lamp_ready"), "lamp not ready");
        talk(w, os);
        expectNode(say(w, "Покажи дорогу"), "lamp_menu");
        leave(helper, k, w);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_15")
    public static void eyeAwakensWithEndOfferings(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        state.set("veld_met");
        state.set("eye_given");
        give(w, new ItemStack(ModItems.WHITE_EYE.get()));
        give(w, new ItemStack(Items.ENDER_PEARL, 16));
        give(w, new ItemStack(Items.CHORUS_FRUIT, 16));
        helper.getLevel().setDayTime(18000);
        TriggerManager.check(w, state);
        check(!state.hasPersonal(w.getUUID(), "eye_awakened"), "eye awakened before visiting the End");
        state.setPersonal(w.getUUID(), "been_end");
        TriggerManager.check(w, state);
        check(state.hasPersonal(w.getUUID(), "eye_awakened"), "eye did not awaken");
        check(count(w, "minecraft:ender_pearl") == 0 && count(w, "minecraft:chorus_fruit") == 0, "offerings not taken");
        check(count(w, "storymod:white_eye") == 1, "eye lost");
        leave(helper, w);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_16")
    public static void lostItemsCanBeRecovered(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        state.set("veld_met");
        state.set("os_met");
        state.set("eye_given");
        StoryNpc os = npc(helper, "os");
        talk(w, os);
        say(w, "Дневник куда-то пропал");
        check(count(w, "storymod:journal") == 1, "journal not returned");
        talk(w, os);
        check(!hasOption(w, "Дневник куда-то пропал"), "journal offered while carried");
        say(w, "Око пропало");
        check(count(w, "storymod:white_eye") == 1, "eye not returned");
        talk(w, os);
        check(!hasOption(w, "Око пропало"), "eye offered while carried");
        leave(helper, w);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_17")
    public static void everyDialogueOpensForEveryRole(GameTestHelper helper) {
        // Smoke test: starting a conversation in many story states never throws and always shows something.
        for (String role : List.of("", PlayerRoles.WANDERER, PlayerRoles.KEEPER)) {
            StoryState state = freshState(helper);
            ServerPlayer p = player(helper, role);
            List<List<String>> stages = List.of(
                    List.of(),
                    List.of("veld_met", "os_met"),
                    List.of("veld_met", "os_met", "ash_met_1", "page_roster", "page_forge_truth", "page_obsession"),
                    List.of("veld_met", "os_met", "ash_met_1", "ash_met_2", "os_repaired", "os_memory", "irmen_met"),
                    List.of("veld_met", "finale_ready", "ash_met_2", "void_awakened"),
                    List.of("ending", "ending_dawn"),
                    List.of("ending", "ending_seal"),
                    List.of("ending", "ending_fight"));
            for (List<String> stage : stages) {
                state.reset();
                state.set("intro_spawned");
                stage.forEach(state::set);
                for (String c : com.arnir.storymod.command.StoryCommand.CHARACTERS) {
                    StoryNpc npc = npc(helper, c);
                    DialogueSessions.forget(p);
                    DialogueSessions.start(p, npc);
                    DialogueSessions.View v = DialogueSessions.view(p);
                    // Opening nothing is allowed only if the node removed the character (a farewell).
                    check(v != null || npc.isRemoved(),
                            c + " opened nothing at stage " + stage + " for role '" + role + "'");
                    DialogueSessions.forget(p);
                    if (!npc.isRemoved()) {
                        npc.discard();
                    }
                }
            }
            leave(helper, p);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_18")
    public static void findingCoresMovesTheStory(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        state.set("veld_met");
        give(p, new ItemStack(ModItems.CORE_DEPTHS.get()));
        give(p, page("roster"));
        com.arnir.storymod.story.StoryEvents.tickStory(p);
        check(state.has("found_core_depths"), "core not noticed");
        check(state.has("page_roster"), "note not noticed");
        check(state.has("ash_spawn_1") && findNpc(helper, "ash") != null, "ash did not appear after the first core");

        giveCores(p);
        com.arnir.storymod.story.StoryEvents.tickStory(p);
        check(!state.has("finale_ready"), "finale without the void awakening");
        state.set("void_awakened");
        state.set("ash_met_2");
        com.arnir.storymod.story.StoryEvents.tickStory(p);
        check(state.has("finale_ready"), "finale not ready");
        check(!state.has("ash_at_anchor"), "ash came before three days passed");
        state.backdate("ash_met_2", 24000L * 3);
        com.arnir.storymod.story.StoryEvents.tickStory(p);
        check(state.has("ash_at_anchor"), "ash did not come to the circle");
        leave(helper, p);
        helper.succeed();
    }

    // ------------------------------------------------------------------ starting kits, trinkets, merchant

    @GameTest(template = T, batch = "story_19")
    public static void starterKitsPerRole(GameTestHelper helper) {
        freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        PlayerRoles.giveStarterKit(w, PlayerRoles.WANDERER);
        PlayerRoles.giveStarterKit(k, PlayerRoles.KEEPER);
        check(count(w, "storymod:white_pearl") == 1, "wanderer has no pearl");
        check(count(w, "storymod:twilight_amulet") == 0, "wanderer still gets the old amulet");
        check(count(w, "storymod:white_greatsword") == 1 && count(k, "storymod:keeper_hammer") == 1, "personal weapons missing");
        check(count(w, "storymod:keeper_hammer") == 0 && count(k, "storymod:white_greatsword") == 0, "weapons mixed up");
        check(count(k, "storymod:keeper_wrench") == 1, "keeper has no wrench");
        check(count(w, "storymod:keeper_wrench") == 0 && count(k, "storymod:white_pearl") == 0, "kits mixed up");
        check(count(w, "minecraft:written_book") == 1 && count(k, "minecraft:written_book") == 1, "welcome notes missing");
        // Welcome notes must not count as story notes.
        for (int i = 0; i < w.getInventory().getContainerSize(); i++) {
            check(Inventories.pageId(w.getInventory().getItem(i)) == null, "welcome note counted as a story note");
        }
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_20")
    public static void roleItemsWorkOnlyForTheirOwner(GameTestHelper helper) {
        freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        w.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        k.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        w.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHITE_PEARL.get()));
        ModItems.WHITE_PEARL.get().use(helper.getLevel(), w, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(w.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "pearl did not hide its owner");
        k.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHITE_PEARL.get()));
        ModItems.WHITE_PEARL.get().use(helper.getLevel(), k, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(!k.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "pearl worked for the wrong player");

        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(200);
        k.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.KEEPER_WRENCH.get()));
        k.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, pick);
        ModItems.KEEPER_WRENCH.get().use(helper.getLevel(), k, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(pick.getDamageValue() == 200, "wrench repaired without copper");
        give(k, new ItemStack(Items.COPPER_INGOT, 2));
        ModItems.KEEPER_WRENCH.get().use(helper.getLevel(), k, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(k.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).getDamageValue() < 200, "wrench did not repair");
        check(count(k, "minecraft:copper_ingot") == 1, "wrench did not spend exactly one copper");

        ItemStack pick2 = new ItemStack(Items.IRON_PICKAXE);
        pick2.setDamageValue(200);
        give(w, new ItemStack(Items.COPPER_INGOT, 2));
        w.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.KEEPER_WRENCH.get()));
        w.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, pick2);
        ModItems.KEEPER_WRENCH.get().use(helper.getLevel(), w, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(w.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).getDamageValue() == 200, "wrench worked for the wrong player");
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_21")
    public static void trinketsGiveTheirEffects(GameTestHelper helper) {
        freshState(helper);
        ServerPlayer p = player(helper, "");
        give(p, new ItemStack(ModItems.ORDER_MEDALLION.get()));
        give(p, new ItemStack(ModItems.ZENITH_RING.get()));
        give(p, new ItemStack(ModItems.FORGE_EMBER.get()));
        helper.getLevel().setDayTime(6000);
        com.arnir.storymod.story.StoryEvents.tickItems(p);
        check(p.hasEffect(net.minecraft.world.effect.MobEffects.LUCK), "medallion gave no luck");
        check(p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "ring gave no regeneration at noon");
        check(!p.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "ember worked outside the Nether");
        p.removeAllEffects();
        helper.getLevel().setDayTime(18000);
        com.arnir.storymod.story.StoryEvents.tickItems(p);
        check(!p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "ring worked at night");
        // A stronger potion effect is never weakened.
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LUCK, 20 * 60, 2));
        com.arnir.storymod.story.StoryEvents.tickItems(p);
        check(p.getEffect(net.minecraft.world.effect.MobEffects.LUCK).getAmplifier() == 2, "trinket weakened a potion");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_22")
    public static void merchantComesEveryThirdDayAndTrades(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        helper.getLevel().setDayTime(24000L * 3 + 2000);
        com.arnir.storymod.story.StoryEvents.updateMerchant(server);
        check(findNpc(helper, "mira") == null, "merchant came before the story started");
        state.set("veld_met");
        com.arnir.storymod.story.StoryEvents.updateMerchant(server);
        StoryNpc mira = findNpc(helper, "mira");
        check(mira != null, "merchant did not come on her day");
        mira.moveTo(p.getX() + 1, p.getY(), p.getZ());

        expectNode(talk(p, mira), "first");
        say(p, "Покажи");
        check(hasOption(p, "Без изумрудов"), "no hint about prices when broke");
        give(p, new ItemStack(Items.EMERALD, 12));
        talk(p, mira);
        expectNode(say(p, "Диковина наугад"), "bought");
        check(count(p, "minecraft:emerald") == 0, "emeralds not taken");
        int artifacts = 0;
        for (String id : ModItems.ARTIFACT_IDS) {
            artifacts += count(p, "storymod:" + id);
        }
        check(artifacts == 1, "expected one trinket, got " + artifacts);

        talk(p, mira);
        check(!hasOption(p, "Есть ещё работа"), "second quest offered before the first");
        expectNode(say(p, "нужна помощь"), "q1");
        give(p, new ItemStack(Items.SWEET_BERRIES, 16));
        give(p, new ItemStack(Items.GLOW_BERRIES, 16));
        give(p, new ItemStack(Items.HONEY_BOTTLE));
        talk(p, mira);
        expectNode(say(p, "Вот ягоды"), "q1_done");
        check(state.hasPersonal(p.getUUID(), "mira_q1_done"), "quest not finished");
        talk(p, mira);
        check(!hasOption(p, "Вот ягоды"), "quest can be turned in twice");
        check(hasOption(p, "Есть ещё работа"), "second quest not offered");

        helper.getLevel().setDayTime(24000L * 3 + 13000);
        com.arnir.storymod.story.StoryEvents.updateMerchant(server);
        check(mira.isRemoved(), "merchant stayed after dusk");
        helper.getLevel().setDayTime(24000L * 4 + 2000);
        com.arnir.storymod.story.StoryEvents.updateMerchant(server);
        check(findNpc(helper, "mira") == null, "merchant came on the wrong day");
        helper.getLevel().setDayTime(24000L * 6 + 2000);
        com.arnir.storymod.story.StoryEvents.updateMerchant(server);
        check(findNpc(helper, "mira") != null, "merchant did not come back");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_23", timeoutTicks = 200)
    public static void trinketsDropFromChests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        checkLoot(level, "chests/jungle_temple", s -> s.is(ModItems.ZENITH_RING.get()), "zenith ring");
        checkLoot(level, "chests/buried_treasure", s -> s.is(ModItems.GUARDIAN_SCALE.get()), "guardian scale");
        checkLoot(level, "chests/woodland_mansion", s -> s.is(ModItems.ORDER_MEDALLION.get()), "order medallion");
        checkLoot(level, "chests/end_city_treasure", s -> s.is(ModItems.VOID_FEATHER.get()), "void feather");
        helper.succeed();
    }

    // ------------------------------------------------------------------ white pearl, Curios, structures

    private static net.minecraft.world.InteractionResult useOnBlock(ServerPlayer p, BlockPos abs) {
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false);
        var ctx = new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return p.getMainHandItem().useOn(ctx);
    }

    @GameTest(template = T, batch = "story_24")
    public static void whitePearlCarriesBlocks(GameTestHelper helper) {
        freshState(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        w.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        k.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var pearl = ModItems.WHITE_PEARL.get();
        BlockPos stone = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos chest = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos target = helper.absolutePos(new BlockPos(1, 1, 3));
        level.setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());

        k.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(pearl));
        k.setShiftKeyDown(true);
        useOnBlock(k, stone);
        check(level.getBlockState(stone).is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE), "pearl lifted a block for the wrong player");

        w.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(pearl));
        w.setShiftKeyDown(true);
        useOnBlock(w, chest);
        check(level.getBlockState(chest).is(net.minecraft.world.level.block.Blocks.CHEST), "pearl lifted a container");
        w.getCooldowns().removeCooldown(pearl);
        useOnBlock(w, stone);
        check(level.getBlockState(stone).isAir(), "pearl did not lift the block");
        var held = com.arnir.storymod.item.WhitePearlItem.carried(w.getMainHandItem(), level);
        check(held != null && held.is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE), "lifted block not kept in the pearl");

        // Using it in the air while holding a block must not trigger the shadow step.
        w.setShiftKeyDown(false);
        pearl.use(level, w, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(!w.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "shadow step while carrying a block");

        // Click the floor under the target: the block goes on top of it.
        level.setBlockAndUpdate(target.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        useOnBlock(w, target.below());
        check(level.getBlockState(target).is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE), "block not set down");
        check(com.arnir.storymod.item.WhitePearlItem.carried(w.getMainHandItem(), level) == null, "pearl still holds the block");
        leave(helper, w, k);
        helper.succeed();
    }

    /** Returns true if the damage was cancelled. */
    private static boolean incoming(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
        var event = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p,
                new net.neoforged.neoforge.common.damagesource.DamageContainer(source, 4.0F));
        return net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled();
    }

    @GameTest(template = T, batch = "story_25")
    public static void whitePearlDodgesArrows(GameTestHelper helper) {
        freshState(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        w.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        k.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        give(w, new ItemStack(ModItems.WHITE_PEARL.get()));
        give(k, new ItemStack(ModItems.WHITE_PEARL.get()));
        var arrow = new net.minecraft.world.damagesource.DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW));
        float old = com.arnir.storymod.item.WhitePearlItem.dodgeChance;
        com.arnir.storymod.item.WhitePearlItem.dodgeChance = 1.0F;
        try {
            // Fresh players are briefly invulnerable, so post the damage event directly.
            Vec3 before = w.position();
            check(incoming(w, arrow), "arrow hit the pearl's owner");
            check(w.position().distanceTo(before) > 1.0, "owner did not slip away");
            check(!incoming(w, arrow), "dodged twice without a pause");
            check(!incoming(k, arrow), "pearl protected the wrong player");
            var fall = new net.minecraft.world.damagesource.DamageSource(level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.FALL));
            w.getPersistentData().remove("storymod_last_dodge");
            check(!incoming(w, fall), "pearl dodged a fall");
        } finally {
            com.arnir.storymod.item.WhitePearlItem.dodgeChance = old;
        }
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_26")
    public static void oldAmuletBecomesThePearl(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        for (String f : List.of("journal_given", "starter_kit", "wanderer_arrived")) {
            state.setPersonal(w.getUUID(), f);
        }
        // The mock player already "logged in" before it got its role; pretend it joined before the pearl existed.
        state.clearPersonal(w.getUUID(), "white_pearl_given");
        give(w, new ItemStack(ModItems.TWILIGHT_AMULET.get()));
        PlayerRoles.onLogin(w, state);
        check(count(w, "storymod:twilight_amulet") == 0, "old amulet not taken");
        check(count(w, "storymod:white_pearl") == 1, "pearl not given");
        PlayerRoles.onLogin(w, state);
        check(count(w, "storymod:white_pearl") == 1, "pearl given twice");
        // The amulet now works for anyone who finds it.
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        k.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.TWILIGHT_AMULET.get()));
        ModItems.TWILIGHT_AMULET.get().use(helper.getLevel(), k, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(k.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "found amulet does not work");
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_27")
    public static void trinketsWorkFromCuriosSlots(GameTestHelper helper) {
        freshState(helper);
        // Curios itself is left out of the test server (it cannot sync to mock players); without it
        // the trinkets must still work from the inventory and the slot tags must be in place.
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        if (com.arnir.storymod.story.PlayerItems.curiosLoaded()) {
            check(com.arnir.storymod.compat.CuriosCompat.equip(k, "necklace", new ItemStack(ModItems.ORDER_MEDALLION.get())), "no necklace slot");
            check(com.arnir.storymod.compat.CuriosCompat.equip(w, "necklace", new ItemStack(ModItems.WHITE_PEARL.get())), "no necklace slot for the pearl");
        } else {
            give(k, new ItemStack(ModItems.ORDER_MEDALLION.get()));
            give(w, new ItemStack(ModItems.WHITE_PEARL.get()));
        }
        com.arnir.storymod.story.StoryEvents.tickItems(k);
        check(k.hasEffect(net.minecraft.world.effect.MobEffects.LUCK), "medallion gives no luck");
        check(com.arnir.storymod.item.WhitePearlItem.protects(w), "pearl does not protect");
        var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:necklace"));
        check(new ItemStack(ModItems.WHITE_PEARL.get()).is(tag), "pearl is not a necklace");
        var ring = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:ring"));
        check(new ItemStack(ModItems.ZENITH_RING.get()).is(ring), "zenith ring is not a ring");
        leave(helper, w, k);
        helper.succeed();
    }

    public static final List<String> STRUCTURES = List.of("white_sanctuary", "keeper_workshop", "order_tower",
            "sand_observatory", "frost_crypt");

    @GameTest(template = T, batch = "story_28", timeoutTicks = 400)
    public static void ownStructuresExistAndHaveLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var access = level.registryAccess();
        var structures = access.registryOrThrow(Registries.STRUCTURE);
        var sets = access.registryOrThrow(Registries.STRUCTURE_SET);
        var biomes = access.registryOrThrow(Registries.BIOME);
        for (String name : STRUCTURES) {
            ResourceLocation id = StoryMod.id(name);
            check(structures.containsKey(id), "structure not registered: " + name);
            check(sets.containsKey(id), "structure set missing: " + name);
            var tag = net.minecraft.tags.TagKey.create(Registries.BIOME, StoryMod.id("has_structure/" + name));
            check(biomes.getTag(tag).map(t -> t.size() > 0).orElse(false), "no biomes for " + name);
            var template = level.getStructureManager().get(id);
            check(template.isPresent(), "template missing: " + name);
            var containers = new ArrayList<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo>();
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
            containers.addAll(template.get().filterBlocks(BlockPos.ZERO, settings, net.minecraft.world.level.block.Blocks.CHEST));
            containers.addAll(template.get().filterBlocks(BlockPos.ZERO, settings, net.minecraft.world.level.block.Blocks.BARREL));
            boolean hasLoot = false;
            for (var info : containers) {
                if (info.nbt() != null && ("storymod:chests/" + name).equals(info.nbt().getString("LootTable"))) {
                    hasLoot = true;
                }
            }
            check(hasLoot, "no loot chest in " + name);
            checkLoot(level, "storymod:chests/" + name, s -> !s.isEmpty(), "anything from " + name);
            checkLoot(level, "storymod:chests/" + name, s -> s.getItem() instanceof com.arnir.storymod.item.ArtifactItem,
                    "a trinket from " + name);
        }
        checkLoot(level, "storymod:chests/frost_crypt", s -> s.is(ModItems.TWILIGHT_AMULET.get()), "the amulet from the crypt");
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_29", timeoutTicks = 200)
    public static void ownStructurePlacesWithItsChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getStructureManager().get(StoryMod.id("keeper_workshop")).orElseThrow();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 40, 0));
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        BlockPos chestPos = origin.offset(7, 1, 4);
        check(level.getBlockState(chestPos).is(net.minecraft.world.level.block.Blocks.CHEST), "chest not placed");
        var be = level.getBlockEntity(chestPos);
        check(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "chest has no contents holder");
        check(((net.minecraft.world.level.block.entity.ChestBlockEntity) be).getLootTable() != null, "chest has no loot table");
        // Clean up so later tests start from empty space.
        var size = template.getSize();
        for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            level.removeBlockEntity(p);
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ other mods

    @GameTest(template = T, batch = "story_30")
    public static void modQuestsStayHiddenWithoutTheirMods(GameTestHelper helper) {
        StoryState state = freshState(helper);
        state.set("veld_met");
        state.set("os_met");
        state.set("os_repaired");
        state.set("irmen_met");
        state.set("mira_here");
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        state.setPersonal(k.getUUID(), "mira_met");

        StoryNpc os = npc(helper, "os");
        expectNode(talk(k, os), "hub");
        check(!hasOption(k, "новые шестерни"), "mechanism quest offered without its mod");
        DialogueSessions.end(k);

        StoryNpc mira = npc(helper, "mira");
        expectNode(talk(k, mira), "hub");
        check(!hasOption(k, "других мирах"), "rumours about other worlds without those mods");
        DialogueSessions.end(k);

        StoryNpc irmen = npc(helper, "irmen");
        expectNode(talk(w, irmen), "hub");
        check(!hasOption(w, "магии"), "magic quest offered without a magic mod");
        check(!hasOption(w, "острова"), "sky islands option before going there");
        DialogueSessions.end(w);

        // Someone who did reach another world can still talk about it; the hand-in stays hidden without the items.
        state.setPersonal(w.getUUID(), "been_aether");
        expectNode(talk(w, irmen), "hub");
        expectNode(say(w, "острова над облаками"), "aether1");
        check(state.has("irmen_aether"), "sky quest not started");
        expectNode(say(w, "спокойно"), "aether_w");
        say(w, "…");
        check(!hasOption(w, "Вот осколки"), "hand-in offered without the items");
        DialogueSessions.end(w);
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_31")
    public static void storyDataNamesOnlyRealItemsAndModChestsMatch(GameTestHelper helper) {
        List<String> unknown = Inventories.unknownReferencedItems();
        check(unknown.isEmpty(), "story data mentions unknown items: " + unknown);
        check(Inventories.REFERENCED.contains("create:brass_ingot"), "mod items are not tracked");
        List<String> ns = List.of("twilightforest", "betterdungeons");
        check(com.arnir.storymod.loot.ModChestCondition.matches(ns, ResourceLocation.parse("twilightforest:chests/hedge_maze")), "mod chest not matched");
        check(com.arnir.storymod.loot.ModChestCondition.matches(ns, ResourceLocation.parse("betterdungeons:skeleton_dungeon/chests/common")), "nested chest not matched");
        check(!com.arnir.storymod.loot.ModChestCondition.matches(ns, ResourceLocation.parse("minecraft:chests/simple_dungeon")), "vanilla chest matched");
        check(!com.arnir.storymod.loot.ModChestCondition.matches(ns, ResourceLocation.parse("twilightforest:entities/naga")), "mob drops matched");

        freshState(helper);
        ServerPlayer p = player(helper, "");
        give(p, new ItemStack(ModItems.CLOCKWORK_HEART.get()));
        give(p, new ItemStack(ModItems.ZENITH_SHARD.get()));
        com.arnir.storymod.story.StoryEvents.tickItems(p);
        check(p.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED), "clockwork heart gives no haste");
        check(!p.hasEffect(net.minecraft.world.effect.MobEffects.JUMP), "zenith shard works without sprinting");
        p.setSprinting(true);
        com.arnir.storymod.story.StoryEvents.tickItems(p);
        check(p.hasEffect(net.minecraft.world.effect.MobEffects.JUMP), "zenith shard gives no jump while sprinting");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_32")
    public static void packSyncReadsThePackFormat(GameTestHelper helper) {
        // Same shape as the files CI publishes to the "pack" branch.
        String pack = "name = \"Story Mod\"\npack-format = \"packwiz:1.1.0\"\n\n[index]\nfile = \"index.toml\"\n"
                + "hash-format = \"sha256\"\nhash = \"ab\"\n\n[versions]\nminecraft = \"1.21.1\"\nneoforge = \"21.1.265\"\n";
        var p = com.arnir.storymod.sync.PackSync.parse(pack);
        check("index.toml".equals(p.get("file")) && "21.1.265".equals(p.get("neoforge")), "pack.toml not read: " + p);
        String index = "hash-format = \"sha256\"\n\n[[files]]\nfile = \"mods/create.pw.toml\"\nhash = \"aa\"\nmetafile = true\n\n"
                + "[[files]]\nfile = \"mods/storymod.jar\"\nhash = \"bb\"\n";
        var files = com.arnir.storymod.sync.PackSync.parseFiles(index);
        check(files.size() == 2, "index entries: " + files);
        check("true".equals(files.get(0).get("metafile")) && files.get(1).get("metafile") == null, "metafile flags wrong");
        var meta = com.arnir.storymod.sync.PackSync.parse("name = \"Create\"\nfilename = \"create-1.jar\"\nside = \"both\"\n\n"
                + "[download]\nurl = \"https://cdn.modrinth.com/x.jar\"\nhash-format = \"sha512\"\nhash = \"cc\"\n\n"
                + "[update.modrinth]\nmod-id = \"abc\"\nversion = \"v1\"\n");
        check("create-1.jar".equals(meta.get("filename")) && "https://cdn.modrinth.com/x.jar".equals(meta.get("url"))
                && "cc".equals(meta.get("hash")), "mod file not read: " + meta);
        check(com.arnir.storymod.sync.PackSync.compareVersions("21.1.172", "21.1.265") < 0, "version compare");
        check(com.arnir.storymod.sync.PackSync.compareVersions("21.1.265", "21.1.265") == 0, "version equal");
        check(com.arnir.storymod.sync.PackSync.compareVersions("21.2.1", "21.1.265") > 0, "version minor");
        check(!com.arnir.storymod.sync.PackSync.safeName("../evil.jar") && !com.arnir.storymod.sync.PackSync.safeName("a/b.jar")
                && com.arnir.storymod.sync.PackSync.safeName("create-6.0.10.jar"), "file name check");
        check(com.arnir.storymod.sync.PackSync.status() == com.arnir.storymod.sync.PackSync.Status.OFF, "sync ran in a dev/test server");
        helper.succeed();
    }

    // ------------------------------------------------------------------ personal powers and weapons

    @GameTest(template = T, batch = "story_33")
    public static void rolePowersFollowTheRole(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        com.arnir.storymod.story.StoryEvents.tickItems(w);
        com.arnir.storymod.story.StoryEvents.tickItems(k);
        check(com.arnir.storymod.story.RoleAbilities.has(w, "wanderer_reach"), "wanderer has no reach");
        check(!com.arnir.storymod.story.RoleAbilities.has(w, "keeper_health"), "wanderer got keeper powers");
        check(com.arnir.storymod.story.RoleAbilities.has(k, "keeper_health") && k.getMaxHealth() >= 24.0F, "keeper is not sturdier");
        check(!com.arnir.storymod.story.RoleAbilities.has(k, "wanderer_reach"), "keeper got wanderer powers");
        check(!com.arnir.storymod.story.RoleAbilities.has(w, "wanderer_one_hand"), "one-hand grip without the blade");
        w.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHITE_GREATSWORD.get()));
        com.arnir.storymod.story.StoryEvents.tickItems(w);
        check(com.arnir.storymod.story.RoleAbilities.has(w, "wanderer_one_hand"), "greatsword not one-handed for its owner");
        state.setRole(w.getUUID(), "none");
        com.arnir.storymod.story.StoryEvents.tickItems(w);
        check(!com.arnir.storymod.story.RoleAbilities.has(w, "wanderer_reach") && !com.arnir.storymod.story.RoleAbilities.has(w, "wanderer_one_hand"),
                "powers stayed after the role was taken away");
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_34")
    public static void personalWeaponsObeyOnlyTheirOwner(GameTestHelper helper) {
        freshState(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        w.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        k.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 0.5));
        w.moveTo(start.x, start.y, start.z, 0.0F, 16.0F);
        var pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new Vec3(2.5, 1, 4.5));
        float pigHealth = pig.getHealth();

        w.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHITE_GREATSWORD.get()));
        ModItems.WHITE_GREATSWORD.get().use(level, w, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(pig.getHealth() < pigHealth || pig.isDeadOrDying(), "phase strike did not hit");
        check(w.position().distanceTo(start) > 1.0, "phase strike did not move the owner");
        check(w.getCooldowns().isOnCooldown(ModItems.WHITE_GREATSWORD.get()), "no cooldown after the strike");

        k.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.WHITE_GREATSWORD.get()));
        Vec3 kStart = k.position();
        ModItems.WHITE_GREATSWORD.get().use(level, k, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(k.position().distanceTo(kStart) < 0.01, "greatsword worked for the wrong player");
        check(com.arnir.storymod.item.RoleWeaponItem.scaleDamage(k, 10.0F) < 5.0F, "foreign weapon hits at full strength");
        check(com.arnir.storymod.item.RoleWeaponItem.scaleDamage(w, 10.0F) == 10.0F, "owner's weapon weakened");

        var pig2 = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new Vec3(2.5, 1, 2.5));
        float pig2Health = pig2.getHealth();
        k.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.KEEPER_HAMMER.get()));
        k.moveTo(pig2.getX() + 1.5, pig2.getY(), pig2.getZ());
        ModItems.KEEPER_HAMMER.get().use(level, k, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(pig2.getHealth() < pig2Health || pig2.isDeadOrDying(), "ground slam did not hurt");
        check(k.getCooldowns().isOnCooldown(ModItems.KEEPER_HAMMER.get()), "no cooldown after the slam");
        pig.discard();
        pig2.discard();
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_35")
    public static void wandererSlipsBehindMeleeAttackers(GameTestHelper helper) {
        freshState(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(2.5, 1, 2.0));
        zombie.setYRot(0.0F);
        zombie.setYHeadRot(0.0F);
        var hit = level.damageSources().mobAttack(zombie);
        float old = com.arnir.storymod.story.RoleAbilities.counterChance;
        com.arnir.storymod.story.RoleAbilities.counterChance = 1.0F;
        try {
            Vec3 before = w.position();
            check(incoming(w, hit), "melee blow was not avoided");
            check(w.position().distanceTo(before) > 0.5, "wanderer did not move behind the attacker");
            check(!incoming(w, hit), "avoided twice without a pause");
            check(!incoming(k, hit), "keeper avoided a blow like the wanderer");
        } finally {
            com.arnir.storymod.story.RoleAbilities.counterChance = old;
        }
        zombie.discard();
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_36")
    public static void weaponsReachPlayersWhoStartedEarlier(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        for (ServerPlayer p : List.of(w, k)) {
            for (String f : List.of("journal_given", "starter_kit", "wanderer_arrived", "white_pearl_given")) {
                state.setPersonal(p.getUUID(), f);
            }
            state.clearPersonal(p.getUUID(), "role_weapon_given");
            PlayerRoles.onLogin(p, state);
            PlayerRoles.onLogin(p, state);
        }
        check(count(w, "storymod:white_greatsword") == 1, "wanderer did not get exactly one greatsword");
        check(count(k, "storymod:keeper_hammer") == 1, "keeper did not get exactly one hammer");

        give(k, new ItemStack(ModItems.STONE_HEART.get()));
        k.setShiftKeyDown(true);
        com.arnir.storymod.story.StoryEvents.tickItems(k);
        check(k.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE), "stone heart gives nothing while sneaking");
        give(w, new ItemStack(ModItems.BERSERKER_CHARM.get()));
        com.arnir.storymod.story.StoryEvents.tickItems(w);
        check(!w.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST), "fury charm at full health");
        w.setHealth(3.0F);
        com.arnir.storymod.story.StoryEvents.tickItems(w);
        check(w.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST), "fury charm silent at low health");
        checkLoot(helper.getLevel(), "storymod:chests/order_tower", st -> st.is(ModItems.ORDER_BLADE.get()), "order blade");
        leave(helper, w, k);
        helper.succeed();
    }

    // ------------------------------------------------------------------ durability, enchantments, bound marks

    private static net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment(GameTestHelper helper, String ns, String id) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ns, id));
        var holder = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolder(key);
        check(holder.isPresent(), "enchantment missing: " + ns + ":" + id);
        return holder.get();
    }

    @GameTest(template = T, batch = "story_37")
    public static void personalWeaponsNeverBreakAndTakeEnchantments(GameTestHelper helper) {
        ItemStack blade = new ItemStack(ModItems.WHITE_GREATSWORD.get());
        ItemStack hammer = new ItemStack(ModItems.KEEPER_HAMMER.get());
        check(!blade.isDamageableItem() && !hammer.isDamageableItem(), "personal weapons can still break");
        check(blade.isEnchantable() && hammer.isEnchantable(), "personal weapons cannot be enchanted");
        check(new ItemStack(ModItems.ORDER_BLADE.get()).isDamageableItem(), "order blade should wear out");
        check(enchantment(helper, "minecraft", "sharpness").value().canEnchant(blade), "sharpness does not fit the greatsword");
        check(enchantment(helper, "minecraft", "looting").value().canEnchant(hammer), "looting does not fit the hammer");
        for (String id : List.of("dusk_edge", "light_thirst", "void_chill", "long_reach")) {
            check(enchantment(helper, "storymod", id).value().canEnchant(blade), id + " does not fit the greatsword");
        }
        check(enchantment(helper, "storymod", "shadow_step").value().canEnchant(new ItemStack(Items.DIAMOND_BOOTS)), "shadow step does not fit boots");
        check(enchantment(helper, "storymod", "sturdy").value().canEnchant(new ItemStack(Items.IRON_CHESTPLATE)), "sturdiness does not fit a chestplate");
        check(!enchantment(helper, "storymod", "sturdy").value().canEnchant(blade), "sturdiness fits a sword");
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_38")
    public static void boundMarksGiveAndTake(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        for (ServerPlayer p : List.of(w, k)) {
            for (String f : List.of("journal_given", "starter_kit", "wanderer_arrived", "white_pearl_given", "role_weapon_given")) {
                state.setPersonal(p.getUUID(), f);
            }
            state.clearPersonal(p.getUUID(), "role_mark_given");
            p.getInventory().clearContent();
            PlayerRoles.onLogin(p, state);
            PlayerRoles.onLogin(p, state);
        }
        check(count(w, "storymod:end_mark") == 1 && count(w, "storymod:copper_oath") == 0, "wanderer did not get exactly one end mark");
        check(count(k, "storymod:copper_oath") == 1 && count(k, "storymod:end_mark") == 0, "keeper did not get exactly one copper oath");

        com.arnir.storymod.story.StoryEvents.tickItems(w);
        com.arnir.storymod.story.StoryEvents.tickItems(k);
        check(com.arnir.storymod.story.RoleAbilities.has(w, "end_mark_strength"), "end mark gives no strength");
        check(com.arnir.storymod.story.RoleAbilities.has(k, "copper_oath_armor") && com.arnir.storymod.story.RoleAbilities.has(k, "copper_oath_heavy"),
                "copper oath gives no armour or weight");
        check(!com.arnir.storymod.story.RoleAbilities.has(k, "end_mark_strength") && !com.arnir.storymod.story.RoleAbilities.has(w, "copper_oath_armor"),
                "marks mixed up");
        // A mark in the wrong hands does nothing.
        give(k, new ItemStack(ModItems.END_MARK.get()));
        com.arnir.storymod.story.StoryEvents.tickItems(k);
        check(!com.arnir.storymod.story.RoleAbilities.has(k, "end_mark_strength"), "end mark works for the keeper");

        // The end mark does not like water.
        helper.setBlock(new BlockPos(2, 1, 3), net.minecraft.world.level.block.Blocks.WATER);
        w.moveTo(helper.absoluteVec(new Vec3(2.5, 1, 3.5)));
        w.baseTick();
        check(w.isInWaterOrRain(), "test setup: wanderer is not in water");
        com.arnir.storymod.story.StoryEvents.tickItems(w);
        check(w.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "end mark gives no weakness in water");
        helper.setBlock(new BlockPos(2, 1, 3), net.minecraft.world.level.block.Blocks.AIR);
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_39")
    public static void personalThingsStayWithTheirOwner(GameTestHelper helper) {
        freshState(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerLevel level = helper.getLevel();
        // Tossing a bound thing brings it straight back.
        w.drop(new ItemStack(ModItems.END_MARK.get()), false);
        w.drop(new ItemStack(ModItems.WHITE_PEARL.get()), false);
        check(count(w, "storymod:end_mark") == 1, "end mark could be thrown away");
        check(count(w, "storymod:white_pearl") == 1, "white pearl could be thrown away");

        // On death personal things are held back and returned on respawn; the rest drops.
        w.getInventory().clearContent();
        List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
        for (ItemStack st : List.of(new ItemStack(ModItems.WHITE_GREATSWORD.get()), new ItemStack(ModItems.END_MARK.get()),
                new ItemStack(Items.DIRT, 5))) {
            drops.add(new net.minecraft.world.entity.item.ItemEntity(level, w.getX(), w.getY(), w.getZ(), st));
        }
        com.arnir.storymod.story.PersonalItems.keepFromDrops(w, drops);
        check(drops.size() == 1 && drops.get(0).getItem().is(Items.DIRT), "personal things fell out on death");
        com.arnir.storymod.story.PersonalItems.restore(w);
        check(count(w, "storymod:white_greatsword") == 1 && count(w, "storymod:end_mark") == 1, "personal things did not come back");
        check(count(w, "minecraft:dirt") == 0, "ordinary loot came back too");
        com.arnir.storymod.story.PersonalItems.restore(w);
        check(count(w, "storymod:white_greatsword") == 1, "personal things duplicated");
        leave(helper, w);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_40")
    public static void storyLootReachesReplacedStructuresAndStaysHidden(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // YUNG's strongholds and dungeons use their own chest tables; story loot must reach them too.
        for (String table : List.of("betterstrongholds:chests/common", "betterdungeons:zombie_dungeon/chests/common")) {
            boolean found = false;
            for (int i = 0; i < 400 && !found; i++) {
                LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
                var ctx = new net.minecraft.world.level.storage.loot.LootContext.Builder(params).create(java.util.Optional.empty());
                var loot = net.neoforged.neoforge.common.CommonHooks.modifyLoot(ResourceLocation.parse(table),
                        new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(), ctx);
                found = loot.stream().anyMatch(st -> st.is(Items.WRITTEN_BOOK) || st.getItem().builtInRegistryHolder().key().location().getNamespace().equals("storymod"));
            }
            check(found, "no story loot in " + table);
        }
        var hidden = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "hidden_from_recipe_viewers"));
        check(new ItemStack(ModItems.END_MARK.get()).is(hidden) && new ItemStack(ModItems.CORE_VOID.get()).is(hidden), "story items are visible in recipe viewers");
        check(!new ItemStack(Items.DIAMOND).is(hidden), "vanilla items hidden");
        helper.succeed();
    }

    // ------------------------------------------------------------------ second season

    private static StoryState actTwo(GameTestHelper helper) {
        StoryState state = freshState(helper);
        state.set("veld_met");
        state.set("ending");
        state.set("ending_seal");
        com.arnir.storymod.story.ActTwo.start(helper.getLevel().getServer());
        return state;
    }

    private static void day(GameTestHelper helper, long time) {
        helper.getLevel().setDayTime(helper.getLevel().getDayTime() / 24000L * 24000L + time);
    }

    private static void triggers(ServerPlayer p, StoryState state) {
        TriggerManager.check(p, state);
    }

    @GameTest(template = T, batch = "story_41")
    public static void secondSeasonStartsADayAfterAnyEnding(GameTestHelper helper) {
        StoryState state = freshState(helper);
        var server = helper.getLevel().getServer();
        com.arnir.storymod.story.ActTwo.tick(server);
        check(!state.has("act2_start"), "season two started without an ending");
        state.set("ending");
        com.arnir.storymod.story.ActTwo.tick(server);
        check(!state.has("act2_start"), "season two started right after the ending");
        state.backdate("ending", 24000L);
        com.arnir.storymod.story.ActTwo.tick(server);
        check(state.has("act2_start") && state.has("seam_1"), "season two did not start a day after the ending");
        check(com.arnir.storymod.story.ActTwo.seam(state) == 1, "seam should be at stage 1");
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_42")
    public static void seamWidensUnlessFourLanternsHold(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        var server = helper.getLevel().getServer();
        day(helper, 6000);
        state.backdate("seam_1", 24000L * 5);
        com.arnir.storymod.story.ActTwo.tick(server);
        check(com.arnir.storymod.story.ActTwo.seam(state) == 2, "seam did not widen after five days");

        ServerLevel level = helper.getLevel();
        BlockPos a = state.anchor();
        List<BlockPos> spots = List.of(a.offset(13, 2, 0), a.offset(-13, 2, 0), a.offset(0, 2, 13), a.offset(0, 2, -13));
        for (BlockPos pos : spots) {
            level.setBlockAndUpdate(pos, com.arnir.storymod.registry.ModBlocks.STAR_LANTERN.get().defaultBlockState());
        }
        // A lantern right next to another one does not count twice.
        level.setBlockAndUpdate(a.offset(14, 2, 1), com.arnir.storymod.registry.ModBlocks.STAR_LANTERN.get().defaultBlockState());
        check(com.arnir.storymod.story.ActTwo.lanternsAroundCircle(state) == 4,
                "expected 4 lanterns, counted " + com.arnir.storymod.story.ActTwo.lanternsAroundCircle(state));
        check(state.has("lanterns_lit"), "lanterns_lit not set");
        state.backdate("seam_2", 24000L * 6);
        com.arnir.storymod.story.ActTwo.tick(server);
        check(com.arnir.storymod.story.ActTwo.seam(state) == 2, "seam widened while the lanterns held");

        level.setBlockAndUpdate(spots.get(0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(a.offset(14, 2, 1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        check(!state.has("lanterns_lit"), "lanterns_lit kept after a lantern broke");
        com.arnir.storymod.story.ActTwo.tick(server);
        check(com.arnir.storymod.story.ActTwo.seam(state) == 3, "seam did not widen after a lantern went out");
        for (BlockPos pos : spots) {
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_43")
    public static void fallenStarBurnsCoolsAndBreaksIntoShards(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos star = com.arnir.storymod.story.ActTwo.impact(level.getServer(), at, false);
        check(level.getBlockState(star).is(com.arnir.storymod.registry.ModBlocks.FALLEN_STAR.get()), "no star at the impact");
        check(com.arnir.storymod.block.FallenStarBlock.isHot(level.getBlockState(star)), "a fresh star should be hot");
        check(state.counter("stars_fallen") == 1 && state.has("first_star"), "star not counted");
        var hotDrops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(star), level, star, null);
        check(hotDrops.isEmpty(), "a hot star dropped " + hotDrops);
        com.arnir.storymod.block.FallenStarBlock.cool(level, star);
        check(!com.arnir.storymod.block.FallenStarBlock.isHot(level.getBlockState(star)), "star did not cool");
        var drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(star), level, star, null);
        check(drops.stream().anyMatch(st -> st.is(ModItems.STAR_SHARD.get())), "a cooled star dropped no shards: " + drops);
        // Water next to a hot star cools it on the spot.
        BlockPos second = star.above(3);
        level.setBlockAndUpdate(second, com.arnir.storymod.registry.ModBlocks.FALLEN_STAR.get().defaultBlockState());
        level.setBlockAndUpdate(second.east(), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        check(!com.arnir.storymod.block.FallenStarBlock.isHot(level.getBlockState(second)), "water did not cool the star");
        // The keeper's shuttle cools a star and pulls out a shard.
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        level.setBlockAndUpdate(second, com.arnir.storymod.registry.ModBlocks.FALLEN_STAR.get().defaultBlockState());
        level.setBlockAndUpdate(second.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(second, com.arnir.storymod.registry.ModBlocks.FALLEN_STAR.get().defaultBlockState());
        k.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.LOOM_SHUTTLE.get()));
        useOnBlock(k, second);
        check(!com.arnir.storymod.block.FallenStarBlock.isHot(level.getBlockState(second)), "shuttle did not cool the star");
        check(count(k, "storymod:star_shard") == 1, "shuttle gave no shard");
        leave(helper, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_44")
    public static void eyepieceForetellsTheNextStar(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        ServerPlayer p = player(helper, PlayerRoles.KEEPER);
        BlockPos target = com.arnir.storymod.story.ActTwo.planStar(helper.getLevel().getServer(), p, 600);
        check(target != null && state.nextStarTime() > 0, "no star planned");
        String text = com.arnir.storymod.story.ActTwo.forecast(p).getString();
        check(text.contains("X " + target.getX()) && text.contains("Z " + target.getZ()), "forecast has no coordinates: " + text);
        state.planStar(0, null);
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_45")
    public static void silenceMarksAndIsCured(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        ServerPlayer p = player(helper, "");
        check(!com.arnir.storymod.story.ActTwo.isMarked(p, state), "marked from the start");
        com.arnir.storymod.story.ActTwo.mark(p);
        check(com.arnir.storymod.story.ActTwo.isMarked(p, state), "mark did not stick");
        com.arnir.storymod.item.QuietDrinkItem.drink(p, false);
        check(com.arnir.storymod.story.ActTwo.isCalm(p), "tea did not calm");
        check(com.arnir.storymod.story.ActTwo.isMarked(p, state), "tea should not cure");
        com.arnir.storymod.item.QuietDrinkItem.drink(p, true);
        check(!com.arnir.storymod.story.ActTwo.isMarked(p, state), "tincture did not cure");
        com.arnir.storymod.story.ActTwo.mark(p);
        check(!com.arnir.storymod.story.ActTwo.isMarked(p, state), "cured player marked again");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_46")
    public static void stargazerQuestsAndHisEars(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        ServerPlayer p = player(helper, PlayerRoles.KEEPER);
        StoryNpc nel = npc(helper, "nel");
        expectNode(talk(p, nel), "intro");
        say(p, "линия");
        say(p, "Принесу");
        check(state.has("nel_q1"), "first quest not given");
        give(p, new ItemStack(ModItems.STAR_SHARD.get(), 3));
        expectNode(talk(p, nel), "hub");
        expectNode(say(p, "Вот три осколка"), "q1_done");
        check(count(p, "storymod:star_eyepiece") == 1 && count(p, "storymod:star_shard") == 0, "eyepiece not given");
        say(p, "Спасибо");
        expectNode(say(p, "удержать небо"), "lanterns");
        say(p, "Понятно");
        check(state.has("nel_lanterns"), "lantern quest not given");
        // Telling him where the lanterns are lets the silence break one.
        BlockPos lantern = state.anchor().offset(20, 3, 0);
        helper.getLevel().setBlockAndUpdate(lantern, com.arnir.storymod.registry.ModBlocks.STAR_LANTERN.get().defaultBlockState());
        talk(p, nel);
        say(p, "фонари у круга");
        check(state.has("nel_heard"), "telling did not register");
        state.backdate("nel_heard", 24000L);
        day(helper, 18000);
        triggers(p, state);
        check(state.has("lantern_broken_by_ear"), "lantern not broken after he heard");
        check(!helper.getLevel().getBlockState(lantern).is(com.arnir.storymod.registry.ModBlocks.STAR_LANTERN.get()), "lantern still stands");
        // Os sees what is wrong with him; then he can be cured.
        state.set("nel_suspect");
        give(p, new ItemStack(ModItems.QUIET_TINCTURE.get()));
        DialogueSessions.forget(p);
        talk(p, nel);
        expectNode(say(p, "не слышишь"), "truth");
        expectNode(say(p, "Выпей"), "cured");
        check(state.has("nel_cured") && count(p, "storymod:seam_needle") == 1, "cure did not work");
        leave(helper, p);
        helper.succeed();
    }

    private static void osHub(ServerPlayer p, StoryNpc os) {
        DialogueSessions.forget(p);
        String node = talk(p, os);
        if (node.equals("a2_intro")) {
            say(p, "…");
        }
    }

    @GameTest(template = T, batch = "story_47")
    public static void councilVotesDecideTheWindow(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        StoryNpc os = npc(helper, "os");
        osHub(w, os);
        check(!hasOption(w, "Мой голос"), "vote offered before the proposal");
        state.set("vote1_proposed");
        osHub(w, os);
        say(w, "открыть окно в Шов");
        osHub(w, os);
        check(!hasOption(w, "Мой голос"), "wanderer can vote twice");
        osHub(k, os);
        say(k, "не открывать");
        triggers(k, state);
        check(state.has("window_split") && state.has("nel_lost"), "split votes did not send the stargazer away");
        check(!state.has("window_open") && !state.has("window_closed"), "split vote produced another outcome too");

        // Both agree to open: the window opens and the seam widens.
        state.clear("window_split");
        state.clear("trigger_a2_vote_split");
        state.clear("v1_k_close");
        osHub(k, os);
        say(k, "открыть окно в Шов");
        int before = com.arnir.storymod.story.ActTwo.seam(state);
        triggers(k, state);
        check(state.has("window_open"), "agreed vote did not open the window");
        check(com.arnir.storymod.story.ActTwo.seam(state) == before + 1, "window did not widen the seam");
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_48")
    public static void lettersLeadToTheLullaby(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        day(helper, 1000);
        triggers(w, state);
        check(!state.has("letter_1"), "letter came on the first morning");
        state.backdate("act2_start", 24000L);
        triggers(k, state);
        check(!state.has("letter_1"), "letter came to the keeper");
        triggers(w, state);
        check(state.has("letter_1") && count(w, "minecraft:written_book") == 1, "first letter not delivered");
        // Riddle 1: the circle at sunset with the pearl.
        day(helper, 12500);
        triggers(w, state);
        check(!state.has("lullaby_1"), "riddle solved without the pearl");
        give(w, new ItemStack(ModItems.WHITE_PEARL.get()));
        triggers(w, state);
        check(state.has("lullaby_1"), "riddle 1 not solved");
        // Riddle 2: above the clouds at midnight.
        state.set("letter_2");
        day(helper, 18000);
        triggers(w, state);
        check(!state.has("lullaby_2"), "riddle 2 solved on the ground");
        w.moveTo(w.getX(), 200, w.getZ());
        triggers(w, state);
        check(state.has("lullaby_2"), "riddle 2 not solved high up");
        // Riddle 6: offerings at the circle at midnight give the thread.
        state.set("letter_6");
        w.moveTo(state.anchor().getX() + 0.5, state.anchor().getY(), state.anchor().getZ() + 1.5);
        give(w, new ItemStack(Items.STRING, 8));
        give(w, new ItemStack(Items.ENDER_PEARL, 4));
        give(w, new ItemStack(ModItems.STAR_SHARD.get(), 4));
        triggers(w, state);
        check(state.has("lullaby_6") && count(w, "storymod:ilva_thread") == 1, "riddle 6 gave no thread");
        check(count(w, "minecraft:string") == 0, "offerings not taken");
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_49", timeoutTicks = 400)
    public static void undersideFightAndSeamEnding(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        var server = helper.getLevel().getServer();
        ServerLevel under = com.arnir.storymod.story.Underside.level(server);
        check(under != null, "the underside dimension is not loaded");
        state.set("rift_open");
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        com.arnir.storymod.story.Underside.enter(k);
        check(com.arnir.storymod.story.Underside.isHere(k), "keeper did not cross the seam");
        check(under.getBlockState(com.arnir.storymod.story.Underside.CENTER).is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE),
                "island not built");
        // Entities in a freshly loaded dimension become visible a few ticks after the chunks get a ticket.
        helper.runAfterDelay(20, () -> {
            // The player tick re-checks the cast once the island chunks are loaded; mock players do not tick.
            com.arnir.storymod.story.Underside.ensureCast(under, state);
            List<StoryNpc> ilvas = new ArrayList<>(under.getEntities(com.arnir.storymod.registry.ModEntities.STORY_NPC.get(),
                    e -> e.getCharacter().equals("ilva") && e.isAlive()));
            StringBuilder seen = new StringBuilder();
            for (net.minecraft.world.entity.Entity e : under.getAllEntities()) {
                seen.append(e.getType().toShortString()).append(e instanceof StoryNpc n ? ":" + n.getCharacter() : "")
                        .append('@').append(e.blockPosition().toShortString()).append(' ');
            }
            check(ilvas.size() == 1, "expected one ilva on the island, found " + ilvas.size() + " (gen "
                    + state.generation("ilva") + ", keeper in " + k.level().dimension().location() + " at "
                    + k.blockPosition().toShortString() + ", entities: " + seen + ")");
            StoryNpc ilva = ilvas.get(0);
            k.teleportTo(ilva.getX() + 1.5, ilva.getY(), ilva.getZ() + 1.5);
            expectNode(talk(k, ilva), "meet");
            say(k, "Что такое Шов");
            say(k, "тень наверху");
            expectNode(say(k, "…"), "hub");
            say(k, "готовы");
            check(state.has("shade_fight"), "fight did not start");
            helper.runAfterDelay(20, () -> {
                List<StoryNpc> shades = new ArrayList<>(under.getEntities(com.arnir.storymod.registry.ModEntities.STORY_NPC.get(),
                        e -> e.getCharacter().equals("shade") && e.isAlive()));
                check(shades.size() == 1, "expected one shade, found " + shades.size());
                StoryNpc shade = shades.get(0);
                check(shade.isHostile() && shade.getMaxHealth() >= 400, "shade is not a boss");
                // Last phase: only the wanderer or starlight can hurt it.
                shade.setHealth(100);
                float before = shade.getHealth();
                shade.invulnerableTime = 0;
                shade.hurt(under.damageSources().playerAttack(k), 10);
                check(shade.getHealth() == before, "keeper hurt the shade without starlight");
                BlockPos near = k.blockPosition().offset(2, 0, 0);
                under.setBlockAndUpdate(near, com.arnir.storymod.registry.ModBlocks.STAR_LANTERN.get().defaultBlockState());
                shade.invulnerableTime = 0;
                shade.hurt(under.damageSources().playerAttack(k), 10);
                check(shade.getHealth() < before, "starlight did not let the keeper hurt the shade");
                under.setBlockAndUpdate(near, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                before = shade.getHealth();
                shade.invulnerableTime = 0;
                shade.hurt(under.damageSources().playerAttack(w), 10);
                check(shade.getHealth() < before, "the wanderer could not hurt the shade");
                shade.kill();
                check(state.has("shade_defeated"), "shade_defeated not set");
                DialogueSessions.forget(k);
                expectNode(talk(k, ilva), "final");
                check(!hasOption(k, "Спой со мной"), "song ending offered to the keeper");
                say(k, "Сшей Шов");
                check(say(k, "Прощай") == null, "dialogue should close");
                check(state.has("ending_seam") && state.has("act2_over"), "seam ending flags missing");
                check(k.level().dimension() == net.minecraft.world.level.Level.OVERWORLD, "keeper not returned home");
                check(count(k, "storymod:comet_tail") == 1, "no reward for the seam ending");
                leave(helper, w, k);
                helper.succeed();
            });
        });
    }

    @GameTest(template = T, batch = "story_50")
    public static void lullabyEndingNeedsEverythingTogether(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        for (int i = 1; i <= 7; i++) {
            state.set("lullaby_" + i);
        }
        state.set("shade_defeated");
        state.set("loom_ready");
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        StoryNpc ilva = npc(helper, "ilva");
        // The keeper exists (role taken) but is far away: no song.
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        k.moveTo(k.getX() + 200, k.getY(), k.getZ());
        state.set("os_star_heart");
        expectNode(talk(w, ilva), "final");
        check(!hasOption(w, "Спой со мной"), "song possible without the keeper nearby");
        k.moveTo(w.getX() + 1, w.getY(), w.getZ());
        DialogueSessions.forget(w);
        talk(w, ilva);
        expectNode(say(w, "Спой со мной"), "end_song1");
        say(w, "…");
        check(say(w, "домой") == null, "dialogue should close");
        check(state.has("ending_lullaby") && state.has("act2_over"), "lullaby ending flags missing");
        check(count(w, "storymod:silent_bell") == 1 && count(k, "storymod:silent_bell") == 1, "bell not given to both");
        check(findNpc(helper, "ilva") != null, "ilva did not come to the circle");
        StoryNpc os = npc(helper, "os");
        expectNode(talk(w, os), "a2_epi_lullaby");
        leave(helper, w, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_51", timeoutTicks = 300)
    public static void eclipseComesInThreeWaves(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        var server = helper.getLevel().getServer();
        state.set("seam_2");
        state.set("seam_3");
        state.set("seam_4");
        ServerPlayer p = player(helper, "");
        day(helper, 18000);
        com.arnir.storymod.story.ActTwo.tick(server);
        check(state.has("eclipse_active"), "eclipse did not start");
        for (int wave = 1; wave <= 3; wave++) {
            int alive = com.arnir.storymod.story.ActTwo.echoesNear(helper.getLevel(), state.anchor(), 40);
            check(state.counter("eclipse_wave") == wave && alive > 0, "wave " + wave + " did not come (alive " + alive + ")");
            for (net.minecraft.world.entity.Entity e : helper.getLevel().getEntities((net.minecraft.world.entity.Entity) null,
                    new net.minecraft.world.phys.AABB(state.anchor()).inflate(40), e -> e.getTags().contains(com.arnir.storymod.story.ActTwo.ECHO_TAG))) {
                e.discard();
            }
            com.arnir.storymod.story.ActTwo.tick(server);
        }
        check(state.has("eclipse_done") && !state.has("eclipse_active"), "eclipse did not end after three waves");
        check(count(p, "storymod:star_shard") == 4, "no reward for the eclipse");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_52")
    public static void keeperBuildsTheLoomAndGivesOsTheHeart(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        state.set("seam_2");
        ServerPlayer k = player(helper, PlayerRoles.KEEPER);
        StoryNpc os = npc(helper, "os");
        expectNode(talk(k, os), "a2_intro");
        expectNode(say(k, "…"), "a2_hub");
        expectNode(say(k, "чем сшивали"), "loom1");
        check(!hasOption(k, "Вот всё"), "loom accepted without parts");
        say(k, "Соберу");
        give(k, new ItemStack(ModItems.STAR_SHARD.get(), 8));
        give(k, new ItemStack(Items.STRING, 16));
        give(k, new ItemStack(Items.AMETHYST_SHARD, 4));
        give(k, new ItemStack(Items.LOOM));
        osHub(k, os);
        say(k, "чем сшивали");
        expectNode(say(k, "Вот всё"), "loom2");
        check(state.has("loom_started") && count(k, "minecraft:loom") == 0, "loom parts not taken");
        day(helper, 18000);
        osHub(k, os);
        check(!state.has("loom_ready"), "loom finished too early");
        state.backdate("loom_started", 24000L * 2);
        DialogueSessions.forget(k);
        expectNode(talk(k, os), "loom_done");
        check(state.has("loom_ready") && count(k, "storymod:loom_shuttle") == 1, "loom not finished");
        give(k, new ItemStack(ModItems.STAR_CORE.get()));
        osHub(k, os);
        expectNode(say(k, "сердце звезды"), "heart1");
        expectNode(say(k, "Возьми его"), "heart2");
        check(state.has("os_star_heart") && count(k, "storymod:star_core") == 0, "heart not given");
        leave(helper, k);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_53")
    public static void whiteThreadPullsTheWandererOut(GameTestHelper helper) {
        actTwo(helper);
        ServerPlayer w = player(helper, PlayerRoles.WANDERER);
        w.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        give(w, new ItemStack(ModItems.ILVA_THREAD.get()));
        w.hurt(helper.getLevel().damageSources().genericKill(), 1000.0F);
        check(w.isAlive() && w.getHealth() > 0, "thread did not save the wanderer");
        check(w.getCooldowns().isOnCooldown(ModItems.ILVA_THREAD.get()), "thread has no cooldown");
        check(com.arnir.storymod.story.PersonalItems.isBound(new ItemStack(ModItems.ILVA_THREAD.get())), "thread is not bound");
        leave(helper, w);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_54")
    public static void secondSeasonDialoguesOpenInEveryStage(GameTestHelper helper) {
        List<List<String>> stages = List.of(
                List.of("act2_start", "seam_1"),
                List.of("act2_start", "seam_1", "seam_2", "nel_met", "nel_q1", "vote1_proposed", "os_a2_intro"),
                List.of("act2_start", "seam_3", "mira_taken", "nel_lost", "window_split", "os_a2_intro"),
                List.of("act2_start", "seam_3", "rift_open", "ilva_met", "shade_fight", "os_a2_intro", "nel_truth"),
                List.of("act2_start", "rift_open", "shade_defeated", "ilva_met", "mira_rescued", "nel_cured", "os_a2_intro"),
                List.of("act2_start", "act2_over", "ending_seam", "nel_cured"),
                List.of("act2_start", "act2_over", "ending_lullaby"),
                List.of("act2_start", "act2_over", "ending_open", "ending_open"));
        for (String role : List.of("", PlayerRoles.WANDERER, PlayerRoles.KEEPER)) {
            StoryState state = freshState(helper);
            ServerPlayer p = player(helper, role);
            for (List<String> stage : stages) {
                state.reset();
                state.set("intro_spawned");
                state.set("ending");
                state.set("veld_met");
                stage.forEach(state::set);
                for (String c : com.arnir.storymod.command.StoryCommand.CHARACTERS) {
                    StoryNpc npc = npc(helper, c);
                    DialogueSessions.forget(p);
                    DialogueSessions.start(p, npc);
                    DialogueSessions.View v = DialogueSessions.view(p);
                    check(v != null || npc.isRemoved(), c + " opened nothing at stage " + stage + " for role '" + role + "'");
                    DialogueSessions.forget(p);
                    if (!npc.isRemoved()) {
                        npc.discard();
                    }
                }
            }
            leave(helper, p);
        }
        // Journal pages render for every stage without leftovers of placeholders.
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, PlayerRoles.WANDERER);
        for (String f : List.of("act2_start", "seam_1", "os_a2_intro", "nel_lanterns", "letter_1", "lullaby_1", "lullaby_2")) {
            state.set(f);
        }
        for (String page : JournalManager.pagesFor(p)) {
            check(!page.contains("{"), "journal placeholder left: " + page);
        }
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_55", timeoutTicks = 300)
    public static void riftOpensAtTheCircleAndLeadsAcross(GameTestHelper helper) {
        StoryState state = actTwo(helper);
        var server = helper.getLevel().getServer();
        state.set("loom_ready");
        state.set("nel_gone");
        com.arnir.storymod.story.ActTwo.tick(server);
        check(state.has("rift_open"), "rift did not open when the loom was ready and the stargazer settled");
        ServerPlayer p = player(helper, "");
        BlockPos a = state.anchor();
        p.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        p.setShiftKeyDown(true);
        com.arnir.storymod.story.ActTwo.tickPlayer(p);
        check(com.arnir.storymod.story.Underside.isHere(p), "sneaking at the lodestone did not lead across");
        p.moveTo(com.arnir.storymod.story.Underside.EXIT.getX() + 0.5, com.arnir.storymod.story.Underside.EXIT.getY(),
                com.arnir.storymod.story.Underside.EXIT.getZ() + 0.5);
        com.arnir.storymod.story.ActTwo.tickPlayer(p);
        check(p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD, "exit stone did not lead back");
        leave(helper, p);
        helper.succeed();
    }

    // ------------------------------------------------------------------ stories of far places

    private static void biome(GameTestHelper helper, BlockPos at, ResourceKey<net.minecraft.world.level.biome.Biome> key) {
        ServerLevel level = helper.getLevel();
        var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
        net.minecraft.server.commands.FillBiomeCommand.fill(level, at.offset(-6, -6, -6), at.offset(6, 6, 6), holder);
    }

    private static void moonNight(GameTestHelper helper, int phase) {
        ServerLevel level = helper.getLevel();
        long cycle = 24000L * 8;
        level.setDayTime((level.getDayTime() / cycle + 1) * cycle + 24000L * phase + 18000L);
    }

    @GameTest(template = T, batch = "story_56", timeoutTicks = 100)
    public static void farStoriesStartWhereTheyAreFoundAndKeepTheirPace(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        BlockPos at = p.blockPosition();
        biome(helper, at, net.minecraft.world.level.biome.Biomes.DESERT);
        day(helper, 18000);
        TriggerManager.resetPace(p);
        triggers(p, state);
        check(!state.has("op_desert_seen"), "the noon mirage came at night");
        day(helper, 6000);
        triggers(p, state);
        check(state.has("op_desert_seen"), "the desert at noon did not start its story");
        check(state.place("sayr") != null, "the place was not remembered");
        StoryNpc sayr = findNpc(helper, "sayr");
        check(sayr != null, "the mirage did not appear");
        // A second discovery right away is held back, then comes after a pause.
        biome(helper, at, net.minecraft.world.level.biome.Biomes.CHERRY_GROVE);
        triggers(p, state);
        check(!state.has("op_cherry_seen"), "two discoveries came in one breath");
        TriggerManager.resetPace(p);
        triggers(p, state);
        check(state.has("op_cherry_seen"), "the cherry grove did not start after the pause");
        // The journal remembers where.
        state.set("op_desert_met");
        String where = state.place("sayr").pos().getX() + " " + state.place("sayr").pos().getY();
        check(JournalManager.pagesFor(p).stream().anyMatch(pg -> pg.contains(where)), "journal has no coordinates");
        // Small moments: never in the first minutes, then at most one per long while.
        biome(helper, at, net.minecraft.world.level.biome.Biomes.TAIGA);
        TriggerManager.resetPace(p);
        for (int i = 0; i < 40; i++) {
            triggers(p, state);
        }
        check(!state.hasPersonal(p.getUUID(), "trigger_amb_taiga_feeder"), "an ambient moment came right after joining");
        p.tickCount = TriggerManager.AMBIENT_WARMUP + 1;
        for (int i = 0; i < 60 && !state.hasPersonal(p.getUUID(), "trigger_amb_taiga_feeder"); i++) {
            TriggerManager.resetPace(p);
            triggers(p, state);
        }
        check(state.hasPersonal(p.getUUID(), "trigger_amb_taiga_feeder"), "the taiga never said anything");
        p.getPersistentData().putLong("storymod_pace_any", 0L);
        biome(helper, at, net.minecraft.world.level.biome.Biomes.BIRCH_FOREST);
        for (int i = 0; i < 60; i++) {
            triggers(p, state);
        }
        check(!state.hasPersonal(p.getUUID(), "trigger_amb_birch_name"), "two ambient moments came too close");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_57")
    public static void desertMirageWaitsForSunset(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        StoryNpc sayr = npc(helper, "sayr");
        day(helper, 6000);
        expectNode(talk(p, sayr), "meet");
        expectNode(say(p, "Какой сейчас год"), "year");
        expectNode(say(p, "очень давно"), "denial");
        expectNode(say(p, "Хорошо"), "hub");
        check(!hasOption(p, "Вот часы"), "clock accepted without a clock");
        say(p, "Пока");
        give(p, new ItemStack(Items.CLOCK));
        expectNode(talk(p, sayr), "hub");
        expectNode(say(p, "Вот часы"), "clock");
        expectNode(say(p, "Где твой караван"), "caravan");
        check(state.has("op_desert_clock") && count(p, "minecraft:clock") == 0, "clock not taken");
        say(p, "Найду");
        triggers(p, state);
        check(!state.has("op_desert_tag"), "the tag was found outside the pyramid");
        state.set("op_desert_tag");
        expectNode(talk(p, sayr), "tag");
        expectNode(say(p, "Груз сдан"), "read");
        say(p, "Приду");
        expectNode(talk(p, sayr), "wait_sunset");
        DialogueSessions.forget(p);
        day(helper, 12500);
        expectNode(talk(p, sayr), "sunset");
        check(say(p, "Прощай") == null, "dialogue should close");
        check(state.has("outpost_done_desert") && count(p, "storymod:sayr_hourglass") == 1
                && count(p, "storymod:outpost_sign") == 1, "no reward from the desert");
        check(sayr.isRemoved(), "the mirage did not fade");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_58")
    public static void swampFerrywomanCarriesAVoice(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, PlayerRoles.WANDERER);
        StoryNpc tina = npc(helper, "tina");
        expectNode(talk(p, tina), "meet");
        expectNode(say(p, "Чьи голоса"), "voices");
        expectNode(say(p, "Чем я могу"), "task");
        expectNode(say(p, "Попробую"), "hub");
        check(!hasOption(p, "Вот три"), "lights accepted without lights");
        say(p, "Пока");
        give(p, new ItemStack(Items.OCHRE_FROGLIGHT));
        give(p, new ItemStack(Items.VERDANT_FROGLIGHT));
        give(p, new ItemStack(Items.PEARLESCENT_FROGLIGHT));
        expectNode(talk(p, tina), "hub");
        expectNode(say(p, "Вот три"), "lit");
        expectNode(say(p, "…"), "voice");
        expectNode(say(p, "голос прав"), "after_voice");
        check(say(p, "Спасибо") == null, "dialogue should close");
        check(state.has("outpost_done_swamp") && count(p, "storymod:tina_shell") == 1, "no shell");
        check(count(p, "minecraft:ochre_froglight") == 0, "lights not taken");
        // The shell talks over the silence.
        state.set("act2_start");
        state.setPersonal(p.getUUID(), "hollow_mark");
        com.arnir.storymod.story.ActTwo.tickPlayer(p);
        check(com.arnir.storymod.story.ActTwo.isCalm(p), "the shell did not calm the silence");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_59")
    public static void bellRingerRingsAtDawn(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        StoryNpc hald = npc(helper, "hald");
        expectNode(talk(p, hald), "meet");
        expectNode(say(p, "Что случилось"), "story");
        expectNode(say(p, "Как тебе помочь"), "task");
        expectNode(say(p, "Принесу"), "hub");
        say(p, "Пока");
        give(p, new ItemStack(Items.BELL));
        give(p, new ItemStack(Items.SOUL_CAMPFIRE));
        expectNode(talk(p, hald), "hub");
        expectNode(say(p, "Вот колокол"), "thaw");
        check(say(p, "Приду") == null, "dialogue should close");
        state.setPlace("hald", net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), p.blockPosition()));
        day(helper, 12000);
        triggers(p, state);
        check(!state.has("op_peaks_rang"), "the bell rang in the evening");
        day(helper, 23500);
        triggers(p, state);
        check(state.has("op_peaks_rang"), "the bell did not ring at dawn");
        expectNode(talk(p, hald), "rang");
        check(say(p, "Спасибо") == null, "dialogue should close");
        check(state.has("outpost_done_peaks") && count(p, "storymod:hald_tongue") == 1, "no clapper");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_60")
    public static void gardenBloomsAtFullMoon(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        StoryNpc seina = npc(helper, "seina");
        day(helper, 6000);
        expectNode(talk(p, seina), "meet");
        expectNode(say(p, "Сколько"), "count");
        expectNode(say(p, "Принесу"), "hub");
        check(!hasOption(p, "Вот пять"), "saplings accepted without saplings");
        say(p, "Пока");
        for (var item : List.of(Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING, Items.SPRUCE_SAPLING,
                Items.MANGROVE_PROPAGULE)) {
            give(p, new ItemStack(item));
        }
        expectNode(talk(p, seina), "hub");
        expectNode(say(p, "Вот пять"), "planted");
        check(say(p, "Приду") == null, "dialogue should close");
        moonNight(helper, 3);
        expectNode(talk(p, seina), "wait_moon");
        DialogueSessions.forget(p);
        moonNight(helper, 0);
        expectNode(talk(p, seina), "bloom");
        expectNode(say(p, "…"), "names");
        expectNode(say(p, "…"), "petal");
        check(say(p, "Спасибо") == null, "dialogue should close");
        check(state.has("outpost_done_cherry") && count(p, "storymod:seina_petal") == 1, "no petal");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_61")
    public static void chroniclerComparesThePages(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        StoryNpc moss = npc(helper, "moss");
        expectNode(talk(p, moss), "meet");
        expectNode(say(p, "Какую правду"), "truth");
        expectNode(say(p, "Почему"), "why");
        check(count(p, "minecraft:written_book") == 1, "no chronicle");
        expectNode(say(p, "Договорились"), "hub");
        check(!hasOption(p, "Я прочёл"), "pages accepted without pages");
        say(p, "Пока");
        state.set("page_founding");
        state.set("page_roster");
        state.set("page_letter");
        expectNode(talk(p, moss), "hub");
        expectNode(say(p, "Я прочёл"), "compare");
        expectNode(say(p, "Найду"), "spore");
        check(say(p, "Спасибо") == null, "dialogue should close");
        check(state.has("outpost_done_mushroom") && count(p, "storymod:moss_spore") == 1, "no spore");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_62")
    public static void captainRemembersHisCargo(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, PlayerRoles.WANDERER);
        StoryNpc varg = npc(helper, "varg");
        expectNode(talk(p, varg), "meet");
        expectNode(say(p, "Что случилось"), "wreck");
        expectNode(say(p, "Что был за груз"), "cargo");
        expectNode(say(p, "Я найду"), "hub");
        DialogueSessions.forget(p);
        triggers(p, state);
        check(!state.has("op_ship_logfound"), "the log was found away from the shore");
        state.set("op_ship_logfound");
        expectNode(talk(p, varg), "log");
        expectNode(say(p, "Читать"), "entry");
        expectNode(say(p, "холодеют"), "remember");
        check(say(p, "Прощай") == null, "dialogue should close");
        check(state.has("outpost_done_ship") && count(p, "storymod:varg_compass") == 1, "no compass");
        check(varg.isRemoved(), "the captain did not fade");
        state.set("ilva_met");
        StoryNpc ilva = npc(helper, "ilva");
        expectNode(talk(p, ilva), "hub");
        check(hasOption(p, "Капитан Варг"), "ilva is not asked about the captain");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_63")
    public static void hostessBargainAndRelease(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, PlayerRoles.KEEPER);
        StoryNpc veira = npc(helper, "veira");
        expectNode(talk(p, veira), "meet");
        expectNode(say(p, "не одна"), "two");
        expectNode(say(p, "Я останусь"), "stay");
        check(count(p, "storymod:veira_mirror") == 1 && state.hasPersonal(p.getUUID(), "hollow_mark"), "the bargain was not struck");
        check(!state.has("outpost_done_mansion"), "the bargain freed the outpost");
        say(p, "…");
        expectNode(talk(p, veira), "again");
        expectNode(say(p, "Хватит"), "fight");
        check(veira.isHostile(), "the silence did not fight");
        DialogueSessions.forget(p);
        veira.kill();
        check(state.has("veira_freed"), "veira_freed not set");
        StoryNpc freed = helper.getLevel().getEntities(com.arnir.storymod.registry.ModEntities.STORY_NPC.get(),
                e -> e.getCharacter().equals("veira") && e.isAlive() && e != veira).stream().findFirst().orElse(null);
        check(freed != null && !freed.isHostile(), "the hostess did not come back as herself");
        expectNode(talk(p, freed), "freed");
        check(!hasOption(p, "Возьми себя"), "a second mirror was offered");
        expectNode(say(p, "А зеркальце"), "mirror");
        check(say(p, "Спасибо") == null, "dialogue should close");
        check(state.has("outpost_done_mansion") && state.hasPersonal(p.getUUID(), "hollow_cured")
                && count(p, "storymod:veira_mirror") == 1, "release did not settle the bargain");
        leave(helper, p);
        helper.succeed();
    }

    @GameTest(template = T, batch = "story_64")
    public static void sevenSignsAndRumours(GameTestHelper helper) {
        StoryState state = freshState(helper);
        ServerPlayer p = player(helper, "");
        // Mira points to the first far place nobody has seen yet.
        state.setPersonal(p.getUUID(), "mira_met");
        StoryNpc mira = npc(helper, "mira");
        expectNode(talk(p, mira), "hub");
        expectNode(say(p, "дальних краях"), "far_desert");
        DialogueSessions.forget(p);
        state.set("op_desert_seen");
        expectNode(talk(p, mira), "hub");
        expectNode(say(p, "дальних краях"), "far_swamp");
        DialogueSessions.forget(p);
        mira.discard();
        // Os sews the banner from seven signs.
        for (String id : com.arnir.storymod.story.Outposts.IDS) {
            com.arnir.storymod.story.Outposts.complete(p, id);
        }
        check(state.has("outposts_all") && count(p, "storymod:outpost_sign") == 7, "seven signs not counted");
        state.set("os_met");
        StoryNpc os = npc(helper, "os");
        expectNode(talk(p, os), "hub");
        expectNode(say(p, "Расскажи о заставах"), "op_tales");
        expectNode(say(p, "Про Сайра"), "op_tale_desert");
        expectNode(say(p, "…"), "op_tales");
        expectNode(say(p, "Потом"), "op_back");
        expectNode(say(p, "…"), "hub");
        expectNode(say(p, "семь знаков"), "op_all");
        check(count(p, "storymod:seven_banner") == 1 && count(p, "storymod:outpost_sign") == 0, "no banner");
        expectNode(say(p, "Спасибо"), "op_back");
        leave(helper, p);
        helper.succeed();
    }
}
