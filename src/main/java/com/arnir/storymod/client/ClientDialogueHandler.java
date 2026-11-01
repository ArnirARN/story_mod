package com.arnir.storymod.client;

import com.arnir.storymod.network.OpenDialoguePayload;
import com.arnir.storymod.network.OpenJournalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Only ever called on the client. */
public final class ClientDialogueHandler {
    private ClientDialogueHandler() {
    }

    public static void open(OpenDialoguePayload payload) {
        Minecraft.getInstance().setScreen(new DialogueScreen(payload));
    }

    public static void openJournal(OpenJournalPayload payload) {
        List<Component> pages = new ArrayList<>();
        for (String page : payload.pages()) {
            pages.add(Component.literal(page));
        }
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DialogueScreen screen) {
            screen.closeSilently();
        }
    }
}
