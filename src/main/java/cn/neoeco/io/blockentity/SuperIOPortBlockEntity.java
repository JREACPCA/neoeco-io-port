package cn.neoeco.io.blockentity;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;

import cn.neoeco.io.NeoEcoIOConfig;
import cn.neoeco.io.registry.NeoEcoRegistry;

/**
 * 超高速 ME IO 端口。
 * <p>
 * <b>设计说明：为什么是「重写」而不是「小改」</b>
 * <p>
 * AE2 1.21.1 的 {@code IOPortBlockEntity} 把搬运循环所需的成员全部设为
 * {@code private}：{@code transferContents}、{@code moveSlot}、{@code manager}、
 * {@code mySrc}、{@code upgrades}、{@code inputCells}、{@code outputCells}、
 * {@code isEnabled}、{@code hasWork}、{@code updateTask}。
 * 并且搬运额度 {@code long itemsToMove = 256;} 是方法内的局部变量，
 * 既不是常量也没有任何配置项。子类无法复用，因此本类用 AE2 的
 * <b>公开 API</b> 重新实现了同一套搬运语义。
 * <p>
 * <b>兼容性的关键</b>：识别存储元件只走 AE2 的公开注册表
 * {@link StorageCells#getCellInventory}。任何按 AE2 标准注册
 * {@code ICellHandler} 的模组（Neo ECO AE Extension、MEGA Cells、
 * ExtendedAE 等）都会被自动识别，无需本模组做任何适配。
 */
public class SuperIOPortBlockEntity extends IOPortBlockEntity {

    /** 输入（以及输出）元件槽数量，与原版一致 */
    private static final int CELL_SLOTS = 6;

    private final IUpgradeInventory myUpgrades;

    private final AppEngInternalInventory myInputCells =
            new AppEngInternalInventory(this, CELL_SLOTS);
    private final AppEngInternalInventory myOutputCells =
            new AppEngInternalInventory(this, CELL_SLOTS);
    private final InternalInventory myCombinedInventory =
            new CombinedInternalInventory(this.myInputCells, this.myOutputCells);

    /** 与父类 getExposedInventoryForSide 的语义保持一致：输入侧只进不出 */
    private final InternalInventory myInputCellsExt =
            new FilteredInternalInventory(this.myInputCells, AEItemFilters.INSERT_ONLY);
    private final InternalInventory myOutputCellsExt =
            new FilteredInternalInventory(this.myOutputCells, AEItemFilters.EXTRACT_ONLY);

    private final IActionSource myActionSource;

    /**
     * 自己跟踪红石状态。
     * <p>
     * 父类的 {@code lastRedstoneState} 与 {@code getRedstoneState()} 都是 private，
     * 无法读取，因此独立记录。方块侧的 {@code neighborChanged} 会调用
     * {@link #updateRedstoneState()}，我们覆写该方法同步自己的字段。
     */
    private YesNo myRedstoneState = YesNo.UNDECIDED;

    public SuperIOPortBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);

        // 用本机方块创建升级库存 → AE2 的白名单匹配到的是我们注册的
        // 「加速卡上限 = maxSpeedCards」，而不是原版 IO 端口的 3 张。
        this.myUpgrades = UpgradeInventories.forMachine(
                NeoEcoRegistry.SUPER_IO_PORT.get(),
                NeoEcoIOConfig.upgradeSlots(),
                this::onUpgradesChanged);

        // 父类的 mySrc 是 private，这里自建等价的机器来源
        this.myActionSource = IActionSource.ofMachine(new IActionHost() {
            @Override
            public IGridNode getActionableNode() {
                return SuperIOPortBlockEntity.this.getMainNode().getNode();
            }
        });
    }

    // ------------------------------------------------------------------
    // 升级槽
    // ------------------------------------------------------------------

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.myUpgrades;
    }

    private void onUpgradesChanged() {
        this.saveChanges();
        // 红石卡的有无会改变本机是否工作，需要重新评估休眠/唤醒
        this.wakeOrSleep();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        // 父类写的是它自己的 3 格升级库存，这里覆盖为本机的多格库存
        this.myUpgrades.writeToNBT(data, "upgrades", registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.myUpgrades.readFromNBT(data, "upgrades", registries);
    }

    // ------------------------------------------------------------------
    // 库存分段：让「搬运循环」与「菜单」看到同一套库存对象
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ISegmentedInventory.UPGRADES)) {
            return this.myUpgrades;
        } else if (id.equals(ISegmentedInventory.CELLS)) {
            return this.myCombinedInventory;
        }
        return super.getSubInventory(id);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.myCombinedInventory;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(net.minecraft.core.Direction facing) {
        if (facing == this.getTop() || facing == this.getTop().getOpposite()) {
            return this.myInputCellsExt;
        }
        return this.myOutputCellsExt;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.myInputCells) {
            this.wakeOrSleep();
        }
    }

    // ------------------------------------------------------------------
    // 红石控制
    // 父类的 isEnabled() 是 private，无法覆盖，因此本类自行判定并决定
    // 是否搬东西（见 tickingRequest）。
    // ------------------------------------------------------------------

    private boolean isRedstonePositive() {
        if (this.myRedstoneState == YesNo.UNDECIDED) {
            this.updateRedstoneState();
        }
        return this.myRedstoneState == YesNo.YES;
    }

    /**
     * 同步自身红石状态，并让父类内部字段也保持同步。
     * 方块在邻居变化时会调用本方法。
     */
    @Override
    public void updateRedstoneState() {
        super.updateRedstoneState();

        if (this.level == null) {
            return;
        }

        YesNo current = this.level.getBestNeighborSignal(this.worldPosition) != 0 ? YesNo.YES : YesNo.NO;
        if (this.myRedstoneState != current) {
            this.myRedstoneState = current;
            this.wakeOrSleep();
        }
    }

    /** 本机是否被允许工作（与原版 isEnabled 语义一致） */
    private boolean isEnabled() {
        if (!this.myUpgrades.isInstalled(AEItems.REDSTONE_CARD)) {
            return true;
        }
        if (this.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED) == appeng.api.config.RedstoneMode.HIGH_SIGNAL) {
            return this.isRedstonePositive();
        }
        return !this.isRedstonePositive();
    }

    private boolean hasWork() {
        return this.isEnabled() && !this.myInputCells.isEmpty();
    }

    private OperationMode getOperationMode() {
        return this.getConfigManager().getSetting(Settings.OPERATION_MODE);
    }

    /**
     * 唤醒或休眠本节点。
     * <p>
     * 父类的 {@code updateTask()} 是 private，这里用公开的
     * {@code IGrid.getTickManager()} 做等价的事。
     */
    private void wakeOrSleep() {
        this.getMainNode().ifPresent((grid, node) -> {
            if (this.hasWork()) {
                grid.getTickManager().wakeDevice(node);
            } else {
                grid.getTickManager().sleepDevice(node);
            }
        });
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.IOPort, !this.hasWork());
    }

    // ------------------------------------------------------------------
    // 核心：搬运
    // ------------------------------------------------------------------

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.getMainNode().isActive()) {
            return TickRateModulation.IDLE;
        }

        // 被红石卡禁用时不做任何事
        if (!this.isEnabled()) {
            return TickRateModulation.IDLE;
        }

        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return TickRateModulation.IDLE;
        }

        // 本模组的速度来源：配置化额度 × 加速卡加成。
        // 原版此处固定为 256，加速卡倍率为 ×2 / ×4 / ×8（上限 3 张）。
        long budget = NeoEcoIOConfig.transferBudget(
                this.myUpgrades.getInstalledUpgrades(AEItems.SPEED_CARD));

        TickRateModulation result = TickRateModulation.SLEEP;

        for (int slot = 0; slot < CELL_SLOTS; slot++) {
            ItemStack cellStack = this.myInputCells.getStackInSlot(slot);

            StorageCell cellInv = StorageCells.getCellInventory(cellStack, null);
            if (cellInv == null) {
                // 不是合法存储元件（含空槽）：尝试把它挪到输出槽，与原版行为一致
                moveSlot(slot);
                continue;
            }

            if (budget > 0) {
                budget = transferContents(grid, cellInv, budget);

                // 额度还有剩 → 这次没搬满，退到最慢间隔；
                // 额度用光 → 还有活干，加速到最快间隔（每 tick 一次）。
                result = budget > 0 ? TickRateModulation.IDLE : TickRateModulation.URGENT;
            }

            if (budget > 0 && matchesFullnessMode(cellInv) && moveSlot(slot)) {
                result = TickRateModulation.URGENT;
            }
        }

        return result;
    }

    /**
     * 把已搬完（或不应继续搬运）的元件从输入槽挪到输出槽。
     *
     * @return 是否成功挪动
     */
    private boolean moveSlot(int slot) {
        ItemStack moved = this.myInputCells.getStackInSlot(slot);
        if (moved.isEmpty()) {
            return false;
        }

        ItemStack remaining = this.myOutputCells.insertItem(slot, moved, false);
        if (remaining.isEmpty()) {
            this.myInputCells.setItemDirect(slot, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    /**
     * 在「存储元件」与「ME 网络」之间搬运内容。
     * <p>
     * 语义与 AE2 原版 {@code transferContents} 一致，区别只有：
     * <ol>
     * <li>额度 {@code budget} 来自配置 + 加速卡数量，而不是写死的 256</li>
     * <li>使用 {@link KeyCounter} 快照，避免遍历过程中底层库存被修改</li>
     * </ol>
     *
     * @param grid   所在网格
     * @param cell   目标存储元件
     * @param budget 本次 tick 剩余的操作额度
     * @return 剩余额度
     */
    private long transferContents(IGrid grid, StorageCell cell, long budget) {
        MEStorage networkInv = grid.getStorageService().getInventory();

        MEStorage source;
        MEStorage destination;
        KeyCounter snapshot;

        if (this.getOperationMode() == OperationMode.EMPTY) {
            // 元件 → 网络
            source = cell;
            destination = networkInv;
            snapshot = cell.getAvailableStacks();
        } else {
            // 网络 → 元件
            source = networkInv;
            destination = cell;
            snapshot = grid.getStorageService().getCachedInventory();
        }

        var energy = grid.getEnergyService();
        boolean didStuff;

        do {
            didStuff = false;

            for (var entry : snapshot) {
                long available = entry.getLongValue();
                if (available <= 0) {
                    continue;
                }

                AEKey what = entry.getKey();
                int amountPerOperation = Math.max(1, what.getAmountPerOperation());

                long accepted = destination.insert(what, available, Actionable.SIMULATE, this.myActionSource);
                if (accepted <= 0) {
                    continue;
                }

                // 关键：额度单位是「操作次数」，换算成实际数量要乘以每操作数量
                accepted = Math.min(accepted, budget * amountPerOperation);

                long extracted = source.extract(what, accepted, Actionable.MODULATE, this.myActionSource);
                if (extracted <= 0) {
                    continue;
                }

                long inserted = StorageHelper.poweredInsert(
                        energy, destination, what, extracted, this.myActionSource);

                if (inserted < extracted) {
                    // 能量不足或目标已满：把没插进去的还回去
                    source.insert(what, extracted - inserted, Actionable.MODULATE, this.myActionSource);
                }

                if (inserted > 0) {
                    budget -= Math.max(1, inserted / amountPerOperation);
                    didStuff = true;
                }

                break;
            }
        } while (budget > 0 && didStuff);

        return budget;
    }

    // ------------------------------------------------------------------
    // 掉落与清空：父类遍历它自己的 private upgrades，这里改为本机的
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack upgrade : this.myUpgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.myUpgrades.clear();
    }

    // ------------------------------------------------------------------
    // 供界面显示
    // ------------------------------------------------------------------

    public int getInstalledSpeedCards() {
        return this.myUpgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
    }

    public long getCurrentTransferBudget() {
        return NeoEcoIOConfig.transferBudget(this.getInstalledSpeedCards());
    }

    public int getUpgradeSlotCount() {
        return this.myUpgrades.size();
    }

    /** 指定输入槽内元件的状态，无元件时返回 null。 */
    @Nullable
    public CellState getCellStatus(int slot) {
        if (slot < 0 || slot >= CELL_SLOTS) {
            return null;
        }
        StorageCell cell = StorageCells.getCellInventory(this.myInputCells.getStackInSlot(slot), null);
        return cell == null ? null : cell.getStatus();
    }
}
