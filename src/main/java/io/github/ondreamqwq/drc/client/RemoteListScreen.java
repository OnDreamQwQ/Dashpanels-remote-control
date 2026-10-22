package io.github.ondreamqwq.drc.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The list of terminals one remote center serves.
 *
 * <p>Only terminals: a page is a terminal, and the player asked to switch between pages, not to be moved
 * onto a single panel inside one.
 */
public class RemoteListScreen extends Screen {

    private static final int WIDTH = 268;
    private static final int HEIGHT = 186;
    private static final int ROW = 18;
    private static final int TOP = 30;
    private static final int BOTTOM = 158;
    private static final int PICKED = 0xFF4A3F12;
    private static final int HOVER = 0xFF2A2A32;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFFB8B8B8;
    private static final int ACCENT = 0xFFFFC400;

    private final PanelRemoteScreen remote;
    private final List<String> rows = new ArrayList<>();
    private final int currentPage;
    private int scroll;

    public RemoteListScreen(PanelRemoteScreen remote, List<PanelRemoteScreen.Page> pages, int page) {
        super(Component.translatable("gui.dashpanels_remote_control.remote.list"));
        this.remote = remote;
        this.currentPage = page;
        for (PanelRemoteScreen.Page one : pages) {
            String label = one.label();
            rows.add((label == null || label.isBlank())
                    ? Component.translatable("gui.dashpanels_remote_control.terminal.unnamed").getString()
                    : label);
        }
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.dashpanels_remote_control.remote.list.back"),
                        button -> Minecraft.getInstance().setScreen(remote))
                .bounds(left + WIDTH - 74, top + HEIGHT - 24, 66, 18)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The world stays visible; this is a small index, not a wall of stone.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        // The screen behind is not rendered while this one is open, so draw the world view read-only
        // underneath: the player keeps the picture they were looking at while choosing a page.
        remote.render(graphics, -1000, -1000, partialTick);
        // Modules queue their vertices on the game's shared source, which is submitted after this menu
        // unless it is drained first - otherwise the panels are painted on top of the list.
        PanelRemoteScreen.beginOverlay(graphics);

        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF0101014);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, 0xFF585858);
        graphics.drawString(font, title, left + 8, top + 8, TEXT, false);

        int visible = (BOTTOM - TOP) / ROW;
        int index = hovered(mouseX, mouseY, left, top, visible);
        for (int i = 0; i < visible; i++) {
            int at = i + scroll;
            if (at >= rows.size()) {
                break;
            }
            int y = top + TOP + i * ROW;
            boolean onThisPage = at == currentPage;
            if (at == index) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 2, HOVER);
            } else if (onThisPage) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 2, PICKED);
            }
            graphics.drawString(font, rows.get(at), left + 10, y + 5, onThisPage ? ACCENT : TEXT, false);
            if (onThisPage) {
                graphics.drawString(font, "*", left + WIDTH - 14, y + 5, ACCENT, false);
            }
        }
        graphics.flush();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Row index the mouse is over, or -1. */
    private int hovered(double mouseX, double mouseY, int left, int top, int visible) {
        for (int i = 0; i < visible; i++) {
            int at = i + scroll;
            if (at >= rows.size()) {
                break;
            }
            int y = top + TOP + i * ROW;
            if (mouseX >= left + 4 && mouseX <= left + WIDTH - 4 && mouseY >= y && mouseY < y + ROW - 2) {
                return at;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        int visible = (BOTTOM - TOP) / ROW;
        int at = hovered(mouseX, mouseY, left, top, visible);
        if (at >= 0 && button == 0) {
            remote.setPage(at, null);
            Minecraft.getInstance().setScreen(remote);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = (BOTTOM - TOP) / ROW;
        scroll = net.minecraft.util.Mth.clamp(scroll - (int) Math.signum(scrollY), 0,
                Math.max(0, rows.size() - visible));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
