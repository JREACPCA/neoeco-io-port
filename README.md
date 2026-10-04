# Neo ECO IO Port

一个 **Minecraft 1.21.1 + NeoForge** 的 AE2（应用能源2）附加模组：
提供 ME IO 端口的**高速替代品**，并通过 AE2 标准存储 API 与
**Neo ECO AE Extension** 等扩展模组的存储元件天然兼容。

---

## 1. 它做了什么

方块：**超级 ME IO 端口**（`neoeco_io:super_io_port`）

功能与原版 ME IO 端口**完全一致**：

- 6 个输入元件槽 + 6 个输出元件槽（与原版同布局、同槽位语义）
- 存储元件 ←→ ME 网络 双向批量搬运
- 操作模式（存入网络 / 填充元件）
- 满度模式（搬空 / 搬一半 / 装满）
- 红石控制（需插红石卡）
- 按方块朝向划定输入面/输出面，可用 AE2 扳手旋转
- 完整能量结算（走 AE2 的 `StorageHelper.poweredInsert`）
- 支持内存卡复制/粘贴设置、扳手拆卸

**不同的只有速度。**

---

## 2. 速度对比

AE2 原版把「单次搬运额度」**写死**在
`IOPortBlockEntity.tickingRequest` 的方法体里，没有任何配置项：

```java
long itemsToMove = 256;
switch (upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
    case 1 -> itemsToMove *= 2;
    case 2 -> itemsToMove *= 4;
    case 3 -> itemsToMove *= 8;
}
```

本模组把它改成「配置 + 加速卡线性叠加」：

```
额度 = baseTransferBudget × (1 + budgetMultiplierPerSpeedCard × 加速卡张数)
```

默认值下（base = 1024，每卡 +4）：

| 加速卡张数 | 原版 ME IO 端口 | 本模组（默认配置） | 倍率 |
|---:|---:|---:|---:|
| 0 | 256 | 1024 | 4.0× |
| 1 | 512 | 5120 | 10× |
| 2 | 1024 | 9216 | 9.0× |
| 3 | 2048 | 13312 | 6.5× |
| 4 | —（上限 3） | 17408 | — |
| 6（默认上限） | — | 25600 | — |

> **额度的单位是「操作次数」，不是物品个数。**
> 实际搬运量 = `额度 × AEKey.getAmountPerOperation()`。
> 对物品 `getAmountPerOperation()` 为 1，所以对物品而言额度就等于物品数。

**搬运频率**：与原版一致。额度用光且还有活干时返回
`TickRateModulation.URGENT`（最快 1 tick 一次，即每刻都搬），
搬空后退到 `IDLE`（最慢 5 tick），无活时节点休眠。
所以本模组的提升是「每次搬得更多」，与原版加速卡的设计意图一致。

**能耗**：单位搬运耗能与原版相同，因此总耗能随吞吐量等比上升，不会白拿性能。

---

## 3. 与 AE2 扩展模组的兼容性

### 为什么自动兼容

本模组识别存储元件的唯一途径是 AE2 的公开注册表：

```java
StorageCell cell = StorageCells.getCellInventory(cellStack, null);
```

`StorageCells` 是一个全局的 `ICellHandler` 列表。任何按 AE2 标准
注册存储单元的模组都会被自动识别，**无需本模组做任何适配**。

AE2 自己的存储元件走的是同一条路径（实现 `IBasicCellItem`）。

### 已验证的目标：Neo ECO AE Extension

[Neo ECO AE Extension](https://github.com/DancingSnow0517/NeoECOAEExtension)
（Modrinth 项目 `neoecoae`，MC 1.21.1）的 1.1.0 更新日志明确写着：

> 让 ECO 的存储矩阵支持 IO 端口 / 扩展 IO 端口（需要安装 ExtendedAE）

也就是说 ECO 的存储矩阵本身已经按 AE2 标准注册好了存储单元处理器。
本模组用的正是同一条识别路径，因此 ECO 的存储矩阵可以直接插进来使用，
**并且因为本模组更快，吞吐量会高于原版 IO 端口**。

在 `neoforge.mods.toml` 中已把 `neoecoae` 声明为**可选依赖**：

```toml
[[dependencies.neoeco_io]]
modId = "neoecoae"
type = "optional"
versionRange = "[1.0.0,)"
```

### 兼容范围说明

| 场景 | 是否兼容 | 原因 |
|---|---|---|
| Neo ECO AE Extension 存储矩阵 | ✅ | 走标准 `ICellHandler` 注册 |
| MEGA Cells、ExtendedAE、AE2 Things 的存储元件 | ✅ | 同上 |
| 遵循 AE2 `MEStorage` / `StorageCell` 的任意扩展 | ✅ | 同上 |
| 流体/气体/化学品等非物品键 | ✅ | 搬运循环基于 `AEKey`，按 `getAmountPerOperation()` 换算 |
| 需要额外模组才有意义的键类型（如 Mekanism 化学品） | ✅ | 只要该模组按 AE2 标准注册了键类型，本模组不关心具体键类型 |

> 本模组**不**需要在编译期依赖任何扩展模组，也不需要打包它们的代码。
> 这是刻意的设计：兼容性来自 AE2 的公开契约，而不是脆弱的实现耦合。

---

## 4. 配置

配置文件：`config/neoeco_io-common.toml`

| 键 | 默认 | 说明 |
|---|---|---|
| `baseTransferBudget` | `1024` | 基础搬运额度（操作次数）。原版为 256。 |
| `budgetMultiplierPerSpeedCard` | `4` | 每张加速卡额外增加的额度倍数（线性叠加）。 |
| `maxSpeedCards` | `6` | 可安装的加速卡上限。原版为 3。 |
| `upgradeSlots` | `7` | 升级槽总数（加速卡 + 红石卡共用）。 |
| `extraRedstoneSlot` | `true` | 是否为红石卡额外预留 1 个槽位。 |
| `cellSlots` | `6` | 输入（及输出）元件槽数量。 |
| `energyMultiplier` | `1.0` | 整体搬运耗能倍率。 |

> ⚠️ `maxSpeedCards` 与 `upgradeSlots` 存在约束关系：
> 升级槽数量必须 ≥ 加速卡上限（+1 若开启红石卡预留）。
> 修改后需要重启游戏，因为升级槽数量在区块实体构造时确定。

---

## 5. 合成

```
工程处理器   加速卡   工程处理器
加速卡      ME IO 端口  加速卡
工程处理器   加速卡   工程处理器
```

即用 4 张加速卡 + 4 个工程处理器升级 1 个原版 ME IO 端口。

> 如果你的整合包想调整配方，直接覆盖
> `data/neoeco_io/recipe/super_io_port.json` 即可。

---

## 6. 构建

本工程使用 **ModDevGradle 2.0.148**。

```bash
# 需要 JDK 21
./gradlew build          # 产物在 build/libs/
./gradlew runClient      # 启动开发客户端
./gradlew runServer      # 启动开发服务端
```

依赖坐标（来自 Maven Central，无需额外仓库）：

```groovy
compileOnly "org.appliedenergistics:appliedenergistics2:19.2.18:api"
runtimeOnly "org.appliedenergistics:appliedenergistics2:19.2.18"
```

详细的环境准备与国内网络注意事项见 [BUILD.md](BUILD.md)。

---

## 7. 实现说明（给改代码的人）

本模组**继承** `appeng.blockentity.storage.IOPortBlockEntity`，复用了它的：

- 元件槽定义与 `ISegmentedInventory.CELLS` 分段库存
- 按朝向划分输入/输出面（`getExposedInventoryForSide`）
- 配置管理器（操作模式 / 满度模式 / 红石模式）
- `getTickingRequest()` 的休眠/唤醒策略
- `matchesFullnessMode()` 的满度判定

**必须重写**的部分及原因——AE2 1.21.1 把这些全设成了 `private`：

| 需要的成员 | 在 AE2 中的可见性 | 本模组的做法 |
|---|---|---|
| `transferContents(...)` | `private` | 用公开 API 重新实现（`MEStorage` + `StorageHelper`） |
| `moveSlot(int)` | `private` | 用 `getSubInventory(CELLS)` 重新实现 |
| `manager` | `private` | 改用公开的 `getConfigManager()` |
| `mySrc` | `private` | 自建 `IActionSource.ofMachine(...)` |
| `upgrades` | `private` | 自建 `IUpgradeInventory` 并覆盖 `getUpgrades()` |
| `lastRedstoneState` | `private` | 自建字段并覆盖 `updateRedstoneState()` |
| `hasWork()` | `private` | 由父类的 `updateTask()` 内部使用，无需访问 |

覆盖 `getUpgrades()` 还带来一个额外好处：AE2 的升级校验会读取
「该方块注册的加速卡上限」，而我们在 `NeoEcoIOMod#registerUpgrades`
里把它注册成了配置值，因此升级槽能真正接受更多加速卡。

---

## 8. 兼容的原版行为差异

请知悉以下**刻意的**差异：

- 加速卡上限从 3 提升到 6（可配置）
- 升级槽从 3 提升到 7（可配置）
- 单次搬运额度远高于原版（可配置）
- **不会**修改原版 ME IO 端口本身的任何数值

---

## 9. 许可证

GPL-3.0（与 Neo ECO AE Extension 保持一致）。

本模组通过 NeoForge 在运行时动态链接 AE2（LGPL-3.0），
不包含或再分发 AE2 的任何代码或资源。

方块贴图、界面贴图与模型均为本工程原创，由
[`tools/gen_textures.py`](tools/gen_textures.py) 生成。

---

## 10. 已知限制

- **尚未在游戏内实测。** 本工程的编译与资源校验已通过，
  但搬运速率、能量结算、界面布局需要你在实机中确认。
- 元件槽布局（6 输入 + 6 输出）与 AE2 的 `CombinedInternalInventory`
  顺序绑定，沿用 AE2 自己菜单的做法；若 AE2 未来改变该布局需要同步更新。
- 界面使用原版 `AbstractContainerScreen` 而非 AE2 的样式系统，
  因为 AE2 的 `StyleManager` 会把样式路径硬编码到 `ae2` 命名空间，
  附属模组无法提供自己的 `screens/*.json`。
