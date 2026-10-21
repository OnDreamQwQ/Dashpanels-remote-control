package io.github.ondreamqwq.drc;

import io.github.ondreamqwq.drc.content.PanelLinkTerminalBlock;
import io.github.ondreamqwq.drc.content.PanelLinkTerminalBlockEntity;
import io.github.ondreamqwq.drc.content.PanelLinkTerminalItem;
import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlock;
import io.github.ondreamqwq.drc.content.PanelRemoteCenterBlockEntity;
import io.github.ondreamqwq.drc.content.PanelRemoteItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DrcRegistry {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DrcMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DrcMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DrcMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DrcMod.MOD_ID);

    public static final DeferredBlock<PanelRemoteCenterBlock> PANEL_REMOTE_CENTER = BLOCKS.register(
            "panel_remote_center",
            () -> new PanelRemoteCenterBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> PANEL_REMOTE_CENTER_ITEM = ITEMS.register(
            "panel_remote_center",
            () -> new BlockItem(PANEL_REMOTE_CENTER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PanelRemoteCenterBlockEntity>>
            PANEL_REMOTE_CENTER_BE = BLOCK_ENTITIES.register("panel_remote_center", () ->
                    BlockEntityType.Builder
                            .of(PanelRemoteCenterBlockEntity::new, PANEL_REMOTE_CENTER.get())
                            .build(null));

    public static final DeferredBlock<PanelLinkTerminalBlock> PANEL_LINK_TERMINAL = BLOCKS.register(
            "panel_link_terminal",
            () -> new PanelLinkTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F)
                    .noOcclusion()));

    public static final DeferredItem<PanelLinkTerminalItem> PANEL_LINK_TERMINAL_ITEM = ITEMS.register(
            "panel_link_terminal",
            () -> new PanelLinkTerminalItem(PANEL_LINK_TERMINAL.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PanelLinkTerminalBlockEntity>>
            PANEL_LINK_TERMINAL_BE = BLOCK_ENTITIES.register("panel_link_terminal", () ->
                    BlockEntityType.Builder
                            .of(PanelLinkTerminalBlockEntity::new, PANEL_LINK_TERMINAL.get())
                            .build(null));

    public static final DeferredItem<PanelRemoteItem> PANEL_REMOTE = ITEMS.register(
            "panel_remote",
            () -> new PanelRemoteItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DrcMod.MOD_ID))
                    .icon(() -> new ItemStack(PANEL_REMOTE.get()))
                    .displayItems((params, output) -> {
                        output.accept(PANEL_REMOTE_CENTER_ITEM.get());
                        output.accept(PANEL_LINK_TERMINAL_ITEM.get());
                        output.accept(PANEL_REMOTE.get());
                    })
                    .build());

    private DrcRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
    }
}
