package cn.neoeco.io.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import cn.neoeco.io.NeoEcoIOMod;
import cn.neoeco.io.menu.SuperIOPortMenu;

/**
 * 超高速 ME IO 端口的界面。
 * <p>
 * <b>为什么不用 AE2 的界面框架</b>：AE2 的 {@code StyleManager} 通过
 * {@code AppEng.makeId(path)} 把样式路径解析到 {@code ae2} 命名空间，
 * 附属模组无法提供自己的 {@code assets/<自己>/screens/*.json}。
 * 因此本界面直接继承原版 {@link AbstractContainerScreen}，自带贴图与布局。
 * <p>
 * <b>槽位定位</b>：菜单里的槽位默认坐标不可用（会互相重叠、且玩家背包会跑到
 * 面板外），所以这里在 {@link #init()} 里按语义把每一个槽位重新摆到与背景贴图
 * 对齐的位置。
 * <p>
 * 坐标系：槽位坐标是<b>面板内相对坐标</b>（左上角为 0,0）；
 * 而背景贴图绘制时每个槽格的凹陷边框画在 {@code x-1 .. x+16} 处，
 * 便于视觉上留出 18 像素间距。
 */
public class SuperIOPortScreen extends AbstractContainerScreen<SuperIOPortMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            NeoEcoIOMod.MOD_ID, "textures/guis/super_io_port.png");

    // ---- 布局常量（必须与 tools/gen_textures.py 的 tex_gui() 逐一对齐）----
    //
    // 面板宽度取原版 GUI 标准的 176，便于玩家背包/快捷栏与其它机器视觉一致。
    // 升级槽做成 2 行 × 4 列（与背包同宽），避免单行 7 格挤爆面板。
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 226;

    private static final int CELL_COLUMNS = 2;

    private static final int INPUT_X = 18;
    private static final int OUTPUT_X = 122;
    private static final int CELL_Y = 80;

    private static final int UPGRADE_X = 8;
    private static final int UPGRADE_Y = 34;
    private static final int UPGRADE_COLUMNS = 4;

    private static final int PLAYER_X = 8;
    private static final int PLAYER_Y = 146;
    private static final int HOTBAR_Y = 206;
    private static final int PLAYER_COLUMNS = 9;

    private static final int INFO_X = 8;
    private static final int INFO_Y = 20;

    public SuperIOPortScreen(SuperIOPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        // 玩家背包标签：紧贴在背包第一行上方
        this.inventoryLabelY = PLAYER_Y - 11;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    /**
     * 按语义重排全部槽位。
     * <p>
     * 顺序很重要：AE2 的 {@code UpgradeableMenu} 构造器依次加入
     * 元件槽（MACHINE_INPUT × 6、MACHINE_OUTPUT × 6）、升级槽（UPGRADE × N），
     * 最后才是玩家背包（PLAYER_INVENTORY 27 + PLAYER_HOTBAR 9）。
     */
    @Override
    protected void init() {
        super.init();

        int input = 0;
        int output = 0;
        int upgrade = 0;
        int player = 0;
        int hotbar = 0;

        for (Slot slot : this.menu.slots) {
            var semantic = this.menu.getSlotSemantic(slot);

            if (semantic == appeng.menu.SlotSemantics.MACHINE_INPUT) {
                setSlotPos(slot, INPUT_X + (input % CELL_COLUMNS) * 18,
                        CELL_Y + (input / CELL_COLUMNS) * 18);
                input++;
            } else if (semantic == appeng.menu.SlotSemantics.MACHINE_OUTPUT) {
                setSlotPos(slot, OUTPUT_X + (output % CELL_COLUMNS) * 18,
                        CELL_Y + (output / CELL_COLUMNS) * 18);
                output++;
            } else if (semantic == appeng.menu.SlotSemantics.UPGRADE) {
                setSlotPos(slot,
                        UPGRADE_X + (upgrade % UPGRADE_COLUMNS) * 18,
                        UPGRADE_Y + (upgrade / UPGRADE_COLUMNS) * 18);
                upgrade++;
            } else if (semantic == appeng.menu.SlotSemantics.PLAYER_INVENTORY) {
                setSlotPos(slot, PLAYER_X + (player % PLAYER_COLUMNS) * 18,
                        PLAYER_Y + (player / PLAYER_COLUMNS) * 18);
                player++;
            } else if (semantic == appeng.menu.SlotSemantics.PLAYER_HOTBAR) {
                setSlotPos(slot, PLAYER_X + hotbar * 18, HOTBAR_Y);
                hotbar++;
            }
            // 其它语义（若有）保持默认位置，不干预
        }
    }

    // ------------------------------------------------------------------
    // 槽位定位
    //
    // 原版 Slot 的 x / y 是 final 字段，Java 不允许直接赋值；
    // NeoForge 的访问转换器里也没有把这两个字段放开（已核对
    // ats/accesstransformer.cfg，其中的 -f 条目不含 inventory.Slot）。
    // 所以只能用反射写入。这只发生在客户端打开界面时，开销可以忽略。
    // ------------------------------------------------------------------

    private static java.lang.reflect.Field SLOT_X;
    private static java.lang.reflect.Field SLOT_Y;

    private static void setSlotPos(Slot slot, int x, int y) {
        try {
            if (SLOT_X == null) {
                SLOT_X = Slot.class.getDeclaredField("x");
                SLOT_X.setAccessible(true);
                SLOT_Y = Slot.class.getDeclaredField("y");
                SLOT_Y.setAccessible(true);
            }
            SLOT_X.setInt(slot, x);
            SLOT_Y.setInt(slot, y);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法设置槽位坐标: " + slot, e);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        // 显示当前速度档位，方便玩家确认加速卡是否生效
        int speedCards = this.menu.getHost().getInstalledSpeedCards();
        long budget = this.menu.getHost().getCurrentTransferBudget();
        Component info = Component.translatable("gui." + NeoEcoIOMod.MOD_ID + ".speed_info", speedCards, budget);
        graphics.drawString(this.font, info, INFO_X, INFO_Y, 0x404040, false);
    }
}
