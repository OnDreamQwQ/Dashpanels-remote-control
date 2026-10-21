package io.github.ondreamqwq.drc.client;

import io.github.ondreamqwq.drc.net.RenameTerminalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Renames one panel-link terminal.
 *
 * <p>It is opened by the server - by right clicking the terminal itself, or from the center's terminal
 * page - so the client never has to guess whether the player may touch that terminal.
 */
public class TerminalRenameScreen extends Screen {

    private static final int WIDTH = 220;
    private static final int HEIGHT = 76;

    private final BlockPos terminal;
    private final String current;
    private EditBox nameBox;

    private TerminalRenameScreen(BlockPos terminal, String current) {
        super(Component.translatable("gui.dashpanels_remote_control.terminal.title"));
        this.terminal = terminal;
        this.current = current;
    }

    public static void open(BlockPos terminal, String current) {
        Minecraft.getInstance().setScreen(new TerminalRenameScreen(terminal, current));
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;

        nameBox = new EditBox(font, left + 12, top + 26, WIDTH - 24, 18,
                Component.translatable("gui.dashpanels_remote_control.terminal.hint"));
        nameBox.setMaxLength(32);
        nameBox.setValue(current);
        nameBox.setFocused(true);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.dashpanels_remote_control.terminal.save"),
                        button -> save())
                .bounds(left + 12, top + 52, 96, 18)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.dashpanels_remote_control.terminal.cancel"),
                        button -> onClose())
                .bounds(left + WIDTH - 108, top + 52, 96, 18)
                .build());
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.length() > 32) {
            name = name.substring(0, 32);
        }
        PacketDistributor.sendToServer(new RenameTerminalPacket(terminal, name));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Left transparent on purpose: the player keeps sight of the terminal they are naming.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xD8101014);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, 0xFF585858);
        graphics.drawString(font, Component.translatable("gui.dashpanels_remote_control.terminal.title"),
                left + 12, top + 10, 0xFFE8E8E8, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
