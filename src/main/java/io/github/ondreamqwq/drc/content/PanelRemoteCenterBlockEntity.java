package io.github.ondreamqwq.drc.content;

import io.github.ondreamqwq.drc.DrcRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import moth.boxxed.panels.api.panel.AbstractPanelBlockEntity;
import moth.boxxed.panels.util.BaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The remote center: it owns one network, serves every panel claimed by the terminals in it, and keeps
 * the list of remotes bound to it.
 *
 * <p><b>It is deliberately not a {@code ModulesNetworkMember}.</b> Ledgering our block into
 * Dashpanels' network makes the game run that network's split/merge bookkeeping the moment this block
 * is broken: {@code ModulesNetwork.removeMember} rebuilds one candidate network per remaining member
 * and pushes {@code networkUpdate} down all of them, which re-sends block entity data for every panel.
 * That showed up as blocks that would not break and reappeared after a right click. A center owns no
 * modules, so it only ever *reads* Dashpanels' networks - through the terminals' neighbours.
 *
 * <p>Panels are resolved on the server and pushed down through the vanilla block entity update tag,
 * because the remote screen renders the real panel block entities and therefore needs them on the
 * client too.
 */
public class PanelRemoteCenterBlockEntity extends BaseBlockEntity implements NetworkMember {

    /** NBT key for the served panels inside the block-entity update tag. */
    public static final String TAG_PANELS = "drc:panels";

    private static final String TAG_TERMINALS = "drc:terminals";
    private static final String TAG_DEFAULT = "drc:default";
    private static final String TAG_OWNER = "drc:owner";
    private static final String TAG_ACCESS = "drc:access";
    private static final String TAG_PLAYER = "player";
    private static final String TAG_LEVEL = "level";
    private static final String TAG_REMOTES = "drc:remotes";
    private static final String TAG_NAME = "name";
    private static final String TAG_SEEN = "seen";

    /** Full control: can open the remote and operate the modules. */
    public static final int ACCESS_OPERATE = 0;

    /** Can look at the setup and change settings, but not operate the modules. */
    public static final int ACCESS_EDIT = 1;

    private UUID networkId = Networks.fresh();
    private UUID owner;
    private final Map<UUID, Integer> access = new LinkedHashMap<>();
    private final List<Remote> remotes = new ArrayList<>();

    /** Every panel served by this center, nearest first. */
    private List<BlockPos> panels = List.of();

    /** The terminal the player picked on the center's screen; only ever one of {@link #terminals()}. */
    private BlockPos defaultTerminal;

    /** Every terminal in this network, nearest first. */
    private List<BlockPos> terminals = List.of();

    private int registryTimer;

    /** One remote that was bound to this center. */
    public record Remote(UUID player, String name, long lastSeen) {
    }

    public PanelRemoteCenterBlockEntity(BlockPos pos, BlockState state) {
        super(DrcRegistry.PANEL_REMOTE_CENTER_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------ network

    @Override
    public UUID networkId() {
        return networkId;
    }

    @Override
    public void setNetworkId(UUID id) {
        if (id == null || id.equals(networkId)) {
            return;
        }
        networkId = id;
        panels = List.of();
        setChanged();
        if (level instanceof ServerLevel server) {
            NetworkRegistry.get(server).ensure(networkId, server.dimension().location(), worldPosition);
        }
    }

    // ------------------------------------------------------------------ access

    public UUID owner() {
        return owner;
    }

    public void claim(ServerPlayer player) {
        if (owner == null) {
            owner = player.getUUID();
            setChanged();
        }
    }

    public Map<UUID, Integer> accessList() {
        return Map.copyOf(access);
    }

    public void grant(UUID player, int accessLevel) {
        access.put(player, accessLevel);
        setChanged();
    }

    public void revoke(UUID player) {
        if (access.remove(player) != null) {
            setChanged();
        }
    }

    public boolean canEdit(UUID player) {
        return player != null && (player.equals(owner) || access.containsKey(player));
    }

    public boolean canOperate(UUID player) {
        if (player == null) {
            return false;
        }
        if (player.equals(owner)) {
            return true;
        }
        Integer level = access.get(player);
        return level != null && level == ACCESS_OPERATE;
    }

    public void recordRemote(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        remotes.removeIf(remote -> remote.player().equals(player.getUUID()));
        remotes.add(new Remote(player.getUUID(), name, System.currentTimeMillis()));
        setChanged();
    }

    public List<Remote> remotes() {
        return List.copyOf(remotes);
    }

    // ------------------------------------------------------------------ panels

    /** The panels this center serves; only meaningful on the client once the update tag arrived. */
    public List<BlockPos> panels() {
        return panels;
    }

    /** The nearest panel, or {@code null} when there is none. */
    public BlockPos panelPos() {
        return panels.isEmpty() ? null : panels.getFirst();
    }

    /** The terminals feeding this center, nearest first. */
    public List<BlockPos> terminals() {
        return terminals;
    }

    /** Whether {@code pos} is one of the terminals in this network. */
    public boolean coversTerminal(BlockPos pos) {
        return terminals.contains(pos);
    }

    /** The terminal the remote should follow, or {@code null} when the player never picked one. */
    public BlockPos defaultTerminal() {
        return defaultTerminal;
    }

    public void setDefaultTerminal(BlockPos terminal) {
        if (terminal == null || terminal.equals(defaultTerminal)) {
            return;
        }
        defaultTerminal = terminal;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Whether {@code pos} is one of the panels this center serves. */
    public boolean covers(BlockPos pos) {
        return panels.contains(pos);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        super.tick(level, pos, state);
        if (level.isClientSide || !(level instanceof ServerLevel server)) {
            return;
        }
        if (++registryTimer >= 20) {
            registryTimer = 0;
            NetworkRegistry registry = NetworkRegistry.get(server);
            registry.ensure(networkId, server.dimension().location(), pos);
            registry.prune(server, networkId, server.dimension().location());
        }
        List<BlockPos> feeding = terminals(server, pos);
        List<BlockPos> found = servedPanels(server);
        if (!found.equals(panels) || !feeding.equals(terminals)) {
            panels = found;
            terminals = feeding;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Every panel claimed by a terminal in this network, nearest first. Sorted by distance rather than
     * by registry order so the list does not reshuffle when a terminal is added.
     */
    /** Every terminal of this network in this dimension, nearest to {@code self} first. */
    private List<BlockPos> terminals(ServerLevel level, BlockPos self) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : NetworkRegistry.get(level).members(networkId, level.dimension().location())) {
            if (level.getBlockEntity(pos) instanceof PanelLinkTerminalBlockEntity) {
                out.add(pos);
            }
        }
        out.sort(Comparator.comparingDouble(self::distSqr));
        return List.copyOf(out);
    }

    private List<BlockPos> servedPanels(ServerLevel level) {
        Set<BlockPos> found = new LinkedHashSet<>();
        for (BlockPos pos : NetworkRegistry.get(level).members(networkId, level.dimension().location())) {
            if (!(level.getBlockEntity(pos) instanceof PanelLinkTerminalBlockEntity terminal)) {
                continue;
            }
            // A terminal claims a whole Dashpanels network, so one terminal usually means a whole row.
            for (BlockPos panel : terminal.panels()) {
                if (level.getBlockEntity(panel) instanceof AbstractPanelBlockEntity live && !live.isRemoved()) {
                    found.add(panel);
                }
            }
        }
        List<BlockPos> out = new ArrayList<>(found);
        out.sort(Comparator.comparingDouble(worldPosition::distSqr));
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put(TAG_PANELS, new LongArrayTag(panels.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put(TAG_TERMINALS, new LongArrayTag(terminals.stream().mapToLong(BlockPos::asLong).toArray()));
        if (defaultTerminal != null) {
            tag.putLong(TAG_DEFAULT, defaultTerminal.asLong());
        }
        return tag;
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UUID stored = Networks.read(tag);
        if (stored != null) {
            networkId = stored;
        }
        owner = tag.contains(TAG_OWNER, Tag.TAG_STRING) ? parseUuid(tag.getString(TAG_OWNER)) : null;
        readAccess(tag);
        readRemotes(tag);
        panels = readPanels(tag, TAG_PANELS);
        terminals = readPanels(tag, TAG_TERMINALS);
        defaultTerminal = tag.contains(TAG_DEFAULT, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_DEFAULT)) : null;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        Networks.write(tag, networkId);
        if (owner != null) {
            tag.putString(TAG_OWNER, owner.toString());
        }
        tag.put(TAG_ACCESS, writeAccess());
        tag.put(TAG_REMOTES, writeRemotes());
        tag.put(TAG_PANELS, new LongArrayTag(panels.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put(TAG_TERMINALS, new LongArrayTag(terminals.stream().mapToLong(BlockPos::asLong).toArray()));
        if (defaultTerminal != null) {
            tag.putLong(TAG_DEFAULT, defaultTerminal.asLong());
        }
    }

    private ListTag writeAccess() {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : access.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString(TAG_PLAYER, entry.getKey().toString());
            one.putInt(TAG_LEVEL, entry.getValue());
            list.add(one);
        }
        return list;
    }

    private void readAccess(CompoundTag tag) {
        access.clear();
        ListTag list = tag.getList(TAG_ACCESS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            UUID player = parseUuid(one.getString(TAG_PLAYER));
            if (player != null) {
                access.put(player, one.getInt(TAG_LEVEL));
            }
        }
    }

    private ListTag writeRemotes() {
        ListTag list = new ListTag();
        for (Remote remote : remotes) {
            CompoundTag one = new CompoundTag();
            one.putString(TAG_PLAYER, remote.player().toString());
            one.putString(TAG_NAME, remote.name());
            one.putLong(TAG_SEEN, remote.lastSeen());
            list.add(one);
        }
        return list;
    }

    private void readRemotes(CompoundTag tag) {
        remotes.clear();
        ListTag list = tag.getList(TAG_REMOTES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            UUID player = parseUuid(one.getString(TAG_PLAYER));
            if (player != null) {
                remotes.add(new Remote(player, one.getString(TAG_NAME), one.getLong(TAG_SEEN)));
            }
        }
    }

    private static List<BlockPos> readPanels(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG_ARRAY)) {
            return List.of();
        }
        long[] raw = tag.getLongArray(key);
        List<BlockPos> out = new ArrayList<>(raw.length);
        for (long packed : raw) {
            out.add(BlockPos.of(packed));
        }
        return List.copyOf(out);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }
}
