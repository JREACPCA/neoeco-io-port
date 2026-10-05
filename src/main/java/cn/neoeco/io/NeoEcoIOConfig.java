package cn.neoeco.io;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的独立配置。
 * <p>
 * 与 ME IO 端口相关：原版 AE2 把「单次操作搬运量 256」写死在
 * {@code IOPortBlockEntity.tickingRequest} 方法体里，没有任何配置项，
 * 所以本模组自己提供可调参数。
 * <p>
 * <b>额度单位说明</b>：额度是「操作次数」，不是物品个数。
 * 实际搬运量 = {@code 额度 × AEKey.getAmountPerOperation()}。
 * 物品的 {@code getAmountPerOperation()} 为 1（1 操作 = 1 个物品），
 * 流体为 125（1 操作 = 125 mB）。
 * <p>
 * <b>量级参考</b>（默认配置：基础 5,000,000、每卡 +6 倍、最多 7 张卡）：
 * <pre>
 *   0 张卡:  5,000,000 操作/tick  =  1 亿物品/秒
 *   7 张卡: 215,000,000 操作/tick = 43 亿物品/秒
 * </pre>
 * 注意耗能与吞吐量等比增长（AE2 按每操作单位收 1 AE），
 * 该量级下每秒能耗可达数亿 AE，请确保能源系统能支撑。
 */
public final class NeoEcoIOConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BASE_TRANSFER_BUDGET = BUILDER
            .comment("基础搬运额度（单位＝操作次数，不是物品个数）。",
                    "原版 ME IO 端口为 256。本模组默认 5,000,000。",
                    "物品的 getAmountPerOperation() 为 1，所以对物品而言额度就等于物品数。",
                    "流体为 125，即 1 操作 = 125 mB。")
            .defineInRange("baseTransferBudget", 5_000_000, 64, 100_000_000);

    public static final ModConfigSpec.IntValue BUDGET_PER_SPEED_CARD = BUILDER
            .comment("每张 AE2 加速卡额外增加的基础额度倍数（加法叠加）。",
                    "例如基础 5,000,000、本项 6 时：1 张卡 = 5,000,000*(1+6) = 35,000,000。")
            .defineInRange("budgetMultiplierPerSpeedCard", 6, 0, 256);

    public static final ModConfigSpec.IntValue MAX_SPEED_CARDS = BUILDER
            .comment("可安装的 AE2 加速卡上限。原版 ME IO 端口为 3。",
                    "注意：该值同时决定升级槽数量，且不能超过 AE2 自身的白名单上限。")
            .defineInRange("maxSpeedCards", 7, 1, 16);

    public static final ModConfigSpec.IntValue UPGRADE_SLOTS = BUILDER
            .comment("升级槽总数（加速卡 + 红石卡共用）。")
            .defineInRange("upgradeSlots", 8, 1, 16);

    public static final ModConfigSpec.BooleanValue EXTRA_REDSTONE_SLOT = BUILDER
            .comment("是否为红石卡额外预留 1 个槽位（即升级槽 = 加速卡上限 + 1）。",
                    "关闭时红石卡与加速卡争用同一批槽位。")
            .define("extraRedstoneSlot", true);

    public static final ModConfigSpec.IntValue CELL_SLOTS = BUILDER
            .comment("输入（以及输出）存储元件槽位数量。原版 ME IO 端口为 6 + 6 = 12。")
            .defineInRange("cellSlots", 6, 1, 18);

    public static final ModConfigSpec.DoubleValue ENERGY_MULTIPLIER = BUILDER
            .comment("搬运耗能倍率。设置为 1.0 表示与原版相同的单位搬运能耗。",
                    "速度提升本身不改变单位能耗（真实搬运量由 AE2 的 poweredInsert 结算），",
                    "本项用于按需提高整体耗电以平衡性能。",
                    "注意：本模组默认速度远高于原版，每秒耗能可达数亿 AE。")
            .defineInRange("energyMultiplier", 1.0, 0.0, 100.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private NeoEcoIOConfig() {
    }

    /**
     * 计算当前的单次操作搬运额度。
     *
     * @param installedSpeedCards 已安装的 AE2 加速卡数量
     */
    public static long transferBudget(int installedSpeedCards) {
        long base = BASE_TRANSFER_BUDGET.get();
        long perCard = BUDGET_PER_SPEED_CARD.get();
        if (installedSpeedCards <= 0 || perCard <= 0) {
            return base;
        }
        return base * (1 + perCard * installedSpeedCards);
    }

    /**
     * 计算升级槽数量。
     */
    public static int upgradeSlots() {
        if (EXTRA_REDSTONE_SLOT.get()) {
            return Math.max(UPGRADE_SLOTS.get(), MAX_SPEED_CARDS.get() + 1);
        }
        return UPGRADE_SLOTS.get();
    }
}
