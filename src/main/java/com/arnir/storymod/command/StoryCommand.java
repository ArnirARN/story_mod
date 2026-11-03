package com.arnir.storymod.command;

import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.story.PlayerRoles;
import com.arnir.storymod.story.StoryEvents;
import com.arnir.storymod.story.StoryState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Admin tools (permission level 2). Warning: /story flags shows story progress, which can spoil the plot.
 * <ul>
 *   <li>/story flags</li>
 *   <li>/story flag set|clear &lt;flag&gt;</li>
 *   <li>/story spawn &lt;character&gt; — respawn a character next to you (if one got lost)</li>
 *   <li>/story role &lt;player&gt; wanderer|keeper|none — personal story role</li>
 *   <li>/story reset — wipe all story progress and characters, the opening replays on next login</li>
 * </ul>
 */
public final class StoryCommand {
    public static final List<String> CHARACTERS = List.of("veld", "os", "ash", "irmen", "mira");

    private StoryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("story")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("flags").executes(ctx -> {
                    StoryState state = StoryState.get(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Флаги сюжета: " + String.join(", ", state.flags())), false);
                    return state.flags().size();
                }))
                .then(Commands.literal("flag")
                        .then(Commands.literal("set").then(Commands.argument("flag", StringArgumentType.word()).executes(ctx -> {
                            String flag = StringArgumentType.getString(ctx, "flag");
                            StoryState.get(ctx.getSource().getServer()).set(flag);
                            ctx.getSource().sendSuccess(() -> Component.literal("Флаг установлен: " + flag), true);
                            return 1;
                        })))
                        .then(Commands.literal("clear").then(Commands.argument("flag", StringArgumentType.word()).executes(ctx -> {
                            String flag = StringArgumentType.getString(ctx, "flag");
                            StoryState.get(ctx.getSource().getServer()).clear(flag);
                            ctx.getSource().sendSuccess(() -> Component.literal("Флаг снят: " + flag), true);
                            return 1;
                        }))))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("character", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    CHARACTERS.forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String character = StringArgumentType.getString(ctx, "character");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                    StoryNpc.spawn(level, character, StoryEvents.groundNear(level, pos, 2), 0.0F);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Персонаж призван: " + character), true);
                                    return 1;
                                })))
                .then(Commands.literal("role")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            List.of(PlayerRoles.WANDERER, PlayerRoles.KEEPER, "none").forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            String role = StringArgumentType.getString(ctx, "role");
                                            StoryState.get(ctx.getSource().getServer()).setRole(target.getUUID(), role);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Роль " + target.getGameProfile().getName() + ": " + role), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("reset").executes(ctx -> {
                    StoryState state = StoryState.get(ctx.getSource().getServer());
                    state.reset();
                    CHARACTERS.forEach(state::bumpGeneration);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Сюжет сброшен. Вступление начнётся заново при следующем входе игрока."), true);
                    return 1;
                })));
    }
}
