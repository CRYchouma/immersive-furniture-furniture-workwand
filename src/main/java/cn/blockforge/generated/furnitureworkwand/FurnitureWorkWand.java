package cn.blockforge.generated.furnitureworkwand;

import cn.blockforge.generated.furnitureworkwand.network.ModNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组入口。
 *
 * <p>本模组没有新增方块：被点化的是「沉浸式家具」自己的家具，外观依旧是那件家具，
 * 功能数据存在世界存档里（见 {@link FurnitureStations}）。</p>
 *
 * <p>物品只有两件：工作棒（选功能、装功能）和工作材料（安装费）。
 * 界面一律用原版的那套容器界面，所以这里不需要注册任何菜单。</p>
 */
@Mod(FurnitureWorkWand.MODID)
public final class FurnitureWorkWand {

    public static final String MODID = "furniture_workwand";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    /** 工作棒：揣着“待安装清单”，右键家具把它变成功能家具。 */
    public static final RegistryObject<Item> WORK_WAND = ITEMS.register("work_wand",
            () -> new WorkWandItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** 工作材料：装功能时的消耗品，一个功能 3 份、箱子每多 9 格再 2 份。 */
    public static final RegistryObject<Item> WORK_MATERIAL = ITEMS.register("work_material",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public FurnitureWorkWand(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::onBuildCreativeTab);
        ModNetwork.register();
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WORK_WAND);
            event.accept(WORK_MATERIAL);
        }
    }
}
