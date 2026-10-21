package io.github.ondreamqwq.drc.content;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import moth.boxxed.panels.api.network.ModulesNetworkMemberBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * The block that claims a panel for a remote center.
 *
 * <p>Place it against a panel and it joins the network the block item remembered; the center then
 * serves that panel. Same four connector properties as the center so the model can grow a stub toward
 * each neighbouring cable.
 */
public class PanelLinkTerminalBlock extends ModulesNetworkMemberBlock implements NetworkBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public static final Map<Direction, BooleanProperty> FACING =
            Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST,
                   Direction.SOUTH, SOUTH, Direction.WEST, WEST);

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public PanelLinkTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, Boolean.FALSE)
                .setValue(EAST, Boolean.FALSE)
                .setValue(SOUTH, Boolean.FALSE)
                .setValue(WEST, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends PanelLinkTerminalBlock> codec() {
        return simpleCodec(PanelLinkTerminalBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PanelLinkTerminalBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isConnecting(LevelReader level, BlockPos pos, BlockState state, Direction direction) {
        return level.getBlockState(pos.relative(direction)).getBlock() instanceof ModulesNetworkMemberBlock;
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
        if (level.getBlockEntity(pos) instanceof PanelLinkTerminalBlockEntity terminal) {
            terminal.setNetworkId(Networks.ofItem(stack));
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BooleanProperty property = FACING.get(direction);
        return property == null ? state : state.setValue(property, isConnecting(level, pos, state, direction));
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (NetworkBlock.isNetworkItem(stack) && level.getBlockEntity(pos) instanceof NetworkMember member) {
            NetworkBlock.binding(level, player, stack, member);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PanelLinkTerminalBlockEntity terminal)) {
            return InteractionResult.SUCCESS;
        }
        Component text;
        BlockPos panel = terminal.panelPos();
        if (panel == null) {
            text = report().append(tone("没有连上面板", ChatFormatting.YELLOW))
                    .append(tone("（贴着一块面板放）", ChatFormatting.DARK_GRAY));
        } else {
            text = report().append(tone("已认领面板 ", ChatFormatting.GRAY))
                    .append(where(panel.getX() + " " + panel.getY() + " " + panel.getZ()));
        }
        text = text.copy().append(tone(" · 网络 ", ChatFormatting.DARK_GRAY))
                .append(where(Networks.shortId(terminal.networkId())));
        player.displayClientMessage(text, false);
        return InteractionResult.SUCCESS;
    }

    private static MutableComponent report() {
        return Component.literal("面板链接终端").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("：").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent tone(String text, ChatFormatting colour) {
        return Component.literal(text).withStyle(colour);
    }

    private static MutableComponent where(String text) {
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }
}
