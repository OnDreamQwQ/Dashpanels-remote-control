package io.github.ondreamqwq.drc.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Picks which terminal a remote control is pinned to.
 *
 * <p>Uses the hand-drawn {@code terminal_select.png}. The two button slots that texture already
 * reserves (one per row, one in the bottom bar) get Create's own 18x18 button so they look and
 * behave like every other button in the pack.
 */
public class TerminalPickerScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/terminal_select.png");
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/widgets.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png");

    private static final int W = 220;
    private static final int H = 158;
    private static final int ROW_X = 13;
    private static final int ROW_W = 194;
    private static final int ROW_H = 32;
    private static final int ROW_U = 0;
    private static final int ROW_V = 156;
    private static final int ROW_FIRST = 26;
    private static final int ROW_STEP = 35;
    private static final int LIST_TOP = 16;
    private static final int LIST_BOTTOM = 127;

    /** 18x18 Create button sitting in the slot the row art already has. */
    private static final int ROW_BTN_DX = 169;
    private static final int ROW_BTN_DY = 8;
    /** 18x18 Create button sitting in the bottom bar slot. */
    private static final int EXIT_BTN_X = 195;
    private static final int EXIT_BTN_Y = 134;

    private static final int BUTTON = 18;
    private static final int CHECK_U = 0;
    private static final int CHECK_V = 16;
    private static final int CLOSE_U = 16;
    private static final int CLOSE_V = 144;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFF9A9A9A;

    private final Screen parent;
    private final BlockPos center;
    private final List<CenterScreen.TerminalRow> rows;
    private int scroll;

    public TerminalPickerScreen(Screen parent, BlockPos center, List<CenterScreen.TerminalRow> rows) {
        super(Component.translatable("gui.dashpanels_remote_control.center.pick_terminal"));
        this.parent = parent;
        this.center = center;
        this.rows = rows;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int maxScroll() {
        int content = rows.size() * ROW_STEP;
        int view = LIST_BOTTOM - LIST_TOP;
        return Math.max(0, content - view);
    }

    private int rowTop(int index) {
        return top() + ROW_FIRST + index * ROW_STEP - scroll;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // the world and the panel stay visible behind the picker
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        graphics.blit(TEXTURE, x, y, 0, 0, W, H);

        Component title = getTitle();
        graphics.drawString(font, title, x + 8, y + 4, 0xFF592424, false);

        scroll = Mth.clamp(scroll, 0, maxScroll());
        int rowX = x + ROW_X;
        graphics.enableScissor(x, top() + LIST_TOP, x + W, top() + LIST_BOTTOM);
        for (int i = 0; i < rows.size(); i++) {
            int ry = rowTop(i);
            if (ry + ROW_H <= top() + LIST_TOP || ry >= top() + LIST_BOTTOM) {
                continue;
            }
            CenterScreen.TerminalRow row = rows.get(i);
            boolean hovered = mouseX >= rowX && mouseX < rowX + ROW_W && mouseY >= ry && mouseY < ry + ROW_H;
            graphics.blit(TEXTURE, rowX, ry, ROW_U, ROW_V, ROW_W, ROW_H);
            if (hovered) {
                graphics.fill(rowX + 2, ry + 2, rowX + ROW_W - 2, ry + ROW_H - 2, 0x30FFFFFF);
            }
            String name = row.name().isEmpty()
                    ? Component.translatable("gui.dashpanels_remote_control.terminal.unnamed").getString()
                    : row.name();
            graphics.drawString(font, name, rowX + 8, ry + 7, hovered ? 0xFFFFC400 : TEXT, false);
            graphics.drawString(font,
                    Component.translatable("gui.dashpanels_remote_control.center.panel_count", row.panels())
                            .getString() + "  " + row.pos().toShortString(),
                    rowX + 8, ry + 19, DIM, false);
            button(graphics, rowX + ROW_BTN_DX, ry + ROW_BTN_DY, CHECK_U, CHECK_V, hovered);
        }
        graphics.disableScissor();

        if (rows.isEmpty()) {
            Component empty = Component.translatable("gui.dashpanels_remote_control.center.empty");
            graphics.drawString(font, empty, x + (W - font.width(empty)) / 2, top() + 68, DIM, false);
        }

        boolean exitHover = hover(mouseX, mouseY, x + EXIT_BTN_X, y + EXIT_BTN_Y, BUTTON, BUTTON);
        button(graphics, x + EXIT_BTN_X, y + EXIT_BTN_Y, CLOSE_U, CLOSE_V, exitHover);
    }

    private void button(GuiGraphics graphics, int bx, int by, int iconU, int iconV, boolean hot) {
        graphics.blit(WIDGETS, bx, by, hot ? BUTTON : 0, 0, BUTTON, BUTTON);
        graphics.blit(ICONS, bx + 1, by + 1, iconU, iconV, 16, 16, 256, 256);
    }

    private static boolean hover(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left();
        int y = top();
        if (hover(mouseX, mouseY, x + EXIT_BTN_X, y + EXIT_BTN_Y, BUTTON, BUTTON)) {
            click();
            onClose();
            return true;
        }
        if (mouseY < top() + LIST_TOP || mouseY >= top() + LIST_BOTTOM) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int rowX = x + ROW_X;
        for (int i = 0; i < rows.size(); i++) {
            int ry = rowTop(i);
            if (mouseX >= rowX && mouseX < rowX + ROW_W && mouseY >= ry && mouseY < ry + ROW_H) {
                click();
                PacketDistributor.sendToServer(
                        new io.github.ondreamqwq.drc.net.RemoteSelectPacket(center, rows.get(i).pos()));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = (int) Mth.clamp(scroll - scrollY * ROW_STEP / 2.0, 0, maxScroll());
        return true;
    }

    private void click() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.0F);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
