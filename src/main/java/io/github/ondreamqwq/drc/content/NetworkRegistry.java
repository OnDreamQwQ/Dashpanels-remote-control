package io.github.ondreamqwq.drc.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which of our blocks sit in which network, per dimension.
 *
 * <p>Kept in the overworld's data storage so a network can span dimensions. Membership is re-asserted
 * by every network block once a second, so the table heals itself after a restart or a crashed save;
 * entries whose block entity is gone are dropped at the same time.
 */
public final class NetworkRegistry extends SavedData {

    private static final String NAME = "dashpanels_remote_control_networks";
    private static final String NETWORKS = "networks";
    private static final String DIMENSIONS = "dimensions";

    private final Map<UUID, Map<String, Set<BlockPos>>> members = new LinkedHashMap<>();

    public NetworkRegistry() {
    }

    public static NetworkRegistry get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NetworkRegistry::new, NetworkRegistry::load), NAME);
    }

    public void ensure(UUID id, ResourceLocation dimension, BlockPos pos) {
        if (id == null) {
            return;
        }
        Map<String, Set<BlockPos>> byDimension = members.computeIfAbsent(id, key -> new LinkedHashMap<>());
        if (byDimension.computeIfAbsent(dimension.toString(), key -> new LinkedHashSet<>()).add(pos.immutable())) {
            setDirty();
        }
    }

    public void remove(UUID id, ResourceLocation dimension, BlockPos pos) {
        Map<String, Set<BlockPos>> byDimension = members.get(id);
        if (byDimension == null) {
            return;
        }
        Set<BlockPos> positions = byDimension.get(dimension.toString());
        if (positions != null && positions.remove(pos)) {
            setDirty();
        }
    }

    public List<BlockPos> members(UUID id, ResourceLocation dimension) {
        Map<String, Set<BlockPos>> byDimension = members.get(id);
        if (byDimension == null) {
            return List.of();
        }
        Set<BlockPos> positions = byDimension.get(dimension.toString());
        return positions == null ? List.of() : List.copyOf(positions);
    }

    /** Drops entries whose block entity is gone, so a removed block cannot keep serving panels. */
    public void prune(ServerLevel level, UUID id, ResourceLocation dimension) {
        Map<String, Set<BlockPos>> byDimension = members.get(id);
        if (byDimension == null) {
            return;
        }
        Set<BlockPos> positions = byDimension.get(dimension.toString());
        if (positions == null) {
            return;
        }
        List<BlockPos> dead = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof NetworkMember)) {
                dead.add(pos);
            }
        }
        if (!dead.isEmpty()) {
            positions.removeAll(dead);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag networks = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Set<BlockPos>>> network : members.entrySet()) {
            CompoundTag dimensions = new CompoundTag();
            for (Map.Entry<String, Set<BlockPos>> dimension : network.getValue().entrySet()) {
                long[] packed = dimension.getValue().stream().mapToLong(BlockPos::asLong).toArray();
                dimensions.put(dimension.getKey(), new LongArrayTag(packed));
            }
            networks.put(network.getKey().toString(), dimensions);
        }
        tag.put(NETWORKS, networks);
        return tag;
    }

    public static NetworkRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkRegistry registry = new NetworkRegistry();
        CompoundTag networks = tag.getCompound(NETWORKS);
        for (String raw : networks.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(raw);
            } catch (IllegalArgumentException broken) {
                continue;
            }
            CompoundTag dimensions = networks.getCompound(raw);
            Map<String, Set<BlockPos>> byDimension = new LinkedHashMap<>();
            for (String dimension : dimensions.getAllKeys()) {
                if (!dimensions.contains(dimension, Tag.TAG_LONG_ARRAY)) {
                    continue;
                }
                Set<BlockPos> positions = new LinkedHashSet<>();
                for (long packed : dimensions.getLongArray(dimension)) {
                    positions.add(BlockPos.of(packed));
                }
                byDimension.put(dimension, positions);
            }
            registry.members.put(id, byDimension);
        }
        return registry;
    }
}
