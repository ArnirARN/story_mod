package com.arnir.storymod.client;

import com.arnir.storymod.dialogue.DialogueSessions;
import com.arnir.storymod.network.ChooseOptionPayload;
import com.arnir.storymod.network.OpenDialoguePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Dialogue window: speaker name, text that types itself out, and numbered answer buttons. */
public class DialogueScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 420;
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 3;
    private static final float CHARS_PER_TICK = 1.6F;

    private final OpenDialoguePayload payload;
    private final List<Button> optionButtons = new ArrayList<>();
    private float revealed;
    private boolean answered;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public DialogueScreen(OpenDialoguePayload payload) {
        super(Component.literal(payload.speaker()));
        this.payload = payload;
    }

    private boolean fullyRevealed() {
        return revealed >= payload.text().length();
    }

    private List<FormattedCharSequence> wrap(String text) {
        return this.font.split(Component.literal(text), panelWidth - PADDING * 2);
    }

    @Override
    protected void init() {
        optionButtons.clear();
        panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 20);
        int textHeight = wrap(payload.text()).size() * (this.font.lineHeight + 1);
        int options = Math.max(1, payload.options().size());
        int buttonsHeight = options * (BUTTON_HEIGHT + BUTTON_GAP);
        panelHeight = PADDING + this.font.lineHeight + 6 + textHeight + 8 + buttonsHeight + PADDING;
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(10, this.height - panelHeight - 16);

        int y = panelY + PADDING + this.font.lineHeight + 6 + textHeight + 8;
        int buttonWidth = panelWidth - PADDING * 2;
        if (payload.options().isEmpty()) {
            addOption(Component.literal("[Уйти]"), DialogueSessions.CLOSE, y, buttonWidth);
        } else {
            for (int i = 0; i < payload.options().size(); i++) {
                Component label = Component.literal((i + 1) + ". " + payload.options().get(i));
                addOption(label, payload.optionIds().get(i), y, buttonWidth);
                y += BUTTON_HEIGHT + BUTTON_GAP;
            }
        }
        updateButtons();
    }

    private void addOption(Component label, int optionId, int y, int width) {
        Button button = Button.builder(label, b -> choose(optionId))
                .bounds(panelX + PADDING, y, width, BUTTON_HEIGHT)
                .build();
        optionButtons.add(addRenderableWidget(button));
    }

    private void updateButtons() {
        boolean ready = fullyRevealed();
        for (Button b : optionButtons) {
            b.visible = ready;
        }
    }

    private void choose(int optionId) {
        if (answered) {
            return;
        }
        answered = true;
        PacketDistributor.sendToServer(new ChooseOptionPayload(payload.npcId(), optionId));
        if (optionId == DialogueSessions.CLOSE) {
            closeSilently();
        }
        // Otherwise the server answers with the next node or a close packet.
    }

    @Override
    public void tick() {
        super.tick();
        if (!fullyRevealed()) {
            int before = (int) revealed;
            revealed = Math.min(payload.text().length(), revealed + CHARS_PER_TICK);
            if ((int) revealed / 6 != before / 6 && this.minecraft != null) {
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                        .forUI(SoundEvents.BOOK_PAGE_TURN, 1.6F, 0.15F));
            }
            updateButtons();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Plain dim instead of the 1.21 menu blur, so the world stays readable behind the dialogue.
        this.renderTransparentBackground(g);
        g.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF8A7A55);
        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xEE15121C);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = panelX + PADDING;
        int y = panelY + PADDING;
        g.drawString(this.font, payload.speaker(), x, y, 0xFFE8C872, true);
        y += this.font.lineHeight + 6;
        String shown = payload.text().substring(0, Math.min(payload.text().length(), (int) revealed));
        for (FormattedCharSequence line : wrap(shown)) {
            g.drawString(this.font, line, x, y, 0xFFE6E2D8, false);
            y += this.font.lineHeight + 1;
        }
        if (!fullyRevealed()) {
            g.drawString(this.font, "▼", panelX + panelWidth - PADDING - 6, panelY + panelHeight - PADDING - 8,
                    0xFF8A7A55, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!fullyRevealed()) {
            revealed = payload.text().length();
            updateButtons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!fullyRevealed() && (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER)) {
            revealed = payload.text().length();
            updateButtons();
            return true;
        }
        if (fullyRevealed() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (payload.options().isEmpty() && index == 0) {
                choose(DialogueSessions.CLOSE);
                return true;
            }
            if (index < payload.optionIds().size()) {
                choose(payload.optionIds().get(index));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!answered) {
            answered = true;
            PacketDistributor.sendToServer(new ChooseOptionPayload(payload.npcId(), DialogueSessions.CLOSE));
        }
        super.onClose();
    }

    /** Closes without telling the server (used when the server closed the dialogue itself). */
    public void closeSilently() {
        answered = true;
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
