package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.DrcMod;
import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: drop one remote from the center's ledger.
 *
 * <p>A remote is recorded the first time it is used on the center, and nothing removes it again - a
 * player who uninstalls the mod or loses the remote would stay listed forever.
 */
public record CenterForgetRemotePacket(BlockPos center, String name) implements CustomPacketPayload {

    public static final Type<CenterForgetRemotePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "center_forget_remote"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CenterForgetRemotePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CenterForgetRemotePacket::center,
                    ByteBufCodecs.STRING_UTF8, CenterForgetRemotePacket::name,
                    CenterForgetRemotePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CenterForgetRemotePacket packet, IPayloadContext context) {
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
                return;
            }
            if (center.forgetRemote(packet.name())) {
                CenterInfo.sendTo(player, center);
            }
        });
    }
}