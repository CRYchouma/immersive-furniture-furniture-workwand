package cn.blockforge.generated.furnitureworkwand;

import java.util.List;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 工作材料的查询 / 扣除。
 *
 * <p>装功能要花「工作材料」（见 {@link FurnitureWorkWand#WORK_MATERIAL}）：</p>
 * <ul>
 *   <li>原版功能：每装<b>一个功能</b>（也就是家具上的一页）花 {@value #COST_PER_FUNCTION} 份；</li>
 *   <li>箱子再按存储空间加价：从最小的 9 格起，<b>每多 9 格</b>多花 {@value #COST_PER_9_SLOTS} 份；</li>
 *   <li>模组方块功能：每页固定 {@value #COST_PER_MOD_FUNCTION} 份。</li>
 * </ul>
 * <p>只统计主背包 36 格（不含盔甲和副手），免得把身上的装备当成材料扣掉。</p>
 */
public final class WorkMaterials {

    /** 装一个功能要几份工作材料。 */
    public static final int COST_PER_FUNCTION = 3;

    /** 箱子的存储空间每多 9 格，多要几份工作材料。 */
    public static final int COST_PER_9_SLOTS = 2;

    /** 装一个模组方块功能（别的模组里带界面的方块）要几份工作材料。 */
    public static final int COST_PER_MOD_FUNCTION = 9;

    private static final int MAIN_SLOTS = 36;

    private WorkMaterials() {
    }

    /**
     * 一份清单的费用明细：原版几页、模组几页、箱子多要几档。
     *
     * <p>「一档」= 9 格存储空间。界面里逐行显示的就是它，装功能时只看 {@link #total()}。</p>
     */
    public record Breakdown(int vanillaPages, int modPages, int chestSteps) {

        /** 原版功能那部分要几份。 */
        public int vanillaCost() {
            return this.vanillaPages * COST_PER_FUNCTION;
        }

        /** 模组方块功能那部分要几份。 */
        public int modCost() {
            return this.modPages * COST_PER_MOD_FUNCTION;
        }

        /** 箱子按格数多花的那部分。 */
        public int chestCost() {
            return this.chestSteps * COST_PER_9_SLOTS;
        }

        /** 整份清单一共要几份工作材料。 */
        public int total() {
            return vanillaCost() + modCost() + chestCost();
        }
    }

    /** 整份清单一共要几份工作材料。 */
    public static int costOf(List<WorkLoadout.Entry> entries) {
        return breakdown(entries).total();
    }

    /** 把清单过一遍，算出费用明细（只遍历一次）。 */
    public static Breakdown breakdown(List<WorkLoadout.Entry> entries) {
        int vanilla = 0;
        int mod = 0;
        int steps = 0;
        for (WorkLoadout.Entry entry : entries) {
            if (entry == null || entry.function() == null) {
                continue;
            }
            if (entry.function().isMod()) {
                mod++;
            } else {
                vanilla++;
            }
            steps += chestSteps(entry);
        }
        return new Breakdown(vanilla, mod, steps);
    }

    /** 箱子这一页比最小格数多出来的档数（每档 9 格）。 */
    public static int chestSteps(WorkLoadout.Entry entry) {
        if (entry == null || !entry.isChest()) {
            return 0;
        }
        return Math.max(0, entry.chestSlots() / WorkLoadout.CHEST_STEP - 1);
    }

    /** 玩家主背包里有多少份工作材料。 */
    public static int count(Player player) {
        Inventory inventory = player.getInventory();
        Item material = FurnitureWorkWand.WORK_MATERIAL.get();
        int total = 0;
        for (int slot = 0; slot < mainSlots(inventory); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(material)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 扣掉需要的份数。材料不够时<b>一点不扣</b>并返回 false，让调用方整单取消。
     */
    public static boolean consume(Player player, int need) {
        if (need <= 0 || player.isCreative()) {
            return true;
        }
        if (count(player) < need) {
            return false;
        }
        Inventory inventory = player.getInventory();
        Item material = FurnitureWorkWand.WORK_MATERIAL.get();
        int left = need;
        for (int slot = 0; slot < mainSlots(inventory) && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(material)) {
                continue;
            }
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        inventory.setChanged();
        return true;
    }

    /** 只数主背包的 36 格（不含盔甲和副手），免得把身上的装备当成材料扣掉。 */
    private static int mainSlots(Inventory inventory) {
        return Math.min(MAIN_SLOTS, inventory.getContainerSize());
    }
}
