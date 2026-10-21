package io.github.ondreamqwq.drc.content;

import io.github.ondreamqwq.drc.DrcRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import moth.boxxed.panels.api.network.ModulesNetwork;
import moth.boxxed.panels.api.network.ModulesNetworkManager;
import moth.boxxed.panels.api.network.ModulesNetworkMember;
import moth.boxxed.panels.api.panel.AbstractPanelBlockEntity;
import moth.boxxed.panels.util.BaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A terminal that claims the panels it is wired to.
 *
 * <p>It claims <b>the whole Dashpanels network</b> it touches, not one block: standing a terminal next
 * to any panel of a row hands the remote the entire row, which is the useful unit for a cockpit. When
 * it touches no Dashpanels member at all it falls back to whatever panel block it is physically
 * against.
 *
 * <p>It is <b>not</b> a {@code ModulesNetworkMember}: it only reads that network through a
 * neighbouring member. Ledgering ourselves into it makes the game re-sync every panel around us
 * whenever this block is broken.
 */
public class PanelLinkTerminalBlockEntity extends BaseBlockEntity implements NetworkMember {

    /** NBT key for the claimed panels, inside the block-entity update tag. */
    public static final String TAG_PANELS = "drc:panels";

    /** NBT key for the Aeroworks consoles standing next to this terminal or its panels. */
    public static final String TAG_CONSOLES = "drc:consoles";

    private UUID networkId = Networks.fresh();
    private List<BlockPos> panels = List.of();
    private List<BlockPos> consoles = List.of();
    private int registryTimer;

    public PanelLinkTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(DrcRegistry.PANEL_LINK_TERMINAL_BE.get(), pos, state);
    }

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
        setChanged();
        if (level instanceof ServerLevel server) {
            NetworkRegistry.get(server).ensure(networkId, server.dimension().location(), worldPosition);
        }
    }

    /** The panels this terminal claims, nearest first; empty while it touches none. */
    public List<BlockPos> panels() {
        return panels;
    }

    /** The Aeroworks consoles this terminal can reach, if that mod is installed; empty otherwise. */
    public List<BlockPos> consoles() {
        return consoles;
    }

    /** The nearest claimed panel, or {@code null} when there is none. */
    public BlockPos panelPos() {
        return panels.isEmpty() ? null : panels.getFirst();
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        super.tick(level, pos, state);
        if (level.isClientSide) {
            return;
        }
        if (level instanceof ServerLevel server && ++registryTimer >= 20) {
            registryTimer = 0;
            NetworkRegistry registry = NetworkRegistry.get(server);
            registry.ensure(networkId, server.dimension().location(), pos);
            registry.prune(server, networkId, server.dimension().location());
        }
        List<BlockPos> found = claim(level, pos);
        List<BlockPos> nearby = io.github.ondreamqwq.drc.compat.AeroworksCompat.findConsoles(level, pos, found);
        if (!found.equals(panels) || !nearby.equals(consoles)) {
            panels = found;
            consoles = nearby;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Every panel of every Dashpanels network this terminal touches, nearest first, plus any panel
     * block it is physically against. Reading the network instead of a single block is what makes one
     * terminal serve a whole row.
     */
    private static List<BlockPos> claim(Level level, BlockPos self) {
        Map<BlockPos, AbstractPanelBlockEntity> found = new LinkedHashMap<>();

        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(self.relative(side)) instanceof AbstractPanelBlockEntity panel
                    && !panel.isRemoved()) {
                found.put(panel.getBlockPos(), panel);
            }
        }

        for (Direction side : Direction.values()) {
            if (!(level.getBlockEntity(self.relative(side)) instanceof ModulesNetworkMember member)) {
                continue;
            }
            ModulesNetwork network = ModulesNetworkManager.getNetwork(member);
            if (network == null) {
                continue;
            }
            // Copy first: a panel's own tick may add members to this very set.
            for (ModulesNetworkMember other : List.copyOf(network.members)) {
                if (other instanceof AbstractPanelBlockEntity panel && !panel.isRemoved()) {
                    found.put(panel.getBlockPos(), panel);
                }
            }
        }

        List<BlockPos> out = new ArrayList<>(found.keySet());
        out.sort(Comparator.comparingDouble(self::distSqr));
        return List.copyOf(out);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put(TAG_PANELS, new LongArrayTag(panels.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put(TAG_CONSOLES, new LongArrayTag(consoles.stream().mapToLong(BlockPos::asLong).toArray()));
        return tag;
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UUID stored = Networks.read(tag);
        if (stored != null) {
            networkId = stored;
        }
        panels = readPanels(tag, TAG_PANELS);
        consoles = readPanels(tag, TAG_CONSOLES);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        Networks.write(tag, networkId);
        tag.put(TAG_PANELS, new LongArrayTag(panels.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put(TAG_CONSOLES, new LongArrayTag(consoles.stream().mapToLong(BlockPos::asLong).toArray()));
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
}
