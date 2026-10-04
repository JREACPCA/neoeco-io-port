package cn.neoeco.io.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import appeng.block.AEBaseBlock;
import appeng.block.AEBaseBlockItem;
import cn.neoeco.io.NeoEcoIOMod;
import cn.neoeco.io.block.SuperIOPortBlock;

/**
 * 方块注册。
 */
public final class NeoEcoBlocks {

    private NeoEcoBlocks() {
    }

    /** 超高速 ME IO 端口 */
    public static final DeferredBlock<SuperIOPortBlock> SUPER_IO_PORT = NeoEcoIOMod.BLOCKS
            .register("super_io_port", () -> new SuperIOPortBlock(metalProps()));

    /**
     * 方块物品。
     * <p>
     * 必须使用 AE2 的 {@link AEBaseBlockItem} 而不是原版 {@code BlockItem}，
     * 否则会丢失 AE2 的机器行为：扳手拆卸、内存卡复制/粘贴设置、
     * 以及升级卡兼容性提示等。AE2 自己的机器（含原版 ME IO 端口）都用它。
     */
    public static final DeferredItem<AEBaseBlockItem> SUPER_IO_PORT_ITEM =
            NeoEcoIOMod.ITEMS.register("super_io_port",
                    () -> new AEBaseBlockItem(SUPER_IO_PORT.get(), new net.minecraft.world.item.Item.Properties()));

    /**
     * 与 AE2 机器方块一致的金属属性。
     * <p>
     * 直接复用 AE2 的 {@link AEBaseBlock#metalProps()}，保证强度、
     * 音效、forceSolidOn 等与原版 IO 端口完全一致。
     */
    private static Block.Properties metalProps() {
        return AEBaseBlock.metalProps();
    }
}
