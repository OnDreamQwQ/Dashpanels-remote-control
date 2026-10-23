package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.DrcMod;
import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import io.github.ondreamqwq.drc.content.PanelRemoteData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: "the remote in my hands is now on this terminal".
 *
 * <p>The choice belongs to the remote item, not to the center: the center is shared, two remotes may
 * follow two different terminals, and the server has to be the one that writes it so it survives a
 * relog and travels with the stack.
 */
public record RemoteSelectPacket(BlockPos center, BlockPos terminal) implements CustomPacketPayload {

    public static final Type<RemoteSelectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "remote_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RemoteSelectPacket::center,
                    BlockPos.STREAM_CODEC, RemoteSelectPacket::terminal,
                    RemoteSelectPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoteSelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(packet.center())) > 64.0D * 64.0D) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.center())
                    instanceof PanelRemoteCenterBlockEntity center)) {
                return;
            }
            if (!center.canOperate(player) || !center.coversTerminal(packet.terminal())) {
                return;
            }
            ItemStack stack = PanelRemoteData.held(player, packet.center());
            if (stack == null) {
                return;
            }
            PanelRemoteData.setTerminal(stack, packet.terminal());
        });
    }
}