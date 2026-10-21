package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import moth.boxxed.panels.api.panel.AbstractPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Builds the center's screen data on the server and pushes it to one player. */
public final class CenterInfo {

    private CenterInfo() {
    }

    public static void sendTo(ServerPlayer player, PanelRemoteCenterBlockEntity center) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag info = new CompoundTag();
        info.putString("network", io.github.ondreamqwq.drc.content.Networks.shortId(center.networkId()));
        info.putBoolean("canEdit", center.canEdit(player.getUUID()));
        info.putBoolean("canOperate", center.canOperate(player.getUUID()));

        BlockPos picked = center.defaultPanel();
        ListTag panels = new ListTag();
        for (BlockPos pos : center.panels()) {
            CompoundTag one = new CompoundTag();
            one.putInt("x", pos.getX());
            one.putInt("y", pos.getY());
            one.putInt("z", pos.getZ());
            one.putInt("modules", modulesAt(level, pos));
            one.putBoolean("selected", pos.equals(picked));
            panels.add(one);
        }
        info.put("panels", panels);

        ListTag remotes = new ListTag();
        for (PanelRemoteCenterBlockEntity.Remote remote : center.remotes()) {
            CompoundTag one = new CompoundTag();
            one.putString("name", remote.name());
            one.putBoolean("online", level.getServer().getPlayerList().getPlayer(remote.player()) != null);
            remotes.add(one);
        }
        info.put("remotes", remotes);

        String ownerName = center.owner() == null ? "-" : nameOf(level, center.owner());
        info.putString("owner", ownerName);

        ListTag access = new ListTag();
        center.accessList().forEach((uuid, levelOfAccess) -> {
            CompoundTag one = new CompoundTag();
            one.putString("name", nameOf(level, uuid));
            one.putString("level", levelOfAccess == PanelRemoteCenterBlockEntity.ACCESS_OPERATE
                    ? "can operate" : "can edit");
            access.add(one);
        });
        info.put("access", access);

        PacketDistributor.sendToPlayer(player, new CenterInfoPacket(center.getBlockPos(), info));
    }

    private static int modulesAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AbstractPanelBlockEntity panel) {
            return panel.getModules() == null ? 0 : panel.getModules().size();
        }
        return -1;
    }

    private static String nameOf(ServerLevel level, java.util.UUID uuid) {
        if (level.getServer().getPlayerList().getPlayer(uuid) instanceof ServerPlayer online) {
            return online.getGameProfile().getName();
        }
        return uuid.toString().substring(0, 8);
    }
}