package cn.neoeco.io.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import cn.neoeco.io.NeoEcoIOMod;
import cn.neoeco.io.blockentity.SuperIOPortBlockEntity;

/**
 * 区块实体类型注册。
 * <p>
 * 注意两点：
 * <ul>
 * <li>{@code BlockEntityType.Builder.of} 的第二个参数必须是本模组方块的
 * {@code Block} 实例（不是 BlockDefinition）。</li>
 * <li>AE2 的 IO 端口通过 {@code IGridTickable} 由网格 TickManager 驱动，
 * 不需要注册原版 BlockEntityTicker。</li>
 * </ul>
 */
public final class NeoEcoBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> DR =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NeoEcoIOMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperIOPortBlockEntity>>
            SUPER_IO_PORT = DR.register("super_io_port",
                    () -> BlockEntityType.Builder.of(
                            // 这里必须用 lambda 而不是 SuperIOPortBlockEntity::new。
                            // BlockEntityType.Builder.of 的工厂接口是 (BlockPos, BlockState)，
                            // 而本区块实体的构造器是 (BlockEntityType<?>, BlockPos, BlockState)
                            // —— 参数个数不同，无法直接用构造器引用，必须显式传入 type。
                            (pos, state) -> new SuperIOPortBlockEntity(
                                    NeoEcoBlockEntities.SUPER_IO_PORT.get(), pos, state),
                            NeoEcoBlocks.SUPER_IO_PORT.get()).build(null));

    private NeoEcoBlockEntities() {
    }
}
