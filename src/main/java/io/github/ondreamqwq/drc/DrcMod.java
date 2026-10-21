package io.github.ondreamqwq.drc;

import io.github.ondreamqwq.drc.net.DrcNet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Dashpanels Remote Control.
 *
 * <p>Adds two things to Dashpanels: a <b>panel remote receiver</b> block that attaches to a panel
 * through Dashpanels' own module network, and a <b>panel remote</b> item that opens that panel on a
 * screen. The screen draws the real panel block entities through their own renderers, so every
 * module looks and behaves exactly like it does in the world - including modules added by other
 * addons, which this mod knows nothing about.
 *
 * <p>The switches live in {@link DrcConfig} ({@code config/dashpanels_remote_control-common.toml}).
 */
@Mod(DrcMod.MOD_ID)
public final class DrcMod {

    public static final String MOD_ID = "dashpanels_remote_control";

    public DrcMod(IEventBus modEventBus, ModContainer container) {
        DrcRegistry.register(modEventBus);
        modEventBus.addListener(DrcNet::register);
        container.registerConfig(ModConfig.Type.COMMON, DrcConfig.SPEC);
    }
}