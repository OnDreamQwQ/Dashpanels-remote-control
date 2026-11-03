package io.github.ondreamqwq.drc.client;

import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import io.github.ondreamqwq.drc.net.CenterAccessPacket;
import io.github.ondreamqwq.drc.net.CenterForgetRemotePacket;
import io.github.ondreamqwq.drc.net.CenterRemoveTerminalPacket;
import io.github.ondreamqwq.drc.net.RemoteSelectPacket;
import io.github.ondreamqwq.drc.net.RenameTerminalPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The center's screen, painted from hand-drawn sheets.
 *
 * <p>Window chrome is sliced out of {@code connect_center.png} (title strip, the darker band the
 * tabs sit in, and the six-row divider); the content area is the same 1px green checkerboard the
 * artist painted, drawn with fill instead of a blit so the window can be any height. Inside it the
 * tabs, the row buttons, the info strip and the bottom edit bar are sliced out of
 * {@code control_center_02.png} at the coordinates measured off the sheet.
 */
public class CenterScreen extends Screen {

    private static final ResourceLocation WINDOW =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/connect_center.png");
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/control_center_02.png");
    private static final ResourceLocation HEADS =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private static final int W = 220;
    private static final int H = 182;

    private static final int BAND_Y = 16;
    private static final int BAND_H = 16;
    private static final int DIVIDER_Y = 32;
    private static final int DIVIDER_H = 6;
    private static final int CONTENT_Y = 38;

    private static final int TAB_Y = 18;
    private static final int TAB_W = 28;
    private static final int TAB_H = 13;
    private static final int SEG_U_DARK = 1;
    private static final int SEG_U_LIT = 30;
    private static final int SEG_V = 72;

    private static final int ROW_H = 22;
    private static final int BTN_W = 28;
    private static final int BTN_H = 13;
    private static final int BAR_H = 22;

    private static final int FILL_A = 0xFF006C58;
    private static final int FILL_B = 0xFF007E68;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFAFC9C4;
    private static final int ACCENT = 0xFFFFC400;
    private static final int ONLINE = 0xFF8BE88B;
    private static final int MUTED = 0xFF7A8F8A;

    private static final byte ACCESS_OPERATE = PanelRemoteCenterBlockEntity.ACCESS_OPERATE;
    private static final byte ACCESS_EDIT = PanelRemoteCenterBlockEntity.ACCESS_EDIT;

    private final BlockPos center;
    private final List<String> tabs = List.of(
            "gui.dashpanels_remote_control.center.terminals",
            "gui.dashpanels_remote_control.center.remotes",
            "gui.dashpanels_remote_control.center.players");

    /** Kept across reopenings so handing out a right does not throw the owner back to page one. */
    private static int lastPage;

    private int page;
    private int scroll;
    private boolean canEdit;
    private boolean isOwner;
    private String network = "";
    private String ownerName = "";

    private final List<TerminalRow> terminals = new ArrayList<>();
    private final List<RemoteRow> remotes = new ArrayList<>();
    private final List<AccessRow> accesses = new ArrayList<>();

    /** Which terminal the remote page's assign button points at; picked on the terminal page. */
    private int picked = -1;

    private EditBox input;
    /** Set while the bottom bar is renaming this terminal. */
    private BlockPos renaming;

    private record TerminalRow(BlockPos pos, String name, int panels) {
    }

    private record RemoteRow(String name, String owner, boolean online, String id) {
    }

    private record AccessRow(String name, int level, String id) {
    }

    public CenterScreen(BlockPos center, CompoundTag info) {
        super(Component.translatable("gui.dashpanels_remote_control.center.title"));
        this.center = center;
        this.page = Mth.clamp(lastPage, 0, tabs.size() - 1);
        read(info);
    }

    public BlockPos center() {
        return center;
    }

    /** A fresh snapshot arrived: repaint in place, keeping the page and the scroll position. */
    public void update(CompoundTag info) {
        read(info);
    }

    public static void open(BlockPos center, CompoundTag info) {
        Minecraft.getInstance().setScreen(new CenterScreen(center, info));
    }

    /** Shows a new name right away, before the server's snapshot confirms it. */
    public void setPendingName(BlockPos terminal, String name) {
        for (int i = 0; i < terminals.size(); i++) {
            TerminalRow row = terminals.get(i);
            if (row.pos().equals(terminal)) {
                terminals.set(i, new TerminalRow(row.pos(), name, row.panels()));
                return;
            }
        }
    }

    private void read(CompoundTag info) {
        network = info.getString("network");
        canEdit = info.getBoolean("canEdit");
        isOwner = info.getBoolean("isOwner");
        ownerName = info.getString("owner");

        terminals.clear();
        ListTag list = info.getList("terminals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            terminals.add(new TerminalRow(
                    new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z")),
                    one.getString("name"), one.getInt("panels")));
        }

        remotes.clear();
        list = info.getList("remotes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            remotes.add(new RemoteRow(one.getString("name"), one.getString("owner"),
                    one.getBoolean("online"), one.getString("id")));
        }

        accesses.clear();
        list = info.getList("access", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            accesses.add(new AccessRow(one.getString("name"), one.getInt("level_id"),
                    one.getString("id")));
        }
        if (picked >= terminals.size()) {
            picked = -1;
        }
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int listTop() {
        return top() + CONTENT_Y + 4;
    }

    private int listBottom() {
        return top() + H - BAR_H - 10;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    private int rowCount() {
        return switch (page) {
            case 0 -> terminals.size();
            case 1 -> remotes.size();
            default -> accesses.size();
        };
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();

        graphics.blit(WINDOW, x, y, 0, 0, W, BAND_Y + 1);
        graphics.blit(WINDOW, x, y + BAND_Y, 0, BAND_Y, W, BAND_H);

        for (int cy = CONTENT_Y; cy < H - 4; cy++) {
            for (int cx = 1; cx < W - 1; cx++) {
                graphics.fill(x + cx, y + cy, x + cx + 1, y + cy + 1,
                        ((cx + cy) & 1) == 0 ? FILL_A : FILL_B);
            }
        }

        graphics.blit(WINDOW, x, y + DIVIDER_Y, 0, DIVIDER_Y, W, DIVIDER_H);

        graphics.drawString(font, title, x + 8, y + 5, TEXT, false);
        if (!network.isEmpty()) {
            String id = "#" + network;
            graphics.drawString(font, id, x + W - 8 - font.width(id), y + 5, DIM, false);
        }

        drawTabs(graphics, mouseX, mouseY, x, y);

        int top = listTop();
        int bottom = listBottom();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rowCount() - visibleRows()));
        graphics.enableScissor(x + 2, top, x + W - 2, bottom);
        for (int i = 0; i < visibleRows(); i++) {
            int index = i + scroll;
            if (index >= rowCount()) {
                break;
            }
            drawRow(graphics, index, x + 6, top + i * ROW_H, mouseX, mouseY);
        }
        graphics.disableScissor();

        if (rowCount() == 0) {
            Component empty = Component.translatable("gui.dashpanels_remote_control.center.empty");
            graphics.drawString(font, empty, x + (W - font.width(empty)) / 2, top + 18, DIM, false);
        }

        drawBottomBar(graphics, mouseX, mouseY, x, y);
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 6 + i * (TAB_W + 2);
            int ty = y + TAB_Y;
            boolean lit = page == i;
            graphics.blit(WIDGETS, tx, ty, lit ? SEG_U_LIT : SEG_U_DARK, SEG_V, TAB_W, TAB_H);
            if (!lit && hover(mouseX, mouseY, tx, ty, TAB_W, TAB_H)) {
                graphics.fill(tx + 1, ty + 1, tx + TAB_W - 1, ty + TAB_H - 1, 0x33FFFFFF);
            }
            Component label = Component.translatable(tabs.get(i));
            graphics.drawString(font, label, tx + (TAB_W - font.width(label)) / 2, ty + 3,
                    lit ? 0xFF16321A : DIM, false);
        }
    }

    private void drawRow(GuiGraphics graphics, int index, int x, int y, int mouseX, int mouseY) {
        if (mouseY >= y && mouseY < y + ROW_H - 2) {
            graphics.fill(x - 2, y, x + W - 10, y + ROW_H - 2, 0x26FFFFFF);
        }
        int bx2 = x + W - 16 - BTN_W;
        int bx1 = bx2 - BTN_W - 3;
        switch (page) {
            case 0 -> {
                TerminalRow row = terminals.get(index);
                String name = row.name().isEmpty()
                        ? Component.translatable("gui.dashpanels_remote_control.terminal.unnamed").getString()
                        : row.name();
                graphics.drawString(font, name, x, y + 2, picked == index ? ACCENT : TEXT, false);
                String meta = Component.translatable("gui.dashpanels_remote_control.center.panel_count",
                        row.panels()).getString() + " · " + row.pos().toShortString();
                graphics.drawString(font, meta, x, y + 12, DIM, false);
                segment(graphics, bx1, y + 4, "gui.dashpanels_remote_control.center.rename", mouseX, mouseY,
                        canEdit && renaming == null);
                segment(graphics, bx2, y + 4, "gui.dashpanels_remote_control.center.remove_terminal",
                        mouseX, mouseY, canEdit);
            }
            case 1 -> {
                RemoteRow row = remotes.get(index);
                graphics.drawString(font, row.name(), x, y + 2, TEXT, false);
                String meta = Component.translatable("gui.dashpanels_remote_control.center.remote_meta",
                        row.owner().isEmpty() ? "-" : row.owner(),
                        row.id().isEmpty() ? "-" : row.id()).getString();
                graphics.drawString(font, meta, x, y + 12, row.online() ? ONLINE : DIM, false);
                segment(graphics, bx1, y + 4, "gui.dashpanels_remote_control.center.assign", mouseX, mouseY,
                        picked >= 0);
                segment(graphics, bx2, y + 4, "gui.dashpanels_remote_control.center.forget", mouseX, mouseY,
                        canEdit);
            }
            default -> {
                AccessRow row = accesses.get(index);
                graphics.blit(HEADS, x, y + 3, 8, 8, 8, 8, 64, 64);
                graphics.drawString(font, row.name(), x + 12, y + 2, TEXT, false);
                String meta = Component.translatable(levelKey(row.level())).getString() + " · "
                        + (row.id().isEmpty() ? "-" : row.id());
                graphics.drawString(font, meta, x + 12, y + 12, DIM, false);
                segment(graphics, bx1, y + 4,
                        row.level() == ACCESS_EDIT
                                ? "gui.dashpanels_remote_control.center.to_operate"
                                : "gui.dashpanels_remote_control.center.to_edit",
                        mouseX, mouseY, isOwner);
                segment(graphics, bx2, y + 4, "gui.dashpanels_remote_control.center.remove_user",
                        mouseX, mouseY, isOwner);
            }
        }
    }

    private void segment(GuiGraphics graphics, int bx, int by, String key, int mouseX, int mouseY,
                         boolean enabled) {
        boolean hot = enabled && hover(mouseX, mouseY, bx, by, BTN_W, BTN_H);
        graphics.blit(WIDGETS, bx, by, hot ? SEG_U_LIT : SEG_U_DARK, SEG_V, BTN_W, BTN_H);
        Component label = Component.translatable(key);
        graphics.drawString(font, label, bx + (BTN_W - font.width(label)) / 2, by + 3,
                enabled ? (hot ? 0xFF16321A : TEXT) : MUTED, false);
    }

    private void drawBottomBar(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        int by = y + H - BAR_H - 4;
        graphics.blit(WIDGETS, x + 6, by, 1, 86, 134, 18);

        if (input != null) {
            input.setPosition(x + 10, by + 5);
            input.render(graphics, mouseX, mouseY, 0.0F);
        } else {
            Component hint = Component.translatable(page == 2
                    ? "gui.dashpanels_remote_control.center.name_hint"
                    : "gui.dashpanels_remote_control.center.rename_hint");
            graphics.drawString(font, hint, x + 10, by + 6, DIM, false);
        }

        int bx2 = x + W - 12 - BTN_W;
        int bx1 = bx2 - BTN_W - 3;
        if (page == 2) {
            segment(graphics, bx1, by + 2, "gui.dashpanels_remote_control.center.add_edit", mouseX, mouseY,
                    isOwner);
            segment(graphics, bx2, by + 2, "gui.dashpanels_remote_control.center.add_operate", mouseX, mouseY,
                    isOwner);
        } else {
            segment(graphics, bx1, by + 2, "gui.dashpanels_remote_control.center.confirm", mouseX, mouseY,
                    input != null && renaming != null);
            segment(graphics, bx2, by + 2, "gui.dashpanels_remote_control.center.cancel", mouseX, mouseY,
                    input != null);
        }
        if (!canEdit && !isOwner) {
            Component ro = Component.translatable("gui.dashpanels_remote_control.center.readonly");
            graphics.drawString(font, ro, x + 8, y + 6 + BAND_H - 8, MUTED, false);
        }
    }

    private static String levelKey(int level) {
        return level == ACCESS_EDIT
                ? "gui.dashpanels_remote_control.center.level_edit"
                : "gui.dashpanels_remote_control.center.level_operate";
    }

    private static boolean hover(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left();
        int y = top();

        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 6 + i * (TAB_W + 2);
            if (hover(mouseX, mouseY, tx, y + TAB_Y, TAB_W, TAB_H)) {
                if (page != i) {
                    page = i;
                    lastPage = i;
                    scroll = 0;
                    clearEditor();
                }
                click();
                return true;
            }
        }

        if (mouseY >= listTop() && mouseY < listBottom()) {
            for (int i = 0; i < visibleRows(); i++) {
                int index = i + scroll;
                if (index >= rowCount()) {
                    break;
                }
                int rowY = listTop() + i * ROW_H;
                int rx = x + 6;
                int bx2 = rx + W - 16 - BTN_W;
                int bx1 = bx2 - BTN_W - 3;
                if (hover(mouseX, mouseY, bx1, rowY + 4, BTN_W, BTN_H)) {
                    return rowButton(index, 0);
                }
                if (hover(mouseX, mouseY, bx2, rowY + 4, BTN_W, BTN_H)) {
                    return rowButton(index, 1);
                }
                if (mouseY >= rowY && mouseY < rowY + ROW_H - 2 && mouseX >= x + 4 && mouseX <= x + W - 4) {
                    if (page == 0) {
                        picked = index;
                        return true;
                    }
                }
            }
        }

        int by = y + H - BAR_H - 4;
        int bx2 = x + W - 12 - BTN_W;
        int bx1 = bx2 - BTN_W - 3;
        if (hover(mouseX, mouseY, bx1, by + 2, BTN_W, BTN_H)) {
            if (page == 2) {
                addAccess(ACCESS_EDIT);
            } else {
                confirmRename();
            }
            return true;
        }
        if (hover(mouseX, mouseY, bx2, by + 2, BTN_W, BTN_H)) {
            if (page == 2) {
                addAccess(ACCESS_OPERATE);
            } else {
                clearEditor();
            }
            return true;
        }
        if (input != null && input.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean rowButton(int index, int which) {
        if (page == 0) {
            TerminalRow row = terminals.get(index);
            if (which == 1) {
                send(new CenterRemoveTerminalPacket(center, row.pos()));
            } else {
                openRename(row);
            }
            return true;
        }
        if (page == 1) {
            RemoteRow row = remotes.get(index);
            if (which == 1) {
                send(new CenterForgetRemotePacket(center, row.name()));
            } else if (picked >= 0) {
                PacketDistributor.sendToServer(new RemoteSelectPacket(center, terminals.get(picked).pos()));
                click();
            } else {
                say("gui.dashpanels_remote_control.center.pick_terminal");
            }
            return true;
        }
        AccessRow row = accesses.get(index);
        if (!isOwner) {
            say("gui.dashpanels_remote_control.center.not_owner");
            return true;
        }
        if (which == 1) {
            send(new CenterAccessPacket(center, CenterAccessPacket.REMOVE, row.name(), (byte) 0, row.id()));
        } else {
            send(new CenterAccessPacket(center, CenterAccessPacket.SET_LEVEL, row.name(),
                    row.level() == ACCESS_EDIT ? ACCESS_OPERATE : ACCESS_EDIT, row.id()));
        }
        return true;
    }

    private void openRename(TerminalRow row) {
        renaming = row.pos();
        input = new EditBox(font, left() + 10, top() + H - BAR_H - 4 + 5, 120, 12,
                Component.translatable("gui.dashpanels_remote_control.center.name_hint"));
        input.setMaxLength(32);
        input.setValue(row.name());
        input.setFocused(true);
        setFocused(input);
    }

    private void confirmRename() {
        if (input == null || renaming == null) {
            return;
        }
        String name = input.getValue().trim();
        PacketDistributor.sendToServer(new RenameTerminalPacket(renaming, name));
        setPendingName(renaming, name);
        click();
        clearEditor();
    }

    private void addAccess(byte level) {
        if (!isOwner) {
            say("gui.dashpanels_remote_control.center.not_owner");
            return;
        }
        if (input == null) {
            input = new EditBox(font, left() + 10, top() + H - BAR_H - 4 + 5, 120, 12,
                    Component.translatable("gui.dashpanels_remote_control.center.name_hint"));
            input.setMaxLength(32);
            input.setFocused(true);
            setFocused(input);
            return;
        }
        String name = input.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        send(new CenterAccessPacket(center, CenterAccessPacket.ADD, name, (byte) 0, ""));
        input.setValue("");
    }

    private void clearEditor() {
        renaming = null;
        setFocused(null);
        input = null;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (input != null && input.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input != null) {
            if (keyCode == 257 || keyCode == 335) {
                if (page == 2) {
                    addAccess(ACCESS_EDIT);
                } else {
                    confirmRename();
                }
                return true;
            }
            if (keyCode == 256) {
                clearEditor();
                return true;
            }
            if (input.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, rowCount() - visibleRows()));
        return true;
    }

    private void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
        click();
    }

    private void click() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.0F);
        }
    }

    private void say(String key) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The window is opaque enough on its own; dimming the world only muddied it.
    }
}
