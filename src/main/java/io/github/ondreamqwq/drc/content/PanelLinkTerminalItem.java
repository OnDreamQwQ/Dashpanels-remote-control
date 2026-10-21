package io.github.ondreamqwq.drc.content;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The terminal item, which shines once it carries a network.
 *
 * <p>The glint is the vanilla foil pass, driven by {@link #isFoil(ItemStack)} - no enchantment is
 * added to the stack, so it cannot be removed by a grindstone, cannot be disenchanted, and does not
 * change the item's data in any way. It is purely "this terminal remembers a network".
 */
public class PanelLinkTerminalItem extends BlockItem {

    public PanelLinkTerminalItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return Networks.ofItem(stack) != null;
    }
}
