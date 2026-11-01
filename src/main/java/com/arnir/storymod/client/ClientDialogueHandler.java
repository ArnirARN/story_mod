package com.arnir.storymod.client;

import com.arnir.storymod.network.OpenDialoguePayload;
import net.minecraft.client.Minecraft;

/** Only ever called on the client. */
public final class ClientDialogueHandler {
    private ClientDialogueHandler() {
    }

    public static void open(OpenDialoguePayload payload) {
        Minecraft.getInstance().setScreen(new DialogueScreen(payload));
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DialogueScreen screen) {
            screen.closeSilently();
        }
    }
}
