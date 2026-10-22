package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.compat.AeroworksCompat;
import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Starts operating an Aeroworks console from the remote.
 *
 * <p>What a console does when it is right clicked in the world is not "open a screen" - it marks the
 * player as its user, and the client turns that into the control session where the modules are dragged
 * around directly. That is the interface the player wants here, so this asks the server for the same
 * thing instead of opening anybody's menu.
 */
public record ConsoleUsePacket(BlockPos center, BlockPos console) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConsoleUsePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "dashpanels_remote_control", "console_use"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleUsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ConsoleUsePacket::center,
                    BlockPos.STREAM_CODEC, ConsoleUsePacket::console,
                    ConsoleUsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConsoleUsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level().getBlockEntity(packet.center())
                    instanceof PanelRemoteCenterBlockEntity center)) {
                return;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(packet.center())) > 64.0D * 64.0D) {
                return;
            }
            if (!center.canOperate(player)) {
                return;
            }
            if (!center.consoles().contains(packet.console())) {
                return;
            }
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return;
            }
            // The control session is a world session: it aims with the player's own eyes, so starting it
            // from far away only makes the HUD blink and die. Say why instead of doing nothing.
            if (player.distanceToSqr(Vec3.atCenterOf(packet.console())) > 8.0D * 8.0D) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "gui.dashpanels_remote_control.remote.console_too_far"), true);
                return;
            }
            AeroworksCompat.startUsing(serverPlayer, packet.console());
        });
    }
}
