package com.arnir.storymod.dialogue;

import com.arnir.storymod.entity.StoryNpc;
import com.arnir.storymod.network.CloseDialoguePayload;
import com.arnir.storymod.network.OpenDialoguePayload;
import com.arnir.storymod.story.StoryState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side state of the conversation each player is currently in. */
public final class DialogueSessions {
    /** Option index meaning "the player closed the window". */
    public static final int CLOSE = -1;
    private static final double MAX_DISTANCE_SQ = 10.0 * 10.0;

    private record Session(int npcId, String character, String node, List<String> labels, List<Integer> ids) {
    }

    /** What the player currently sees: option index → label. Empty if not in a conversation. */
    public record View(int npcId, String character, String node, List<String> labels, List<Integer> ids) {
    }

    public static View view(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : new View(s.npcId(), s.character(), s.node(), s.labels(), s.ids());
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DialogueSessions() {
    }

    public static void start(ServerPlayer player, StoryNpc npc) {
        Dialogue dialogue = DialogueManager.get(npc.getCharacter());
        if (dialogue == null) {
            player.sendSystemMessage(Component.literal("…"));
            return;
        }
        StoryState state = StoryState.get(player.getServer());
        for (Dialogue.Entry entry : dialogue.entries()) {
            if (entry.condition().test(player, state)) {
                show(player, npc, dialogue, entry.node());
                return;
            }
        }
    }

    private static void show(ServerPlayer player, StoryNpc npc, Dialogue dialogue, String nodeId) {
        Dialogue.Node node = dialogue.nodes().get(nodeId);
        if (node == null) {
            end(player);
            return;
        }
        for (Action action : node.actions()) {
            action.run(player, npc);
        }
        StoryState state = StoryState.get(player.getServer());
        List<String> labels = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < node.options().size(); i++) {
            Dialogue.Option option = node.options().get(i);
            if (option.condition().test(player, state)) {
                labels.add(option.text());
                ids.add(i);
            }
        }
        String speaker = node.speaker() != null ? node.speaker() : dialogue.speaker();
        String text = node.text().replace("{player}", player.getGameProfile().getName());
        SESSIONS.put(player.getUUID(), new Session(npc.getId(), npc.getCharacter(), nodeId, List.copyOf(labels), List.copyOf(ids)));
        send(player, new OpenDialoguePayload(npc.getId(), speaker, text, labels, ids));
    }

    public static void choose(ServerPlayer player, int npcId, int optionIndex) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.npcId() != npcId) {
            return;
        }
        if (optionIndex == CLOSE) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        Entity entity = player.serverLevel().getEntity(npcId);
        if (!(entity instanceof StoryNpc npc) || !npc.isAlive() || npc.isHostile()
                || npc.distanceToSqr(player) > MAX_DISTANCE_SQ) {
            end(player);
            return;
        }
        Dialogue dialogue = DialogueManager.get(session.character());
        Dialogue.Node node = dialogue == null ? null : dialogue.nodes().get(session.node());
        if (node == null || optionIndex < 0 || optionIndex >= node.options().size()) {
            end(player);
            return;
        }
        Dialogue.Option option = node.options().get(optionIndex);
        if (!option.condition().test(player, StoryState.get(player.getServer()))) {
            // The world changed since the window opened (for example a friend took the item). Re-show the node.
            show(player, npc, dialogue, session.node());
            return;
        }
        SESSIONS.remove(player.getUUID());
        for (Action action : option.actions()) {
            action.run(player, npc);
        }
        if (option.next() == null || npc.isRemoved()) {
            end(player);
        } else {
            show(player, npc, dialogue, option.next());
        }
    }

    public static void end(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        send(player, new CloseDialoguePayload());
    }

    /** Sends a packet, tolerating clients without the mod's channel (for example game-test mock players). */
    public static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException e) {
            com.arnir.storymod.StoryMod.LOGGER.debug("Could not send {} to {}", payload.type().id(), player.getGameProfile().getName());
        }
    }

    public static void forget(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }
}
