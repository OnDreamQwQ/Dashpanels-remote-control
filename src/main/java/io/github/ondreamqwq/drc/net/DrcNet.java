package io.github.ondreamqwq.drc.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration for the remote screen. */
public final class DrcNet {

    private DrcNet() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(TerminalClickPacket.TYPE, TerminalClickPacket.STREAM_CODEC,
                TerminalClickPacket::handle);
    }
}