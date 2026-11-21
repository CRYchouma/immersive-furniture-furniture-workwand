package cn.blockforge.generated.furnitureworkwand.menu;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.BiFunction;

import cn.blockforge.generated.furnitureworkwand.StationData;
import cn.blockforge.generated.furnitureworkwand.StationFunction;
import cn.blockforge.generated.furnitureworkwand.WorkLoadout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 把“工作家具的第 N 页”翻译成原版对应的容器界面。
 *
 * <p>箱子也走原版：用原版箱子菜单 + 原版箱子界面，格数 9~54（1~6 行），界面随行数往上长，
 * 不滚动、不加滑条。</p>
 *
 * <p><b>为什么给这些菜单一个真的方块坐标</b>：原版的工作台、附魔台、织布机、切石机、制图台……
 * 收到“格子变了”的通知之后，都要先问一句“我在世界里的哪个方块上”才肯算结果：
 * 工作台靠它跑配方、附魔台靠它数书架、铁砧靠它放音效。以前这里传的是
 * {@link ContainerLevelAccess#NULL}（等于“我没有位置”），于是这些界面点开是空的：
 * 工作台放了东西不出成品、附魔台三个按钮全是 0 级——这就是“部分功能页面无法使用”。
 * 现在传家具自己的坐标，它们算得跟站在原生方块前一模一样。</p>
 *
 * <p>代价是原版会顺手校验“脚下是不是本方块”，而家具当然不是工作台。所以每个界面都把
 * {@link AbstractContainerMenu#stillValid(Player)} 改成永远有效：界面归我们管，
 * 家具还在、人没走远就够了。</p>
 *
 * <p>因为原版不再替我们做“关界面时把输入格还给你”这件事，这里统一在
 * {@link #reclaim} 里还给玩家；原版自己已经还过的（容器被清空）就什么都不做。</p>
 *
 * <p>「模组方块功能」正常情况下开的是那个模组自己的界面（见 {@code ModStationRunner}），
 * 只有它开不出来的时候才落到这里：给这一页套一个原版 3 行储物界面，东西存在家具存档里，
 * 这样点下去至少有个能用的箱子，不会白点。</p>
 */
public final class WorkStationMenus {

    private WorkStationMenus() {
    }

    public static AbstractContainerMenu create(StationData station, int pageIndex, int containerId,
                                               Inventory inventory, Level level, BlockPos pos) {
        StationData.Page page = station.page(pageIndex);
        if (page == null) {
            return new Crafting(containerId, inventory, access(level, pos));
        }
        StationFunction function = page.function();
        if (function.isMod()) {
            // 模组方块自己的界面没开出来，退回通用储物页。
            return chest(page, containerId, inventory);
        }
        ContainerLevelAccess where = access(level, pos);
        return switch (function.vanilla()) {
            case FURNACE -> new Furnace(containerId, inventory, page.inventory(), page.furnaceData());
            case BREWING_STAND -> new Brewing(containerId, inventory, page.inventory(), page.brewData());
            case CHEST -> chest(page, containerId, inventory);
            case CRAFTING_TABLE -> new Crafting(containerId, inventory, where);
            case CARTOGRAPHY_TABLE -> new Cartography(containerId, inventory, where);
            case LOOM -> new Loom(containerId, inventory, where);
            case STONECUTTER -> new Stonecutter(containerId, inventory, where);
            case ENCHANTING_TABLE -> new Enchanting(containerId, inventory, where);
            case ANVIL -> new Anvil(containerId, inventory, where);
            case GRINDSTONE -> new Grindstone(containerId, inventory, where);
            case SMITHING_TABLE -> new Smithing(containerId, inventory, where);
        };
    }

    /** 家具自己的坐标；拿不到（理论上不会有）就退回“没有位置”，至少不会崩。 */
    private static ContainerLevelAccess access(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return ContainerLevelAccess.NULL;
        }
        return new StationAccess(level, pos);
    }

    /** 箱子：直接套原版箱子菜单，行数决定用 9x1 ~ 9x6 哪一种原版界面。 */
    private static AbstractContainerMenu chest(StationData.Page page, int containerId, Inventory inventory) {
        int rows = Math.max(1, Math.min(WorkLoadout.MAX_CHEST_SLOTS / WorkLoadout.CHEST_STEP, page.rows()));
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        return new ChestMenu(type, containerId, inventory, page.inventory(), rows);
    }

    /** 关闭界面时，把属于菜单自己（不是玩家背包）的格子清空并还给玩家。 */
    private static void reclaim(AbstractContainerMenu menu, Player player) {
        for (Slot slot : new ArrayList<>(menu.slots)) {
            if (slot.container instanceof Inventory || slot instanceof ResultSlot) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            slot.set(ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /**
     * 「这个界面挂在家具的哪一格上」。
     *
     * <p>只回答坐标，不管“脚下是不是本方块”——有效性由各界面自己的
     * {@code stillValid} 决定（永远有效）。</p>
     */
    private record StationAccess(Level level, BlockPos pos) implements ContainerLevelAccess {

        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> function) {
            return Optional.ofNullable(function.apply(this.level, this.pos));
        }
    }

    public static final class Furnace extends FurnaceMenu {
        public Furnace(int containerId, Inventory inventory, Container container, ContainerData data) {
            super(containerId, inventory, container, data);
        }
    }

    public static final class Brewing extends BrewingStandMenu {
        public Brewing(int containerId, Inventory inventory, Container container, ContainerData data) {
            super(containerId, inventory, container, data);
        }
    }

    public static final class Crafting extends CraftingMenu {
        public Crafting(int containerId, Inventory inventory, ContainerLevelAccess where) {
            super(containerId, inventory, where);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            reclaim(this, player);
        }
    }

    public static final class Cartography extends CartographyTableMenu {
        public Cartography(int containerId, Inventory inventory, ContainerLevelAccess where) {
            super(containerId, inventory, where);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            reclaim(this, player);
        }
    }

    public static final class Loom extends LoomMenu {
        public Loom(int containerId, Inventory inventory, ContainerLevelAccess where) {
            super(containerId, inventory, where);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            reclaim(this, player);
        }
    }

    public static final class Stonecutter extends StonecutterMenu {
        public Stonecutter(int containerId, Inventory inventory, ContainerLevelAccess where) {
            super(containerId, inventory, where);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            reclaim(this, player);
        }
    }

    public static final class Enchanting extends EnchantmentMenu {
        public Enchanting(int containerId, Inventory inventory, ContainerLevelAccess where) {
            super(containerId, inventory, where);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            reclaim(this, player);
        }
    }

    public static final class Anvil extends AnvilMenu {
        public Anvil(int containerId, Inventory inventory, ContainerLevelAccess where) {
            super(containerId, inventory, where);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            reclaim(this, player);
        }
    }

    public static final class Grindstone extends GrindstoneMenu {
        public Grindstone(int containerId, Inventory inventory, ContainerLevelAccess where) {
            super(containerId, inventory, where);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            reclaim(this, player);
        }
    }

    public static final class Smithing extends SmithingMenu {
        public Smithing(int containerId, Inventory inventory, ContainerLevelAccess where) {
            super(containerId, inventory, where);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            reclaim(this, player);
        }
    }
}
