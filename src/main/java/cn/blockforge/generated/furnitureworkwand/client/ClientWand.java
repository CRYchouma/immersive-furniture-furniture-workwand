package cn.blockforge.generated.furnitureworkwand.client;

import cn.blockforge.generated.furnitureworkwand.WorkWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 打开工作棒配置界面的唯一入口（只在客户端加载）。 */
@OnlyIn(Dist.CLIENT)
public final class ClientWand {

    private ClientWand() {
    }

    public static void openScreen(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!(stack.getItem() instanceof WorkWandItem)) {
            return;
        }
        if (minecraft.screen instanceof WorkWandScreen) {
            return;
        }
        minecraft.setScreen(new WorkWandScreen(stack));
    }
}
