package io.github.ondreamqwq.drc.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The list of everything one remote center serves: terminals down the side, and one row per panel or
 * console inside them. Picking a row jumps the remote to that page.
 */
public class RemoteListScreen extends Screen {

    private static final int WIDTH = 268;
    private static final int HEIGHT = 186;
    private static final int ROW = 14;
    private static final int TOP = 30;
    private static final int BOTTOM = 158;

    private final PanelRemoteScreen remote;
    private final List<PanelRemoteScreen.Page> pages;
    private final List<Row> rows = new ArrayList<>();
    private final int wrapped;
    private int scroll;

    private record Row(String header, String label, int index) {
    }

    public RemoteListScreen(PanelRemoteScreen remote, List<PanelRemoteScreen.Page> pages, int page) {
        super(Component.translatable("gui.dashpanels_remote_control.remote.list"));
        this.remote = remote;
        this.pages = pages;
        this.wrapped = page;

        String last = null;
        for (int i = 0; i < pages.size(); i++) {
            PanelRemoteScreen.Page one = pages.get(i);
            if (!one.terminal().equals(last)) {
                last = one.terminal();
                rows.add(new Row(last, "", -1));
            }
            String kind = one.console() ? "控制台" : "面板";
            rows.add(new Row("", kind + " " + one.label(), i));
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

        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF0101014);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, 0xFF585858);
        graphics.drawString(font, title, left + 8, top + 8, 0xFFE8E8E8, false);

        int visible = (BOTTOM - TOP) / ROW;
        for (int i = 0; i < visible; i++) {
            int index = i + scroll;
            if (index >= rows.size()) {
                break;
            }
            Row row = rows.get(index);
            int y = top + TOP + i * ROW;
            if (row.index() < 0) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 1, 0xFF1E1E24);
                graphics.drawString(font, row.header(), left + 8, y + 3, 0xFFB8B8B8, false);
                continue;
            }
            int color = 0xFFE8E8E8;
            if (row.index() == wrapped) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 1, 0xFF4A3F12);
                color = 0xFFFFC400;
            } else if (mouseX >= left + 4 && mouseX <= left + WIDTH - 4 && mouseY >= y && mouseY < y + ROW - 1) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 1, 0xFF2A2A32);
            }
            graphics.drawString(font, row.label(), left + 14, y + 3, color, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        int visible = (BOTTOM - TOP) / ROW;
        for (int i = 0; i < visible; i++) {
            int index = i + scroll;
            if (index >= rows.size()) {
                break;
            }
            Row row = rows.get(index);
            int y = top + TOP + i * ROW;
            if (row.index() < 0) {
                continue;
            }
            if (mouseX >= left + 4 && mouseX <= left + WIDTH - 4 && mouseY >= y && mouseY < y + ROW - 1) {
                remote.setPage(row.index());
                Minecraft.getInstance().setScreen(remote);
                return true;
            }
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
