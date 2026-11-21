package cn.blockforge.generated.furnitureworkwand;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 工作棒里记着的一份「待安装清单」：可以一次选好几个功能，同一个功能也能选好几份
 * （每份在家具上占一页）。
 *
 * <p>箱子额外记格数：只能是 9 的倍数，最小 9 格、最大 54 格（一趟加 9 格）。
 * 上限之所以是 54，是因为箱子界面用的是原版那套：原版箱子最多 6 行，一行 9 格。</p>
 *
 * <p>数据存在工作棒自己的 NBT 里，所以每根棒子互不影响。</p>
 */
public final class WorkLoadout {

    public static final String TAG = "WorkLoadout";

    /** 一根棒子最多记多少页，防止 NBT 被写爆。 */
    public static final int MAX_ENTRIES = 32;

    /** 箱子格数：9 的倍数，9 ~ 54（原版箱子界面最多 6 行）。 */
    public static final int CHEST_STEP = 9;
    public static final int MIN_CHEST_SLOTS = 9;
    public static final int MAX_CHEST_SLOTS = 54;
    public static final int DEFAULT_CHEST_SLOTS = 9;

    /** 模组方块功能没有自己的格子界面，兜底用 3 行储物格（27 格）。 */
    public static final int MOD_SLOTS = 27;

    private static final String TAG_FUNCTION = "fn";
    private static final String TAG_SLOTS = "slots";

    private WorkLoadout() {
    }

    /** 把任意数字夹到合法的箱子格数（9 的倍数、9~54）。 */
    public static int clampChestSlots(int slots) {
        int stepped = Math.round(slots / (float) CHEST_STEP) * CHEST_STEP;
        return Math.max(MIN_CHEST_SLOTS, Math.min(MAX_CHEST_SLOTS, stepped));
    }

    /** 清单里的一项：功能 + 这一页的格数（只有箱子和模组方块功能用得上）。 */
    public record Entry(StationFunction function, int chestSlots) {

        public Entry {
            chestSlots = clampChestSlots(chestSlots);
        }

        public static Entry of(StationFunction function) {
            return new Entry(function, function != null && function.isMod() ? MOD_SLOTS : DEFAULT_CHEST_SLOTS);
        }

        public Entry withChestSlots(int slots) {
            return new Entry(this.function, slots);
        }

        public boolean isChest() {
            return this.function != null && this.function.isChest();
        }
    }

    /** 读工作棒的清单；没配过就是空表。 */
    public static List<Entry> read(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_LIST)) {
            return new ArrayList<>();
        }
        ListTag list = tag.getList(TAG, Tag.TAG_COMPOUND);
        List<Entry> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            CompoundTag one = list.getCompound(i);
            StationFunction function = StationFunctions.byId(one.getString(TAG_FUNCTION));
            if (function == null) {
                continue;
            }
            int slots = one.contains(TAG_SLOTS) ? one.getInt(TAG_SLOTS) : DEFAULT_CHEST_SLOTS;
            entries.add(new Entry(function, slots));
        }
        return entries;
    }

    /** 把清单写回工作棒；空清单就把这个标签整个删掉。 */
    public static void write(ItemStack stack, List<Entry> entries) {
        if (entries.isEmpty()) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(TAG);
            }
            return;
        }
        ListTag list = new ListTag();
        for (int i = 0; i < entries.size() && i < MAX_ENTRIES; i++) {
            Entry entry = entries.get(i);
            CompoundTag one = new CompoundTag();
            one.putString(TAG_FUNCTION, entry.function().id());
            one.putInt(TAG_SLOTS, entry.chestSlots());
            list.add(one);
        }
        stack.getOrCreateTag().put(TAG, list);
    }

    /** 给聊天栏 / 提示用的一句话概要，例如「熔炉、箱子（27 格）」。 */
    public static String describe(List<Entry> entries) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (i > 0) {
                builder.append("、");
            }
            builder.append(entry.function().display().getString());
            if (entry.isChest()) {
                builder.append("（").append(entry.chestSlots()).append(" 格）");
            }
        }
        return builder.toString();
    }
}
