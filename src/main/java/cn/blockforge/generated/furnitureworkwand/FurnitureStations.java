package cn.blockforge.generated.furnitureworkwand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import cn.blockforge.generated.furnitureworkwand.menu.WorkStationMenus;
import cn.blockforge.generated.furnitureworkwand.network.ModNetwork;
import cn.blockforge.generated.furnitureworkwand.network.PageInfoPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 世界里所有「功能家具」的账本：坐标 → {@link StationData}。
 *
 * <p>为什么不用方块自己存？因为家具是沉浸式家具模组的方块，它的方块数据不归我们管，
 * 换掉它外观就变了。所以本模组<b>不动家具本身</b>，只在自己的存档文件里记一笔：
 * “这个坐标上的家具装了熔炉 + 箱子”。家具外观因此一个像素都不变。</p>
 */
public final class FurnitureStations extends SavedData {

    /** 存档文件名：{@code world/data/furniture_workwand_stations.dat}。 */
    public static final String DATA_NAME = "furniture_workwand_stations";

    private static final String TAG_STATIONS = "Stations";
    private static final String TAG_POS = "Pos";
    private static final String TAG_DATA = "Data";
    private static final String TAG_BORROWED = "Borrowed";
    private static final String TAG_BORROWED_POS = "Pos";
    private static final String TAG_BORROWED_BLOCK = "Block";
    private static final String TAG_BORROWED_RESTORE = "Restore";

    private final Map<BlockPos, StationData> stations = new HashMap<>();

    /**
     * 借位放下的临时方块：格子坐标 → 放了什么、原来是什么。
     *
     * <p>正常情况下界面一关就还原了，这里永远是空的；只有服务器崩在“界面开着”的那一刻，
     * 存档里才会留下一个临时方块。开服时按这张表还原（{@link #sweepBorrowed}）。</p>
     */
    private final Map<BlockPos, Borrowed> borrowed = new HashMap<>();

    public static FurnitureStations get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FurnitureStations::load, FurnitureStations::new, DATA_NAME);
    }

    /** 客户端拿不到存档，返回 {@code null}。 */
    public static FurnitureStations of(Level level) {
        return level instanceof ServerLevel server ? get(server) : null;
    }

    public StationData get(BlockPos pos) {
        return this.stations.get(pos);
    }

    public boolean isEmpty() {
        return this.stations.isEmpty();
    }

    /** 给这个坐标装上新的一份功能清单（多页）。 */
    public StationData install(ServerLevel level, BlockPos pos, List<WorkLoadout.Entry> entries) {
        BlockPos key = pos.immutable();
        StationData data = this.stations.get(key);
        if (data == null) {
            data = new StationData();
            data.setDirtyListener(this::setDirty);
            this.stations.put(key, data);
        }
        // 换清单会让旧的功能页作废：先把这件家具上开着的模组机器收掉，
        // 免得它们还挂在一个已经不存在的页上（格子里的东西会跟着丢）。
        ModStationRunner.closeAt(level, key);
        data.setPages(entries);
        this.setDirty();
        return data;
    }

    /** 移除并返回这个坐标的记录（拆掉家具、或家具已经不是家具时用）。 */
    public StationData remove(BlockPos pos) {
        StationData removed = this.stations.remove(pos);
        if (removed != null) {
            this.setDirty();
        }
        return removed;
    }

    // ------------------------------------------------------------------ 借位记录

    /**
     * 记下“这个格子借了一个什么方块、原来是什么”（见 {@code ModStationRunner}）。
     *
     * @param restoreId 原来那个方块的注册名；原来是空气就传 {@code null}
     */
    public void noteBorrowed(BlockPos spot, String blockId, String restoreId) {
        this.borrowed.put(spot.immutable(), new Borrowed(blockId, restoreId));
        this.setDirty();
    }

    /** 临时方块收掉了，记录也跟着删，存档里不留没用的东西。 */
    public void forgetBorrowed(BlockPos spot) {
        if (this.borrowed.remove(spot) != null) {
            this.setDirty();
        }
    }

    /** 这一格是不是已经被某次打开借走了（两个人同时开界面时别抢同一格）。 */
    public boolean isBorrowed(BlockPos spot) {
        return this.borrowed.containsKey(spot);
    }

    /**
     * 开服时清一次借位记录：只可能是上次崩服没来得及收摊留下的。
     *
     * <p>只动“那一格还是当初那个临时方块”的：万一玩家后来在那儿建了别的东西，一个像素都不改。
     * 清的时候会让区块读一下盘，但这种情况本来就很少（只在崩服后的第一次开服发生）。</p>
     *
     * <p>还原之前先把临时方块格子里的东西清掉：那个方块被换走时会把自己的东西掉一地，
     * 而这些东西每次开界面都已经抄回家具存档了，不清就会白捡一份。</p>
     */
    public void sweepBorrowed(ServerLevel level) {
        if (this.borrowed.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, Borrowed> entry : new ArrayList<>(this.borrowed.entrySet())) {
            BlockPos spot = entry.getKey();
            this.borrowed.remove(spot);
            try {
                ResourceLocation key = ForgeRegistries.BLOCKS.getKey(level.getBlockState(spot).getBlock());
                if (key == null || !entry.getValue().placed().equals(key.toString())) {
                    continue;
                }
                if (level.getBlockEntity(spot) instanceof Container container) {
                    container.clearContent();
                }
                level.setBlock(spot, restoreState(entry.getValue()), Block.UPDATE_ALL);
            } catch (RuntimeException ignored) {
                // 方块还在但读不出来（区块坏了之类）：这一格不动，别拖累开服。
            }
        }
        this.setDirty();
    }

    /** 崩服后要还原成什么方块（记录里没写、或者那个方块没了就是空气）。 */
    private static BlockState restoreState(Borrowed borrowed) {
        if (borrowed.restore() == null) {
            return Blocks.AIR.defaultBlockState();
        }
        ResourceLocation id = ResourceLocation.tryParse(borrowed.restore());
        if (id == null) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
    }

    /**
     * 打开某件功能家具的某一页（钳到合法范围）。
     *
     * <p>界面标题用家具自己的名字（比如「沙发」）：箱子界面会把这行字画在顶上，
     * 写「箱子」就不知道是哪件家具的箱子了。</p>
     *
     * <p>模组方块功能（庄园的馈赠里那 8 台厨房机器）开的是那个模组自己的界面，界面里的东西存在这一页上；
     * 它开不出来时才退回本模组的通用储物页。</p>
     *
     * <p>菜单建好之后立刻把“这是第几页、一共几页”告诉客户端，界面顶部才能画翻页箭头。</p>
     */
    public void open(ServerPlayer player, BlockPos pos, int page) {
        StationData data = this.stations.get(pos);
        if (data == null || data.pageCount() <= 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int index = Math.max(0, Math.min(data.pageCount() - 1, page));
        data.setLastPage(index);
        int total = data.pageCount();
        // 模组方块功能：开那个模组自己的界面（界面里的东西抄进这一页的存档）。
        int modMenu;
        try {
            modMenu = ModStationRunner.open(player, level, pos, data.page(index));
        } catch (RuntimeException exception) {
            // 模组方块那边出了意外就退回通用储物页，别让右键直接报错。
            modMenu = -1;
        }
        if (modMenu >= 0) {
            ModNetwork.sendToPlayer(player, new PageInfoPacket(modMenu, pos, index, total));
            this.setDirty();
            return;
        }
        // 标题用家具自己的名字（比如「沙发」），比方块一律叫「家具」清楚。
        Component furnitureName = FurnitureAccess.nameOf(level, pos);
        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, p) -> WorkStationMenus.create(data, index, containerId,
                        inventory, level, pos),
                data.displayName(index, furnitureName));
        OptionalInt opened = player.openMenu(provider);
        if (opened.isEmpty()) {
            return;
        }
        ModNetwork.sendToPlayer(player, new PageInfoPacket(opened.getAsInt(), pos, index, total));
        this.setDirty();
    }

    /**
     * 每秒（由服务端 tick 事件调用）跑一遍所有记录：家具还在就继续跑炉子 / 酿造台，
     * 家具没了就把格子里的东西吐出来并删掉记录。区块没加载时跳过，免得把东西扔进虚空。
     */
    public void tickAll(ServerLevel level) {
        if (this.stations.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<BlockPos, StationData> entry : new ArrayList<>(this.stations.entrySet())) {
            BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!FurnitureAccess.isFurnitureModel(state)) {
                ModStationRunner.closeAt(level, pos);
                entry.getValue().dropContents(level, pos);
                this.stations.remove(pos);
                changed = true;
                continue;
            }
            entry.getValue().tick(level);
        }
        if (changed) {
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, StationData> entry : this.stations.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong(TAG_POS, entry.getKey().asLong());
            one.put(TAG_DATA, entry.getValue().save(new CompoundTag()));
            list.add(one);
        }
        tag.put(TAG_STATIONS, list);
        ListTag borrowedList = new ListTag();
        for (Map.Entry<BlockPos, Borrowed> entry : this.borrowed.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong(TAG_BORROWED_POS, entry.getKey().asLong());
            one.putString(TAG_BORROWED_BLOCK, entry.getValue().placed());
            if (entry.getValue().restore() != null) {
                one.putString(TAG_BORROWED_RESTORE, entry.getValue().restore());
            }
            borrowedList.add(one);
        }
        tag.put(TAG_BORROWED, borrowedList);
        return tag;
    }

    private static FurnitureStations load(CompoundTag tag) {
        FurnitureStations data = new FurnitureStations();
        if (tag.contains(TAG_STATIONS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_STATIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag one = list.getCompound(i);
                BlockPos pos = BlockPos.of(one.getLong(TAG_POS));
                StationData station = new StationData();
                station.setDirtyListener(data::setDirty);
                station.load(one.getCompound(TAG_DATA));
                data.stations.put(pos, station);
            }
        }
        ListTag borrowedList = tag.getList(TAG_BORROWED, Tag.TAG_COMPOUND);
        for (int i = 0; i < borrowedList.size(); i++) {
            CompoundTag one = borrowedList.getCompound(i);
            String restore = one.contains(TAG_BORROWED_RESTORE, Tag.TAG_STRING)
                    ? one.getString(TAG_BORROWED_RESTORE)
                    : null;
            data.borrowed.put(BlockPos.of(one.getLong(TAG_BORROWED_POS)),
                    new Borrowed(one.getString(TAG_BORROWED_BLOCK), restore));
        }
        return data;
    }

    /** 一个借位记录：放下去的临时方块，和借位之前那一格原来是什么（{@code null} = 空气）。 */
    private record Borrowed(String placed, String restore) {
    }
}
