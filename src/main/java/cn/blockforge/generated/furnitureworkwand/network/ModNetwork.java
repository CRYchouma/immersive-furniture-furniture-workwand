package cn.blockforge.generated.furnitureworkwand.network;

import cn.blockforge.generated.furnitureworkwand.FurnitureWorkWand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** 本模组的网络通道：工作棒清单、家具翻页、界面页码、每次右键按没按 Ctrl 都在这里走。 */
public final class ModNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(FurnitureWorkWand.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SaveLoadoutPacket.class,
                SaveLoadoutPacket::encode, SaveLoadoutPacket::decode, SaveLoadoutPacket::handle);
        CHANNEL.registerMessage(id++, SwitchPagePacket.class,
                SwitchPagePacket::encode, SwitchPagePacket::decode, SwitchPagePacket::handle);
        CHANNEL.registerMessage(id++, PageInfoPacket.class,
                PageInfoPacket::encode, PageInfoPacket::decode, PageInfoPacket::handle);
        CHANNEL.registerMessage(id++, ClickModifierPacket.class,
                ClickModifierPacket::encode, ClickModifierPacket::decode, ClickModifierPacket::handle);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
