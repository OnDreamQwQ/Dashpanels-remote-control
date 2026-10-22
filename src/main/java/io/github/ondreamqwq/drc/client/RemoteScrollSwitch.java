package io.github.ondreamqwq.drc.client;

import io.github.ondreamqwq.drc.content.PanelRemoteData;
import io.github.ondreamqwq.drc.net.CycleTerminalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sneak and scroll in the world to walk through the center's terminals, the way a Drive By Wire cable
 * cycles its channel. The remote is a tool you hold, so its terminal switch belongs on the tool, not
 * behind a screen: the player looks at the world, aims at the panel they want, and scrolls.
 */
@EventBusSubscriber(modid = "dashpanels_remote_control", value = Dist.CLIENT)
public final class RemoteScrollSwitch {

    private RemoteScrollSwitch() {
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!minecraft.player.isShiftKeyDown()) {
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!(held.getItem() instanceof io.github.ondreamqwq.drc.content.PanelRemoteItem)) {
            return;
        }
        PanelRemoteData.Bound bound = PanelRemoteData.get(held);
        if (bound == null || minecraft.level == null
                || !bound.dimension().equals(minecraft.level.dimension().location())) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) {
            return;
        }
        PacketDistributor.sendToServer(new CycleTerminalPacket(bound.pos(), delta > 0.0D ? 1 : -1));
        // The packet owns the wheel while sneaking, so a plain scroll does not also zoom the hotbar.
        event.setCanceled(true);
    }
}
