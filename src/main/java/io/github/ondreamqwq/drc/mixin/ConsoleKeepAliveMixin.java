package io.github.ondreamqwq.drc.mixin;

import io.github.ondreamqwq.drc.content.PanelRemoteData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Stops Aeroworks from dropping a console session just because the player walked away.
 *
 * <p>{@code ConsoleBlockEntity.tick()} disconnects the user as soon as
 * {@code playerInRange(...) && ConsoleGroup.anyInRange(...)} is false, which is exactly the case a
 * remote is bought for: the whole point is to sit at the base and drive a console somewhere else. The
 * session is only kept open-ended while the player still carries a remote that is bound to a center,
 * so an ordinary walk-away with nothing in hand still ends the session the way the mod intends.
 *
 * <p>{@code @Pseudo} + {@code require = 0}: Aeroworks is optional, and a version bump upstream must
 * silently disable this instead of crashing the game.
 */
@Pseudo
@Mixin(targets = "com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity")
public abstract class ConsoleKeepAliveMixin {

    @Shadow
    private UUID currentUser;

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mred231/aeroworks/content/controls/console/ConsoleBlockEntity;disconnectUser()V"),
            cancellable = true,
            require = 0)
    private void drc$keepRemoteSession(CallbackInfo ci) {
        if (currentUser == null) {
            return;
        }
        // The mixin only sees the target's public surface, so the cast goes through Object.
        net.minecraft.world.level.block.entity.BlockEntity self =
                (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        Player player = level.getPlayerByUUID(currentUser);
        if (player != null && carriesRemote(player)) {
            ci.cancel();
        }
    }

    private static boolean carriesRemote(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && PanelRemoteData.get(stack) != null) {
                return true;
            }
        }
        return false;
    }

    /** Keeps the shadowed position referenced so obfuscation remapping stays stable. */
    @SuppressWarnings("unused")
    private BlockPos drc$unused() {
        return BlockPos.ZERO;
    }
}
