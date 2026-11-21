package cn.blockforge.generated.furnitureworkwand.client;

import cn.blockforge.generated.furnitureworkwand.network.PageInfoPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 服务端发来的包转到客户端表现层；单独一个类，保证专用服务器上不会加载到客户端代码。 */
@OnlyIn(Dist.CLIENT)
public final class ClientPackets {

    private ClientPackets() {
    }

    public static void pageInfo(PageInfoPacket packet) {
        StationPageOverlay.accept(packet);
    }
}
