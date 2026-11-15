package com.arnir.storymod.registry;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.item.ArtifactItem;
import com.arnir.storymod.item.EffectItem;
import com.arnir.storymod.item.KeeperWrenchItem;
import com.arnir.storymod.item.TwilightAmuletItem;
import net.minecraft.world.level.Level;
import com.arnir.storymod.item.JournalItem;
import com.arnir.storymod.item.WhiteEyeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StoryMod.MODID);

    public static final DeferredItem<Item> CORE_DEPTHS = core("core_depths");
    public static final DeferredItem<Item> CORE_FLAME = core("core_flame");
    public static final DeferredItem<Item> CORE_TIDES = core("core_tides");
    public static final DeferredItem<Item> CORE_VOID = core("core_void");

    public static final DeferredItem<EffectItem> DAWN_STAR = ITEMS.register("dawn_star",
            () -> new EffectItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), 300,
                    ChatFormatting.GOLD,
                    new EffectItem.Effect(MobEffects.REGENERATION, 30, 1),
                    new EffectItem.Effect(MobEffects.NIGHT_VISION, 120, 0),
                    new EffectItem.Effect(MobEffects.DAMAGE_RESISTANCE, 30, 0)));

    public static final DeferredItem<EffectItem> KEEPER_GEAR = ITEMS.register("keeper_gear",
            () -> new EffectItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant(), 180,
                    ChatFormatting.AQUA,
                    new EffectItem.Effect(MobEffects.DIG_SPEED, 120, 1),
                    new EffectItem.Effect(MobEffects.MOVEMENT_SPEED, 60, 0)));

    public static final DeferredItem<WhiteEyeItem> WHITE_EYE = ITEMS.register("white_eye",
            () -> new WhiteEyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<JournalItem> JOURNAL = ITEMS.register("journal",
            () -> new JournalItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TwilightAmuletItem> TWILIGHT_AMULET = ITEMS.register("twilight_amulet",
            () -> new TwilightAmuletItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<com.arnir.storymod.item.WhitePearlItem> WHITE_PEARL = ITEMS.register("white_pearl",
            () -> new com.arnir.storymod.item.WhitePearlItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<KeeperWrenchItem> KEEPER_WRENCH = ITEMS.register("keeper_wrench",
            () -> new KeeperWrenchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    // Trinkets found in chests: work while carried.
    public static final DeferredItem<ArtifactItem> ZENITH_RING = artifact("zenith_ring", MobEffects.REGENERATION, 0,
            p -> p.level().dimension() == Level.OVERWORLD && p.level().getDayTime() % 24000L < 12300L);
    public static final DeferredItem<ArtifactItem> ECHO_PENDANT = artifact("echo_pendant", MobEffects.NIGHT_VISION, 0,
            p -> p.getY() < 0);
    public static final DeferredItem<ArtifactItem> GUARDIAN_SCALE = artifact("guardian_scale", MobEffects.WATER_BREATHING, 0,
            p -> p.isUnderWater());
    public static final DeferredItem<ArtifactItem> FORGE_EMBER = artifact("forge_ember", MobEffects.FIRE_RESISTANCE, 0,
            p -> p.level().dimension() == Level.NETHER);
    public static final DeferredItem<ArtifactItem> VOID_FEATHER = artifact("void_feather", MobEffects.SLOW_FALLING, 0,
            p -> p.isShiftKeyDown() && !p.onGround() && p.fallDistance > 2.0F);
    public static final DeferredItem<ArtifactItem> ORDER_MEDALLION = artifact("order_medallion", MobEffects.LUCK, 0,
            p -> true);

    // Personal weapons: full power only for their role.
    public static final DeferredItem<com.arnir.storymod.item.RoleWeaponItem> WHITE_GREATSWORD = ITEMS.register("white_greatsword",
            () -> new com.arnir.storymod.item.RoleWeaponItem(net.minecraft.world.item.Tiers.NETHERITE, new Item.Properties()
                    .rarity(Rarity.EPIC).fireResistant()
                    .component(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true))
                    .attributes(net.minecraft.world.item.SwordItem.createAttributes(net.minecraft.world.item.Tiers.NETHERITE, 7, -3.0F)),
                    com.arnir.storymod.story.PlayerRoles.WANDERER));
    public static final DeferredItem<com.arnir.storymod.item.RoleWeaponItem> KEEPER_HAMMER = ITEMS.register("keeper_hammer",
            () -> new com.arnir.storymod.item.RoleWeaponItem(net.minecraft.world.item.Tiers.NETHERITE, new Item.Properties()
                    .rarity(Rarity.EPIC).fireResistant()
                    .component(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true))
                    .attributes(net.minecraft.world.item.SwordItem.createAttributes(net.minecraft.world.item.Tiers.NETHERITE, 6, -3.2F)),
                    com.arnir.storymod.story.PlayerRoles.KEEPER));
    // Bound marks: cannot be thrown away, stay after death; a gift and a burden at once.
    public static final DeferredItem<com.arnir.storymod.item.BoundMarkItem> END_MARK = ITEMS.register("end_mark",
            () -> new com.arnir.storymod.item.BoundMarkItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                    com.arnir.storymod.story.PlayerRoles.WANDERER));
    public static final DeferredItem<com.arnir.storymod.item.BoundMarkItem> COPPER_OATH = ITEMS.register("copper_oath",
            () -> new com.arnir.storymod.item.BoundMarkItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                    com.arnir.storymod.story.PlayerRoles.KEEPER));

    // A sword of the old Order, found in ruins.
    public static final DeferredItem<net.minecraft.world.item.SwordItem> ORDER_BLADE = ITEMS.register("order_blade",
            () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.IRON, new Item.Properties().rarity(Rarity.RARE)
                    .attributes(net.minecraft.world.item.SwordItem.createAttributes(net.minecraft.world.item.Tiers.IRON, 4, -2.2F))));

    // More trinkets for the random pool.
    public static final DeferredItem<ArtifactItem> BERSERKER_CHARM = artifact("berserker_charm", MobEffects.DAMAGE_BOOST, 0,
            p -> p.getHealth() <= p.getMaxHealth() * 0.3F);
    public static final DeferredItem<ArtifactItem> STONE_HEART = artifact("stone_heart", MobEffects.DAMAGE_RESISTANCE, 0,
            p -> p.isShiftKeyDown());
    public static final DeferredItem<ArtifactItem> STORM_FEATHER = artifact("storm_feather", MobEffects.MOVEMENT_SPEED, 0,
            p -> p.level().isRainingAt(p.blockPosition().above()));

    // Quest rewards (not in the random pool).
    public static final DeferredItem<ArtifactItem> CLOCKWORK_HEART = artifact("clockwork_heart", MobEffects.DIG_SPEED, 0,
            p -> true);
    public static final DeferredItem<ArtifactItem> ZENITH_SHARD = artifact("zenith_shard", MobEffects.JUMP, 1,
            p -> p.isSprinting());

    // Second season: falling stars and the silence behind the sky.
    public static final DeferredItem<com.arnir.storymod.item.LoreItem> STAR_SHARD = ITEMS.register("star_shard",
            () -> new com.arnir.storymod.item.LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant(), false));
    public static final DeferredItem<com.arnir.storymod.item.LoreItem> STAR_CORE = ITEMS.register("star_core",
            () -> new com.arnir.storymod.item.LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), true));
    public static final DeferredItem<com.arnir.storymod.item.StarEyepieceItem> STAR_EYEPIECE = ITEMS.register("star_eyepiece",
            () -> new com.arnir.storymod.item.StarEyepieceItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<com.arnir.storymod.item.QuietDrinkItem> QUIET_TEA = ITEMS.register("quiet_tea",
            () -> new com.arnir.storymod.item.QuietDrinkItem(new Item.Properties().stacksTo(16), false));
    public static final DeferredItem<com.arnir.storymod.item.QuietDrinkItem> QUIET_TINCTURE = ITEMS.register("quiet_tincture",
            () -> new com.arnir.storymod.item.QuietDrinkItem(new Item.Properties().stacksTo(4).rarity(Rarity.RARE), true));
    public static final DeferredItem<com.arnir.storymod.item.LoreItem> ILVA_THREAD = ITEMS.register("ilva_thread",
            () -> new com.arnir.storymod.item.LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), true));
    public static final DeferredItem<com.arnir.storymod.item.LoomShuttleItem> LOOM_SHUTTLE = ITEMS.register("loom_shuttle",
            () -> new com.arnir.storymod.item.LoomShuttleItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<ArtifactItem> SILENT_BELL = artifact("silent_bell", MobEffects.INVISIBILITY, 0,
            p -> p.isShiftKeyDown() && p.level().getDayTime() % 24000L >= 13000L && p.level().getDayTime() % 24000L <= 23000L);
    public static final DeferredItem<ArtifactItem> COMET_TAIL = artifact("comet_tail", MobEffects.MOVEMENT_SPEED, 1,
            p -> p.level().dimension() == Level.OVERWORLD && p.level().getDayTime() % 24000L >= 13000L
                    && p.level().getDayTime() % 24000L <= 23000L);
    public static final DeferredItem<ArtifactItem> SEAM_NEEDLE = artifact("seam_needle", MobEffects.DAMAGE_RESISTANCE, 1,
            p -> p.getHealth() <= p.getMaxHealth() * 0.25F);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STAR_LANTERN = ITEMS.registerSimpleBlockItem("star_lantern",
            ModBlocks.STAR_LANTERN);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FALLEN_STAR = ITEMS.registerSimpleBlockItem("fallen_star",
            ModBlocks.FALLEN_STAR);

    // Rewards of the stories that live in far places (any order, any chapter).
    public static final DeferredItem<com.arnir.storymod.item.LoreItem> OUTPOST_SIGN = ITEMS.register("outpost_sign",
            () -> new com.arnir.storymod.item.LoreItem(new Item.Properties().stacksTo(7).rarity(Rarity.RARE).fireResistant(), true));
    public static final DeferredItem<ArtifactItem> SAYR_HOURGLASS = artifact("sayr_hourglass", MobEffects.DIG_SPEED, 1,
            p -> p.level().dimension() == Level.OVERWORLD && p.level().getDayTime() % 24000L < 12300L);
    public static final DeferredItem<ArtifactItem> TINA_SHELL = artifact("tina_shell", MobEffects.CONDUIT_POWER, 0,
            p -> p.isUnderWater());
    public static final DeferredItem<ArtifactItem> HALD_TONGUE = artifact("hald_tongue", MobEffects.DAMAGE_RESISTANCE, 0,
            p -> p.level().getBiome(p.blockPosition()).value().coldEnoughToSnow(p.blockPosition()));
    public static final DeferredItem<ArtifactItem> SEINA_PETAL = artifact("seina_petal", MobEffects.REGENERATION, 0,
            p -> p.getHealth() < p.getMaxHealth() * 0.5F && p.tickCount - p.getLastHurtByMobTimestamp() > 100);
    public static final DeferredItem<ArtifactItem> MOSS_SPORE = artifact("moss_spore", MobEffects.SATURATION, 0,
            p -> p.getFoodData().getFoodLevel() < 6);
    public static final DeferredItem<ArtifactItem> VARG_COMPASS = artifact("varg_compass", MobEffects.DOLPHINS_GRACE, 0,
            p -> p.isInWater());
    public static final DeferredItem<ArtifactItem> VEIRA_MIRROR = artifact("veira_mirror", MobEffects.DAMAGE_BOOST, 0,
            p -> p.level().getMaxLocalRawBrightness(p.blockPosition()) < 7);
    public static final DeferredItem<ArtifactItem> SEVEN_BANNER = artifact("seven_banner", MobEffects.HERO_OF_THE_VILLAGE, 1,
            p -> true);

    public static final List<String> ARTIFACT_IDS = List.of("zenith_ring", "echo_pendant", "guardian_scale",
            "forge_ember", "void_feather", "order_medallion", "berserker_charm", "stone_heart", "storm_feather",
            "silent_bell", "comet_tail", "seam_needle");

    private static DeferredItem<ArtifactItem> artifact(String name, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                                       int amplifier, java.util.function.Predicate<net.minecraft.server.level.ServerPlayer> when) {
        return ITEMS.register(name, () -> new ArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant(),
                effect, amplifier, when));
    }

    /** Ids of the four cores, used for story flags ("found_core_depths", ...). */
    public static final List<String> CORE_IDS = List.of("core_depths", "core_flame", "core_tides", "core_void");

    private static DeferredItem<Item> core(String name) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    private ModItems() {
    }
}
