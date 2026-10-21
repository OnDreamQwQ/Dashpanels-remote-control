package io.github.ondreamqwq.drc.client;

import io.github.ondreamqwq.drc.DrcMod;
import io.github.ondreamqwq.drc.DrcRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only wiring: the glint pass the terminal shows when it is bound to a network. */
@EventBusSubscriber(modid = DrcMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DrcClientSetup {

    private DrcClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DrcRegistry.PANEL_LINK_TERMINAL_BE.get(),
                (BlockEntityRendererProvider.Context context) -> new PanelLinkTerminalRenderer(context));
    }
}
