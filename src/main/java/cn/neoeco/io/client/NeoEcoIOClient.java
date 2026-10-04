package cn.neoeco.io.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import cn.neoeco.io.NeoEcoIOMod;
import cn.neoeco.io.menu.SuperIOPortMenu;

/**
 * 客户端注册。仅物理客户端加载。
 */
@EventBusSubscriber(modid = NeoEcoIOMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NeoEcoIOClient {

    private NeoEcoIOClient() {
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(SuperIOPortMenu.TYPE, SuperIOPortScreen::new);
    }
}
