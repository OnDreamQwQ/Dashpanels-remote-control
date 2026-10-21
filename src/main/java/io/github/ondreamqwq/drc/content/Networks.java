package io.github.ondreamqwq.drc.content;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * A network id, carried both by a placed network block and by the block item that is about to become
 * one.
 *
 * <p>The rule is Create's stock link rule: place a block on its own and it starts a network of its
 * own; right click a placed block with another network block item and that item remembers the
 * network, so the next block placed from that stack joins it; sneak and right click clears the
 * remembered id again.
 */
public final class Networks {

    private static final String ROOT = "drc:network";
    private static final String ID = "id";

    private Networks() {
    }

    public static UUID fresh() {
        return UUID.randomUUID();
    }

    /** The first block of an id, so chat reports stay readable. */
    public static String shortId(UUID id) {
        return id == null ? "?" : id.toString().substring(0, 8);
    }

    public static UUID read(CompoundTag tag) {
        if (!tag.contains(ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        String raw = tag.getCompound(ROOT).getString(ID);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }

    public static void write(CompoundTag tag, UUID id) {
        CompoundTag root = new CompoundTag();
        if (id != null) {
            root.putString(ID, id.toString());
        }
        tag.put(ROOT, root);
    }

    /** The network this stack will join when placed, or {@code null} when it starts a new one. */
    public static UUID ofItem(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : read(data.copyTag());
    }

    public static void setItem(ItemStack stack, UUID id) {
        CompoundTag tag = new CompoundTag();
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            tag = existing.copyTag();
        }
        tag.remove(ROOT);
        if (id != null) {
            CompoundTag root = new CompoundTag();
            root.putString(ID, id.toString());
            tag.put(ROOT, root);
        }
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
