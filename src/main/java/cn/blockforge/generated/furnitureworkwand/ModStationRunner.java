package cn.blockforge.generated.furnitureworkwand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 让「模组方块功能」真的开出那个模组自己的界面（目前是庄园的馈赠里的 8 台厨房机器）。
 *
 * <p>这些模组方块都是「右键方块 → 打开它自己的界面」，界面里拿的是<b>世界里那个方块</b>的方块实体。
 * 家具本身不是那个方块，所以这里做了个“借位”：</p>
 * <ol>
 *   <li>在家具附近找一个<b>玩家这一眼看不见</b>的格子（隔壁房间、地板下、墙里的实心块），
 *       把这个模组方块临时放下去（方块实体跟着一起建好）；</li>
 *   <li>把这一页存的东西填进它的格子，调它自己的右键 —— 玩家看到的就是原汁原味的模组界面；</li>
 *   <li>界面开着的时候每个 tick 把格子里的东西抄回家具存档；</li>
 *   <li>玩家走到别处 / 下线 / 家具被拆时，再抄一次、清空格子、把那一格还原成原来那个方块。</li>
 * </ol>
 *
 * <p><b>每一页各借各的、各跑各的</b>：一件家具上的每一页都有自己的 {@link Session}，
 * 翻到别的页 <b>不会</b>把这一页的机器收掉。机器留在世界里，它自己的方块实体照样每 tick 干活
 * （炉子照烧、油照热），所以“第 1 页在烤肉、翻到第 2 页找东西、再翻回来”东西还在、
 * 进度也没断。只有人真的离开这件家具（走出 {@link #KEEP_RANGE} 格）、下线、换维度，
 * 或者家具被拆了，才把这一页收摊。</p>
 *
 * <p><b>为什么临时方块必须“两边都有”</b>：那个模组的界面是客户端拿 {@code level.getBlockEntity(坐标)}
 * 去画图画的。只放在服务端的话，客户端那边是空气，界面一开就崩（errors 报告里那条
 * {@code SaucepanAndWhiskScreen.renderHeat} 的空指针就是这么来的），而且机器自己改方块状态时
 * 会把那个方块广播到客户端，就出现“点一下冒出一台机器、过一会儿又没了”。所以现在：</p>
 * <ul>
 *   <li>服务端放方块时用 {@link Block#UPDATE_NONE}，不广播给任何人；</li>
 *   <li>只<b>单独给开界面的这个玩家</b>发一条“这里有个机器”的方块更新，客户端就有了真方块实体；</li>
 *   <li>别的玩家那边这一格一直是原来那个方块，不会跟着冒出一台机器。</li>
 * </ul>
 *
 * <p><b>机器绝不出现在眼前，也绝不挡路</b>：临时方块只会放在</p>
 * <ul>
 *   <li>被不透光方块挡住的空气格（隔壁房间、地板下面的空腔）——不动世界里的方块；</li>
 *   <li>埋在实心地里 / 厚墙里的普通方块（石头、泥土这类）——借一格，碰撞和借之前一模一样，
 *       收摊时原样放回去。</li>
 * </ul>
 * <p>再不行才用“你正背对着的格子”兜一次底（界面开着时镜头动不了）。实在一个都找不到就<b>不放</b>，
 * 直接退回本模组自己的储物页：宁可少一个模组界面，也不让一台机器凭空出现在你面前、挡你的路。
 * 原版的 11 个预设功能本来就不往世界里放方块，走的是同一个入口，规则一样。</p>
 *
 * <p>万一放不下（周围没格子）或者那个方块右键其实不开界面，就返回失败，
 * 由 {@link FurnitureStations} 退回本模组自己的通用储物页——不会出现点了没反应的按钮。</p>
 */
public final class ModStationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurnitureWorkWand.MODID);

    /** 在家具周围横着找几格。 */
    private static final int SEARCH_HORIZONTAL = 6;

    /** 在家具周围竖着找几格（往下多找一点，地下/地板下最容易藏）。 */
    private static final int SEARCH_DOWN = 5;
    private static final int SEARCH_UP = 4;

    /** 临时方块最远离玩家几格：模组的界面自己会检查 8 格，留一点余量。 */
    private static final double REACH = 7.0;

    /** 最多认真算多少个“看不看得见”（每条视线都要算，别算太久）。 */
    private static final int MAX_SIGHT_CHECKS = 140;

    /** 隔多少 tick 给别的玩家补一条“这里是空气”（防止机器自己改状态时把方块广播出去）。 */
    private static final int HIDE_OTHERS_TICKS = 10;

    /** 给别的玩家补空气时，只管这个范围内的人。 */
    private static final double HIDE_OTHERS_RANGE = 64.0;

    /**
     * 人离开这件家具这么远，借出去的格子就收回来（方块还原、东西抄回家具存档）。
     *
     * <p>比玩家能点到家具的距离（约 4.5 格）宽一点：在附近转转、翻几页都不收摊，
     * 真走开了才收。</p>
     */
    private static final double KEEP_RANGE = 8.0;

    /** 往方块里几个点看（别用正中心一个点：机器模型有的地方会伸出来）。 */
    private static final Vec3[] SAMPLES = new Vec3[]{
            new Vec3(0.5, 0.5, 0.5),
            new Vec3(0.2, 0.2, 0.2), new Vec3(0.8, 0.2, 0.2), new Vec3(0.2, 0.8, 0.2),
            new Vec3(0.2, 0.2, 0.8), new Vec3(0.8, 0.8, 0.2), new Vec3(0.8, 0.2, 0.8),
            new Vec3(0.2, 0.8, 0.8), new Vec3(0.8, 0.8, 0.8)
    };

    /** 每个玩家身上开着的那些页：一件家具的每一页各一条，互不影响。 */
    private static final Map<UUID, List<Session>> SESSIONS = new HashMap<>();

    private ModStationRunner() {
    }

    /**
     * 试着用模组方块自己的界面打开这一页。
     *
     * <p>这一页本来就开着（刚翻到别的页去了）就<b>接着用原来的机器</b>：方块没收、进度还在，
     * 只是把界面重新指过去。别的页的机器一概不碰。</p>
     *
     * @return 真的开出界面了，返回那个界面的 containerId；开不出来返回 {@code -1}
     */
    public static int open(ServerPlayer player, ServerLevel level, BlockPos furniturePos,
                           StationData.Page page) {
        if (page == null || !page.function().isMod()) {
            return -1;
        }
        Block block = page.function().block();
        if (block == null) {
            return -1;
        }
        // 跑到另一件家具上来了：上一件家具上借的格子先全收掉，别把人家的机器留在原地。
        closeOtherFurniture(player, furniturePos);

        // 这一页本来就开着：直接接着用，机器和它里面的进度一点不动。
        Session kept = sessionOf(player, furniturePos, page);
        if (kept != null) {
            int reopened = reuse(player, kept);
            if (reopened >= 0) {
                return reopened;
            }
            // 机器已经不在了（方块被换掉之类）：按新的一页重来。
            dropSession(player, kept);
        }

        // 同一页同时只能有一个玩家开着：这一页只有一个机器，两个界面一起改会把东西弄乱。
        closeSamePage(page);

        BlockState placed = stateToPlace(player, block, furniturePos);
        Spot spot = findSpot(level, player, furniturePos, placed);
        if (spot == null) {
            // 一个藏得住、又碰不到的格子都没找到：宁可不放机器（不然会出现在你眼前、还挡路），
            // 直接退回本模组自己的储物页，东西照样能存。
            LOGGER.info("家具 {} 周围找不到能藏下 {} 的格子，这次不放机器，改用本模组的储物页",
                    furniturePos, blockId(block));
            return -1;
        }
        BlockPos pos = spot.pos();
        try {
            // 只放在服务端，不用广播：客户端由下面那条包单独通知开界面的人。
            level.setBlock(pos, placed, Block.UPDATE_NONE);
            remember(level, pos, block, spot.restore());
        } catch (RuntimeException exception) {
            LOGGER.warn("{} 在这个位置放不下来，改用本模组的储物页", blockId(block));
            teardown(level, pos, page, spot.restore());
            return -1;
        }
        // 临时方块被收掉时，里面的东西只能靠这两份格子互抄来保住。
        // 抄不了的（没有 Container、或者格子比家具存档还多）宁可不借用它的界面，也不冒丢东西的险。
        Container container = level.getBlockEntity(pos) instanceof Container found ? found : null;
        if (container == null || container.getContainerSize() > page.inventory().getContainerSize()) {
            LOGGER.warn("{} 的格子接不下来（不是容器或比存档大），改用本模组的储物页", blockId(block));
            teardown(level, pos, page, spot.restore());
            return -1;
        }
        copy(page.inventory(), container);
        // 这一步是修崩溃的关键：客户端也得有一份真的方块 + 方块实体，模组自己的界面才画得出来。
        // 先给方块，再把方块实体里的内容（油、温度这些）发过去，界面才不会显示成空的。
        sendBlock(player, pos, placed);
        sendBlockEntity(player, level, pos);
        try {
            placed.use(level, player, InteractionHand.MAIN_HAND, hit(pos));
        } catch (RuntimeException exception) {
            // 模组方块自己炸了不算我们的：界面没开出来就走兜底。
            LOGGER.warn("右键 {} 时它自己报错了，改用本模组的储物页", blockId(block));
        }
        if (!opened(player)) {
            LOGGER.warn("右键 {} 没打开界面，改用本模组的储物页", blockId(block));
            teardown(level, pos, page, spot.restore());
            return -1;
        }
        Session session = new Session(level, furniturePos, pos, page, container, spot.restore());
        SESSIONS.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).add(session);
        showOnly(player, session);
        return session.containerId;
    }

    /** 服务端每 tick：格子里的东西抄回家具存档，人走了就收摊。 */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        boolean hideTick = server.getTickCount() % HIDE_OTHERS_TICKS == 0;
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            List<Session> sessions = SESSIONS.get(id);
            if (sessions == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                // 人下线了：每一页都抄回去、还原方块。
                for (Session session : new ArrayList<>(sessions)) {
                    finish(session, null);
                }
                SESSIONS.remove(id);
                continue;
            }
            for (Session session : new ArrayList<>(sessions)) {
                try {
                    if (player.level() != session.level || away(player, session)) {
                        sessions.remove(session);
                        finish(session, player);
                        continue;
                    }
                    if (session.containerId >= 0 && player.containerMenu.containerId != session.containerId) {
                        // 玩家已经换到别的界面去了（翻页 / 关掉 / 开了箱子）：这一页记成没开着。
                        session.hide();
                    }
                    if (session.container != null) {
                        // 界面是不是开着都照抄：没开的那一页机器照样在跑，产出也得存下来。
                        copy(session.container, session.page.inventory());
                    }
                    if (hideTick) {
                        hideFromOthers(session, player);
                    }
                } catch (RuntimeException exception) {
                    // 模组那边的容器出了意外：先撤掉，别让每个 tick 都炸一次。
                    sessions.remove(session);
                    teardown(session.level, session.spot, session.page, session.restore);
                }
            }
            if (sessions.isEmpty()) {
                SESSIONS.remove(id);
            }
        }
    }

    /** 这件家具被拆 / 没了：挂在上面的临时方块先收掉，免得格子里的东西跟着丢。 */
    public static void closeAt(ServerLevel level, BlockPos furniturePos) {
        closeWhere(null, session -> session.level == level && session.furniturePos.equals(furniturePos));
    }

    /** 服务器关掉之前把临时方块全收干净，别把方块留在存档里。 */
    public static void closeAll() {
        closeWhere(null, session -> true);
    }

    /** 人跑到另一件家具上来了：上一件家具上借的格子全收掉（只收他自己的）。 */
    private static void closeOtherFurniture(ServerPlayer player, BlockPos furniturePos) {
        closeWhere(player.getUUID(), session -> !session.furniturePos.equals(furniturePos));
    }

    /** 这一页被别的玩家开着：先替他把格子抄回去、收掉。（同一页只有一个机器的格子。） */
    private static void closeSamePage(StationData.Page page) {
        closeWhere(null, session -> session.page == page);
    }

    /**
     * 把所有符合条件的页收掉：抄回格子、还原方块，一份不剩。
     *
     * <p>「家具被拆」「换到别的家具」「同一页被别人开」「关服」都是这一件事的不同筛法，
     * 所以只留这一份实现，别再各写一遍遍历。</p>
     *
     * @param owner 只看这个玩家名下的页；{@code null} = 所有人都看
     */
    private static void closeWhere(UUID owner, Predicate<Session> match) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            if (owner != null && !owner.equals(id)) {
                continue;
            }
            List<Session> sessions = SESSIONS.get(id);
            if (sessions == null) {
                continue;
            }
            for (Session session : new ArrayList<>(sessions)) {
                if (!match.test(session)) {
                    continue;
                }
                sessions.remove(session);
                finish(session, playerOf(id, session.level));
            }
            if (sessions.isEmpty()) {
                SESSIONS.remove(id);
            }
        }
    }

    /** 按 UUID 找玩家（可能已经下线，那就返回 {@code null}）。 */
    private static ServerPlayer playerOf(UUID id, ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static Session sessionOf(ServerPlayer player, BlockPos furniturePos, StationData.Page page) {
        List<Session> sessions = SESSIONS.get(player.getUUID());
        if (sessions == null) {
            return null;
        }
        for (Session session : sessions) {
            if (session.page == page && session.furniturePos.equals(furniturePos)) {
                return session;
            }
        }
        return null;
    }

    private static void dropSession(ServerPlayer player, Session session) {
        List<Session> sessions = SESSIONS.get(player.getUUID());
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                SESSIONS.remove(player.getUUID());
            }
        }
        teardown(session.level, session.spot, session.page, session.restore);
    }

    /**
     * 这一页的机器还在世界里：把界面重新指过去，东西一件不动。
     *
     * @return 新界面的 containerId；接不下去返回 {@code -1}
     */
    private static int reuse(ServerPlayer player, Session session) {
        BlockState state = session.level.getBlockState(session.spot);
        try {
            // 客户端那边保持有真的方块 + 方块实体（重新进界面时可能已经重发过区块）。
            sendBlock(player, session.spot, state);
            sendBlockEntity(player, session.level, session.spot);
            state.use(session.level, player, InteractionHand.MAIN_HAND, hit(session.spot));
        } catch (RuntimeException exception) {
            LOGGER.warn("接着打开 {} 上的界面时报错", session.spot);
            return -1;
        }
        if (!opened(player)) {
            return -1;
        }
        showOnly(player, session);
        return session.containerId;
    }

    /** 这一页现在显示着：同一件家具上别的页就都算“界面没开着”（机器照样在跑）。 */
    private static void showOnly(ServerPlayer player, Session shown) {
        List<Session> sessions = SESSIONS.get(player.getUUID());
        if (sessions != null) {
            for (Session session : sessions) {
                if (session != shown) {
                    session.hide();
                }
            }
        }
        shown.show(player.containerMenu.containerId);
    }

    private static boolean opened(ServerPlayer player) {
        return player.containerMenu != player.inventoryMenu && player.containerMenu.containerId != 0;
    }

    private static boolean away(ServerPlayer player, Session session) {
        BlockPos pos = session.furniturePos;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > KEEP_RANGE * KEEP_RANGE;
    }

    // ------------------------------------------------------------------ 收摊

    private static void finish(Session session, ServerPlayer player) {
        try {
            if (player != null && session.containerId >= 0
                    && player.containerMenu.containerId == session.containerId) {
                player.closeContainer();
            }
        } catch (RuntimeException exception) {
            // 关界面这一步出错也别耽误收摊：下面照样把方块还回去。
            LOGGER.warn("关 {} 上的界面时报错，继续收摊", session.spot);
        }
        teardown(session.level, session.spot, session.page, session.restore);
    }

    /**
     * 收摊：先把格子里的东西抄回家具存档、清空格子，再把那一格还原成借之前的方块。
     *
     * <p>每一步都自己兜住异常：抄不动也要把方块还回去，别留一台机器埋在人家地里。</p>
     */
    private static void teardown(ServerLevel level, BlockPos pos, StationData.Page page,
                                 BlockState restore) {
        try {
            if (level.getBlockEntity(pos) instanceof Container container) {
                if (page != null) {
                    copy(container, page.inventory());
                }
                container.clearContent();
            }
        } catch (RuntimeException ignored) {
            // 抄不动就算了，下面照样还原方块。
        }
        restoreBlock(level, pos, restore);
    }

    /**
     * 把那一格还原成借来之前的样子（空气格就是空气）。
     *
     * <p>这里用 {@link Block#UPDATE_ALL}：客户端本来就没收到过这个方块（只单独发给了开界面的人），
     * 多收一条更新只是白跑一趟；但万一客户端确实拿到过（比如区块重发），这一步能把画面纠正回来。</p>
     */
    private static void restoreBlock(ServerLevel level, BlockPos pos, BlockState restore) {
        BlockState target = restore == null ? Blocks.AIR.defaultBlockState() : restore;
        try {
            level.setBlock(pos, target, Block.UPDATE_ALL);
            forget(level, pos);
        } catch (RuntimeException exception) {
            // 区块没了之类的情况还原不了：借位记录先留着，下次开服再按记录还原。
            LOGGER.warn("临时方块 {} 没能还原成 {}", pos, target);
        }
    }

    /** 单独给某个玩家发一条方块更新（不动别人的客户端）。 */
    private static void sendBlock(ServerPlayer player, BlockPos pos, BlockState state) {
        try {
            player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        } catch (RuntimeException ignored) {
            // 发不出去最多是界面打不开，后面 opened 检查会发现并走兜底。
        }
    }

    /** 把临时方块实体里的内容发一份给客户端（界面要照着这些画）。 */
    private static void sendBlockEntity(ServerPlayer player, ServerLevel level, BlockPos pos) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                player.connection.send(ClientboundBlockEntityDataPacket.create(blockEntity));
            }
        } catch (RuntimeException ignored) {
            // 少这一包最多是界面上的油量 / 温度显示成 0，不影响能用。
        }
    }

    /**
     * 别的玩家那边这一格保持“借位之前的样子”。
     *
     * <p>机器自己改方块状态（比如“工作中”）时会把方块广播给附近所有玩家，那时别人就会看到
     * 凭空多出一台机器。这里隔一会儿给他们补一条还原后的方块，只有开界面的那个人有真的方块实体。</p>
     */
    private static void hideFromOthers(Session session, ServerPlayer opener) {
        BlockPos pos = session.spot;
        BlockState restore = session.restore == null ? Blocks.AIR.defaultBlockState() : session.restore;
        for (ServerPlayer other : session.level.players()) {
            if (other == opener || other.isSpectator()) {
                continue;
            }
            if (other.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    > HIDE_OTHERS_RANGE * HIDE_OTHERS_RANGE) {
                continue;
            }
            sendBlock(other, pos, restore);
        }
    }

    // ------------------------------------------------------------------ 借位记录

    /**
     * 记下“这个格子借了一个什么方块、原来是什么”。
     *
     * <p>正常情况下走了就还回去了，记录也跟着删；只有服务器突然崩掉才会在存档里留下这种
     * 临时方块，下次开服用它还原（见 {@link FurnitureStations#sweepBorrowed}）。</p>
     */
    private static void remember(ServerLevel level, BlockPos pos, Block block, BlockState restore) {
        try {
            String restoreId = restore == null || restore.isAir()
                    ? null
                    : String.valueOf(ForgeRegistries.BLOCKS.getKey(restore.getBlock()));
            FurnitureStations.get(level).noteBorrowed(pos, blockId(block), restoreId);
        } catch (RuntimeException ignored) {
            // 记不下来最多是崩服时留个临时方块，别为此开不出界面。
        }
    }

    private static void forget(ServerLevel level, BlockPos pos) {
        try {
            FurnitureStations.get(level).forgetBorrowed(pos);
        } catch (RuntimeException ignored) {
            // 同上：记录删不掉不影响这次收摊。
        }
    }

    // ------------------------------------------------------------------ 找格子

    /**
     * 找个能临时放下这台机器、而且<b>玩家绝对看不到、也碰不到</b>的格子。
     *
     * <p>只有两种格子算数，其余一律不放（宁可退回本模组自己的储物页，也不让机器出现在眼前）：</p>
     * <ol>
     *   <li>被不透光方块挡住的空气格（隔壁房间、地板下面的空腔）——<b>不动世界里的任何方块</b>；</li>
     *   <li>埋在地里 / 厚墙里的普通实心方块——借一格，收摊时原样放回去。这一格本来就是个挡人的实心块，
     *       机器占的还是同一格，碰撞和原来一样，玩家也进不来。</li>
     * </ol>
     *
     * <p>还有一类“你正背对着的格子”当备选：界面开着的时候游戏不让转镜头，所以只要打开的那一刻
     * 它整个在镜头后面，就看不见。这类不用算视线，最省事。</p>
     *
     * <p>实在一个都找不到就返回 {@code null}：<b>不往看得见的地方放</b>，由调用方退回通用储物页。</p>
     */
    private static Spot findSpot(ServerLevel level, ServerPlayer player, BlockPos base,
                                 BlockState placed) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        FurnitureStations ledger = stationsOf(level);
        List<BlockPos> airSpots = new ArrayList<>();
        List<BlockPos> solidSpots = new ArrayList<>();
        for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
            for (int dx = -SEARCH_HORIZONTAL; dx <= SEARCH_HORIZONTAL; dx++) {
                for (int dz = -SEARCH_HORIZONTAL; dz <= SEARCH_HORIZONTAL; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (!level.isLoaded(pos) || !withinReach(player, pos)) {
                        continue;
                    }
                    // 已经被别的界面借走的格子不能抢：两个界面共用一个格子会把东西弄乱。
                    if (ledger != null && ledger.isBorrowed(pos)) {
                        continue;
                    }
                    if (level.isEmptyBlock(pos)) {
                        airSpots.add(pos);
                    } else if (isBorrowable(level, pos)) {
                        solidSpots.add(pos);
                    }
                }
            }
        }
        Comparator<BlockPos> nearestToFurniture = Comparator.comparingDouble(pos -> pos.distSqr(base));
        airSpots.sort(nearestToFurniture);
        solidSpots.sort(nearestToFurniture);

        // 1) 被墙 / 地板挡住的空气格：不动世界里的任何方块，最干净。
        BlockPos blind = null;
        int checked = 0;
        for (BlockPos pos : airSpots) {
            if (checked >= MAX_SIGHT_CHECKS) {
                break;
            }
            if (!canStand(level, pos, placed) || !isFree(level, pos, placed)) {
                continue;
            }
            if (isBehind(eye, look, pos)) {
                // 先记下“正背对着的”格子当最后备选，继续找真正被挡住的那一种。
                if (blind == null) {
                    blind = pos;
                }
                continue;
            }
            checked++;
            if (hiddenFrom(level, player, eye, look, pos)) {
                return new Spot(pos.immutable(), Blocks.AIR.defaultBlockState());
            }
        }

        // 2) 借一格埋在地里的普通方块：这一格本来就挡人，机器占的还是同一格，
        //    看得见的部分一点没变，碰撞也没变，收摊时原样放回去。
        for (BlockPos pos : solidSpots) {
            if (canStand(level, pos, placed) && isBuried(level, pos)) {
                return new Spot(pos.immutable(), level.getBlockState(pos));
            }
        }

        // 3) 退一步：正背对着你的空气格。界面开着时镜头动不了，第一人称看不见；
        //    第三人称可能会瞄到，所以排最后。
        if (blind != null) {
            return new Spot(blind.immutable(), Blocks.AIR.defaultBlockState());
        }

        // 4) 真找不到：返回 null，不放在玩家眼前。
        return null;
    }

    /** 拿到借位账本（拿不到就当没有，不影响这一次开界面）。 */
    private static FurnitureStations stationsOf(ServerLevel level) {
        try {
            return FurnitureStations.get(level);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 机器放下去会不会卡住谁（人 / 生物的身体和它的碰撞箱重叠）。
     *
     * <p>只看这一格不够：有的方块碰撞箱会比一格还高，所以按它的真实碰撞箱比。</p>
     */
    private static boolean isFree(ServerLevel level, BlockPos pos, BlockState placed) {
        if (!level.isEmptyBlock(pos)) {
            return false;
        }
        List<Entity> entities = level.getEntities((Entity) null, new AABB(pos).inflate(1.0),
                entity -> !entity.isSpectator() && entity.isAlive());
        if (entities.isEmpty()) {
            return true;
        }
        AABB box;
        try {
            VoxelShape shape = placed.getCollisionShape(level, pos);
            box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        } catch (RuntimeException exception) {
            box = new AABB(pos);
        }
        for (Entity entity : entities) {
            if (entity.getBoundingBox().intersects(box)) {
                return false;
            }
        }
        return true;
    }

    /** 这个方块能不能借来放临时方块：普通实心方块，原样就能还原的那一种。 */
    private static boolean isBorrowable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        // 家具本身、多格家具的隐形占位格、拆不掉的方块（基岩这种）都不借。
        if (FurnitureAccess.isFurnitureModel(state) || FurnitureAccess.isProxy(state)
                || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        // 只借“没有任何状态属性”的方块（石头、泥土、深板岩这类）：
        // 默认状态就是它现在的样子，还原时不会把朝向、半砖、水位之类弄丢。
        if (!state.getProperties().isEmpty()) {
            return false;
        }
        return state.isSolidRender(level, pos);
    }

    /** 六个面都是不透光的实心方块：里面放什么都看不见。 */
    private static boolean isBuried(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos near = pos.relative(direction);
            if (!level.isLoaded(near) || !level.getBlockState(near).isSolidRender(level, near)) {
                return false;
            }
        }
        return true;
    }

    /** 方块放得下吗（有些方块要求脚下有支撑之类）。 */
    private static boolean canStand(ServerLevel level, BlockPos pos, BlockState placed) {
        try {
            return placed.canSurvive(level, pos);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean withinReach(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= REACH * REACH;
    }

    /** 玩家这一眼看得到这个格子吗。 */
    private static boolean hiddenFrom(ServerLevel level, Player player, Vec3 eye, Vec3 look,
                                      BlockPos pos) {
        if (isBehind(eye, look, pos)) {
            return true;
        }
        for (Vec3 sample : SAMPLES) {
            Vec3 target = new Vec3(pos.getX() + sample.x, pos.getY() + sample.y, pos.getZ() + sample.z);
            BlockHitResult hit = level.clip(new ClipContext(eye, target,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (!blockedByOpaque(level, hit, pos)) {
                return false;
            }
        }
        return true;
    }

    /** 视线尽头是不是先撞上了一块不透光的方块（玻璃、栏杆这种挡不住眼睛的不算）。 */
    private static boolean blockedByOpaque(ServerLevel level, BlockHitResult hit, BlockPos target) {
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos hitPos = hit.getBlockPos();
        if (hitPos.equals(target)) {
            return false;
        }
        return level.getBlockState(hitPos).isSolidRender(level, hitPos);
    }

    /** 整个方块都在镜头后面吗（玩家背对着它）。 */
    private static boolean isBehind(Vec3 eye, Vec3 look, BlockPos pos) {
        double closest = Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 point = new Vec3(pos.getX() + ((corner & 1) == 0 ? 0.0 : 1.0),
                    pos.getY() + ((corner & 2) == 0 ? 0.0 : 1.0),
                    pos.getZ() + ((corner & 4) == 0 ? 0.0 : 1.0));
            closest = Math.min(closest, point.subtract(eye).dot(look));
        }
        return closest < -0.1;
    }

    // ------------------------------------------------------------------ 摆放 / 拷贝

    /** 按“正常放置”算方块状态（朝向之类）；方块自己不给状态就退回默认状态。 */
    private static BlockState stateToPlace(ServerPlayer player, Block block, BlockPos spot) {
        try {
            BlockState state = block.getStateForPlacement(new BlockPlaceContext(player,
                    InteractionHand.MAIN_HAND, new ItemStack(block), hit(spot)));
            if (state != null) {
                return state;
            }
        } catch (RuntimeException ignored) {
            // 有些方块不接受凭空摆放的上下文，用默认状态就行。
        }
        return block.defaultBlockState();
    }

    private static BlockHitResult hit(BlockPos spot) {
        return new BlockHitResult(Vec3.atCenterOf(spot), Direction.UP, spot, false);
    }

    /** 日志里写的方块名（注册名，比如 {@code manors_bounty_machine:oven}）。 */
    private static String blockId(Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? String.valueOf(block) : key.toString();
    }

    /** 把 {@code from} 的格子抄进 {@code to}（只抄两份都有的那部分，内容一样就跳过）。 */
    private static void copy(Container from, Container to) {
        int size = Math.min(from.getContainerSize(), to.getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            ItemStack source = from.getItem(slot);
            if (ItemStack.matches(source, to.getItem(slot))) {
                continue;
            }
            to.setItem(slot, source.copy());
        }
    }

    /** 一个可以临时放方块的位置：坐标，以及收摊时要还原成什么（{@code null} = 空气）。 */
    private record Spot(BlockPos pos, BlockState restore) {
    }

    /**
     * 一件家具上的<b>一页</b>借出去的那台机器。
     *
     * <p>翻页只换“哪一页现在显示着”（{@link #containerId}），这条记录本身留着，
     * 机器和它里面的进度都留在世界里。</p>
     */
    private static final class Session {

        private final ServerLevel level;
        private final BlockPos furniturePos;
        private final BlockPos spot;
        private final StationData.Page page;
        private final Container container;
        private final BlockState restore;
        /** 现在显示着的那个界面的 id；{@code -1} = 这一页的界面没开着。 */
        private int containerId = -1;

        private Session(ServerLevel level, BlockPos furniturePos, BlockPos spot,
                        StationData.Page page, Container container, BlockState restore) {
            this.level = level;
            this.furniturePos = furniturePos;
            this.spot = spot;
            this.page = page;
            this.container = container;
            this.restore = restore;
        }

        private void show(int containerId) {
            this.containerId = containerId;
        }

        private void hide() {
            this.containerId = -1;
        }
    }
}
