package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.DrcMod;
import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/** Client to server: change the center's player list. Only the owner is allowed to. */
public record CenterAccessPacket(BlockPos center, byte action, String name, byte level, String uuid)
        implements CustomPacketPayload {

    public static final byte ADD = 0;
    public static final byte REMOVE = 1;
    public static final byte SET_LEVEL = 2;

    public static final Type<CenterAccessPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DrcMod.MOD_ID, "center_access"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CenterAccessPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CenterAccessPacket::center,
                    ByteBufCodecs.BYTE, CenterAccessPacket::action,
                    ByteBufCodecs.STRING_UTF8, CenterAccessPacket::name,
                    ByteBufCodecs.BYTE, CenterAccessPacket::level,
                    ByteBufCodecs.STRING_UTF8, CenterAccessPacket::uuid,
                    CenterAccessPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CenterAccessPacket packet, IPayloadContext context) {
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
            // Only the owner may hand out rights; "can edit" is not enough to escalate.
            if (center.owner() == null || !center.owner().equals(player.getUUID())) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("gui.dashpanels_remote_control.center.not_owner"),
                        true);
                return;
            }
            UUID target = parseUuid(packet.uuid());
            if (target == null) {
                target = resolve(player, packet.name());
            }
            if (target == null) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("gui.dashpanels_remote_control.center.unknown_player",
                                packet.name()), true);
                return;
            }
            if (target.equals(center.owner())) {
                return;
            }
            switch (packet.action()) {
                case ADD -> center.grant(target, packet.level());
                case REMOVE -> center.revoke(target);
                case SET_LEVEL -> center.grant(target, packet.level());
                default -> {
                    return;
                }
            }
            CenterInfo.sendTo(player, center);
        });
    }

    /** Online players first, then the server's name cache, so offline players can be added too. */
    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }
    private static UUID resolve(ServerPlayer player, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (player.getServer() == null) {
            return null;
        }
        ServerPlayer online = player.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        Optional<GameProfile> cached = player.getServer().getProfileCache().get(name);
        return cached.map(GameProfile::getId).orElse(null);
    }
}
