package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.DrcMod;
import io.github.ondreamqwq.drc.content.PanelLinkTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: give this terminal a name, so the remote can tell terminals apart. */
public record RenameTerminalPacket(BlockPos terminal, String name) implements CustomPacketPayload {

    public static final Type<RenameTerminalPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "rename_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameTerminalPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RenameTerminalPacket::terminal,
                    ByteBufCodecs.STRING_UTF8, RenameTerminalPacket::name,
                    RenameTerminalPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RenameTerminalPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.distanceToSqr(packet.terminal().getCenter()) > 64.0D * 64.0D) {
            return;
        }
        if (!(player.level().getBlockEntity(packet.terminal())
                instanceof PanelLinkTerminalBlockEntity terminal)) {
            return;
        }
        terminal.setName(packet.name());
    }
}
