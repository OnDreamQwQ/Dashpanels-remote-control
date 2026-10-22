package io.github.ondreamqwq.drc.client;

import io.github.ondreamqwq.drc.compat.AeroworksCompat;
import io.github.ondreamqwq.drc.net.ConsoleOpenPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The console's own operating page, as the remote sees it.
 *
 * <p>Aeroworks opens a socket's module menu from its console screen, and that screen refuses players who
 * are not standing next to the console. The remote is by design not standing next to it, so the menu is
 * requested through the center instead, which already answered the permission question. The list here is
 * the same one Aeroworks shows: one row per socket, sub sockets indented under their parent.
 */
public class ConsoleListScreen extends Screen {

    private static final int WIDTH = 268;
    private static final int HEIGHT = 186;
    private static final int ROW = 14;
    private static final int TOP = 30;
    private static final int BOTTOM = 158;
    private static final int HEADER = 0xFF1E1E24;
    private static final int HOVER = 0xFF2A2A32;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFFB8B8B8;

    private final BlockPos center;
    private final BlockPos console;
    private final PanelRemoteScreen back;
    private final List<AeroworksCompat.Node> nodes = new ArrayList<>();
    private int scroll;

    public ConsoleListScreen(BlockPos center, BlockPos console, PanelRemoteScreen back) {
        super(Component.translatable("gui.dashpanels_remote_control.remote.console_list"));
        this.center = center;
        this.console = console;
        this.back = back;
        if (Minecraft.getInstance().level != null) {
            nodes.addAll(AeroworksCompat.nodes(Minecraft.getInstance().level, List.of(console)));
        }
    }

    public static void open(BlockPos center, BlockPos console, PanelRemoteScreen back) {
        Minecraft.getInstance().setScreen(new ConsoleListScreen(center, console, back));
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.dashpanels_remote_control.remote.list.back"),
                        button -> Minecraft.getInstance().setScreen(back))
                .bounds(left + WIDTH - 74, top + HEIGHT - 24, 66, 18)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        back.render(graphics, -1000, -1000, partialTick);

        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF0101014);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, 0xFF585858);
        graphics.drawString(font, title, left + 8, top + 8, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.dashpanels_remote_control.remote.console_at",
                console.getX() + ", " + console.getY() + ", " + console.getZ()), left + 8, top + 20, DIM, false);

        int visible = (BOTTOM - TOP) / ROW;
        if (nodes.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                    "gui.dashpanels_remote_control.remote.console_empty"), left + 8, top + TOP + 4, DIM, false);
        }
        int hovered = hovered(mouseX, mouseY, left, top, visible);
        for (int i = 0; i < visible; i++) {
            int at = i + scroll;
            if (at >= nodes.size()) {
                break;
            }
            AeroworksCompat.Node node = nodes.get(at);
            int y = top + TOP + i * ROW;
            int depth = node.subPath().isEmpty() ? 0 : node.subPath().split("/").length;
            if (at == hovered) {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW - 1, HOVER);
            }
            graphics.drawString(font, node.label(), left + 10 + depth * 10, y + 3, TEXT, false);
            graphics.drawString(font, Component.translatable(
                            "gui.dashpanels_remote_control.remote.socket", node.socket()),
                    left + WIDTH - 60, y + 3, DIM, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int hovered(double mouseX, double mouseY, int left, int top, int visible) {
        for (int i = 0; i < visible; i++) {
            int at = i + scroll;
            if (at >= nodes.size()) {
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
            AeroworksCompat.Node node = nodes.get(at);
            PacketDistributor.sendToServer(
                    new ConsoleOpenPacket(center, node.console(), node.socket(), node.subPath()));
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = (BOTTOM - TOP) / ROW;
        scroll = net.minecraft.util.Mth.clamp(scroll - (int) Math.signum(scrollY), 0,
                Math.max(0, nodes.size() - visible));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
