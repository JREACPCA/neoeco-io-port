package cn.neoeco.io.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import cn.neoeco.io.NeoEcoIOMod;
import cn.neoeco.io.menu.SuperIOPortMenu;

/**
 * 超高速 ME IO 端口的界面。
 * <p>
 * 这里刻意使用原版 {@link AbstractContainerScreen}，而不继承 AE2 的
 * {@code UpgradeableScreen}：AE2 的样式加载器
 * （{@code StyleManager.loadStyleDoc}）会把样式路径硬编码解析到
 * {@code ae2} 命名空间（内部调用 {@code AppEng.makeId}），附属模组
 * 无法提供自己的 {@code assets/<自己>/screens/*.json}。
 * <p>
 * 因此本界面自带一套贴图与布局，槽位坐标在
 * {@code ClientSetup#onRegisterMenuScreens} 与
 * {@link SuperIOPortMenu} 之间对齐：
 * <ul>
 * <li>输入元件槽：x=19, y=17，每行 2 个，共 6 格</li>
 * <li>输出元件槽：x=122, y=17，每行 2 个，共 6 格</li>
 * <li>升级槽：由 AE2 的 {@code SlotSemantics.UPGRADE} 语义给出，
 * 这里按固定位置绘制背景</li>
 * </ul>
 */
public class SuperIOPortScreen extends AbstractContainerScreen<SuperIOPortMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            NeoEcoIOMod.MOD_ID, "textures/guis/super_io_port.png");

    public SuperIOPortScreen(SuperIOPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
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

        // 右下角显示当前速度档位，方便玩家确认加速卡是否生效
        int speedCards = this.menu.getHost().getInstalledSpeedCards();
        long budget = this.menu.getHost().getCurrentTransferBudget();
        Component info = Component.translatable("gui." + NeoEcoIOMod.MOD_ID + ".speed_info", speedCards, budget);
        graphics.drawString(this.font, info, 8, 40, 0x404040, false);
    }
}
