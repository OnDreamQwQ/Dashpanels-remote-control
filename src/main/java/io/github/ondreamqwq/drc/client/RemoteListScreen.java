package io.github.ondreamqwq.drc.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The index of everything one remote center serves.
 *
 * <p>One block of the list per terminal, then a row for each panel and console that terminal feeds.
 * Picking the header shows the whole terminal; picking a row shows the same terminal with the camera
 * centred on that block. That way the assembly is never cut apart, and every panel is still one click
 * away - which is what the player asked for.
 */
public class RemoteListScreen extends Screen {

    private static final int WIDTH = 268;
    private static final int HEIGHT = 186;
    private static final int ROW = 14;
    private static final int TOP = 30;
    private static final int BOTTOM = 158;
    private static final int HEADER = 0xFF1E1E24;
    private static final int PICKED = 0xFF4A3F12;
    private static final int HOVER = 0xFF2A2A32;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFFB8B8B8;
    private static final int ACCENT = 0xFFFFC400;

    private final PanelRemoteScreen remote;
    private final List<Row> rows = new ArrayList<>();
    private final int currentPage;
    private int scroll;

    /** {@code header} marks a terminal title row; {@code focus} is the block to centre on, if any. */
    private record Row(String header, String label, int page, BlockPos focus) {
    }

    public RemoteListScreen(PanelRemoteScreen remote, List<PanelRemoteScreen.Page> pages, int page) {
        super(Component.translatable("gui.dashpanels_remote_control.remote.list"));
        this.remote = remote;
        this.currentPage = page;
        for (int i = 0; i < pages.size(); i++) {
            PanelRemoteScreen.Page one = pages.get(i);
            rows.add(new Row(one.label(), "", i, null));
            for (BlockPos pos : one.panels()) {
                rows.add(new Row(null, Component.translatable(
                        "gui.dashpanels_remote_control.remote.row.panel", format(pos)).getString(), i, pos));
            }
            for (BlockPos pos : one.consoles()) {
                rows.add(new Row(null, Component.translatable(
                        "gui.dashpanels_remote_control.remote.row.console", format(pos)).getString(), i, pos));
            }
        }
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
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
        graphics.drawString(font, title, left + 8, top + 8, TEXT, false);

        int visible = (BOTTOM - TOP) / ROW;
        int index = hovered(mouseX, mouseY, left, top, visible);
        for (int i = 0; i < visible; i++) {
            int at = i + scroll;
            if (at >= rows.size()) {
                break;
            }
            Row row = rows.get(at);
            int y = top + TOP + i * ROW;
            boolean onThisPage = row.page() == currentPage;
            if (row.header() != null) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 1, HEADER);
                graphics.drawString(font, row.header(), left + 8, y + 3, onThisPage ? ACCENT : DIM, false);
                continue;
            }
            if (at == index) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 1, HOVER);
            } else if (onThisPage) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 1, PICKED);
            }
            graphics.drawString(font, row.label(), left + 16, y + 3, onThisPage ? ACCENT : TEXT, false);
        }
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
            if (mouseX >= left + 4 && mouseX <= left + WIDTH - 4 && mouseY >= y && mouseY < y + ROW - 1) {
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
            Row row = rows.get(at);
            remote.setPage(row.page(), row.focus());
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
