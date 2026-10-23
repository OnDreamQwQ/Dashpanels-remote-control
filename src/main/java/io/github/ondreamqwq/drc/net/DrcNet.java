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
        registrar.playToClient(CenterInfoPacket.TYPE, CenterInfoPacket.STREAM_CODEC,
                CenterInfoPacket::handle);
        registrar.playToServer(RemoteSelectPacket.TYPE, RemoteSelectPacket.STREAM_CODEC,
                RemoteSelectPacket::handle);
        registrar.playToServer(CenterAccessPacket.TYPE, CenterAccessPacket.STREAM_CODEC,
                CenterAccessPacket::handle);
        registrar.playToClient(TerminalOpenPacket.TYPE, TerminalOpenPacket.STREAM_CODEC,
                TerminalOpenPacket::handle);
        registrar.playToServer(RenameTerminalPacket.TYPE, RenameTerminalPacket.STREAM_CODEC,
                RenameTerminalPacket::handle);
        registrar.playToServer(ConsoleUsePacket.TYPE, ConsoleUsePacket.STREAM_CODEC,
                ConsoleUsePacket::handle);
        registrar.playToServer(CycleTerminalPacket.TYPE, CycleTerminalPacket.STREAM_CODEC,
                CycleTerminalPacket::handle);
    }
}