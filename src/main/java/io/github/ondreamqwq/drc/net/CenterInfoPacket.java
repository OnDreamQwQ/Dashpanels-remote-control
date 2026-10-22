package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.DrcMod;
import io.github.ondreamqwq.drc.client.CenterScreen;
import io.github.ondreamqwq.drc.client.PanelRemoteScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: everything the center's screen shows, in one compound tag. */
public record CenterInfoPacket(BlockPos center, CompoundTag info) implements CustomPacketPayload {

    public static final Type<CenterInfoPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "center_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CenterInfoPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CenterInfoPacket::center,
                    ByteBufCodecs.COMPOUND_TAG, CenterInfoPacket::info,
                    CenterInfoPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CenterInfoPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // The remote asks for terminal switches while it is open, and the server answers with a fresh
            // info tag. Opening the center from that answer would shove the remote away - so only the
            // center's own screen takes it.
            if (Minecraft.getInstance().screen instanceof PanelRemoteScreen) {
                return;
            }
            CenterScreen.open(packet.center(), packet.info());
        });
    }
}
