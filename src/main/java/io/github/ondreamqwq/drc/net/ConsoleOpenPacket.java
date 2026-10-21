package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.DrcMod;
import io.github.ondreamqwq.drc.compat.AeroworksCompat;
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
 * Client to server: "open this Aeroworks module's menu for me".
 *
 * <p>Aeroworks' own packet asks the console whether the player is close enough; ours does not, because
 * the authorization has already happened - the player must be allowed to operate this center, must be
 * within reach of it, and the console must be one this center actually serves. Everything after that is
 * Aeroworks' own menu.
 */
public record ConsoleOpenPacket(BlockPos center, BlockPos console, int socket, String subPath)
        implements CustomPacketPayload {

    public static final Type<ConsoleOpenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "console_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleOpenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ConsoleOpenPacket::center,
                    BlockPos.STREAM_CODEC, ConsoleOpenPacket::console,
                    ByteBufCodecs.VAR_INT, ConsoleOpenPacket::socket,
                    ByteBufCodecs.STRING_UTF8, ConsoleOpenPacket::subPath,
                    ConsoleOpenPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConsoleOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.center()) instanceof PanelRemoteCenterBlockEntity center)) {
                return;
            }
            if (!center.canOperate(player.getUUID())) {
                return;
            }
            if (player.distanceToSqr(packet.center().getCenter()) > 64.0 * 64.0) {
                return;
            }
            if (!center.consoles().contains(packet.console())) {
                return;
            }
            AeroworksCompat.openMenu(player, packet.console(), packet.socket(), packet.subPath());
        });
    }
}
