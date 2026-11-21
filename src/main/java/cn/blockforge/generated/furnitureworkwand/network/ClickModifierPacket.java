package cn.blockforge.generated.furnitureworkwand.network;

import java.util.function.Supplier;

import cn.blockforge.generated.furnitureworkwand.ClickModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 -> 服务端：我这一下右键按没按 Ctrl，点的是这一格。
 *
 * <p>只在<b>右键被按下</b>的那一刻发一次，紧跟在右键包前面，所以服务端处理这一下右键时
 * 拿到的一定是这一次的按键，不会串成上一次的。包里就一个「按没按 Ctrl」+ 一个方块坐标。</p>
 */
public record ClickModifierPacket(boolean ctrl, BlockPos pos) {

    public static void encode(ClickModifierPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.ctrl());
        buffer.writeBlockPos(message.pos());
    }

    public static ClickModifierPacket decode(FriendlyByteBuf buffer) {
        return new ClickModifierPacket(buffer.readBoolean(), buffer.readBlockPos());
    }

    public static void handle(ClickModifierPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> ClickModifiers.remember(player, message.pos(), message.ctrl(),
                    player.level().getGameTime()));
        }
        context.setPacketHandled(true);
    }
}
