package cn.neoeco.io.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import cn.neoeco.io.NeoEcoIOMod;
import cn.neoeco.io.menu.SuperIOPortMenu;

/**
 * 菜单类型注册。
 */
public final class NeoEcoMenus {

    public static final DeferredRegister<MenuType<?>> DR =
            DeferredRegister.create(Registries.MENU, NeoEcoIOMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SuperIOPortMenu>> SUPER_IO_PORT =
            DR.register("super_io_port", () -> SuperIOPortMenu.TYPE);

    private NeoEcoMenus() {
    }
}
