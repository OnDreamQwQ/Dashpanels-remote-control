package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.content.PanelLinkTerminalBlockEntity;
import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Moves the center's default terminal one step, sent by sneak scrolling with the remote in hand.
 *
 * <p>The remote opens on the terminal the center says is default, so this is what makes "sneak and
 * scroll to pick a terminal" work from the world: the choice is stored on the center, and the next
 * time the remote opens it starts there.
 */
public record CycleTerminalPacket(BlockPos center, int step) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CycleTerminalPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "dashpanels_remote_control", "cycle_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleTerminalPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CycleTerminalPacket::center,
                    ByteBufCodecs.VAR_INT, CycleTerminalPacket::step,
                    CycleTerminalPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CycleTerminalPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level().getBlockEntity(packet.center())
                    instanceof PanelRemoteCenterBlockEntity center)) {
                return;
            }
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(packet.center())) > 64.0D * 64.0D) {
                return;
            }
            if (!center.canOperate(player)) {
                return;
            }
            // Only the terminals that still exist: breaking one must not leave a step in the list that
            // scrolls the remote onto a block that is gone.
            var terminals = center.liveTerminals(player.level());
            if (terminals.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("gui.dashpanels_remote_control.remote.no_terminal"), true);
                return;
            }
            // The choice lives on the remote stack, so this only ever moves the remote in hand.
            net.minecraft.world.item.ItemStack held =
                    io.github.ondreamqwq.drc.content.PanelRemoteData.held(player, packet.center());
            if (held == null) {
                return;
            }
            BlockPos current = io.github.ondreamqwq.drc.content.PanelRemoteData.terminal(held);
            int index = current == null ? -1 : terminals.indexOf(current);
            if (current != null && index < 0) {
                io.github.ondreamqwq.drc.content.PanelRemoteData.setTerminal(held, terminals.get(0));
                index = -1;
            }
            int next = Math.floorMod(index + (index < 0 ? 0 : packet.step()), terminals.size());
            BlockPos picked = terminals.get(next);
            io.github.ondreamqwq.drc.content.PanelRemoteData.setTerminal(held, picked);
            String name = player.level().getBlockEntity(picked) instanceof PanelLinkTerminalBlockEntity terminal
                    && !terminal.name().isBlank()
                    ? terminal.name()
                    : Component.translatable("gui.dashpanels_remote_control.terminal.unnamed").getString();
            player.displayClientMessage(Component.translatable(
                    "gui.dashpanels_remote_control.remote.terminal_switched",
                    next + 1, terminals.size(), name), true);
        });
    }
}
