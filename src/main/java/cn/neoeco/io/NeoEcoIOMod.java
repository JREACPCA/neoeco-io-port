package cn.neoeco.io;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.api.upgrades.Upgrades;

import cn.neoeco.io.registry.NeoEcoBlockEntities;
import cn.neoeco.io.registry.NeoEcoBlocks;
import cn.neoeco.io.registry.NeoEcoMenus;

/**
 * Neo ECO IO Port —— ME IO 端口的高速替代品。
 * <p>
 * 设计要点：
 * <ul>
 * <li>继承 AE2 的 {@code IOPortBlockEntity}，因此存储元件槽、输入/输出面、
 * 满度模式、红石模式、以及所有通过 AE2 标准存储 API（{@code MEStorage} /
 * {@code StorageCells}）注册的存储单元都原样可用。</li>
 * <li>只重写搬运预算与节奏：额度来自本模组配置，并按 AE2 原版加速卡数量提升。</li>
 * <li>因此 Neo ECO AE Extension、MEGA Cells、ExtendedAE 等扩展模组的存储元件
 * 无需任何额外适配即可被识别。</li>
 * </ul>
 */
@Mod(NeoEcoIOMod.MOD_ID)
public class NeoEcoIOMod {

    public static final String MOD_ID = "neoeco_io";

    // ---- DeferredRegisters ----
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS
            .register("main", () -> CreativeModeTab.builder(
                            // 1.21.1 的 builder 需要 (Row, column) 两个参数，没有无参重载
                            CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + MOD_ID))
                    .icon(() -> NeoEcoBlocks.SUPER_IO_PORT_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(NeoEcoBlocks.SUPER_IO_PORT_ITEM.get());
                    })
                    .build());

    public NeoEcoIOMod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        NeoEcoBlockEntities.DR.register(modEventBus);
        NeoEcoMenus.DR.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, NeoEcoIOConfig.SPEC);

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(this::registerUpgrades);
    }

    /**
     * 向 AE2 注册本机方块支持的升级卡，并复用 AE2 原版加速卡。
     * <p>
     * 必须推迟到 common setup 执行，因为 AE2 在 FMLCommonSetupEvent 时才建立
     * 存储单元处理器与升级表。
     */
    private void registerUpgrades() {
        // 允许安装 AE2 原版加速卡与红石卡。
        // 加速卡上限同时决定 UpgradeInventory 允许插入的张数，
        // 因此这里读取的必须与区块实体实际使用的槽位数一致。
        // 注意：AE2 的 AEItems.XXX 是 ItemDefinition，用 asItem() 取 ItemLike。
        Upgrades.add(AEItems.SPEED_CARD.asItem(), NeoEcoBlocks.SUPER_IO_PORT.get(),
                NeoEcoIOConfig.MAX_SPEED_CARDS.get());
        Upgrades.add(AEItems.REDSTONE_CARD.asItem(), NeoEcoBlocks.SUPER_IO_PORT.get(), 1);

        // 确保 AE2 方块表已完成类初始化（避免类加载顺序问题）。
        // AEBlocks.IO_PORT 是 BlockDefinition，取方块要用 block()，它没有 get()。
        AEBlocks.IO_PORT.block();
    }
}
