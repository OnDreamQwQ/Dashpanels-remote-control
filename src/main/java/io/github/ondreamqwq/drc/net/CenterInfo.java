package io.github.ondreamqwq.drc.net;

import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import io.github.ondreamqwq.drc.content.PanelLinkTerminalBlockEntity;
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
        info.putBoolean("canEdit", center.canEdit(player));
        info.putBoolean("canOperate", center.canOperate(player));
        info.putBoolean("isOwner", center.owner() != null && center.owner().equals(player.getUUID()));

        BlockPos picked = center.defaultTerminal();
        ListTag terminals = new ListTag();
        for (BlockPos pos : center.terminals()) {
            CompoundTag one = new CompoundTag();
            one.putInt("x", pos.getX());
            one.putInt("y", pos.getY());
            one.putInt("z", pos.getZ());
            one.putInt("panels", claimedAt(level, pos));
            one.putBoolean("selected", pos.equals(picked));
            // Blank stays blank so the screen can show its own "unnamed" wording.
            if (level.getBlockEntity(pos)
                    instanceof io.github.ondreamqwq.drc.content.PanelLinkTerminalBlockEntity terminal) {
                one.putString("name", terminal.name());
            }
            terminals.add(one);
        }
        info.put("terminals", terminals);

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
        center.accessList().forEach((name, levelOfAccess) -> {
            CompoundTag one = new CompoundTag();
            one.putString("name", name);
            one.putInt("level_id", levelOfAccess);
            access.add(one);
        });
        info.put("access", access);

        PacketDistributor.sendToPlayer(player, new CenterInfoPacket(center.getBlockPos(), info));
    }

    private static int claimedAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PanelLinkTerminalBlockEntity terminal) {
            return terminal.panels().size();
        }
        return -1;
    }

    private static String nameOf(ServerLevel level, java.util.UUID uuid) {
        if (level.getServer().getPlayerList().getPlayer(uuid) instanceof ServerPlayer online) {
            return online.getGameProfile().getName();
        }
        return uuid.toString();
    }
}