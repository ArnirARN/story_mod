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
                ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace(table)));
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
        check(count(w, "storymod:twilight_amulet") == 1, "wanderer has no amulet");
        check(count(k, "storymod:keeper_wrench") == 1, "keeper has no wrench");
        check(count(w, "storymod:keeper_wrench") == 0 && count(k, "storymod:twilight_amulet") == 0, "kits mixed up");
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

        w.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.TWILIGHT_AMULET.get()));
        ModItems.TWILIGHT_AMULET.get().use(helper.getLevel(), w, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(w.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "amulet did not hide its owner");
        k.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.TWILIGHT_AMULET.get()));
        ModItems.TWILIGHT_AMULET.get().use(helper.getLevel(), k, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(!k.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "amulet worked for the wrong player");

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
}
