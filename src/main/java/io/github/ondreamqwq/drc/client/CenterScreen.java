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
    private static final ResourceLocation HEADS =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private static final int WIN_X = 2;
    private static final int WIN_Y = 2;
    private static final int W = 218;
    private static final int H = 244;

    private static final int TAB_W = 28;
    private static final int TAB_H = 13;
    private static final int TAB_Y = 18;
    private static final int[] TAB_U = {1, 30, 59};
    private static final int TAB_V = 72;

    private static final int SUQARE_U = 238;
    private static final int[] SQUARE_V = {0, 19};

    private static final int STRIP_W = 134;
    private static final int STRIP_H = 18;
    private static final int STRIP_U = 1;
    private static final int STRIP_V = 86;

    private static final int CARD_X = 6;
    private static final int CARD_W = 188;
    private static final int CARD_H = 46;
    private static final int CARD_GAP = 5;
    private static final int LIST_TOP = 36;
    private static final int LIST_BOTTOM = 210;
    private static final int BAR_TOP = 214;

    private static final int ICON_SIZE = 16;
    private static final int ICON_TRASH_U = 16;
    private static final int ICON_TRASH_V = 0;
    private static final int ICON_LABEL_U = 64;
    private static final int ICON_LABEL_V = 16;
    private static final int ICON_ADD_U = 0;
    private static final int ICON_ADD_V = 0;
    private static final int ICON_CONFIRM_U = 0;
    private static final int ICON_CONFIRM_V = 16;
    private static final int ICON_CLOSE_U = 16;
    private static final int ICON_CLOSE_V = 144;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFAFC9C4;
    private static final int ACCENT = 0xFFFFC400;
    private static final int ONLINE = 0xFF8BE88B;
    private static final int MUTED = 0xFF6F8681;
    private static final int CARD_FILL = 0xC0006C58;
    private static final int CARD_EDGE = 0xFF00C5A1;
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

        graphics.blit(WINDOW, x, y, WIN_X, WIN_Y, W, H);

        graphics.drawString(font, title, x + 8, y + 5, TEXT, false);
        if (!network.isEmpty()) {
            String id = "#" + network;
            graphics.drawString(font, id, x + W - 10 - font.width(id), y + 5, DIM, false);
        }

        for (int i = 0; i < tabs.size(); i++) {
            int tx = x + 6 + i * (TAB_W + 1);
            int ty = y + TAB_Y;
            int state = i == page ? 2 : (hover(mouseX, mouseY, tx, ty, TAB_W, TAB_H) ? 1 : 0);
            graphics.blit(STRIPS, tx, ty, TAB_U[state], TAB_V, TAB_W, TAB_H);
            Component label = Component.translatable(tabs.get(i));
            graphics.drawString(font, label, tx + (TAB_W - font.width(label)) / 2, ty + 3,
                    state == 0 ? DIM : 0xFF0A291D, false);
        }

        scroll = Mth.clamp(scroll, 0, maxScroll());
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

        drawScrollbar(graphics, x, y);
        drawBar(graphics, x, y, mouseX, mouseY);
    }

    private void drawCard(GuiGraphics graphics, int index, int cx, int cy, int mouseX, int mouseY) {
        boolean picked = index == selected;
        graphics.fill(cx, cy, cx + CARD_W, cy + CARD_H, picked ? CARD_PICKED : CARD_FILL);
        graphics.renderOutline(cx, cy, CARD_W, CARD_H, CARD_EDGE);

        int bx = cx + CARD_W - ICON_SIZE - 4;
        switch (page) {
            case 0 -> {
                TerminalRow row = terminals.get(index);
                String name = row.name().isEmpty()
                        ? Component.translatable("gui.dashpanels_remote_control.terminal.unnamed").getString()
                        : row.name();
                graphics.drawString(font, name, cx + 6, cy + 5, picked ? ACCENT : TEXT, false);
                graphics.drawString(font, row.pos().toShortString(), cx + 6, cy + 16, DIM, false);
                graphics.drawString(font,
                        Component.translatable("gui.dashpanels_remote_control.center.panel_count", row.panels()),
                        cx + 6, cy + 27, DIM, false);
                graphics.drawString(font, "#" + network, cx + 6, cy + 38, MUTED, false);
                icon(graphics, bx, cy + 5, ICON_LABEL_U, ICON_LABEL_V, mouseX, mouseY, canEdit);
                icon(graphics, bx, cy + 25, ICON_TRASH_U, ICON_TRASH_V, mouseX, mouseY, canEdit);
            }
            case 1 -> {
                RemoteRow row = remotes.get(index);
                graphics.drawString(font, row.name(), cx + 6, cy + 5, picked ? ACCENT : TEXT, false);
                graphics.drawString(font,
                        Component.translatable("gui.dashpanels_remote_control.center.field_owner").getString()
                                + " " + (row.owner().isEmpty() ? "-" : row.owner()),
                        cx + 6, cy + 16, DIM, false);
                graphics.drawString(font, row.id().isEmpty() ? "-" : row.id(), cx + 6, cy + 27, MUTED, false);
                graphics.drawString(font,
                        Component.translatable(row.online()
                                ? "gui.dashpanels_remote_control.center.online"
                                : "gui.dashpanels_remote_control.center.offline"),
                        cx + 6, cy + 38, row.online() ? ONLINE : MUTED, false);
                icon(graphics, bx, cy + 5, ICON_LABEL_U, ICON_LABEL_V, mouseX, mouseY, true);
                icon(graphics, bx, cy + 25, ICON_TRASH_U, ICON_TRASH_V, mouseX, mouseY, canEdit);
            }
            default -> {
                AccessRow row = accesses.get(index);
                graphics.blit(HEADS, cx + 6, cy + 4, 8, 8, 8, 8, 64, 64);
                graphics.drawString(font, row.name(), cx + 18, cy + 5, picked ? ACCENT : TEXT, false);
                graphics.drawString(font,
                        Component.translatable(levelKey(row.level())), cx + 6, cy + 18, DIM, false);
                graphics.drawString(font, row.id().isEmpty() ? "-" : row.id(), cx + 6, cy + 29, MUTED, false);
                icon(graphics, bx, cy + 5, ICON_LABEL_U, ICON_LABEL_V, mouseX, mouseY, isOwner);
                icon(graphics, bx, cy + 25, ICON_TRASH_U, ICON_TRASH_V, mouseX, mouseY, isOwner);
            }
        }
    }

    private void icon(GuiGraphics graphics, int bx, int by, int u, int v, int mouseX, int mouseY,
                      boolean enabled) {
        if (hover(mouseX, mouseY, bx, by, ICON_SIZE, ICON_SIZE)) {
            graphics.fill(bx - 2, by - 2, bx + ICON_SIZE + 2, by + ICON_SIZE + 2, 0x50FFFFFF);
        }
        if (enabled) {
            graphics.blit(ICONS, bx, by, u, v, ICON_SIZE, ICON_SIZE, 256, 256);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y) {
        int trackX = x + CARD_X + CARD_W + 2;
        int trackTop = y + LIST_TOP;
        int trackHeight = LIST_BOTTOM - LIST_TOP;
        graphics.fill(trackX, trackTop, trackX + 7, trackTop + trackHeight, TRACK);
        graphics.renderOutline(trackX, trackTop, 7, trackHeight, CARD_EDGE);

        int content = rowCount() * cardStep();
        int view = trackHeight;
        if (content <= view) {
            graphics.fill(trackX + 1, trackTop + 1, trackX + 6, trackTop + trackHeight - 1, THUMB);
            return;
        }
        int thumbHeight = Math.max(12, view * view / content);
        int span = trackHeight - thumbHeight - 2;
        int thumbTop = trackTop + 1 + (int) Math.round(span * (scroll / (double) maxScroll()));
        graphics.fill(trackX + 1, thumbTop, trackX + 6, thumbTop + thumbHeight, THUMB);
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int by = y + BAR_TOP;
        int ix = x + CARD_X;
        graphics.blit(STRIPS, ix, by, STRIP_U, STRIP_V, STRIP_W, STRIP_H);

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

        int bx = ix + STRIP_W + 3;
        square(graphics, bx, by, ICON_ADD_U, ICON_ADD_V, mouseX, mouseY, isOwner);
        square(graphics, bx + 19, by, ICON_CONFIRM_U, ICON_CONFIRM_V, mouseX, mouseY, true);
        square(graphics, bx + 38, by, ICON_CLOSE_U, ICON_CLOSE_V, mouseX, mouseY, true);
    }

    private void square(GuiGraphics graphics, int bx, int by, int u, int v, int mouseX, int mouseY,
                        boolean enabled) {
        int state = enabled && hover(mouseX, mouseY, bx, by, 18, 18) ? 1 : 0;
        graphics.blit(WINDOW, bx, by, SUQARE_U, SQUARE_V[state], 18, 18);
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

        int cx = x + CARD_X;
        for (int i = 0; i < rowCount(); i++) {
            int cy = y + LIST_TOP + (int) Math.round(i * cardStep() - scroll);
            if (cy + CARD_H < y + LIST_TOP || cy > y + LIST_BOTTOM) {
                continue;
            }
            int bx = cx + CARD_W - ICON_SIZE - 4;
            if (hover(mouseX, mouseY, bx, cy + 5, ICON_SIZE, ICON_SIZE)
                    || hover(mouseX, mouseY, bx, cy + 25, ICON_SIZE, ICON_SIZE)) {
                return cardButton(i, mouseY < cy + 23);
            }
            if (hover(mouseX, mouseY, cx, cy, CARD_W, CARD_H)) {
                selected = i;
                click();
                return true;
            }
        }

        int by = y + BAR_TOP;
        int ix = x + CARD_X;
        int bx = ix + STRIP_W + 3;
        if (hover(mouseX, mouseY, bx, by, 18, 18)) {
            addAccess(ACCESS_EDIT);
            return true;
        }
        if (hover(mouseX, mouseY, bx + 19, by, 18, 18)) {
            if (typing) {
                confirmRename();
            } else {
                addAccess(ACCESS_EDIT);
            }
            return true;
        }
        if (hover(mouseX, mouseY, bx + 38, by, 18, 18)) {
            stopTyping();
            return true;
        }
        if (hover(mouseX, mouseY, ix, by, STRIP_W, STRIP_H)) {
            makeBox();
            return true;
        }

        int barX = x + CARD_X + CARD_W + 2;
        if (hover(mouseX, mouseY, barX, y + LIST_TOP, 7, LIST_BOTTOM - LIST_TOP)) {
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
            input = new EditBox(font, left() + WIN_X + CARD_X + 4, top() + WIN_Y + BAR_TOP + 5,
                    STRIP_W - 8, 10, Component.translatable("gui.dashpanels_remote_control.center.name_hint"));
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
