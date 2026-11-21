package cn.blockforge.generated.furnitureworkwand.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import cn.blockforge.generated.furnitureworkwand.FurnitureWorkWand;
import cn.blockforge.generated.furnitureworkwand.network.ClickModifierPacket;
import cn.blockforge.generated.furnitureworkwand.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 每次按下右键，把「这一下按着 Ctrl 没有」告诉服务端。
 *
 * <p>只在这一刻发一次，紧跟在这下右键自己的包前面（这个事件在原版发包之前触发），
 * 所以服务端拿到的按键一定是这一次的。平时不报「我现在按着什么」，也就不存在「拿着上次的按键
 * 办这次的事」——那正是以前几种按键偶尔互相换着用的原因。</p>
 */
@Mod.EventBusSubscriber(modid = FurnitureWorkWand.MODID, value = Dist.CLIENT)
public final class ClickModifierReporter {

    private ClickModifierReporter() {
    }

    @SubscribeEvent
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.player == null) {
            return;
        }
        // 点的是方块才有「这一格」可言；点空、点生物都轮不到家具，不发。
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        try {
            ModNetwork.CHANNEL.sendToServer(new ClickModifierPacket(ctrlDown(minecraft), hit.getBlockPos()));
        } catch (RuntimeException ignored) {
            // 发不出去最坏就是这一下按「没按 Ctrl」处理：原样还给家具自己，不会报错。
        }
    }

    /** 现在按没按 Ctrl（左 Ctrl、右 Ctrl 都算）。 */
    private static boolean ctrlDown(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        if (window == null) {
            return false;
        }
        long handle = window.getWindow();
        return down(handle, InputConstants.KEY_LCONTROL) || down(handle, InputConstants.KEY_RCONTROL);
    }

    private static boolean down(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }
}
