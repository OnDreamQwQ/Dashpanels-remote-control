package io.github.ondreamqwq.drc.content;

import io.github.ondreamqwq.drc.DrcRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Marker for the blocks that can carry a network id, plus the Create-style binding gesture. */
public interface NetworkBlock {

    /** Whether the stack is a block item of one of our network blocks. */
    static boolean isNetworkItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof NetworkBlock;
    }

    /** Whether the given block is a network block. */
    static boolean isNetworkBlock(Block block) {
        return block instanceof NetworkBlock;
    }

    /** Convenience so callers do not have to reach into the registry themselves. */
    static boolean isCenterItem(ItemStack stack) {
        return stack.is(DrcRegistry.PANEL_REMOTE_CENTER_ITEM.get());
    }

    /**
     * The Create stock-link gesture: right click a placed network block to make this stack remember its
     * network, sneak and right click to forget it again. Whatever the stack remembers is the network the
     * next block placed from it joins.
     */
    static void binding(Level level, Player player, ItemStack stack, NetworkMember member) {
        if (level.isClientSide || player == null) {
            return;
        }
        if (player.isShiftKeyDown()) {
            member.setNetworkId(Networks.fresh());
            Networks.setItem(stack, null);
            player.displayClientMessage(Component.literal("面板网络：已清除绑定，这个方块现在是新网络的起点")
                    .withStyle(ChatFormatting.GOLD), false);
        } else {
            Networks.setItem(stack, member.networkId());
            player.displayClientMessage(Component.literal("面板网络：已记下 " + Networks.shortId(member.networkId())
                            + "，放下后加入这个网络")
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }
}
