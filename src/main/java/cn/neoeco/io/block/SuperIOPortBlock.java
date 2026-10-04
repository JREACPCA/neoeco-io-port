package cn.neoeco.io.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import cn.neoeco.io.blockentity.SuperIOPortBlockEntity;
import cn.neoeco.io.menu.SuperIOPortMenu;

/**
 * 超高速 ME IO 端口的方块。
 * <p>
 * 与原版 {@code IOPortBlock} 行为一致：POWERED 状态用于正面指示灯，
 * 邻居变化时刷新红石状态，右键打开界面。
 * <p>
 * 注意：本方块<b>不</b>注册原版 {@code BlockEntityTicker}。
 * AE2 的机器由网格的 TickManager 通过 {@code IGridTickable} 驱动，
 * ticker 由 AE2 的 {@code AEBaseEntityBlock#setBlockEntity} 在区块实体
 * 类型工厂里装配；我们直接继承该基类并保持其默认行为即可。
 */
public class SuperIOPortBlock extends AEBaseEntityBlock<SuperIOPortBlockEntity> {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public SuperIOPortBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block blockIn, BlockPos fromPos,
            boolean isMoving) {
        super.neighborChanged(state, level, pos, blockIn, fromPos, isMoving);
        SuperIOPortBlockEntity be = this.getBlockEntity(level, pos);
        if (be != null) {
            be.updateRedstoneState();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState currentState, SuperIOPortBlockEntity be) {
        return currentState.setValue(POWERED, be.isActive());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof SuperIOPortBlockEntity be) {
            if (!level.isClientSide()) {
                MenuOpener.open(SuperIOPortMenu.TYPE, player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return super.useWithoutItem(state, level, pos, player, hitResult);
    }
}
