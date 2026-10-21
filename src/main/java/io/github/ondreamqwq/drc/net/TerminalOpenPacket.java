package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.DrcMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: open the rename screen for this terminal, prefilled with its current name. */
public record TerminalOpenPacket(BlockPos terminal, String name) implements CustomPacketPayload {

    public static final Type<TerminalOpenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "terminal_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalOpenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalOpenPacket::terminal,
                    ByteBufCodecs.STRING_UTF8, TerminalOpenPacket::name,
                    TerminalOpenPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> io.github.ondreamqwq.drc.client.TerminalRenameScreen
                .open(packet.terminal(), packet.name()));
    }
}
