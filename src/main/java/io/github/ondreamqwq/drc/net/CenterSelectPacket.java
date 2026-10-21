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

/** Client to server: "this is the panel the remote should open on". */
public record CenterSelectPacket(BlockPos center, BlockPos panel) implements CustomPacketPayload {

    public static final Type<CenterSelectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "center_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CenterSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CenterSelectPacket::center,
                    BlockPos.STREAM_CODEC, CenterSelectPacket::panel,
                    CenterSelectPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CenterSelectPacket packet, IPayloadContext context) {
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
            if (!center.canOperate(player.getUUID()) || !center.covers(packet.panel())) {
                return;
            }
            center.setDefaultPanel(packet.panel());
            CenterInfo.sendTo(player, center);
        });
    }
}
