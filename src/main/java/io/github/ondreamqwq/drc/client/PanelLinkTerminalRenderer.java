package io.github.ondreamqwq.drc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ondreamqwq.drc.content.PanelLinkTerminalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the terminal a second time through {@link RenderType#entityGlint()}, which is the same pass an
 * enchanted item uses.
 *
 * <p>No enchantment is involved: the block model itself is still drawn by the chunk renderer, this only
 * adds the glint pass on top of it, and only once the terminal belongs to a network with another block
 * in it. That makes "this one is bound" visible without opening anything.
 */
public class PanelLinkTerminalRenderer implements BlockEntityRenderer<PanelLinkTerminalBlockEntity> {

    public PanelLinkTerminalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PanelLinkTerminalBlockEntity terminal, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!terminal.bound()) {
            return;
        }
        BlockState state = terminal.getBlockState();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderType.entityGlint());
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
