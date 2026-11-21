package cn.blockforge.generated.furnitureworkwand.network;

import java.util.function.Supplier;

import cn.blockforge.generated.furnitureworkwand.client.ClientPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -> 客户端：刚打开的工作界面属于哪件家具、当前第几页、一共几页。
 *
 * <p>客户端靠它决定界面顶部画不画翻页箭头。</p>
 */
public record PageInfoPacket(int containerId, BlockPos pos, int page, int total) {

    public static void encode(PageInfoPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.containerId());
        buffer.writeBlockPos(message.pos());
        buffer.writeVarInt(Math.max(0, message.page()));
        buffer.writeVarInt(Math.max(1, message.total()));
    }

    public static PageInfoPacket decode(FriendlyByteBuf buffer) {
        return new PageInfoPacket(buffer.readVarInt(), buffer.readBlockPos(),
                buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(PageInfoPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPackets.pageInfo(message)));
        context.setPacketHandled(true);
    }
}
