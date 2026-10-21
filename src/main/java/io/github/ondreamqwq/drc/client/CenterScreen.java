package io.github.ondreamqwq.drc.client;

import io.github.ondreamqwq.drc.DrcMod;
import java.util.ArrayList;
import java.util.List;
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
            Component.translatable("gui.dashpanels_remote_control.center.players").getString());

    private final List<Row> panelRows = new ArrayList<>();
    private final List<Row> remoteRows = new ArrayList<>();
    private final List<Row> playerRows = new ArrayList<>();

    private int page;
    private int scroll;
    private int selected = -1;
    private boolean canOperate;

    private record Row(String text, String hint, BlockPos panel) {
    }

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
        canOperate = info.getBoolean("canOperate");
        selected = -1;

        ListTag panels = info.getList("terminals", Tag.TAG_COMPOUND);
        for (int i = 0; i < panels.size(); i++) {
            CompoundTag one = panels.getCompound(i);
            BlockPos pos = new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z"));
            int claimed = one.getInt("panels");
            String label = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            panelRows.add(new Row(label, claimed + " panel(s)", pos));
            if (one.getBoolean("selected")) {
                selected = i;
            }
        }

        ListTag remotes = info.getList("remotes", Tag.TAG_COMPOUND);
        for (int i = 0; i < remotes.size(); i++) {
            CompoundTag one = remotes.getCompound(i);
            remoteRows.add(new Row(one.getString("name"), one.getBoolean("online") ? "online" : "offline", null));
        }

        ListTag access = info.getList("access", Tag.TAG_COMPOUND);
        for (int i = 0; i < access.size(); i++) {
            CompoundTag one = access.getCompound(i);
            playerRows.add(new Row(one.getString("name"), one.getString("level"), null));
        }
        playerRows.addFirst(new Row(info.getString("owner"), "owner", null));
    }

    private List<Row> rows() {
        return switch (page) {
            case 1 -> remoteRows;
            case 2 -> playerRows;
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
        return super.mouseClicked(mouseX, mouseY, button);
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
