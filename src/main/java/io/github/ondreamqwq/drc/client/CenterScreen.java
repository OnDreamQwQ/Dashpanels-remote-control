package io.github.ondreamqwq.drc.client;

import io.github.ondreamqwq.drc.DrcMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The center's own screen: one page per thing the network owns.
 *
 * <p>Panels are what a cockpit actually cares about, so that page is live - clicking a row picks the
 * panel the remote opens first. Remotes and players are listed as they are; editing them comes later.
 */
public class CenterScreen extends Screen {

    private static final int WIDTH = 260;
    private static final int HEIGHT = 176;
    private static final int TAB_HEIGHT = 18;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_TOP = 44;
    private static final int ROW_BOTTOM = 166;

    private static final int BACKDROP = 0xD8101014;
    private static final int EDGE = 0xFF585858;
    private static final int TAB_ACTIVE = 0xFF2E6F46;
    private static final int TAB_IDLE = 0xFF2A2A2E;
    private static final int ROW_IDLE = 0xFF1A1A1F;
    private static final int ROW_HOVER = 0xFF2A2A32;
    private static final int ROW_PICKED = 0xFF4A3F12;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFFFFC400;

    private final BlockPos center;
    private final List<String> tabs = List.of(
            Component.translatable("gui.dashpanels_remote_control.center.terminals").getString(),
            Component.translatable("gui.dashpanels_remote_control.center.remotes").getString(),
            Component.translatable("gui.dashpanels_remote_control.center.players").getString(),
            Component.translatable("gui.dashpanels_remote_control.center.consoles").getString());

    private final List<Row> panelRows = new ArrayList<>();
    private final List<Row> remoteRows = new ArrayList<>();
    private final List<Row> playerRows = new ArrayList<>();
    private final List<Row> consoleRows = new ArrayList<>();

    private int page;
    private int scroll;
    private int selected = -1;
    private boolean canOperate;
    private boolean isOwner;
    private net.minecraft.client.gui.components.EditBox nameBox;
    private int editBoxTop;

    private record Row(String text, String hint, BlockPos panel, int level, String subPath, String uuid) {
    }

    private String ownerName = "";

    public CenterScreen(BlockPos center, CompoundTag info) {
        super(Component.translatable("gui.dashpanels_remote_control.center.title"));
        this.center = center;
        read(info);
    }

    public static void open(BlockPos center, CompoundTag info) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new CenterScreen(center, info));
    }

    private void read(CompoundTag info) {
        panelRows.clear();
        remoteRows.clear();
        playerRows.clear();
        consoleRows.clear();
        canOperate = info.getBoolean("canOperate");
        isOwner = info.getBoolean("isOwner");
        selected = -1;

        ListTag panels = info.getList("terminals", Tag.TAG_COMPOUND);
        for (int i = 0; i < panels.size(); i++) {
            CompoundTag one = panels.getCompound(i);
            BlockPos pos = new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z"));
            int claimed = one.getInt("panels");
            String name = one.getString("name").trim();
            String label = name.isEmpty()
                    ? Component.translatable("gui.dashpanels_remote_control.terminal.unnamed").getString()
                    : name;
            label = label + "  (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            panelRows.add(new Row(label, claimed + " panel(s)", pos, -1, null, null));
            if (one.getBoolean("selected")) {
                selected = i;
            }
        }

        ListTag remotes = info.getList("remotes", Tag.TAG_COMPOUND);
        for (int i = 0; i < remotes.size(); i++) {
            CompoundTag one = remotes.getCompound(i);
            remoteRows.add(new Row(one.getString("name"), one.getBoolean("online") ? "online" : "offline", null, -1, null, null));
        }

        ownerName = info.getString("owner");
        ListTag access = info.getList("access", Tag.TAG_COMPOUND);
        for (int i = 0; i < access.size(); i++) {
            CompoundTag one = access.getCompound(i);
            playerRows.add(new Row(one.getString("name"), one.getString("level"), null,
                    one.getInt("level_id"), null, one.getString("uuid")));
        }
        playerRows.addFirst(new Row(ownerName, "owner", null, -1, null, null));

        ListTag consoles = info.getList("consoles", Tag.TAG_COMPOUND);
        for (int i = 0; i < consoles.size(); i++) {
            CompoundTag one = consoles.getCompound(i);
            BlockPos pos = new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z"));
            String label = one.getString("label");
            String where = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            consoleRows.add(new Row(label, where, pos, one.getInt("socket"), one.getString("sub_path"), null));
        }
    }

    private List<Row> rows() {
        return switch (page) {
            case 1 -> remoteRows;
            case 2 -> playerRows;
            case 3 -> consoleRows;
            default -> panelRows;
        };
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKDROP);
        graphics.renderOutline(x, y, WIDTH, HEIGHT, EDGE);

        graphics.drawString(font, title, x + 8, y + 7, ACCENT, false);

        int tabWidth = (WIDTH - 8 - (tabs.size() - 1) * 2) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 4 + i * (tabWidth + 2);
            int ty = y + 20;
            graphics.fill(tx, ty, tx + tabWidth, ty + TAB_HEIGHT, i == page ? TAB_ACTIVE : TAB_IDLE);
            graphics.renderOutline(tx, ty, tabWidth, TAB_HEIGHT, EDGE);
            int color = i == page ? 0xFFFFFFFF : TEXT_DIM;
            graphics.drawString(font, tabs.get(i), tx + 6, ty + 5, color, false);
        }

        List<Row> rows = rows();
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.dashpanels_remote_control.center.empty"),
                    x + 10, y + ROW_TOP + 4, TEXT_DIM, false);
        } else {
            int visible = (ROW_BOTTOM - ROW_TOP) / ROW_HEIGHT;
            scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
            for (int i = 0; i < visible && i + scroll < rows.size(); i++) {
                int index = i + scroll;
                Row row = rows.get(index);
                int ry = y + ROW_TOP + i * ROW_HEIGHT;
                boolean hovered = mouseX >= x + 4 && mouseX <= x + WIDTH - 4
                        && mouseY >= ry && mouseY < ry + ROW_HEIGHT - 1;
                int fill = index == selected ? ROW_PICKED : hovered ? ROW_HOVER : ROW_IDLE;
                graphics.fill(x + 4, ry, x + WIDTH - 4, ry + ROW_HEIGHT - 1, fill);
                if (index == selected) {
                    graphics.renderOutline(x + 4, ry, WIDTH - 8, ROW_HEIGHT - 1, ACCENT);
                }
                graphics.drawString(font, row.text(), x + 10, ry + 4, TEXT, false);
                if (row.hint() != null) {
                    int hintWidth = font.width(row.hint());
                    graphics.drawString(font, row.hint(), x + WIDTH - 12 - hintWidth, ry + 4, TEXT_DIM, false);
                }
            }
        }

        if (page == 0 && !canOperate) {
            graphics.drawString(font, Component.translatable("gui.dashpanels_remote_control.center.readonly"),
                    x + 8, y + HEIGHT - 12, TEXT_DIM, false);
        }

        if (page == 2) {
            drawPlayerEditor(graphics, x, y, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ players page

    private int editorTop() {
        return top() + HEIGHT - 20;
    }

    private void drawPlayerEditor(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (!isOwner) {
            graphics.drawString(font, Component.translatable("gui.dashpanels_remote_control.center.not_owner"),
                    x + 8, y + HEIGHT - 12, TEXT_DIM, false);
            return;
        }
        int row = editorTop();
        if (nameBox == null) {
            nameBox = new net.minecraft.client.gui.components.EditBox(font, x + 6, row, 96, 12,
                    Component.translatable("gui.dashpanels_remote_control.center.name_hint"));
            nameBox.setMaxLength(32);
            nameBox.setValue("");
        }
        nameBox.setPosition(x + 6, row);
        nameBox.render(graphics, mouseX, mouseY, 0.0F);

        button(graphics, x + 108, row, 60, 12,
                Component.translatable("gui.dashpanels_remote_control.center.add_operate"), mouseX, mouseY);
        button(graphics, x + 172, row, 60, 12,
                Component.translatable("gui.dashpanels_remote_control.center.add_edit"), mouseX, mouseY);
    }

    private void button(GuiGraphics graphics, int bx, int by, int bw, int bh, Component label,
                        int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? TAB_ACTIVE : TAB_IDLE);
        graphics.renderOutline(bx, by, bw, bh, EDGE);
        graphics.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 2, TEXT, false);
    }

    private void sendAccess(byte action, String name, int level, String uuid) {
        PacketDistributor.sendToServer(
                new io.github.ondreamqwq.drc.net.CenterAccessPacket(center, action, name, (byte) level, uuid));
        click();
    }

    private void addHovered(int level) {
        if (nameBox == null) {
            return;
        }
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        sendAccess(io.github.ondreamqwq.drc.net.CenterAccessPacket.ADD, name, level, "");
        nameBox.setValue("");
    }

    private void clickRows(double mouseX, double mouseY, int button) {
        int x = left();
        int y = top();
        int visible = (ROW_BOTTOM - ROW_TOP) / ROW_HEIGHT;
        if (page == 0 && button == 1) {
            // Right clicking a terminal names it; the remote then lists terminals by that name.
            for (int i = 0; i < visible; i++) {
                int index = i + scroll;
                if (index >= panelRows.size()) {
                    break;
                }
                int ry = y + ROW_TOP + i * ROW_HEIGHT;
                if (mouseX >= x + 4 && mouseX <= x + WIDTH - 4 && mouseY >= ry && mouseY < ry + ROW_HEIGHT - 1) {
                    Row row = panelRows.get(index);
                    TerminalRenameScreen.open(row.panel(), "");
                    return;
                }
            }
            return;
        }
        for (int i = 0; i < visible; i++) {
            int index = i + scroll;
            if (index >= playerRows.size()) {
                break;
            }
            int ry = y + ROW_TOP + i * ROW_HEIGHT;
            if (mouseX < x + 4 || mouseX > x + WIDTH - 4 || mouseY < ry || mouseY >= ry + ROW_HEIGHT - 1) {
                continue;
            }
            Row row = playerRows.get(index);
            if (index == 0 || !isOwner) {
                return;
            }
            if (button == 1) {
                sendAccess(io.github.ondreamqwq.drc.net.CenterAccessPacket.REMOVE, row.text(), 0, row.uuid());
            } else {
                int wanted = row.level() == 0 ? 1 : 0;
                sendAccess(io.github.ondreamqwq.drc.net.CenterAccessPacket.SET_LEVEL, row.text(), wanted, row.uuid());
            }
            return;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left();
        int y = top();
        int tabWidth = (WIDTH - 8 - (tabs.size() - 1) * 2) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 4 + i * (tabWidth + 2);
            int ty = y + 20;
            if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= ty && mouseY <= ty + TAB_HEIGHT) {
                page = i;
                scroll = 0;
                click();
                return true;
            }
        }

        if (page == 0) {
            int visible = (ROW_BOTTOM - ROW_TOP) / ROW_HEIGHT;
            for (int i = 0; i < visible; i++) {
                int index = i + scroll;
                if (index >= panelRows.size()) {
                    break;
                }
                int ry = y + ROW_TOP + i * ROW_HEIGHT;
                if (mouseX >= x + 4 && mouseX <= x + WIDTH - 4 && mouseY >= ry && mouseY < ry + ROW_HEIGHT - 1) {
                    click();
                    BlockPos panel = panelRows.get(index).panel();
                    if (panel != null && canOperate) {
                        selected = index;
                        PacketDistributor.sendToServer(
                                new io.github.ondreamqwq.drc.net.CenterSelectPacket(center, panel));
                    }
                    return true;
                }
            }
        }
        if (page == 3) {
            int visible = (ROW_BOTTOM - ROW_TOP) / ROW_HEIGHT;
            for (int i = 0; i < visible; i++) {
                int index = i + scroll;
                if (index >= consoleRows.size()) {
                    break;
                }
                int ry = y + ROW_TOP + i * ROW_HEIGHT;
                if (mouseX >= x + 4 && mouseX <= x + WIDTH - 4 && mouseY >= ry && mouseY < ry + ROW_HEIGHT - 1) {
                    click();
                    Row row = consoleRows.get(index);
                    if (row.panel() != null && canOperate) {
                        // Same as clicking a console in the remote: start operating it, not open a menu.
                        Minecraft.getInstance().setScreen(null);
                        PacketDistributor.sendToServer(
                                new io.github.ondreamqwq.drc.net.ConsoleUsePacket(center, row.panel()));
                    }
                    return true;
                }
            }
        }
        if (page == 2) {
            int row = editorTop();
            if (isOwner) {
                if (nameBox != null && nameBox.isMouseOver(mouseX, mouseY)) {
                    nameBox.setFocused(true);
                    return nameBox.mouseClicked(mouseX, mouseY, button);
                }
                if (mouseX >= x + 108 && mouseX <= x + 168 && mouseY >= row && mouseY <= row + 12) {
                    addHovered(io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity.ACCESS_OPERATE);
                    return true;
                }
                if (mouseX >= x + 172 && mouseX <= x + 232 && mouseY >= row && mouseY <= row + 12) {
                    addHovered(io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity.ACCESS_EDIT);
                    return true;
                }
            }
            clickRows(mouseX, mouseY, button);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && nameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused()) {
            if (nameBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {   // enter / keypad enter
                addHovered(io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity.ACCESS_OPERATE);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    private void click() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.0F);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The panels underneath are the point; dimming the world would only get in the way.
    }
}
