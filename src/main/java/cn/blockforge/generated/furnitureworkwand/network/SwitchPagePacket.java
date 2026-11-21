package cn.blockforge.generated.furnitureworkwand.network;

import java.util.function.Supplier;

import cn.blockforge.generated.furnitureworkwand.FurnitureAccess;
import cn.blockforge.generated.furnitureworkwand.FurnitureStations;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** 客户端 -> 服务端：在家具的工作界面顶部点左右箭头，请求翻到另一页。 */
public record SwitchPagePacket(BlockPos pos, int page) {

    /** 玩家离开家具多远就不认这个请求了。 */
    private static final int MAX_DISTANCE_SQR = 64;

    public static void encode(SwitchPagePacket message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos());
        buffer.writeVarInt(Math.max(0, message.page()));
    }

    public static SwitchPagePacket decode(FriendlyByteBuf buffer) {
        return new SwitchPagePacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(SwitchPagePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> {
                if (!(player.level() instanceof ServerLevel level)) {
                    return;
                }
                if (player.blockPosition().distSqr(message.pos()) > MAX_DISTANCE_SQR) {
                    return;
                }
                if (!FurnitureAccess.isFurnitureModel(level.getBlockState(message.pos()))) {
                    return;
                }
                FurnitureStations.get(level).open(player, message.pos(), message.page());
            });
        }
        context.setPacketHandled(true);
    }
}
