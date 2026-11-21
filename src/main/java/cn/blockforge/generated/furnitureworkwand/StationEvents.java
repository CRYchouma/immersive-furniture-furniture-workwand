package cn.blockforge.generated.furnitureworkwand;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 功能家具的“运行时”：Ctrl+右键开工作界面、炉子 / 酿造台继续跑、家具没了就收摊。
 *
 * <p>它是唯一碰这些数据的地方，所以家具本体真的一个像素都没改。</p>
 *
 * <p>右键只剩一种用法：<b>Ctrl + 右键</b> 打开这件家具的工作界面（工作台、熔炉、箱子……）。</p>
 * <p>普通右键、Shift + 右键、Alt + 右键一概不拦，原样还给家具自己——该坐就坐、该躺就躺、
 * 该开灯就开灯，跟没装这个模组时一样。储物页也不再单独占一个键：它是界面里的一页，
 * 进去后用顶上的箭头（或键盘 ← →）翻过去就行。</p>
 * <p>Ctrl 是键盘修饰键，服务端看不见，所以客户端在按下右键的那一刻把「按没按 Ctrl」报过来，
 * 一次点击只对应一次报告。</p>
 * <p>手里拿着工作棒时这些都不管：工作棒要装功能、要开配置界面，它比这个事件更早接管方块。</p>
 */
@Mod.EventBusSubscriber(modid = FurnitureWorkWand.MODID)
public final class StationEvents {

    private StationEvents() {
    }

    /** 服务端每 tick 跑一下所有功能家具（熔炉、酿造台要靠它干活）。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            FurnitureStations stations = FurnitureStations.get(level);
            if (!stations.isEmpty()) {
                stations.tickAll(level);
            }
        }
        // 模组方块的那几页（不管界面现在开没开着）都要一直抄格子、该收摊的收摊。
        ModStationRunner.tick(server);
    }

    /** 服务器关掉之前把借位放下的临时方块全收走，别让它们跟着存档留下来。 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ModStationRunner.closeAll();
        ClickModifiers.clear();
    }

    /**
     * 开服时清一次“借位记录”。
     *
     * <p>正常情况下临时方块关界面就收掉了；只有服务器崩在界面开着的时刻，存档里才会留下一个
     * 看不见的机器方块（放下去时不通知客户端，客户端一直当它是空气）。这里按记录把它清掉。</p>
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            FurnitureStations.get(level).sweepBorrowed(level);
        }
    }

    /** 按着 Ctrl 右键「盖过章」的家具：打开它的工作界面。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        // 手里拿着工作棒时交给它处理（装功能 / 打开配置），它比这个事件更早接管方块。
        if (event.getItemStack().getItem() instanceof WorkWandItem) {
            return;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // 没按 Ctrl 的这一下不归本模组管：原样放行，家具自己的坐、躺、开灯都还在。
        // 按键是客户端在按下右键的那一刻报过来的，只对这一次点击有效，所以不会串到下一次。
        if (!ClickModifiers.ctrlHeld(serverPlayer, event.getPos())) {
            return;
        }
        FurnitureStations stations = FurnitureStations.get(server);
        if (stations.isEmpty()) {
            return;
        }
        BlockPos target = FurnitureAccess.resolveBase(level, event.getPos());
        if (target == null) {
            return;
        }
        StationData station = stations.get(target);
        if (station == null) {
            return;
        }
        // 工作功能页；只装了箱子的家具，这一页就是那页箱子。
        int page = station.workPage();
        if (page < 0) {
            return;
        }
        stations.open(serverPlayer, target, page);
        consume(event);
    }

    /** 这一下右键到此为止：方块和手里的物品都别再处理了。 */
    private static void consume(PlayerInteractEvent.RightClickBlock event) {
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** 玩家下线：把他「按没按 Ctrl」的记录删掉。 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ClickModifiers.forget(event.getEntity());
    }

    /** 家具被拆：每一页格子里的东西还给玩家，记录删掉（家具本身的掉落物照旧）。 */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        FurnitureStations stations = FurnitureStations.get(level);
        if (stations.isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (stations.get(pos) == null) {
            // 拆的是多格家具的隐形格：记录挂在主体那一格上，换算过去一起清掉。
            if (!FurnitureAccess.isProxy(event.getState())) {
                return;
            }
            BlockPos base = FurnitureAccess.resolveBase(level, pos);
            if (base == null) {
                return;
            }
            pos = base;
        }
        ModStationRunner.closeAt(level, pos);
        StationData station = stations.remove(pos);
        if (station != null) {
            station.dropContents(level, pos);
        }
    }
}
