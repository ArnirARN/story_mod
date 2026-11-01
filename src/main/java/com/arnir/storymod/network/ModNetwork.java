package com.arnir.storymod.network;

import com.arnir.storymod.client.ClientDialogueHandler;
import com.arnir.storymod.dialogue.DialogueSessions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Client handlers are wrapped in lambdas so the dedicated server never loads client classes.
        registrar.playToClient(OpenDialoguePayload.TYPE, OpenDialoguePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientDialogueHandler.open(payload)));
        registrar.playToClient(CloseDialoguePayload.TYPE, CloseDialoguePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(ClientDialogueHandler::close));
        registrar.playToServer(ChooseOptionPayload.TYPE, ChooseOptionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DialogueSessions.choose(player, payload.npcId(), payload.option());
                    }
                }));
    }
}
