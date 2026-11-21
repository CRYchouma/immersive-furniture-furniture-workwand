package cn.blockforge.generated.furnitureworkwand.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import cn.blockforge.generated.furnitureworkwand.StationFunction;
import cn.blockforge.generated.furnitureworkwand.StationFunctions;
import cn.blockforge.generated.furnitureworkwand.WorkLoadout;
import cn.blockforge.generated.furnitureworkwand.WorkWandItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** 客户端 -> 服务端：把界面里选好的功能清单写进手里那根工作棒的 NBT。 */
public record SaveLoadoutPacket(List<WorkLoadout.Entry> entries) {

    public static void encode(SaveLoadoutPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entries().size());
        for (WorkLoadout.Entry entry : message.entries()) {
            buffer.writeUtf(entry.function().id(), 64);
            buffer.writeVarInt(entry.chestSlots());
        }
    }

    public static SaveLoadoutPacket decode(FriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), WorkLoadout.MAX_ENTRIES);
        List<WorkLoadout.Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            StationFunction function = StationFunctions.byId(buffer.readUtf(64));
            int slots = buffer.readVarInt();
            if (function != null) {
                entries.add(new WorkLoadout.Entry(function, slots));
            }
        }
        return new SaveLoadoutPacket(entries);
    }

    public static void handle(SaveLoadoutPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> {
                ItemStack wand = findWand(player);
                if (wand == null) {
                    return;
                }
                WorkLoadout.write(wand, message.entries());
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            });
        }
        context.setPacketHandled(true);
    }

    /** 优先两只手，再兜底翻一遍背包（界面开着的时候玩家可能换了快捷栏）。 */
    private static ItemStack findWand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WorkWandItem) {
                return stack;
            }
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof WorkWandItem) {
                return stack;
            }
        }
        return null;
    }
}
