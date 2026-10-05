package cn.neoeco.io.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.block.AEBaseBlock;
import appeng.block.AEBaseBlockItem;

import cn.neoeco.io.NeoEcoIOMod;
import cn.neoeco.io.block.SuperIOPortBlock;
import cn.neoeco.io.blockentity.SuperIOPortBlockEntity;
import cn.neoeco.io.menu.SuperIOPortMenu;

/**
 * 本模组的全部注册项，**集中在一个类里**。
 * <p>
 * <b>为什么必须集中在一个类（重要）</b>
 * <p>
 * NeoForge 的 {@code DeferredRegister} 有这样一个保护机制：
 * 一旦某个注册表（registry）的 {@code RegisterEvent} 已经触发，
 * 就<b>不允许再向该注册表的 DeferredRegister 添加新条目</b>，否则抛出：
 * <pre>
 * IllegalStateException: Cannot register new entries to DeferredRegister
 *     after RegisterEvent has been fired.
 * </pre>
 * 它依赖静态初始化器在类加载时完成注册。
 * 如果方块、物品、区块实体分散在多个类里，就会出现这样的<b>类加载时机错位</b>：
 * <ol>
 * <li>游戏开始注册「区块实体类型」，触发 {@code RegisterEvent}</li>
 * <li>该事件里会调用本模组区块实体的工厂 lambda</li>
 * <li>若那个 lambda 引用了「方块注册类」，就会<b>此刻才</b>触发它的类加载</li>
 * <li>于是它试图注册方块 —— 但方块注册事件<b>早就过去了</b> → 崩溃</li>
 * </ol>
 * 把全部注册项放进同一个类，并在模组构造器里主动引用一次
 * （见 {@link NeoEcoIOMod} 构造器），就能保证所有注册都在任何
 * {@code RegisterEvent} 之前完成，彻底避免这类问题。
 */
public final class NeoEcoRegistry {

    private NeoEcoRegistry() {
    }

    // ------------------------------------------------------------------
    // DeferredRegisters
    // ------------------------------------------------------------------

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(NeoEcoIOMod.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(NeoEcoIOMod.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NeoEcoIOMod.MOD_ID);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NeoEcoIOMod.MOD_ID);

    // ------------------------------------------------------------------
    // 方块与物品
    // ------------------------------------------------------------------

    /** 超高速 ME IO 端口 */
    public static final DeferredBlock<SuperIOPortBlock> SUPER_IO_PORT =
            BLOCKS.register("super_io_port", () -> new SuperIOPortBlock(metalProps()));

    /**
     * 方块物品。
     * <p>
     * 必须用 AE2 的 {@link AEBaseBlockItem} 而不是原版 {@code BlockItem}，
     * 否则会丢失 AE2 的机器行为：扳手拆卸、内存卡复制/粘贴设置、
     * 以及升级卡兼容性提示等。AE2 自己的机器都用它。
     */
    public static final DeferredHolder<Item, AEBaseBlockItem> SUPER_IO_PORT_ITEM =
            ITEMS.register("super_io_port",
                    () -> new AEBaseBlockItem(SUPER_IO_PORT.get(), new Item.Properties()));

    // ------------------------------------------------------------------
    // 区块实体类型
    //
    // 这里刻意用一个嵌套的 holder 类，并在**静态初始化块**里回填。
    //
    // 原因：工厂 lambda 需要拿到 BlockEntityType 自身，但 Java 规定
    // 字段初始化式里不能引用正在初始化的字段（无论怎么嵌套都会报
    // "self-reference in initializer"）。改为：
    //   1. 先在静态块里用 lambda 创建 holder（此时还不读取它）
    //   2. 再把结果赋给字段（赋值语句的右侧不算"在初始化式中引用自己"）
    // 这样在语法上完全合法，运行时语义也一致。
    // ------------------------------------------------------------------

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperIOPortBlockEntity>>
            SUPER_IO_PORT_BE;

    static {
        SUPER_IO_PORT_BE = BLOCK_ENTITY_TYPES.register("super_io_port",
                () -> BlockEntityType.Builder.of(
                        // 必须用 lambda 显式传入 type：本区块实体的构造器是
                        // (BlockEntityType<?>, BlockPos, BlockState)，
                        // 而工厂接口只要 (BlockPos, BlockState)，参数个数不同。
                        (pos, state) -> new SuperIOPortBlockEntity(
                                SUPER_IO_PORT_BE.get(), pos, state),
                        SUPER_IO_PORT.get()).build(null));
    }

    // ------------------------------------------------------------------
    // 菜单
    // ------------------------------------------------------------------

    public static final DeferredHolder<MenuType<?>, MenuType<SuperIOPortMenu>> SUPER_IO_PORT_MENU =
            MENUS.register("super_io_port", () -> SuperIOPortMenu.TYPE);

    // ------------------------------------------------------------------

    /** 与 AE2 机器方块一致的金属属性。 */
    private static Block.Properties metalProps() {
        return AEBaseBlock.metalProps();
    }
}
