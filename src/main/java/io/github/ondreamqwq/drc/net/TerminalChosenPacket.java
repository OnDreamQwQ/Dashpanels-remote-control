package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.content.PanelRemoteData;
import io.github.ondreamqwq.drc.content.PanelRemoteItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells the client which terminal the server just put on its remote.
 *
 * <p>Writing the tag on the server copy of the stack is not enough on its own: the client keeps its own
 * copy of the item, and until the container slot is resent the remote screen still reads the old
 * terminal - which looks exactly like "the wheel switch does nothing". Echoing the choice makes the
 * client side update on the spot.
 */
public record TerminalChosenPacket(BlockPos center, BlockPos terminal) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TerminalChosenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "dashpanels_remote_control", "terminal_chosen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalChosenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalChosenPacket::center,
                    BlockPos.STREAM_CODEC, TerminalChosenPacket::terminal,
                    TerminalChosenPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalChosenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack held = PanelRemoteData.held(context.player(), packet.center());
            if (held == null || !(held.getItem() instanceof PanelRemoteItem)) {
                return;
            }
            PanelRemoteData.setTerminal(held, packet.terminal());
            io.github.ondreamqwq.drc.client.PanelRemoteScreen.announceTerminal(packet.terminal());
        });
    }
}
