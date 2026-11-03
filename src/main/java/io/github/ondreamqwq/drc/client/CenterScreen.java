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
 * One window, a scrollable stack of cards, and Create's own icons for every action.
 * <p>
 * Everything a card has to say lives inside that card - the window frame is not drawn twice, and
 * the card area scrolls under a scrollbar on the right, right under the sheet's button column.
 */
public class CenterScreen extends Screen {

    private static final ResourceLocation WINDOW =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/connect_center.png");
    private static final ResourceLocation STRIPS =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/control_center_02.png");
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png");
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath("dashpanels_remote_control", "textures/gui/widgets.png");
    private static final ResourceLocation HEADS =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private static final int WIN_X = 1;
    private static final int WIN_Y = 1;
    private static final int W = 220;
    private static final int H = 244;

    private static final int HDR_H = 37;
    private static final int BODY_W = 219;
    private static final int BODY_H = 177;
    private static final int BOTTOM_W = 227;
    private static final int BOTTOM_H = 30;

    private static final int TAB_W = 28;
    private static final int TAB_H = 13;
    private static final int TAB_Y = 18;
    private static final int[] TAB_U = {1, 30, 59};
    private static final int TAB_V = 72;

    private static final int SUQARE_U = 238;
    private static final int[] SQUARE_V = {0, 19};

    private static final int INPUT_X = 8;
    private static final int STRIP_W = 135;
    private static final int STRIP_H = 18;
    private static final int STRIP_U = 1;
    private static final int STRIP_V = 86;
    private static final int FRAME_U = 1;
    private static final int FRAME_V = 1;

    private static final int CARD_X = 9;
    private static final int CARD_W = 194;
    private static final int CARD_H = 70;
    private static final int CARD_GAP = 4;
    private static final int LIST_TOP = 44;
    private static final int LIST_BOTTOM = 214;
    private static final int CLIP_TOP = 37;
    private static final int CLIP_BOTTOM = 213;
    private static final int BAR_TOP = 219;

    private static final int[] BOTTOM_BTN_X = {147, 169, 196};
    private static final int TRACK_X = 209;
    private static final int TRACK_TOP = 37;
    private static final int TRACK_BOTTOM = 201;
    private static final int THUMB_U = 238;
    private static final int THUMB_V = 57;
    private static final int THUMB_W = 7;
    private static final int THUMB_H = 13;
    private static final int ICON_SIZE = 18;
    private static final int ICON_TRASH_U = 16;
    private static final int ICON_TRASH_V = 0;
    private static final int ICON_RENAME_U = 0;
    private static final int ICON_RENAME_V = 112;
    private static final int ICON_EDIT_U = 0;
    private static final int ICON_EDIT_V = 160;
    private static final int ICON_OPERATE_U = 16;
    private static final int ICON_OPERATE_V = 160;
    private static final int ICON_ADD_U = 0;
    private static final int ICON_ADD_V = 0;
    private static final int ICON_CONFIRM_U = 0;
    private static final int ICON_CONFIRM_V = 16;
    private static final int ICON_CLOSE_U = 128;
    private static final int ICON_CLOSE_V = 160;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFAFC9C4;
    private static final int ACCENT = 0xFFFFC400;
    private static final int ONLINE = 0xFF8BE88B;
    private static final int MUTED = 0xFF6F8681;
    private static final int CARD_FILL = 0xC0006C58;
    private static final int CARD_EDGE = 0xFF00C5A1;
    private static final int CARD_EDGE_DIM = 0x8000C5A1;
    private static final int CARD_PICKED = 0xC0008A70;
    private static final int TRACK = 0xC000302A;
    private static final int THUMB = 0xFF00C5A1;

    private static final byte ACCESS_OPERATE = PanelRemoteCenterBlockEntity.ACCESS_OPERATE;
    private static final byte ACCESS_EDIT = PanelRemoteCenterBlockEntity.ACCESS_EDIT;

    private final BlockPos center;
    private final List<String> tabs = List.of(
            "gui.dashpanels_remote_control.center.terminals",
            "gui.dashpanels_remote_control.center.remotes",
            "gui.dashpanels_remote_control.center.players");

    private static int lastPage;

    private int page;
    private double scroll;
    private int selected = -1;
    private boolean canEdit;
    private boolean isOwner;
    private String network = "";
    private String ownerName = "";

    private final List<TerminalRow> terminals = new ArrayList<>();
    private final List<RemoteRow> remotes = new ArrayList<>();
    private final List<AccessRow> accesses = new ArrayList<>();

    private EditBox input;
    private boolean typing;
    private BlockPos renaming;

    private boolean draggingBar;

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

    private int cardStep() {
        return CARD_H + CARD_GAP;
    }

    private int maxScroll() {
        int content = rowCount() * cardStep();
        int view = LIST_BOTTOM - LIST_TOP;
        return Math.max(0, content - view);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left() + WIN_X;
        int y = top() + WIN_Y;

        graphics.blit(WINDOW, x, y, WIN_X, WIN_Y, W, HDR_H);
        graphics.blit(WINDOW, x, y + HDR_H, WIN_X, WIN_Y + HDR_H, BODY_W, BODY_H);
        graphics.blit(WINDOW, x + BODY_W, y + HDR_H, WIN_X + BODY_W - 1, WIN_Y + HDR_H, 1, BODY_H);
        graphics.blit(WINDOW, x, y + HDR_H + BODY_H, WIN_X, WIN_Y + HDR_H + BODY_H, BOTTOM_W, BOTTOM_H);

        graphics.drawString(font, title, x + 8, y + 5, TEXT, false);
        if (!network.isEmpty()) {
            String id = "#" + network;
            graphics.drawString(font, id, x + W - 10 - font.width(id), y + 5, DIM, false);
        }

        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 5 + i * (TAB_W + 2);
            int ty = y + TAB_Y;
            int state = i == page ? 2 : (hover(mouseX, mouseY, tx, ty, TAB_W, TAB_H) ? 1 : 0);
            graphics.blit(STRIPS, tx, ty, TAB_U[state], TAB_V, TAB_W, TAB_H);
            Component label = Component.translatable(tabs.get(i));
            graphics.drawString(font, label, tx + (TAB_W - font.width(label)) / 2, ty + 3,
                    state == 0 ? DIM : 0xFF0A291D, false);
        }

        scroll = Mth.clamp(scroll, 0, maxScroll());
        graphics.enableScissor(x + 1, y + CLIP_TOP, x + BODY_W, y + CLIP_BOTTOM);
        int cx = x + CARD_X;
        for (int i = 0; i < rowCount(); i++) {
            int cy = y + LIST_TOP + (int) Math.round(i * cardStep() - scroll);
            if (cy + CARD_H < y + LIST_TOP || cy > y + LIST_BOTTOM) {
                continue;
            }
            drawCard(graphics, i, cx, cy, mouseX, mouseY);
        }
        if (rowCount() == 0) {
            Component empty = Component.translatable("gui.dashpanels_remote_control.center.empty");
            graphics.drawString(font, empty, x + (W - font.width(empty)) / 2, y + LIST_TOP + 16, DIM, false);
        }
        graphics.disableScissor();

        drawScrollbar(graphics, x, y);
        if (typing || page == 2) {
            drawBar(graphics, x, y, mouseX, mouseY);
        }
    }

    private void drawCard(GuiGraphics graphics, int index, int cx, int cy, int mouseX, int mouseY) {
        boolean picked = index == selected;
        graphics.blit(STRIPS, cx, cy, FRAME_U, FRAME_V, CARD_W, CARD_H);
        if (picked) {
            graphics.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + CARD_H - 1, 0x2800FFC4);
        }

        int bx = cx + CARD_W - ICON_SIZE - 6;
        switch (page) {
            case 0 -> {
                TerminalRow row = terminals.get(index);
                String name = row.name().isEmpty()
                        ? Component.translatable("gui.dashpanels_remote_control.terminal.unnamed").getString()
                        : row.name();
                graphics.drawString(font, name, cx + 10, cy + 10, picked ? ACCENT : TEXT, false);
                graphics.drawString(font, row.pos().toShortString(), cx + 10, cy + 28, DIM, false);
                graphics.drawString(font,
                        Component.translatable("gui.dashpanels_remote_control.center.panel_count", row.panels()),
                        cx + 10, cy + 40, DIM, false);
                graphics.drawString(font, "#" + network, cx + 10, cy + 52, MUTED, false);
                renameIcon(graphics, bx, cy + 12, mouseX, mouseY, canEdit);
                icon(graphics, bx, cy + 40, ICON_TRASH_U, ICON_TRASH_V, mouseX, mouseY, canEdit);
            }
            case 1 -> {
                RemoteRow row = remotes.get(index);
                graphics.drawString(font, row.name(), cx + 10, cy + 10, picked ? ACCENT : TEXT, false);
                graphics.drawString(font,
                        Component.translatable("gui.dashpanels_remote_control.center.field_owner").getString()
                                + " " + (row.owner().isEmpty() ? "-" : row.owner()),
                        cx + 10, cy + 28, DIM, false);
                graphics.drawString(font, row.id().isEmpty() ? "-" : row.id(), cx + 10, cy + 40, MUTED, false);
                graphics.drawString(font,
                        Component.translatable(row.online()
                                ? "gui.dashpanels_remote_control.center.online"
                                : "gui.dashpanels_remote_control.center.offline"),
                        cx + 10, cy + 52, row.online() ? ONLINE : MUTED, false);
                icon(graphics, bx, cy + 12, 64, 16, mouseX, mouseY, true);
                icon(graphics, bx, cy + 40, ICON_TRASH_U, ICON_TRASH_V, mouseX, mouseY, canEdit);
            }
            default -> {
                AccessRow row = accesses.get(index);
                graphics.blit(HEADS, cx + 10, cy + 8, 8, 8, 8, 8, 64, 64);
                graphics.drawString(font, row.name(), cx + 22, cy + 10, picked ? ACCENT : TEXT, false);
                graphics.drawString(font,
                        Component.translatable(levelKey(row.level())), cx + 10, cy + 30, DIM, false);
                graphics.drawString(font, row.id().isEmpty() ? "-" : row.id(), cx + 10, cy + 42, MUTED, false);
                icon(graphics, bx, cy + 12,
                        row.level() == ACCESS_EDIT ? ICON_OPERATE_U : ICON_EDIT_U,
                        row.level() == ACCESS_EDIT ? ICON_OPERATE_V : ICON_EDIT_V,
                        mouseX, mouseY, isOwner);
                icon(graphics, bx, cy + 40, ICON_TRASH_U, ICON_TRASH_V, mouseX, mouseY, isOwner);
            }
        }
    }

    private void icon(GuiGraphics graphics, int bx, int by, int u, int v, int mouseX, int mouseY,
                      boolean enabled) {
        int state = enabled && hover(mouseX, mouseY, bx, by, ICON_SIZE, ICON_SIZE) ? 1 : 0;
        graphics.blit(WINDOW, bx, by, SUQARE_U, SQUARE_V[state], ICON_SIZE, ICON_SIZE);
        if (enabled) {
            graphics.blit(ICONS, bx + 1, by + 1, u, v, ICON_SIZE - 2, ICON_SIZE - 2, 256, 256);
        }
    }

    private void renameIcon(GuiGraphics graphics, int bx, int by, int mouseX, int mouseY, boolean enabled) {
        int state = enabled && hover(mouseX, mouseY, bx, by, ICON_SIZE, ICON_SIZE) ? 1 : 0;
        graphics.blit(WINDOW, bx, by, SUQARE_U, SQUARE_V[state], ICON_SIZE, ICON_SIZE);
        if (enabled) {
            graphics.blit(STRIPS, bx + 1, by + 1, ICON_RENAME_U, ICON_RENAME_V,
                    ICON_SIZE - 2, ICON_SIZE - 2, 256, 256);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y) {
        int trackX = x + TRACK_X - 2;
        int trackTop = y + TRACK_TOP;
        int trackHeight = TRACK_BOTTOM - TRACK_TOP;
        int span = trackHeight - THUMB_H;
        int thumbTop = trackTop;
        if (maxScroll() > 0 && span > 0) {
            thumbTop = trackTop + (int) Math.round(span * (scroll / (double) maxScroll()));
        }
        graphics.blit(WINDOW, trackX, thumbTop, THUMB_U, THUMB_V, THUMB_W, THUMB_H);
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int by = y + BAR_TOP;
        int ix = x + INPUT_X;
        graphics.blit(STRIPS, ix, by, STRIP_U, STRIP_V, STRIP_W, STRIP_H);

        if (input != null) {
            input.setX(ix + 4);
            input.setY(by + 4);
            input.render(graphics, mouseX, mouseY, 0.0F);
        } else {
            Component hint = Component.translatable(typing
                    ? "gui.dashpanels_remote_control.center.rename_hint"
                    : "gui.dashpanels_remote_control.center.name_hint");
            graphics.drawString(font, hint, ix + 4, by + 4, DIM, false);
        }

        if (typing) {
            widgetButton(graphics, x + BOTTOM_BTN_X[0], by, ICON_CONFIRM_U, ICON_CONFIRM_V, mouseX, mouseY, true);
            widgetButton(graphics, x + BOTTOM_BTN_X[1], by, ICON_CLOSE_U, ICON_CLOSE_V, mouseX, mouseY, true);
            widgetButton(graphics, x + BOTTOM_BTN_X[2], by, ICON_CLOSE_U, ICON_CLOSE_V, mouseX, mouseY, false);
            return;
        }
        if (page == 2) {
            widgetButton(graphics, x + BOTTOM_BTN_X[0], by, ICON_EDIT_U, ICON_EDIT_V, mouseX, mouseY, isOwner);
            widgetButton(graphics, x + BOTTOM_BTN_X[1], by, ICON_OPERATE_U, ICON_OPERATE_V, mouseX, mouseY, isOwner);
            widgetButton(graphics, x + BOTTOM_BTN_X[2], by, ICON_CLOSE_U, ICON_CLOSE_V, mouseX, mouseY, isOwner);
            return;
        }
        widgetButton(graphics, x + BOTTOM_BTN_X[0], by, ICON_ADD_U, ICON_ADD_V, mouseX, mouseY, isOwner);
        widgetButton(graphics, x + BOTTOM_BTN_X[1], by, ICON_CONFIRM_U, ICON_CONFIRM_V, mouseX, mouseY, false);
        widgetButton(graphics, x + BOTTOM_BTN_X[2], by, ICON_CLOSE_U, ICON_CLOSE_V, mouseX, mouseY, false);
    }

    private void widgetButton(GuiGraphics graphics, int bx, int by, int u, int v, int mouseX, int mouseY,
                              boolean enabled) {
        boolean hot = enabled && hover(mouseX, mouseY, bx, by, 18, 18);
        graphics.blit(WIDGETS, bx, by, hot ? 18 : 0, 0, 18, 18, 256, 256);
        if (enabled) {
            graphics.blit(ICONS, bx + 1, by + 1, u, v, ICON_SIZE, ICON_SIZE, 256, 256);
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
            int tx = x + 5 + i * (TAB_W + 2);
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

        int cx = x + CARD_X;
        for (int i = 0; i < rowCount(); i++) {
            int cy = y + LIST_TOP + (int) Math.round(i * cardStep() - scroll);
            if (cy + CARD_H < y + LIST_TOP || cy > y + LIST_BOTTOM) {
                continue;
            }
            int bx = cx + CARD_W - ICON_SIZE - 6;
            if (hover(mouseX, mouseY, bx, cy + 12, ICON_SIZE, ICON_SIZE)
                    || hover(mouseX, mouseY, bx, cy + 40, ICON_SIZE, ICON_SIZE)) {
                return cardButton(i, mouseY < cy + 35);
            }
            if (hover(mouseX, mouseY, cx, cy, CARD_W, CARD_H)) {
                selected = i;
                click();
                return true;
            }
        }

        if (!typing && page != 2) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int by = y + BAR_TOP;
        int ix = x + INPUT_X;
        if (hover(mouseX, mouseY, x + BOTTOM_BTN_X[0], by, 18, 18)) {
            if (typing) {
                confirmRename();
            } else if (page == 2) {
                addAccess(ACCESS_EDIT);
            } else {
                addAccess(ACCESS_EDIT);
            }
            return true;
        }
        if (hover(mouseX, mouseY, x + BOTTOM_BTN_X[1], by, 18, 18)) {
            if (typing) {
                stopTyping();
            } else if (page == 2) {
                addAccess(ACCESS_OPERATE);
            }
            return true;
        }
        if (hover(mouseX, mouseY, x + BOTTOM_BTN_X[2], by, 18, 18)) {
            if (page == 2 && !typing) {
                stopTyping();
            }
            return true;
        }
        if (hover(mouseX, mouseY, ix, by, STRIP_W, STRIP_H)) {
            makeBox();
            return true;
        }

        int barX = x + TRACK_X;
        if (hover(mouseX, mouseY, barX, y + LIST_TOP, 5, LIST_BOTTOM - LIST_TOP)) {
            draggingBar = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean cardButton(int index, boolean top) {
        selected = index;
        if (page == 0) {
            TerminalRow row = terminals.get(index);
            if (top) {
                startRename(row);
            } else {
                send(new CenterRemoveTerminalPacket(center, row.pos()));
            }
            return true;
        }
        if (page == 1) {
            RemoteRow row = remotes.get(index);
            if (top) {
                PacketDistributor.sendToServer(new RemoteSelectPacket(center,
                        terminals.isEmpty() ? center : terminals.get(0).pos()));
                click();
            } else {
                send(new CenterForgetRemotePacket(center, row.name()));
            }
            return true;
        }
        AccessRow row = accesses.get(index);
        if (!isOwner) {
            say("gui.dashpanels_remote_control.center.not_owner");
            return true;
        }
        if (top) {
            send(new CenterAccessPacket(center, CenterAccessPacket.SET_LEVEL, row.name(),
                    row.level() == ACCESS_EDIT ? ACCESS_OPERATE : ACCESS_EDIT, row.id()));
        } else {
            send(new CenterAccessPacket(center, CenterAccessPacket.REMOVE, row.name(), (byte) 0, row.id()));
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
            input = new EditBox(font, left() + WIN_X + INPUT_X + 5, top() + WIN_Y + BAR_TOP + 5,
                    STRIP_W - 10, 10, Component.translatable("gui.dashpanels_remote_control.center.name_hint"));
            input.setMaxLength(32);
            input.setBordered(false);
            input.setTextColor(0xFFFFFFFF);
            input.setTextColorUneditable(0xFFFFFFFF);
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
        if (input != null && input.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input != null) {
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingBar) {
            int trackTop = top() + WIN_Y + LIST_TOP;
            int trackHeight = LIST_BOTTOM - LIST_TOP;
            float ratio = (float) ((mouseY - trackTop) / Math.max(1.0, trackHeight));
            scroll = Mth.clamp(ratio * maxScroll(), 0, maxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * cardStep() / 2.0, 0, maxScroll());
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
