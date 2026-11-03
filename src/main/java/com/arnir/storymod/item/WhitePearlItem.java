package com.arnir.storymod.item;

import com.arnir.storymod.registry.ModItems;
import com.arnir.storymod.story.PlayerItems;
import com.arnir.storymod.story.PlayerRoles;
import com.arnir.storymod.story.StoryState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

/**
 * The wanderer's personal keepsake.
 * <ul>
 *   <li>Sneak + use on a block: lift it and carry it inside the pearl. Use on a block again: set it down.</li>
 *   <li>Use in the air: step into the shadows for a few seconds.</li>
 *   <li>Carried or worn: sometimes slips away from arrows and other projectiles, ender pearls do not hurt,
 *   faster in darkness.</li>
 * </ul>
 * Works only for the wanderer.
 */
public class WhitePearlItem extends Item {
    public static final int STEP_COOLDOWN = 20 * 40;
    public static final int DODGE_COOLDOWN = 20 * 3;
    private static final String CARRIED = "carried_block";
    private static final String LAST_DODGE = "storymod_last_dodge";
    /** Chance to slip away from a projectile. Tests set it to 1. */
    public static float dodgeChance = 0.5F;

    public WhitePearlItem(Properties properties) {
        super(properties);
    }

    public static boolean isOwner(Player player) {
        return player instanceof ServerPlayer sp
                && StoryState.get(sp.getServer()).role(sp.getUUID()).equals(PlayerRoles.WANDERER);
    }

    private static void refuse(ServerPlayer player) {
        player.displayClientMessage(Component.literal("Жемчужина холодная и молчит.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    // ---- Carrying blocks

    public static BlockState carried(ItemStack stack, Level level) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(CARRIED)) {
            return null;
        }
        CompoundTag tag = data.copyTag().getCompound(CARRIED);
        BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), tag);
        return state.isAir() ? null : state;
    }

    private static void setCarried(ItemStack stack, BlockState state) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (state == null) {
                tag.remove(CARRIED);
            } else {
                tag.put(CARRIED, NbtUtils.writeBlockState(state));
            }
        });
    }

    /** Blocks the pearl refuses: containers, unbreakable and two-part blocks, liquids. */
    public static boolean canLift(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.liquid() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0 || state.is(BlockTags.WITHER_IMMUNE) || state.is(BlockTags.DRAGON_IMMUNE)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !state.hasProperty(BlockStateProperties.BED_PART)
                && !state.hasProperty(BlockStateProperties.EXTENDED);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!isOwner(player)) {
            refuse(player);
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        BlockState held = carried(stack, level);
        if (held != null) {
            return place(context, player, serverLevel, stack, held);
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return lift(player, serverLevel, context.getClickedPos(), stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    public static boolean lift(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        if (!canLift(level, pos, state) || !level.mayInteract(player, pos) || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) {
            return false;
        }
        BlockState keep = state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.levelEvent(2001, pos, Block.getId(state));
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.4F);
        setCarried(stack, keep);
        player.getCooldowns().addCooldown(stack.getItem(), 10);
        return true;
    }

    private static InteractionResult place(UseOnContext context, ServerPlayer player, ServerLevel level, ItemStack stack, BlockState held) {
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        BlockPos pos = placeContext.getClickedPos();
        if (!level.getBlockState(pos).canBeReplaced(placeContext)) {
            return InteractionResult.FAIL;
        }
        return place(player, level, pos, stack, held) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    public static boolean place(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack, BlockState held) {
        BlockState state = Block.updateFromNeighbourShapes(held, level, pos);
        if (!level.mayInteract(player, pos) || !state.canSurvive(level, pos)
                || !level.isUnobstructed(state, pos, CollisionContext.empty())) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && level.getFluidState(pos).isSource()
                && level.getFluidState(pos).getType() == net.minecraft.world.level.material.Fluids.WATER) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, true);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
        level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.3);
        setCarried(stack, null);
        player.getCooldowns().addCooldown(stack.getItem(), 10);
        return true;
    }

    // ---- Shadow step

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!isOwner(sp)) {
            refuse(sp);
            return InteractionResultHolder.fail(stack);
        }
        if (sp.isShiftKeyDown() || carried(stack, level) != null) {
            return InteractionResultHolder.pass(stack);
        }
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 8, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 8, 1, false, false, true));
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 50, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.6F);
        sp.getCooldowns().addCooldown(this, STEP_COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    // ---- Passive powers

    /** Called every two seconds while carried or worn. */
    public static void pulse(ServerPlayer player) {
        if (!isOwner(player)) {
            return;
        }
        int light = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
        int sky = player.level().getBrightness(LightLayer.SKY, player.blockPosition()) - player.level().getSkyDarken();
        if (Math.max(light, sky) <= 5) {
            MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SPEED);
            if (current == null || current.getDuration() <= ArtifactItem.PULSE_TICKS) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ArtifactItem.PULSE_TICKS, 0, true, false, true));
            }
        }
    }

    public static boolean protects(ServerPlayer player) {
        return isOwner(player) && PlayerItems.has(player, ModItems.WHITE_PEARL.get());
    }

    /** A projectile is about to hit: maybe slip away like the endermen do. Returns true if dodged. */
    public static boolean tryDodge(ServerPlayer player) {
        if (!protects(player)) {
            return false;
        }
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(LAST_DODGE);
        if (last != 0 && now - last < DODGE_COOLDOWN && now >= last) {
            return false;
        }
        if (player.getRandom().nextFloat() >= dodgeChance) {
            return false;
        }
        if (!slipAway(player)) {
            return false;
        }
        player.getPersistentData().putLong(LAST_DODGE, now);
        return true;
    }

    public static boolean slipAway(ServerPlayer player) {
        double x0 = player.getX(), y0 = player.getY(), z0 = player.getZ();
        for (int i = 0; i < 16; i++) {
            double x = x0 + (player.getRandom().nextDouble() - 0.5) * 12.0;
            double y = y0 + (player.getRandom().nextInt(7) - 3);
            double z = z0 + (player.getRandom().nextDouble() - 0.5) * 12.0;
            if (Math.abs(x - x0) + Math.abs(z - z0) < 2.0) {
                continue;
            }
            if (player.randomTeleport(x, y, z, true)) {
                player.level().playSound(null, x0, y0, z0, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.2F);
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.PORTAL, x0, y0 + 1, z0, 30, 0.3, 0.6, 0.3, 0.3);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.storymod.white_pearl.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.storymod.white_pearl.effect").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.storymod.white_pearl.effect2").withStyle(ChatFormatting.BLUE));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(CARRIED) && context.registries() != null) {
            try {
                BlockState state = NbtUtils.readBlockState(context.registries().lookupOrThrow(Registries.BLOCK),
                        data.copyTag().getCompound(CARRIED));
                tooltip.add(Component.translatable("item.storymod.white_pearl.holding", state.getBlock().getName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } catch (RuntimeException ignored) {
                // Unknown block from a removed mod: nothing to show.
            }
        }
    }
}
