package io.github.ondreamqwq.drc.content;

import io.github.ondreamqwq.drc.DrcRegistry;
import java.util.List;
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
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A terminal that claims the panel it is attached to.
 *
 * <p>It is <b>not</b> a {@code ModulesNetworkMember}: it only reads the Dashpanels network through a
 * neighbouring member to work out which panel it is touching. Ledgering ourselves into that network
 * makes the game re-sync every panel around us whenever this block is broken.
 */
public class PanelLinkTerminalBlockEntity extends BaseBlockEntity implements NetworkMember {

    /** NBT key for the claimed panel, inside the block-entity update tag. */
    public static final String TAG_PANEL = "drc:panel";

    private UUID networkId = Networks.fresh();
    private BlockPos panel = BlockPos.ZERO;
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

    /** The panel this terminal claims, or {@code null} while it touches none. */
    public BlockPos panelPos() {
        return panel.equals(BlockPos.ZERO) ? null : panel;
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
        BlockPos found = findPanel(level, pos);
        if (!found.equals(panel)) {
            panel = found;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** The panel touching this terminal, falling back to the nearest panel on a neighbour's network. */
    private static BlockPos findPanel(Level level, BlockPos self) {
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(self.relative(side)) instanceof AbstractPanelBlockEntity panel
                    && !panel.isRemoved()) {
                return panel.getBlockPos();
            }
        }
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(self.relative(side)) instanceof ModulesNetworkMember member) {
                ModulesNetwork network = ModulesNetworkManager.getNetwork(member);
                if (network == null) {
                    continue;
                }
                BlockPos nearest = null;
                double best = Double.MAX_VALUE;
                for (ModulesNetworkMember other : List.copyOf(network.members)) {
                    if (other instanceof AbstractPanelBlockEntity panel && !panel.isRemoved()) {
                        double distance = self.distSqr(panel.getBlockPos());
                        if (distance < best) {
                            best = distance;
                            nearest = panel.getBlockPos();
                        }
                    }
                }
                if (nearest != null) {
                    return nearest;
                }
            }
        }
        return BlockPos.ZERO;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong(TAG_PANEL, panel.asLong());
        return tag;
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UUID stored = Networks.read(tag);
        if (stored != null) {
            networkId = stored;
        }
        panel = tag.contains(TAG_PANEL, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_PANEL)) : BlockPos.ZERO;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        Networks.write(tag, networkId);
        tag.putLong(TAG_PANEL, panel.asLong());
    }
}
