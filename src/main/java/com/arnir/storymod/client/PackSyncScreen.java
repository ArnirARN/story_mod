package com.arnir.storymod.client;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.sync.PackSync;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Shown over the title screen while the mod pack downloads, and to ask for a restart afterwards. */
public class PackSyncScreen extends Screen {
    private static boolean shown;

    private final Screen parent;
    private PackSync.Status lastStatus;

    public PackSyncScreen(Screen parent) {
        super(Component.literal("Story Mod"));
        this.parent = parent;
    }

    @EventBusSubscriber(modid = StoryMod.MODID, value = Dist.CLIENT)
    public static final class Hook {
        private Hook() {
        }

        @SubscribeEvent
        public static void onOpen(ScreenEvent.Opening event) {
            if (shown || !(event.getNewScreen() instanceof TitleScreen title)) {
                return;
            }
            PackSync.Status s = PackSync.status();
            if (s == PackSync.Status.OFF || s == PackSync.Status.UP_TO_DATE) {
                return;
            }
            shown = true;
            event.setNewScreen(new PackSyncScreen(title));
        }
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        lastStatus = PackSync.status();
        int y = height / 2 + 30;
        switch (lastStatus) {
            case RESTART -> {
                addRenderableWidget(Button.builder(Component.literal("Выйти из игры"), b -> minecraft.stop())
                        .bounds(width / 2 - 100, y, 200, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Играть без новых модов"), b -> onClose())
                        .bounds(width / 2 - 100, y + 24, 200, 20).build());
            }
            case RUNNING -> addRenderableWidget(Button.builder(Component.literal("Пропустить"), b -> onClose())
                    .bounds(width / 2 - 100, y, 200, 20).build());
            default -> addRenderableWidget(Button.builder(Component.literal("Продолжить"), b -> onClose())
                    .bounds(width / 2 - 100, y, 200, 20).build());
        }
    }

    @Override
    public void tick() {
        if (PackSync.status() != lastStatus) {
            rebuild();
        }
        if (PackSync.status() == PackSync.Status.UP_TO_DATE) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int y = height / 2 - 40;
        String[] lines = switch (PackSync.status()) {
            case RUNNING -> new String[]{"Скачиваю моды сборки…", PackSync.done() + " из " + Math.max(PackSync.total(), 1)};
            case RESTART -> new String[]{"Сборка скачана и обновлена.", "Перезапустите игру, чтобы новые моды загрузились."};
            case NEEDS_NEOFORGE -> new String[]{"Для сборки нужен NeoForge " + PackSync.detail() + " или новее.",
                    "Выберите его в лаунчере и запустите игру снова."};
            case FAILED -> new String[]{"Не получилось скачать сборку.", "Играем с теми модами, что уже есть. " + PackSync.detail()};
            default -> new String[]{""};
        };
        for (String line : lines) {
            g.drawCenteredString(font, line, width / 2, y, 0xFFFFFF);
            y += 14;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return PackSync.status() != PackSync.Status.RUNNING;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
