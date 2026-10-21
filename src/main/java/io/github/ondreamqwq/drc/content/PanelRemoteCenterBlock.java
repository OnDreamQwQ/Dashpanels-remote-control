package io.github.ondreamqwq.drc.content;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import moth.boxxed.panels.api.network.ModulesNetworkMemberBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The receiving end of the panel remote: it sits on a Dashpanels cable network next to a panel and
 * tells a bound {@link PanelRemoteItem} which panel to project.
 *
 * <p>Same four connector properties as the panel link center block it replaced - they only exist so the model
 * can grow a stub toward each neighbouring cable, and so the block joins the same connectivity graph.
 */
public class PanelRemoteCenterBlock extends ModulesNetworkMemberBlock implements NetworkBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public static final Map<Direction, BooleanProperty> FACING =
            Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST,
                   Direction.SOUTH, SOUTH, Direction.WEST, WEST);

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public PanelRemoteCenterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, Boolean.FALSE)
                .setValue(EAST, Boolean.FALSE)
                .setValue(SOUTH, Boolean.FALSE)
                .setValue(WEST, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends PanelRemoteCenterBlock> codec() {
        return simpleCodec(PanelRemoteCenterBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PanelRemoteCenterBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isConnecting(LevelReader level, BlockPos pos, BlockState state, Direction direction) {
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        return neighbour.getBlock() instanceof ModulesNetworkMemberBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> entry : FACING.entrySet()) {
            state = state.setValue(entry.getValue(),
                    isConnecting(context.getLevel(), context.getClickedPos(), state, entry.getKey()));
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof PanelRemoteCenterBlockEntity center) {
            center.setNetworkId(Networks.ofItem(stack));
            if (placer instanceof ServerPlayer player) {
                center.claim(player);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (NetworkBlock.isNetworkItem(stack) && level.getBlockEntity(pos) instanceof NetworkMember member) {
            NetworkBlock.binding(level, player, stack, member);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BooleanProperty property = FACING.get(direction);
        if (property == null) {
            return state;
        }
        return state.setValue(property, isConnecting(level, pos, state, direction));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PanelRemoteCenterBlockEntity receiver)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // The screen carries the same information the chat report does, only browsable.
            io.github.ondreamqwq.drc.net.CenterInfo.sendTo(serverPlayer, receiver);
            return InteractionResult.SUCCESS;
        }
        BlockPos panel = receiver.panelPos();
        Component text;
        if (panel == null) {
            text = report().append(tone("还没有连上面板", ChatFormatting.YELLOW))
                    .append(tone("（用面板链接终端认领面板）", ChatFormatting.DARK_GRAY));
        } else {
            text = report().append(tone("服务 " + receiver.panels().size() + " 块面板，最近的在 ", ChatFormatting.GRAY))
                    .append(where(panel.getX() + " " + panel.getY() + " " + panel.getZ()));
        }
        text = text.copy().append(tone(" · 网络 ", ChatFormatting.DARK_GRAY))
                .append(where(Networks.shortId(receiver.networkId())));
        player.displayClientMessage(text, false);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ report styling

    private static MutableComponent report() {
        return Component.literal("面板遥控中心").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("：").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent tone(String text, ChatFormatting colour) {
        return Component.literal(text).withStyle(colour);
    }

    private static MutableComponent where(String text) {
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }
}
