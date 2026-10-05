package cn.neoeco.io;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import cn.neoeco.io.blockentity.SuperIOPortBlockEntity;
import cn.neoeco.io.registry.NeoEcoRegistry;

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

    /** 创造模式标签页 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS
            .register("main", () -> CreativeModeTab.builder(
                            // 1.21.1 的 builder 需要 (Row, column) 两个参数，无无参重载
                            CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + MOD_ID))
                    .icon(() -> NeoEcoRegistry.SUPER_IO_PORT_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(NeoEcoRegistry.SUPER_IO_PORT_ITEM.get());
                    })
                    .build());

    public NeoEcoIOMod(IEventBus modEventBus, ModContainer modContainer) {
        // ------------------------------------------------------------------
        // 【关键】主动触发 NeoEcoRegistry 的类初始化。
        //
        // DeferredRegister 依赖静态初始化器在类加载时收集条目；而 NeoForge 规定：
        // 某个注册表的 RegisterEvent 一旦触发，就不允许再向它的 DeferredRegister
        // 添加条目，否则抛 IllegalStateException
        //   "Cannot register new entries to DeferredRegister after
        //    RegisterEvent has been fired."
        //
        // 如果本类只是把 DeferredRegister 注册到事件总线而不引用 NeoEcoRegistry，
        // 那么 NeoEcoRegistry 会一直拖到「区块实体类型」注册时才被某个 lambda
        // 间接加载 —— 那时方块注册事件早已过去，方块注册会直接崩溃。
        //
        // 访问一个字段即可强制其 <clinit> 立即执行，保证全部注册都发生在
        // 任何 RegisterEvent 之前。
        // ------------------------------------------------------------------
        if (NeoEcoRegistry.SUPER_IO_PORT_ITEM == null) {
            throw new IllegalStateException("registry not initialized");
        }

        NeoEcoRegistry.BLOCKS.register(modEventBus);
        NeoEcoRegistry.ITEMS.register(modEventBus);
        NeoEcoRegistry.BLOCK_ENTITY_TYPES.register(modEventBus);
        NeoEcoRegistry.MENUS.register(modEventBus);

        CREATIVE_TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, NeoEcoIOConfig.SPEC);

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            bindBlockEntity();
            registerUpgrades();
        });
    }

    /**
     * 把区块实体类型装配进方块。
     * <p>
     * <b>这一步不能省。</b>AE2 的 {@code AEBaseEntityBlock} 把方块实体类型存在一个
     * 内部字段里，靠 {@code setBlockEntity(...)} 注入；{@code newBlockEntity()} 直接
     * 使用该字段。如果没调用它，放置方块时会抛：
     * <pre>
     * NullPointerException: Cannot invoke
     *   "BlockEntityType.create(BlockPos, BlockState)"
     *   because "this.blockEntityType" is null
     *     at appeng.block.AEBaseEntityBlock.newBlockEntity
     * </pre>
     * AE2 自己的方块是在其注册工厂里完成的，我们这里在 common setup
     * （两个注册表都已填充）时补上。
     * <p>
     * 两个 ticker 传 null：AE2 的机器由网格 TickManager 通过
     * {@code IGridTickable} 驱动，不需要原版 BlockEntityTicker。
     */
    private void bindBlockEntity() {
        NeoEcoRegistry.SUPER_IO_PORT.get().setBlockEntity(
                SuperIOPortBlockEntity.class,
                NeoEcoRegistry.SUPER_IO_PORT_BE.get(),
                null,
                null);
    }

    /**
     * 向 AE2 注册本机方块支持的升级卡，并复用 AE2 原版加速卡。
     * <p>
     * 必须推迟到 common setup 执行，因为 AE2 在 FMLCommonSetupEvent 时才建立
     * 存储单元处理器与升级表。
     */
    private void registerUpgrades() {
        // AE2 的 AEItems.XXX 是 ItemDefinition，用 asItem() 取 ItemLike。
        Upgrades.add(AEItems.SPEED_CARD.asItem(), NeoEcoRegistry.SUPER_IO_PORT.get(),
                NeoEcoIOConfig.MAX_SPEED_CARDS.get());
        Upgrades.add(AEItems.REDSTONE_CARD.asItem(), NeoEcoRegistry.SUPER_IO_PORT.get(), 1);

        // 确保 AE2 方块表已完成类初始化。
        // AEBlocks.IO_PORT 是 BlockDefinition，取方块要用 block()，它没有 get()。
        AEBlocks.IO_PORT.block();
    }
}
