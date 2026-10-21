package io.github.ondreamqwq.drc.compat;

import io.github.ondreamqwq.drc.content.PanelLinkTerminalBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Aeroworks support. A console of theirs is not a Dashpanels panel - it is a Create smart block with
 * sockets and mounted modules and its own screens - so the adapter does not pretend it is a panel:
 *
 * <ul>
 *   <li>a terminal also remembers the consoles standing next to it, or next to one of its panels;</li>
 *   <li>the remote lists a console's mounted modules the way Aeroworks' own console screen does -
 *       one row per socket and sub-socket, with the module's own icon and name;</li>
 *   <li>picking a row opens that module's own menu. That is Aeroworks' real control surface, and it is
 *       opened for the player by the server, so it works from the remote instead of only up close.</li>
 * </ul>
 *
 * <p>Nothing in this class mentions an Aeroworks type: the typed code lives in {@link Hooks}, which is
 * only touched after {@link #isLoaded()} said the mod is there. Without Aeroworks installed the class
 * loads fine and every entry point is a no-op, so the mod still runs.
 */
public final class AeroworksCompat {

    /** How many consoles one terminal remembers, so a big deck cannot bloat the save. */
    public static final int MAX_CONSOLES = 16;

    private AeroworksCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("aeroworks");
    }

    /** A console's mounted module: one row of the list the remote shows. */
    public record Node(BlockPos console, int socket, String subPath, String label, ItemStack icon) {
    }

    public static List<BlockPos> findConsoles(Level level, BlockPos terminal, List<BlockPos> panels) {
        return isLoaded() ? Hooks.findConsoles(level, terminal, panels) : List.of();
    }

    public static List<Node> nodes(Level level, List<BlockPos> consoles) {
        return isLoaded() ? Hooks.nodes(level, consoles) : List.of();
    }

    public static String describe(Level level, BlockPos pos) {
        return isLoaded() ? Hooks.describe(level, pos) : pos.toShortString();
    }

    public static boolean openMenu(ServerPlayer player, BlockPos consolePos, int socket, String subPath) {
        return isLoaded() && Hooks.openMenu(player, consolePos, socket, subPath);
    }

    public static List<BlockPos> consolesOf(PanelLinkTerminalBlockEntity terminal) {
        return terminal.consoles();
    }

    /** The typed half. Loaded on first call, which only happens when Aeroworks is present. */
    private static final class Hooks {

        private Hooks() {
        }

        static List<BlockPos> findConsoles(Level level, BlockPos terminal, List<BlockPos> panels) {
            java.util.Set<BlockPos> found = new java.util.LinkedHashSet<>();
            scan(level, terminal, found);
            for (BlockPos panel : panels) {
                scan(level, panel, found);
                if (found.size() >= MAX_CONSOLES) {
                    break;
                }
            }
            List<BlockPos> all = List.copyOf(found);
            return all.size() <= MAX_CONSOLES ? all : all.subList(0, MAX_CONSOLES);
        }

        private static void scan(Level level, BlockPos around, java.util.Set<BlockPos> found) {
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos pos = around.relative(direction);
                if (found.contains(pos)) {
                    continue;
                }
                if (level.getBlockEntity(pos) instanceof com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity) {
                    found.add(pos.immutable());
                }
            }
        }

        static List<Node> nodes(Level level, List<BlockPos> consoles) {
            List<Node> out = new java.util.ArrayList<>();
            for (BlockPos pos : consoles) {
                if (!(level.getBlockEntity(pos)
                        instanceof com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity console)) {
                    continue;
                }
                for (int socket = 0; socket < console.socketCount(); socket++) {
                    com.mred231.aeroworks.content.controls.module.MountedModule module = console.module(socket);
                    if (module != null) {
                        collect(pos, socket, "", module, out);
                    }
                }
            }
            return out;
        }

        private static void collect(BlockPos pos, int socket, String subPath,
                                    com.mred231.aeroworks.content.controls.module.MountedModule module,
                                    List<Node> out) {
            ItemStack icon = module.toStack();
            String label = icon.isEmpty() ? module.type().toString() : icon.getHoverName().getString();
            out.add(new Node(pos, socket, subPath, label, icon));
            module.type().subSockets().forEach((id, sub) -> {
                com.mred231.aeroworks.content.controls.module.MountedModule head = module.heads().get(id);
                if (head != null) {
                    collect(pos, socket, subPath.isEmpty() ? id : subPath + "/" + id, head, out);
                }
            });
        }

        static String describe(Level level, BlockPos pos) {
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return pos.toShortString();
            }
            return blockEntity.getBlockState().getBlock().getName().getString() + " " + pos.toShortString();
        }

        /**
         * Opens the module's own menu without asking Aeroworks whether the player is close enough: the
         * center already decided they may operate it. Everything else stays theirs - their menu, their
         * screen, their packets.
         */
        static boolean openMenu(ServerPlayer player, BlockPos consolePos, int socket, String subPath) {
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return false;
            }
            if (!(level.getBlockEntity(consolePos)
                    instanceof com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity console)) {
                return false;
            }
            com.mred231.aeroworks.content.controls.module.MountedModule node =
                    console.moduleNodeAt((byte) socket, subPath);
            if (node == null) {
                return false;
            }
            if (console.hasController() && !console.checkUser(player.getUUID())) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.aeroworks.console.busy"), true);
                return false;
            }
            com.mred231.aeroworks.content.controls.socket.ConsoleSocket holder =
                    new com.mred231.aeroworks.content.controls.socket.ConsoleSocket(console, socket, subPath);
            net.minecraft.network.chat.Component title = node.toStack().getHoverName();
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, p) -> new com.mred231.aeroworks.content.controls.module.ModuleMenu(
                            com.mred231.aeroworks.AeroworksMenuTypes.MODULE.get(), id, inventory, holder),
                    title), holder::writeTo);
            return true;
        }
    }
}
