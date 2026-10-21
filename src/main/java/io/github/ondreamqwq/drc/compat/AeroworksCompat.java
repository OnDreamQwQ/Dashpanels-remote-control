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

    /** One socket of a console, with the box its module occupies inside the console block. */
    public record SocketBox(int socket, net.minecraft.world.phys.AABB box) {
    }

    /**
     * Draws a console's mounted modules, the way Aeroworks draws them on a real console.
     *
     * <p>The model of the console block itself is not drawn here - the caller draws that - and this adds
     * only the modules on top of it, in the console's own space, so the remote can show the console as a
     * model instead of a list. Aeroworks normally hands this to Flywheel; when the remote asks, the
     * instanced path is unavailable, so the plain vertex path is reproduced.
     */
    public static boolean render(Level level, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack pose,
                                 net.minecraft.client.renderer.MultiBufferSource buffers, float partialTick,
                                 int light, int overlay) {
        return isLoaded() && Hooks.render(level, pos, pose, buffers, partialTick, light, overlay);
    }

    /** The hit boxes of a console's sockets, in the console's own block space; empty without Aeroworks. */
    public static List<SocketBox> socketBoxes(Level level, BlockPos pos, float partialTick) {
        return isLoaded() ? Hooks.socketBoxes(level, pos, partialTick) : List.of();
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

    /**
     * Every block that makes up the console at {@code pos}: the console itself, its desk, and any
     * neighbour belonging to the same row. Without this the desk stays behind and the console looks cut.
     */
    public static List<BlockPos> parts(Level level, BlockPos pos) {
        return isLoaded() ? Hooks.parts(level, pos) : List.of();
    }

    /** Opens the console's own overview page - what right clicking the console in the world does. */
    public static boolean openPage(Level level, BlockPos pos) {
        return isLoaded() && Hooks.openPage(level, pos);
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

        static boolean render(Level level, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack pose,
                              net.minecraft.client.renderer.MultiBufferSource buffers, float partialTick,
                              int light, int overlay) {
            if (!(level.getBlockEntity(pos)
                    instanceof com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity console)) {
                return false;
            }
            java.util.List<com.mred231.aeroworks.content.controls.socket.Socket> sockets = console.sockets();
            org.joml.Quaternionf rotation =
                    com.mred231.aeroworks.content.controls.console.ConsoleBlock.rotationFor(console.getBlockState());
            boolean mirrored =
                    com.mred231.aeroworks.content.controls.console.ConsoleBlock.isCeiling(console.getBlockState());
            com.mojang.blaze3d.vertex.VertexConsumer vertices =
                    buffers.getBuffer(net.minecraft.client.renderer.RenderType.cutout());

            for (int socketIndex = 0; socketIndex < sockets.size(); socketIndex++) {
                final int i = socketIndex;
                com.mred231.aeroworks.content.controls.module.MountedModule module = console.module(i);
                if (module == null) {
                    continue;
                }
                com.mred231.aeroworks.content.controls.socket.Socket socket = sockets.get(i);
                net.minecraft.world.phys.Vec3 offset = socket.offset();
                java.util.List<com.mred231.aeroworks.content.controls.module.ModulePartRender.RenderPart> parts =
                        com.mred231.aeroworks.content.controls.module.ModulePartRender.flatten(module, socket.type());
                for (com.mred231.aeroworks.content.controls.module.ModulePartRender.RenderPart part : parts) {
                    dev.engine_room.flywheel.lib.model.baked.PartialModel partial =
                            com.mred231.aeroworks.content.controls.module.ModulePartials.get(part.model());
                    if (partial == null) {
                        continue;
                    }
                    net.createmod.catnip.render.SuperByteBuffer partBuffer =
                            net.createmod.catnip.render.CachedBuffers.partial(partial, console.getBlockState())
                                    .translate(0.5F, 0.5F, 0.5F)
                                    .rotate(rotation)
                                    .translate(offset.x - 0.5D, offset.y - 0.5D, offset.z - 0.5D)
                                    .rotate(socket.orientation())
                                    .translate(-0.5F, 0.0F, -0.5F);
                    com.mred231.aeroworks.content.controls.module.ModulePartRender.apply(partBuffer, part,
                            com.mred231.aeroworks.content.controls.module.ModulePartRender.displayValues(
                                    module, id -> console.channelValueAt(i, id, partialTick)),
                            socket.orientation(), mirrored);
                    partBuffer.light(light).renderInto(pose, vertices);
                }
            }
            return true;
        }

        static List<SocketBox> socketBoxes(Level level, BlockPos pos, float partialTick) {
            if (!(level.getBlockEntity(pos)
                    instanceof com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity console)) {
                return List.of();
            }
            List<SocketBox> out = new java.util.ArrayList<>();
            for (int socket = 0; socket < console.socketCount(); socket++) {
                net.minecraft.world.phys.AABB box = console.moduleBounds(socket, partialTick);
                if (box != null) {
                    out.add(new SocketBox(socket, box));
                }
            }
            return out;
        }

        static List<BlockPos> parts(Level level, BlockPos pos) {
            java.util.Set<BlockPos> found = new java.util.LinkedHashSet<>();
            if (level.getBlockEntity(pos)
                    instanceof com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity console) {
                for (com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity member
                        : com.mred231.aeroworks.content.controls.console.ConsoleGroup.of(console)) {
                    found.add(member.getBlockPos());
                }
            }
            // The desk, the stand and the copycat variants are ordinary blocks around the console, and the
            // console model alone does not include them.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos around = pos.offset(dx, dy, dz);
                        if (!level.isLoaded(around)) {
                            continue;
                        }
                        if (isPart(level.getBlockState(around).getBlock())) {
                            found.add(around);
                        }
                    }
                }
            }
            return java.util.List.copyOf(found);
        }

        private static boolean isPart(net.minecraft.world.level.block.Block block) {
            return block instanceof com.mred231.aeroworks.content.controls.console.ConsoleBlock
                    || block instanceof com.mred231.aeroworks.content.controls.console.ConsoleDeskBlock
                    || block instanceof com.mred231.aeroworks.content.controls.console.ControlStandBlock
                    || block instanceof com.mred231.aeroworks.content.controls.console.CopycatConsoleDeskBlock
                    || block instanceof com.mred231.aeroworks.content.controls.console.CopycatControlStandBlock;
        }

        static boolean openPage(Level level, BlockPos pos) {
            if (!(level.getBlockEntity(pos)
                    instanceof com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity console)) {
                return false;
            }
            com.mred231.aeroworks.content.controls.console.ConsoleScreenOpener.open(console);
            return true;
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
