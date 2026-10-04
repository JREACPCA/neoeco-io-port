package cn.neoeco.io.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;

import cn.neoeco.io.NeoEcoIOMod;
import cn.neoeco.io.blockentity.SuperIOPortBlockEntity;

/**
 * 超高速 ME IO 端口的容器界面逻辑。
 * <p>
 * 与原版 {@code IOPortMenu} 的差别：
 * <ul>
 * <li>宿主类型是本机区块实体，因此升级槽会读到本机更宽的升级库存</li>
 * <li>注册走 {@code buildUnregistered} 并使用本模组的命名空间。
 * 父类 {@code build(String)} 会把 id 挂到 {@code ae2} 命名空间并塞进
 * AE2 自己的注册队列，附属模组不能用。</li>
 * </ul>
 */
public class SuperIOPortMenu extends UpgradeableMenu<SuperIOPortBlockEntity> {

    private static final ResourceLocation MENU_ID =
            ResourceLocation.fromNamespaceAndPath(NeoEcoIOMod.MOD_ID, "super_io_port");

    public static final MenuType<SuperIOPortMenu> TYPE = MenuTypeBuilder
            .<SuperIOPortMenu, SuperIOPortBlockEntity>create(SuperIOPortMenu::new, SuperIOPortBlockEntity.class)
            .buildUnregistered(MENU_ID);

    /** 与原版 IO 端口一致：输入 6 格（GuiSync 编号沿用原版约定） */
    private static final int CELL_SLOTS = 6;

    @GuiSync(2)
    public FullnessMode fMode = FullnessMode.EMPTY;

    @GuiSync(3)
    public OperationMode opMode = OperationMode.EMPTY;

    public SuperIOPortMenu(int id, Inventory playerInventory, SuperIOPortBlockEntity host) {
        super(TYPE, id, playerInventory, host);
    }

    @Override
    protected void setupConfig() {
        var cells = this.getHost().getSubInventory(ISegmentedInventory.CELLS);

        for (int i = 0; i < CELL_SLOTS; i++) {
            this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS, cells, i),
                    SlotSemantics.MACHINE_INPUT);
        }

        for (int i = 0; i < CELL_SLOTS; i++) {
            this.addSlot(new OutputSlot(cells, CELL_SLOTS + i,
                    RestrictedInputSlot.PlacableItemType.STORAGE_CELLS.icon), SlotSemantics.MACHINE_OUTPUT);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setOperationMode(cm.getSetting(Settings.OPERATION_MODE));
        this.setFullMode(cm.getSetting(Settings.FULLNESS_MODE));
        this.setRedStoneMode(cm.getSetting(Settings.REDSTONE_CONTROLLED));
    }

    public FullnessMode getFullMode() {
        return this.fMode;
    }

    private void setFullMode(FullnessMode mode) {
        this.fMode = mode;
    }

    public OperationMode getOperationMode() {
        return this.opMode;
    }

    private void setOperationMode(OperationMode mode) {
        this.opMode = mode;
    }
}
