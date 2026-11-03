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
 * The center's screen, blitted from the painted sheets at native scale - nothing is stretched.
 * Window: 218x244 at (2,1) of connect_center.png. Tabs: the three 28x13 strips at y=72 of
 * control_center_02.png (plain / hover / selected). Buttons: the 18x18 squares in
 * connect_center.png's right column (plain / hover / selected). Detail frame: 194x70 at (1,1) of
 * control_center_02.png. Edit strip: 134x18 at (1,86) of control_center_02.png.
 */
public class CenterScreen extends Screen {

    private static final ResourceLocation WINDOW =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/connect_center.png");
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/control_center_02.png");
    private static final ResourceLocation HEADS =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private static final int WIN_X = 2;
    private static final int WIN_Y = 1;
    private static final int W = 218;
    private static final int H = 244;

    private static final int TAB_W = 28;
    private static final int TAB_H = 13;
    private static final int TAB_Y = 18;
    private static final int[] TAB_U = {1, 30, 59};
    private static final int TAB_V = 72;

    private static final int BTN_W = 18;
    private static final int BTN_H = 18;
    private static final int[] BTN_V = {0, 19, 39};
    private static final int BTN_U = 238;

    private static final int FRAME_W = 194;
    private static final int FRAME_H = 70;
    private static final int FRAME_U = 1;
    private static final int FRAME_V = 1;

    private static final int STRIP_W = 134;
    private static final int STRIP_H = 18;
    private static final int STRIP_U = 1;
    private static final int STRIP_V = 86;
    private static final int GAP = 3;

    private static final int LIST_TOP = 42;
    private static final int LIST_BOTTOM = 140;
    private static final int ROW_H = 20;
    private static final int FRAME_TOP = LIST_BOTTOM + 2;
    private static final int BAR_TOP = FRAME_TOP + FRAME_H + 4;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFAFC9C4;
    private static final int ACCENT = 0xFFFFC400;
    private static final int ONLINE = 0xFF8BE88B;
    private static final int MUTED = 0xFF6F8681;

    private static final byte ACCESS_OPERATE = PanelRemoteCenterBlockEntity.ACCESS_OPERATE;
    private static final byte ACCESS_EDIT = PanelRemoteCenterBlockEntity.ACCESS_EDIT;

    private final BlockPos center;
    private final List<String> tabs = List.of(
            "gui.dashpanels_remote_control.center.terminals",
            "gui.dashpanels_remote_control.center.remotes",
            "gui.dashpanels_remote_control.center.players");

    private static int lastPage;

    private int page;
    private int scroll;
    private int selected = -1;
    private boolean canEdit;
    private boolean isOwner;
    private String network = "";
    private String ownerName = "";

    private final List<TerminalRow> terminals = new ArrayList<>();
    private final List<RemoteRow> remotes = new ArrayList<>();
    private final List<AccessRow> accesses = new ArrayList<>();

    private EditBox input;
    /** True only while a rename is armed; the bar and its two buttons appear just for that. */
    private boolean typing;
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

    public void update(CompoundTag info) {
        read(info);
    }

    public static void open(BlockPos center, CompoundTag info) {
        Minecraft.getInstance().setScreen(new CenterScreen(center, info));
    }

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
        if (selected >= rowCount()) {
            selected = -1;
        }
    }

    private int rowCount() {
        return switch (page) {
            case 0 -> terminals.size();
            case 1 -> remotes.size();
            default -> accesses.size();
        };
    }

    private int left() {
        return (width - W) / 2 - WIN_X;
    }

    private int top() {
        return (height - H) / 2 - WIN_Y;
    }

    private int visibleRows() {
        return Math.max(1, (LIST_BOTTOM - LIST_TOP) / ROW_H);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left() + WIN_X;
        int y = top() + WIN_Y;

        graphics.blit(WINDOW, x, y, WIN_X, WIN_Y, W, H);

        graphics.drawString(font, title, x + 8, y + 5, TEXT, false);
        if (!network.isEmpty()) {
            String id = "#" + network;
            graphics.drawString(font, id, x + W - 8 - font.width(id), y + 5, DIM, false);
        }

        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 6 + i * (TAB_W + 1);
            int ty = y + TAB_Y;
            int state = i == page ? 2 : (hover(mouseX, mouseY, tx, ty, TAB_W, TAB_H) ? 1 : 0);
            graphics.blit(WIDGETS, tx, ty, TAB_U[state], TAB_V, TAB_W, TAB_H);
            Component label = Component.translatable(tabs.get(i));
            graphics.drawString(font, label, tx + (TAB_W - font.width(label)) / 2, ty + 3,
                    state == 0 ? DIM : 0xFF0A291D, false);
        }

        scroll = Mth.clamp(scroll, 0, Math.max(0, rowCount() - visibleRows()));
        for (int i = 0; i < visibleRows(); i++) {
            int index = i + scroll;
            if (index >= rowCount()) {
                break;
            }
            drawRow(graphics, index, x + 4, y + LIST_TOP + i * ROW_H, mouseX, mouseY);
        }
        if (rowCount() == 0) {
            Component empty = Component.translatable("gui.dashpanels_remote_control.center.empty");
            graphics.drawString(font, empty, x + (W - font.width(empty)) / 2, y + LIST_TOP + 12, DIM, false);
        }

        drawDetails(graphics, x, y);
        if (barVisible()) {
            drawBar(graphics, x, y, mouseX, mouseY);
        }
    }

    private void drawRow(GuiGraphics graphics, int index, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + W - 8 && mouseY >= y && mouseY < y + ROW_H - 2;
        if (hovered || index == selected) {
            graphics.fill(x - 1, y - 1, x + W - 9, y + ROW_H - 3,
                    index == selected ? 0x40000000 : 0x20000000);
        }
        int bx2 = x + W - 12 - BTN_W;
        int bx1 = bx2 - BTN_W - GAP;
        switch (page) {
            case 0 -> {
                TerminalRow row = terminals.get(index);
                String name = row.name().isEmpty()
                        ? Component.translatable("gui.dashpanels_remote_control.terminal.unnamed").getString()
                        : row.name();
                graphics.drawString(font, name, x + 2, y + 6, index == selected ? ACCENT : TEXT, false);
                String meta = Component.translatable("gui.dashpanels_remote_control.center.panel_count",
                        row.panels()).getString();
                graphics.drawString(font, meta, x + 4 + font.width(name) + 6, y + 6, DIM, false);
                square(graphics, bx1, y, "改", mouseX, mouseY, canEdit);
                square(graphics, bx2, y, "断", mouseX, mouseY, canEdit);
            }
            case 1 -> {
                RemoteRow row = remotes.get(index);
                graphics.drawString(font, row.name(), x + 2, y + 2, TEXT, false);
                String meta = Component.translatable("gui.dashpanels_remote_control.center.remote_meta",
                        row.owner().isEmpty() ? "-" : row.owner(),
                        row.id().isEmpty() ? "-" : row.id()).getString();
                graphics.drawString(font, meta, x + 2, y + 11, row.online() ? ONLINE : DIM, false);
                square(graphics, bx1, y, "指", mouseX, mouseY, true);
                square(graphics, bx2, y, "断", mouseX, mouseY, canEdit);
            }
            default -> {
                AccessRow row = accesses.get(index);
                graphics.blit(HEADS, x + 2, y + 5, 8, 8, 8, 8, 64, 64);
                graphics.drawString(font, row.name(), x + 14, y + 2, TEXT, false);
                String meta = Component.translatable(levelKey(row.level())).getString() + " · "
                        + (row.id().isEmpty() ? "-" : row.id());
                graphics.drawString(font, meta, x + 14, y + 11, DIM, false);
                square(graphics, bx1, y, row.level() == ACCESS_EDIT ? "只" : "改", mouseX, mouseY, isOwner);
                square(graphics, bx2, y, "删", mouseX, mouseY, isOwner);
            }
        }
    }

    private void square(GuiGraphics graphics, int bx, int by, String glyph, int mouseX, int mouseY,
                        boolean enabled) {
        int state = enabled && hover(mouseX, mouseY, bx, by, BTN_W, BTN_H) ? 1 : 0;
        graphics.blit(WINDOW, bx, by, BTN_U, BTN_V[state], BTN_W, BTN_H);
        int color = enabled ? (state == 1 ? 0xFF0A291D : TEXT) : MUTED;
        graphics.drawString(font, glyph, bx + (BTN_W - font.width(glyph)) / 2, by + 5, color, false);
    }

    private void drawDetails(GuiGraphics graphics, int x, int y) {
        int fx = x + (W - FRAME_W) / 2;
        int fy = y + FRAME_TOP;
        graphics.blit(WIDGETS, fx, fy, FRAME_U, FRAME_V, FRAME_W, FRAME_H);

        int tx = fx + 6;
        int ty = fy + 7;
        switch (page) {
            case 0 -> {
                if (selected >= 0 && selected < terminals.size()) {
                    TerminalRow row = terminals.get(selected);
                    line(graphics, tx, ty,
                            Component.translatable("gui.dashpanels_remote_control.center.field_name"),
                            row.name().isEmpty()
                                    ? Component.translatable("gui.dashpanels_remote_control.terminal.unnamed")
                                    : Component.literal(row.name()));
                    line(graphics, tx, ty + 14,
                            Component.translatable("gui.dashpanels_remote_control.center.field_pos"),
                            Component.literal(row.pos().toShortString()));
                    line(graphics, tx, ty + 28,
                            Component.translatable("gui.dashpanels_remote_control.center.field_panels"),
                            Component.literal(String.valueOf(row.panels())));
                    line(graphics, tx, ty + 42,
                            Component.translatable("gui.dashpanels_remote_control.center.field_id"),
                            Component.literal(network.isEmpty() ? "-" : network));
                } else {
                    hint(graphics, fx, fy, Component.translatable(
                            "gui.dashpanels_remote_control.center.pick_row"));
                }
            }
            case 1 -> {
                if (selected >= 0 && selected < remotes.size()) {
                    RemoteRow row = remotes.get(selected);
                    line(graphics, tx, ty,
                            Component.translatable("gui.dashpanels_remote_control.center.field_name"),
                            Component.literal(row.name()));
                    line(graphics, tx, ty + 14,
                            Component.translatable("gui.dashpanels_remote_control.center.field_owner"),
                            Component.literal(row.owner().isEmpty() ? "-" : row.owner()));
                    line(graphics, tx, ty + 28,
                            Component.translatable("gui.dashpanels_remote_control.center.field_id"),
                            Component.literal(row.id().isEmpty() ? "-" : row.id()));
                } else {
                    hint(graphics, fx, fy, Component.translatable(
                            "gui.dashpanels_remote_control.center.pick_row"));
                }
            }
            default -> {
                line(graphics, tx, ty,
                        Component.translatable("gui.dashpanels_remote_control.center.field_owner"),
                        Component.literal(ownerName.isEmpty() ? "-" : ownerName));
                line(graphics, tx, ty + 14,
                        Component.translatable("gui.dashpanels_remote_control.center.field_id"),
                        Component.literal(network.isEmpty() ? "-" : network));
                if (selected >= 0 && selected < accesses.size()) {
                    AccessRow row = accesses.get(selected);
                    line(graphics, tx, ty + 28,
                            Component.translatable("gui.dashpanels_remote_control.center.field_name"),
                            Component.literal(row.name()));
                    line(graphics, tx, ty + 42,
                            Component.translatable("gui.dashpanels_remote_control.center.field_level"),
                            Component.translatable(levelKey(row.level())));
                }
            }
        }
    }

    private void line(GuiGraphics graphics, int x, int y, Component label, Component value) {
        graphics.drawString(font, label, x, y, DIM, false);
        graphics.drawString(font, value, x + 58, y, TEXT, false);
    }

    private void hint(GuiGraphics graphics, int fx, int fy, Component text) {
        graphics.drawString(font, text, fx + (FRAME_W - font.width(text)) / 2, fy + FRAME_H / 2 - 4, DIM, false);
    }

    private boolean barVisible() {
        return typing || page == 2;
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int by = y + BAR_TOP;
        int ix = x + 4;
        graphics.blit(WIDGETS, ix, by, STRIP_U, STRIP_V, STRIP_W, STRIP_H);

        if (input != null) {
            input.setX(ix + 4);
            input.setY(by + 5);
            input.render(graphics, mouseX, mouseY, 0.0F);
        } else {
            Component hint = Component.translatable(typing
                    ? "gui.dashpanels_remote_control.center.rename_hint"
                    : "gui.dashpanels_remote_control.center.name_hint");
            graphics.drawString(font, hint, ix + 4, by + 5, DIM, false);
        }

        int bx1 = ix + STRIP_W + GAP;
        int bx2 = bx1 + BTN_W + GAP;
        if (typing) {
            square(graphics, bx1, by, "✓", mouseX, mouseY, true);
            square(graphics, bx2, by, "✕", mouseX, mouseY, true);
        } else {
            square(graphics, bx1, by, "改", mouseX, mouseY, isOwner);
            square(graphics, bx2, by, "只", mouseX, mouseY, isOwner);
        }
        if (!canEdit && !isOwner) {
            Component ro = Component.translatable("gui.dashpanels_remote_control.center.readonly");
            graphics.drawString(font, ro, bx2 + BTN_W + GAP, by + 5, MUTED, false);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left() + WIN_X;
        int y = top() + WIN_Y;

        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 6 + i * (TAB_W + 1);
            if (hover(mouseX, mouseY, tx, y + TAB_Y, TAB_W, TAB_H)) {
                if (page != i) {
                    page = i;
                    lastPage = i;
                    scroll = 0;
                    selected = -1;
                    stopTyping();
                }
                click();
                return true;
            }
        }

        for (int i = 0; i < visibleRows(); i++) {
            int index = i + scroll;
            if (index >= rowCount()) {
                break;
            }
            int ry = y + LIST_TOP + i * ROW_H;
            int rx = x + 4;
            int bx2 = rx + W - 12 - BTN_W;
            int bx1 = bx2 - BTN_W - GAP;
            if (hover(mouseX, mouseY, bx1, ry, BTN_W, BTN_H)) {
                return rowButton(index, 0);
            }
            if (hover(mouseX, mouseY, bx2, ry, BTN_W, BTN_H)) {
                return rowButton(index, 1);
            }
            if (mouseY >= ry && mouseY < ry + ROW_H - 2 && mouseX >= x + 2 && mouseX <= x + W - 2) {
                selected = index;
                click();
                return true;
            }
        }

        if (barVisible()) {
            int by = y + BAR_TOP;
            int ix = x + 4;
            int bx1 = ix + STRIP_W + GAP;
            int bx2 = bx1 + BTN_W + GAP;
            if (hover(mouseX, mouseY, bx1, by, BTN_W, BTN_H)) {
                if (typing) {
                    confirmRename();
                } else {
                    addAccess(ACCESS_EDIT);
                }
                return true;
            }
            if (hover(mouseX, mouseY, bx2, by, BTN_W, BTN_H)) {
                if (typing) {
                    stopTyping();
                } else {
                    addAccess(ACCESS_OPERATE);
                }
                return true;
            }
            if (hover(mouseX, mouseY, ix, by, STRIP_W, STRIP_H)) {
                makeBox();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean rowButton(int index, int which) {
        selected = index;
        if (page == 0) {
            TerminalRow row = terminals.get(index);
            if (which == 1) {
                send(new CenterRemoveTerminalPacket(center, row.pos()));
            } else {
                startRename(row);
            }
            return true;
        }
        if (page == 1) {
            RemoteRow row = remotes.get(index);
            if (which == 1) {
                send(new CenterForgetRemotePacket(center, row.name()));
            } else {
                PacketDistributor.sendToServer(new RemoteSelectPacket(center,
                        terminals.isEmpty() ? center : terminals.get(0).pos()));
                click();
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

    private void startRename(TerminalRow row) {
        if (!canEdit) {
            say("gui.dashpanels_remote_control.center.readonly");
            return;
        }
        typing = true;
        renaming = row.pos();
        makeBox();
        if (input != null) {
            input.setValue(row.name());
        }
    }

    private void makeBox() {
        if (input == null) {
            input = new EditBox(font, left() + WIN_X + 8, top() + WIN_Y + BAR_TOP + 5, STRIP_W - 8, 10,
                    Component.translatable("gui.dashpanels_remote_control.center.name_hint"));
            input.setMaxLength(32);
        }
        input.setFocused(true);
        setFocused(input);
    }

    private void confirmRename() {
        if (!typing || input == null || renaming == null) {
            stopTyping();
            return;
        }
        String name = input.getValue().trim();
        PacketDistributor.sendToServer(new RenameTerminalPacket(renaming, name));
        setPendingName(renaming, name);
        click();
        stopTyping();
    }

    private void addAccess(byte level) {
        if (!isOwner) {
            say("gui.dashpanels_remote_control.center.not_owner");
            return;
        }
        if (input == null) {
            makeBox();
            return;
        }
        String name = input.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        send(new CenterAccessPacket(center, CenterAccessPacket.ADD, name, level, ""));
        input.setValue("");
    }

    private void stopTyping() {
        typing = false;
        renaming = null;
        if (input != null) {
            input.setFocused(false);
        }
        setFocused(null);
        if (page != 2) {
            input = null;
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (barVisible() && input != null && input.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (barVisible() && input != null) {
            if (keyCode == 257 || keyCode == 335) {
                if (typing) {
                    confirmRename();
                } else {
                    addAccess(ACCESS_EDIT);
                }
                return true;
            }
            if (keyCode == 256) {
                stopTyping();
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
        // The window is painted opaque; dimming the world only muddied it.
    }
}
