package cn.blockforge.generated.furnitureworkwand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;

/**
 * 一件「功能家具」的全部数据：装了几页功能、每页是什么、每页自己的格子与进度。
 *
 * <p>它不绑定任何方块。家具本体保持原样不动（所以外观一点不变），这份数据按坐标挂在家具上，
 * 由 {@link FurnitureStations} 统一存档。</p>
 *
 * <p>多页家具每页只打开一个工作界面，靠界面顶部的左右箭头（鼠标点，或者键盘 ← →）翻页。</p>
 */
public final class StationData {

    /** 每页都留 90 格：箱子 9~54 格只用到前 54，熔炉只用前 3 格，界面里不会多露出来。 */
    public static final int SLOT_COUNT = 90;

    private static final String TAG_PAGES = "Pages";
    private static final String TAG_LAST_PAGE = "LastPage";

    /**
     * 第 3 版以前的单功能存档只写着这串 id；那一版的格子与进度用的标签名和现在完全一样
     * （{@code Function} / {@code Items} / {@code LitTime} …），所以直接交给 {@link Page} 读就行，
     * 不用再照抄一份字段。
     */
    private static final String TAG_LEGACY_FUNCTION = "Function";

    private final List<Page> pages = new ArrayList<>();
    private Runnable dirtyListener = () -> {
    };
    private int lastPage;

    public StationData() {
    }

    /** 交给存档层：任何改动都提醒它写盘。 */
    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener == null ? () -> {
        } : listener;
        for (Page page : this.pages) {
            page.bindInventory(this::touch);
        }
    }

    private void touch() {
        this.dirtyListener.run();
    }

    public int pageCount() {
        return this.pages.size();
    }

    public Page page(int index) {
        if (this.pages.isEmpty()) {
            return null;
        }
        return this.pages.get(Math.max(0, Math.min(this.pages.size() - 1, index)));
    }

    public int lastPage() {
        return this.pages.isEmpty() ? 0 : Math.max(0, Math.min(this.pages.size() - 1, this.lastPage));
    }

    /**
     * 「工作功能」该开哪一页：第一页不是箱子的功能（熔炉、工作台、模组机器……）。
     *
     * <p>Ctrl + 右键开的就是这一页；只装了箱子时退回第 1 页，那一页就是那页箱子。
     * 储物页不单独给键，进界面后用顶上的箭头（或键盘 ← →）翻过去就行。</p>
     *
     * @return 页码；这家具一页功能都没有时返回 {@code -1}
     */
    public int workPage() {
        for (int i = 0; i < this.pages.size(); i++) {
            if (!this.pages.get(i).isStorage()) {
                return i;
            }
        }
        return this.pages.isEmpty() ? -1 : 0;
    }

    public void setLastPage(int index) {
        this.lastPage = Math.max(0, index);
    }

    /**
     * 界面标题：直接用<b>家具自己的名字</b>（比如「沙发」），不再写「家具（箱子）」。
     * 多页家具在后面补一个页码，免得几页看着一模一样。
     */
    public Component displayName(int index, Component furnitureName) {
        if (this.pages.size() <= 1) {
            return furnitureName;
        }
        int shown = Math.max(0, Math.min(this.pages.size() - 1, index)) + 1;
        return Component.translatable("gui.furniture_workwand.station_multi",
                furnitureName, shown, this.pages.size());
    }

    /** 整体换掉功能页（装功能 / 换功能都走这里）。 */
    public void setPages(List<WorkLoadout.Entry> entries) {
        this.pages.clear();
        for (WorkLoadout.Entry entry : entries) {
            Page page = new Page(entry.function(), entry.chestSlots());
            page.bindInventory(this::touch);
            this.pages.add(page);
        }
        this.lastPage = 0;
        this.touch();
    }

    public void dropContents(Level level, BlockPos pos) {
        for (Page page : this.pages) {
            Containers.dropContents(level, pos, page.inventory());
            page.inventory().clearContent();
        }
        this.touch();
    }

    // ------------------------------------------------------------------ ticking

    public void tick(Level level) {
        for (Page page : this.pages) {
            page.tick(level, this::touch);
        }
    }

    // ------------------------------------------------------------------ 存档

    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Page page : this.pages) {
            list.add(page.save(new CompoundTag()));
        }
        tag.put(TAG_PAGES, list);
        tag.putInt(TAG_LAST_PAGE, this.lastPage);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.pages.clear();
        if (tag.contains(TAG_PAGES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_PAGES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                this.addPage(Page.load(list.getCompound(i)));
            }
        } else if (tag.contains(TAG_LEGACY_FUNCTION, Tag.TAG_STRING)) {
            // 旧存档：只有一页、没有格数记录；键名和现在一样，Page 自己就读得了。
            this.addPage(Page.load(tag));
        }
        this.lastPage = tag.getInt(TAG_LAST_PAGE);
    }

    /** 加一页；功能 id 认不出来（模组被卸了）时 {@code page} 是 null，直接跳过。 */
    private void addPage(Page page) {
        if (page == null) {
            return;
        }
        page.bindInventory(this::touch);
        this.pages.add(page);
    }

    /**
     * 一页功能：一个工作界面 + 它自己的格子 / 进度。
     */
    public static final class Page {

        private static final int BREW_TIME_TOTAL = 400;

        private static final String TAG_FUNCTION = "Function";
        private static final String TAG_SLOTS = "Slots";
        private static final String TAG_ITEMS = "Items";
        private static final String TAG_LIT_TIME = "LitTime";
        private static final String TAG_LIT_DURATION = "LitDuration";
        private static final String TAG_COOK_PROGRESS = "CookProgress";
        private static final String TAG_COOK_TOTAL = "CookTotal";
        private static final String TAG_BREW_TIME = "BrewTime";
        private static final String TAG_FUEL = "Fuel";

        private final StationFunction function;
        private final int chestSlots;
        private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT);
        private final SimpleContainerData furnaceData = new SimpleContainerData(4);
        private final SimpleContainerData brewData = new SimpleContainerData(2);

        private int litTime;
        private int litDuration;
        private int cookingProgress;
        private int cookingTotal = 200;
        private int brewTime;
        private int fuel;
        private Item lastIngredient = Items.AIR;
        /** 格子监听只挂一次：{@link StationData#setDirtyListener} 再来一遍也不会叠出第二条。 */
        private boolean listening;

        Page(StationFunction function, int chestSlots) {
            this.function = function;
            this.chestSlots = WorkLoadout.clampChestSlots(chestSlots);
        }

        private void bindInventory(Runnable listener) {
            if (this.listening) {
                return;
            }
            this.listening = true;
            this.inventory.addListener(container -> listener.run());
        }

        public StationFunction function() {
            return this.function;
        }

        /** 这一页是不是储物（箱子）——工作功能页只找不是箱子的页，就靠它分辨。 */
        public boolean isStorage() {
            return this.function.isChest();
        }

        public int chestSlots() {
            return this.chestSlots;
        }

        /** 箱子按 9 格一行显示。 */
        public int rows() {
            return this.chestSlots / WorkLoadout.CHEST_STEP;
        }

        public SimpleContainer inventory() {
            return this.inventory;
        }

        public ContainerData furnaceData() {
            return this.furnaceData;
        }

        public ContainerData brewData() {
            return this.brewData;
        }

        public void tick(Level level, Runnable dirty) {
            if (this.function.is(WorkFunction.FURNACE)) {
                this.tickFurnace(level, dirty);
            } else if (this.function.is(WorkFunction.BREWING_STAND)) {
                this.tickBrewing(dirty);
            }
        }

        private void tickFurnace(Level level, Runnable dirty) {
            boolean changed = false;
            if (this.litTime > 0) {
                --this.litTime;
                changed = true;
            }

            ItemStack input = this.inventory.getItem(0);
            ItemStack fuel = this.inventory.getItem(1);
            ItemStack output = this.inventory.getItem(2);

            SmeltInfo info = this.findSmelt(level);
            boolean canSmelt = !info.result().isEmpty() && canAccept(output, info.result());
            if (canSmelt && this.cookingProgress <= 0) {
                this.cookingTotal = info.time();
            }

            if (this.litTime > 0 || (canSmelt && !fuel.isEmpty())) {
                if (this.litTime == 0 && canSmelt) {
                    int burn = ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
                    if (burn > 0) {
                        this.litTime = burn;
                        this.litDuration = burn;
                        Item remainder = fuel.getItem().getCraftingRemainingItem();
                        fuel.shrink(1);
                        if (fuel.isEmpty() && remainder != null) {
                            this.inventory.setItem(1, new ItemStack(remainder));
                        }
                        changed = true;
                    }
                }
                if (this.litTime > 0 && canSmelt) {
                    ++this.cookingProgress;
                    if (this.cookingProgress >= this.cookingTotal) {
                        this.cookingProgress = 0;
                        if (output.isEmpty()) {
                            this.inventory.setItem(2, info.result().copy());
                        } else {
                            output.grow(info.result().getCount());
                        }
                        input.shrink(1);
                        changed = true;
                    }
                } else if (!canSmelt) {
                    this.cookingProgress = 0;
                }
            } else if (this.cookingProgress > 0) {
                this.cookingProgress = Math.max(0, this.cookingProgress - 2);
                changed = true;
            }

            this.furnaceData.set(0, this.litTime);
            this.furnaceData.set(1, this.litDuration);
            this.furnaceData.set(2, Math.max(0, this.cookingProgress));
            this.furnaceData.set(3, this.cookingTotal);
            if (changed) {
                dirty.run();
            }
        }

        private void tickBrewing(Runnable dirty) {
            boolean changed = false;
            ItemStack fuelStack = this.inventory.getItem(4);
            if (this.fuel <= 0 && fuelStack.is(Items.BLAZE_POWDER)) {
                this.fuel = 20;
                fuelStack.shrink(1);
                changed = true;
            }

            boolean canBrew = this.canBrew();
            ItemStack ingredient = this.inventory.getItem(3);

            if (this.brewTime > 0) {
                --this.brewTime;
                changed = true;
                if (this.brewTime == 0) {
                    if (canBrew) {
                        this.doBrew();
                    }
                } else if (!canBrew || !ingredient.is(this.lastIngredient)) {
                    this.brewTime = 0;
                }
            } else if (canBrew && this.fuel > 0) {
                --this.fuel;
                this.brewTime = BREW_TIME_TOTAL;
                this.lastIngredient = ingredient.getItem();
                changed = true;
            }

            this.brewData.set(0, Math.max(0, this.brewTime));
            this.brewData.set(1, Math.max(0, this.fuel));
            if (changed) {
                dirty.run();
            }
        }

        private boolean canBrew() {
            ItemStack ingredient = this.inventory.getItem(3);
            if (ingredient.isEmpty() || !PotionBrewing.isIngredient(ingredient)) {
                return false;
            }
            for (int i = 0; i < 3; i++) {
                ItemStack bottle = this.inventory.getItem(i);
                if (!bottle.isEmpty() && PotionBrewing.hasMix(bottle, ingredient)) {
                    return true;
                }
            }
            return false;
        }

        private void doBrew() {
            ItemStack ingredient = this.inventory.getItem(3);
            for (int i = 0; i < 3; i++) {
                ItemStack bottle = this.inventory.getItem(i);
                if (!bottle.isEmpty() && PotionBrewing.hasMix(bottle, ingredient)) {
                    this.inventory.setItem(i, PotionBrewing.mix(ingredient, bottle));
                }
            }
            ingredient.shrink(1);
        }

        private SmeltInfo findSmelt(Level level) {
            ItemStack input = this.inventory.getItem(0);
            if (input.isEmpty()) {
                return SmeltInfo.EMPTY;
            }
            SimpleContainer probe = new SimpleContainer(1);
            probe.setItem(0, input);
            Optional<?> found = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, probe, level);
            if (found.isEmpty()) {
                return SmeltInfo.EMPTY;
            }
            Object value = found.get();
            if (!(value instanceof Recipe<?> recipe)) {
                return SmeltInfo.EMPTY;
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            int time = value instanceof AbstractCookingRecipe cooking ? cooking.getCookingTime() : 200;
            return new SmeltInfo(result, time);
        }

        private static boolean canAccept(ItemStack current, ItemStack result) {
            if (current.isEmpty()) {
                return true;
            }
            if (!ItemStack.isSameItemSameTags(current, result)) {
                return false;
            }
            return current.getCount() + result.getCount() <= current.getMaxStackSize();
        }

        private CompoundTag save(CompoundTag tag) {
            tag.putString(TAG_FUNCTION, this.function.id());
            tag.putInt(TAG_SLOTS, this.chestSlots);
            tag.put(TAG_ITEMS, this.inventory.createTag());
            tag.putInt(TAG_LIT_TIME, this.litTime);
            tag.putInt(TAG_LIT_DURATION, this.litDuration);
            tag.putInt(TAG_COOK_PROGRESS, this.cookingProgress);
            tag.putInt(TAG_COOK_TOTAL, this.cookingTotal);
            tag.putInt(TAG_BREW_TIME, this.brewTime);
            tag.putInt(TAG_FUEL, this.fuel);
            return tag;
        }

        private static Page load(CompoundTag tag) {
            StationFunction function = StationFunctions.byId(tag.getString(TAG_FUNCTION));
            if (function == null) {
                return null;
            }
            int slots = tag.contains(TAG_SLOTS) ? tag.getInt(TAG_SLOTS) : WorkLoadout.DEFAULT_CHEST_SLOTS;
            Page page = new Page(function, slots);
            if (tag.contains(TAG_ITEMS, Tag.TAG_LIST)) {
                page.inventory.fromTag(tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND));
            }
            page.litTime = tag.getInt(TAG_LIT_TIME);
            page.litDuration = tag.getInt(TAG_LIT_DURATION);
            page.cookingProgress = tag.getInt(TAG_COOK_PROGRESS);
            page.cookingTotal = Math.max(1, tag.getInt(TAG_COOK_TOTAL));
            page.brewTime = tag.getInt(TAG_BREW_TIME);
            page.fuel = tag.getInt(TAG_FUEL);
            return page;
        }
    }

    private record SmeltInfo(ItemStack result, int time) {
        private static final SmeltInfo EMPTY = new SmeltInfo(ItemStack.EMPTY, 200);
    }
}
