package cn.blockforge.generated.furnitureworkwand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * 「这一下右键按着 Ctrl 没有」。
 *
 * <p>右键之后发生什么由<b>服务端</b>决定，而 Ctrl 只是键盘按键，服务端本来不知道。
 * 所以客户端每点一次右键，就把「按没按 Ctrl + 点的是哪一格」单独送过来。</p>
 *
 * <p>为什么不是「一直记着玩家此刻按着哪些键」：那样这份记录只会在按键变化时更新，
 * 一旦有哪一包晚了、漏了、或者右键和状态包前后脚到，服务端就会拿着<b>上一次</b>的按键去办
 * <b>这一次</b>的事。改成跟着每一次点击走以后，一次点击只对应一次记录，点完就作废，不会串到下一次。</p>
 *
 * <p>记录还带着坐标和时限：点的不是同一格、或者隔了半秒以上（网络卡了、包丢了），
 * 一律当「没按 Ctrl」处理——也就是把这一下原样还给家具自己，不会莫名其妙开出界面。</p>
 */
public final class ClickModifiers {

    /** 一条记录最多活这么久（tick）。40 tick = 2 秒，再高的延迟也该到了，过期就作废。 */
    private static final long MAX_AGE = 40L;

    /** 一次点击：点在哪一格、按没按 Ctrl、第几 tick 点的。 */
    private record Click(BlockPos pos, boolean ctrl, long tick) {
    }

    private static final Map<UUID, Click> CLICKS = new HashMap<>();

    private ClickModifiers() {
    }

    /** 客户端刚送来的：这一下右键是不是按着 Ctrl、点的是 {@code pos} 那一格。 */
    public static void remember(Player player, BlockPos pos, boolean ctrl, long gameTime) {
        if (player == null || pos == null) {
            return;
        }
        CLICKS.put(player.getUUID(), new Click(pos.immutable(), ctrl, gameTime));
    }

    /**
     * 这一下右键是不是按着 Ctrl。
     *
     * <p>拿过不删：同一下右键主手、副手各会走一遍，删了第二遍就会当成没按。
     * 反正下一次点击会把它盖掉，过期也会自己清掉。</p>
     */
    public static boolean ctrlHeld(Player player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }
        UUID id = player.getUUID();
        Click click = CLICKS.get(id);
        if (click == null) {
            return false;
        }
        if (!click.pos().equals(pos) || player.level().getGameTime() - click.tick() > MAX_AGE) {
            CLICKS.remove(id);
            return false;
        }
        return click.ctrl();
    }

    /** 玩家下线：把他的记录删掉，别越攒越多。 */
    public static void forget(Player player) {
        if (player != null) {
            CLICKS.remove(player.getUUID());
        }
    }

    public static void clear() {
        CLICKS.clear();
    }
}
