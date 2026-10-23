package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.DrcMod;
import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: forget one terminal.
 *
 * <p>Needed because a terminal can outlive its structure - drop a physics contraption into the void and
 * the terminal block may still be remembered by the network while every panel it served is gone. Such a
 * terminal has nothing to draw, so the player has to be able to take it off the list by hand.
 */
public record CenterRemoveTerminalPacket(BlockPos center, BlockPos terminal) implements CustomPacketPayload {

    public static final Type<CenterRemoveTerminalPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "center_remove_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CenterRemoveTerminalPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CenterRemoveTerminalPacket::center,
                    BlockPos.STREAM_CODEC, CenterRemoveTerminalPacket::terminal,
                    CenterRemoveTerminalPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CenterRemoveTerminalPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(packet.center().getCenter()) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.center()) instanceof PanelRemoteCenterBlockEntity center)) {
                return;
            }
            if (!center.canEdit(player)) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("gui.dashpanels_remote_control.center.readonly"),
                        true);
                return;
            }
            if (player.level() instanceof ServerLevel level) {
                center.forgetTerminal(level, packet.terminal());
            }
            CenterInfo.sendTo(player, center);
        });
    }
}