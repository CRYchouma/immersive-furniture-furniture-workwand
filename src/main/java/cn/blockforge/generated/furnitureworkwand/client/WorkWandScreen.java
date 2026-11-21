package cn.blockforge.generated.furnitureworkwand.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.blockforge.generated.furnitureworkwand.FurnitureWorkWand;
import cn.blockforge.generated.furnitureworkwand.StationFunction;
import cn.blockforge.generated.furnitureworkwand.StationFunctions;
import cn.blockforge.generated.furnitureworkwand.WorkLoadout;
import cn.blockforge.generated.furnitureworkwand.WorkMaterials;
import cn.blockforge.generated.furnitureworkwand.network.ModNetwork;
import cn.blockforge.generated.furnitureworkwand.network.SaveLoadoutPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 工作棒配置界面。
 *
 * <p>排版照原版容器界面来：灰色面板、内凹格子、字用原版的深灰 {@code #404040} 且不带阴影
 * （和箱子、熔炉界面里的字一样细），按钮全用原版的 {@link Button}（悬停变色、点击音效都是原版的）。</p>
 *
 * <p>三栏从左到右：功能（点一下加一页）、已选（点一下取消一页，箱子那行右边调格数）、材料。
 * 前两栏的翻页条是同一套 {@link Pager}，都居中放在栏底，页码格式也统一成「1 / 2」。
 * 三栏的下沿都和右栏最下面那个按钮对齐，底边是一条齐的。</p>
 *
 * <p>操作只有鼠标左键；ESC 或「写入工作棒」保存。界面里只留必要的结果提示，不写操作说明。</p>
 *
 * <p>左栏的功能除了原版那 11 样，还会列出装上的模组里能开界面的方块
 * （庄园的馈赠里那 8 台厨房机器，见 {@code ModGuiBlocks}）；名字和图标直接用方块自己的。</p>
 *
 * <p>所有可点区域都用 {@link Rect} 算一次、画和点共用，不会出现“看得见点不到”。</p>
 */
public class WorkWandScreen extends Screen {

    // ---------------------------------------------------------------- 面板
    private static final int W = 364;
    private static final int H = 200;
    private static final int PAD = 8;

    // ---------------------------------------------------------------- 纵向（相对面板顶部）
    private static final int TITLE_Y = 6;
    private static final int HEADER_Y = 26;
    private static final int LIST_TOP = 38;
    private static final int ROWS = 7;
    /** 翻页条：底边和右栏最下面那个按钮对齐，正好贴着面板内圈。 */
    private static final int PAGER_Y = H - PAD - Pager.HEIGHT;
    /** 翻页条上面那道凹槽。 */
    private static final int GROOVE_Y = PAGER_Y - 3;

    // ---------------------------------------------------------------- 横向三栏
    // 左栏给方块名留够位置（「冰淇淋机」这种五个字也放得下），中栏放图标、名字和箱子的加减号，
    // 右栏放费用明细和按钮。
    private static final int COL_LEFT_X = PAD;
    private static final int COL_LEFT_W = 100;
    private static final int COL_MID_X = COL_LEFT_X + COL_LEFT_W + 10;
    private static final int COL_MID_W = 132;
    private static final int COL_RIGHT_X = COL_MID_X + COL_MID_W + 10;
    private static final int COL_RIGHT_W = W - COL_RIGHT_X - PAD;

    // ---------------------------------------------------------------- 右栏（相对面板顶部）
    private static final int RIGHT_MATERIAL_Y = 38;
    private static final int RIGHT_NEED_Y = 58;
    /** 费用明细的第一行；有几条就往下排几行，中间不留空档。 */
    private static final int RIGHT_DETAIL_Y = 70;
    private static final int RIGHT_DETAIL_STEP = 12;
    /** 费用明细和「身上有多少」之间刻一道凹槽，把“要花多少”和“手上有多少”分开。 */
    private static final int RIGHT_DIVIDER_Y = 106;
    private static final int RIGHT_HAVE_Y = 108;
    private static final int RIGHT_APPLY_Y = 134;
    /** 最下面那个按钮贴着面板内圈放，另外两栏的翻页条也照这条线对齐。 */
    private static final int RIGHT_CLEAR_Y = H - PAD - 20;

    /** 右栏两个大按钮的高度。 */
    private static final int BUTTON_H = 20;

    /** 箱子那一行的 - / + 小按钮边长。 */
    private static final int STEP_SIZE = 16;

    /** 列表行里名字从图标格子右边 3px 开始写，右边留 14px 给「x2」那种角标。 */
    private static final int NAME_X = 21;
    private static final int NAME_RIGHT = 14;

    /** 左栏能选的全部功能：原版那 11 样 + 装上的模组里带界面的方块。 */
    private static final List<StationFunction> FUNCTIONS = StationFunctions.all();

    private final ItemStack wand;
    private final List<WorkLoadout.Entry> entries;
    /** 自己管的一批原版按钮：只画、只点，不注册进 Screen，免得和自绘的列表抢鼠标。 */
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Button[] chestMinus = new Button[ROWS];
    private final Button[] chestPlus = new Button[ROWS];
    private final Map<StationFunction, Integer> counts = new HashMap<>();
    private Pager functionPager;
    private Pager basketPager;
    private Button applyButton;
    private Button clearButton;

    private int page;
    private int midPage;
    private boolean saved;
    private int leftPos;
    private int topPos;
    /** 箱子格数文字预留的宽度：所有箱子行共用，加减号才能一列对齐。 */
    private int chestLabelWidth;
    /** 箱子行右侧三个位置（相对中间栏左边）：减号、格数文字、加号；排一次就够，不在每帧里重算。 */
    private int chestMinusX;
    private int chestTextX;
    private int chestPlusX;
    /** 当前清单的费用明细与总价，每次清单变了才算一遍。 */
    private WorkMaterials.Breakdown cost = new WorkMaterials.Breakdown(0, 0, 0);
    /** 右栏那几行文字（「需要 N 份」+ 费用明细），和 {@link #cost} 一起重算，不在每帧里拼。 */
    private Component needText = Component.empty();
    private List<Component> costLines = List.of();

    public WorkWandScreen(ItemStack wand) {
        super(Component.translatable("gui.furniture_workwand.title"));
        this.wand = wand;
        this.entries = WorkLoadout.read(wand);
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - W) / 2;
        this.topPos = (this.height - H) / 2;
        this.widgets.clear();
        this.functionPager = new Pager(this.widgets, () -> this.turnListPage(-1), () -> this.turnListPage(1));
        this.basketPager = new Pager(this.widgets, () -> this.turnBasketPage(-1), () -> this.turnBasketPage(1));
        this.chestLabelWidth = this.font.width(chestSlots(WorkLoadout.MAX_CHEST_SLOTS));
        for (int row = 0; row < ROWS; row++) {
            int index = row;
            this.chestMinus[row] = this.button("-", () -> this.stepChest(index, -1));
            this.chestPlus[row] = this.button("+", () -> this.stepChest(index, 1));
        }
        this.applyButton = this.button(Component.translatable("gui.furniture_workwand.apply"), () -> {
            this.save();
            this.onClose();
        });
        this.clearButton = this.button(Component.translatable("gui.furniture_workwand.clear"), this::clear);
        this.midPage = this.clampedMidPage();
        this.updateLayout();
    }

    // ---------------------------------------------------------------- 清单状态

    private int maxPage() {
        return (FUNCTIONS.size() - 1) / ROWS;
    }

    private int maxMidPage() {
        return Math.max(0, (this.entries.size() - 1) / ROWS);
    }

    /** 把“现在翻到第几页”夹回合法范围，保证永远是 0 ~ 最大页 之间的确定值。 */
    private int clampedMidPage() {
        return Math.max(0, Math.min(this.midPage, this.maxMidPage()));
    }

    private WorkLoadout.Entry entryAt(int row) {
        int index = this.midPage * ROWS + row;
        return index >= 0 && index < this.entries.size() ? this.entries.get(index) : null;
    }

    private void add(StationFunction function) {
        if (this.entries.size() >= WorkLoadout.MAX_ENTRIES) {
            return;
        }
        this.entries.add(WorkLoadout.Entry.of(function));
        // 新加的一页在最后，直接翻到它那一页。
        this.midPage = (this.entries.size() - 1) / ROWS;
        VanillaStyle.playClick();
        this.updateLayout();
    }

    private void remove(int index) {
        if (index < 0 || index >= this.entries.size()) {
            return;
        }
        this.entries.remove(index);
        this.midPage = this.clampedMidPage();
        VanillaStyle.playClick();
        this.updateLayout();
    }

    /** 箱子的一次加减 = 9 格；{@link WorkLoadout.Entry} 自己会把结果夹到 9~54。 */
    private void stepChest(int row, int delta) {
        WorkLoadout.Entry entry = this.entryAt(row);
        if (entry == null || !entry.isChest()) {
            return;
        }
        int index = this.midPage * ROWS + row;
        this.entries.set(index, entry.withChestSlots(entry.chestSlots() + delta * WorkLoadout.CHEST_STEP));
        this.updateLayout();
    }

    private void clear() {
        if (this.entries.isEmpty()) {
            return;
        }
        this.entries.clear();
        this.midPage = 0;
        this.updateLayout();
    }

    private void turnListPage(int delta) {
        this.page = Math.max(0, Math.min(this.maxPage(), this.page + delta));
        this.updateLayout();
    }

    private void turnBasketPage(int delta) {
        this.midPage = Math.max(0, Math.min(this.maxMidPage(), this.midPage + delta));
        this.updateLayout();
    }

    private void save() {
        if (this.saved) {
            return;
        }
        this.saved = true;
        ModNetwork.CHANNEL.sendToServer(new SaveLoadoutPacket(List.copyOf(this.entries)));
    }

    @Override
    public void onClose() {
        this.save();
        super.onClose();
    }

    // ---------------------------------------------------------------- 按钮位置

    private Button button(String label, Runnable action) {
        return this.button(Component.literal(label), action);
    }

    private Button button(Component label, Runnable action) {
        Button button = Button.builder(label, b -> action.run()).build();
        this.widgets.add(button);
        return button;
    }

    private void place(AbstractWidget widget, int x, int y, int width, int height) {
        widget.setX(this.leftPos + x);
        widget.setY(this.topPos + y);
        widget.setWidth(width);
        widget.setHeight(height);
    }

    /** 每次清单或页码一变就重排一遍：位置、文字、能不能点，全都只在这里算。 */
    private void updateLayout() {
        this.midPage = this.clampedMidPage();
        this.refreshCounts();
        this.refreshCost();
        this.updateChestX();

        this.functionPager.layout(this.font, this.leftPos + COL_LEFT_X, COL_LEFT_W,
                this.topPos + PAGER_Y, this.page, this.maxPage() + 1);
        this.basketPager.layout(this.font, this.leftPos + COL_MID_X, COL_MID_W,
                this.topPos + PAGER_Y, this.midPage, this.maxMidPage() + 1);

        for (int row = 0; row < ROWS; row++) {
            WorkLoadout.Entry entry = this.entryAt(row);
            boolean isChest = entry != null && entry.isChest();
            this.chestMinus[row].visible = isChest;
            this.chestPlus[row].visible = isChest;
            if (!isChest) {
                continue;
            }
            int y = this.rowY(row) + 1;
            this.place(this.chestMinus[row], COL_MID_X + this.chestMinusX, y, STEP_SIZE, STEP_SIZE);
            this.place(this.chestPlus[row], COL_MID_X + this.chestPlusX, y, STEP_SIZE, STEP_SIZE);
            // 已经是最小 / 最大格数时，对应那个按钮就没必要点了。
            this.chestMinus[row].active = entry.chestSlots() > WorkLoadout.MIN_CHEST_SLOTS;
            this.chestPlus[row].active = entry.chestSlots() < WorkLoadout.MAX_CHEST_SLOTS;
        }

        this.place(this.applyButton, COL_RIGHT_X, RIGHT_APPLY_Y, COL_RIGHT_W, BUTTON_H);
        this.place(this.clearButton, COL_RIGHT_X, RIGHT_CLEAR_Y, COL_RIGHT_W, BUTTON_H);
        this.clearButton.active = !this.entries.isEmpty();
    }

    /** 每个功能已经被选了几份（列表右边那个「x2」角标）。清单变了才算，不在每帧里数。 */
    private void refreshCounts() {
        this.counts.clear();
        for (WorkLoadout.Entry entry : this.entries) {
            this.counts.merge(entry.function(), 1, Integer::sum);
        }
    }

    /**
     * 费用明细 + 总价，清单变了才算一遍。
     *
     * <p>右栏要写的几行字也在这里拼好：它们只跟着清单变，没必要每帧重新翻译一遍。</p>
     */
    private void refreshCost() {
        this.cost = WorkMaterials.breakdown(this.entries);
        this.needText = Component.translatable("gui.furniture_workwand.material_need", this.cost.total());

        // 原版功能一页 3 份（箱子按格数加价），模组方块功能一页固定 9 份；没有的那种就不占一行。
        List<Component> lines = new ArrayList<>(3);
        if (this.cost.vanillaPages() > 0) {
            lines.add(Component.translatable("gui.furniture_workwand.cost_detail",
                    this.cost.vanillaPages(), WorkMaterials.COST_PER_FUNCTION, this.cost.vanillaCost()));
        }
        if (this.cost.modPages() > 0) {
            lines.add(Component.translatable("gui.furniture_workwand.cost_mod",
                    this.cost.modPages(), WorkMaterials.COST_PER_MOD_FUNCTION, this.cost.modCost()));
        }
        if (this.cost.chestSteps() > 0) {
            lines.add(Component.translatable("gui.furniture_workwand.cost_chest",
                    this.cost.chestSteps(), this.cost.chestCost()));
        }
        this.costLines = lines;
    }

    /**
     * 箱子那行右侧三个位置的 x（相对中间栏左边）：减号、格数文字的左边、加号。
     *
     * <p>格数文字占的是 {@link #chestLabelWidth} 那么宽的固定槽，所以 9 格和 54 格的行
     * 加减号都在同一条竖线上，不会一行一个样。</p>
     */
    private void updateChestX() {
        this.chestPlusX = COL_MID_W - 2 - STEP_SIZE;
        this.chestTextX = this.chestPlusX - 4 - this.chestLabelWidth;
        this.chestMinusX = this.chestTextX - 4 - STEP_SIZE;
    }

    // ---------------------------------------------------------------- 坐标

    /** 屏幕坐标上的一个矩形，画和点共用，省得两边坐标对不上。 */
    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.w
                    && mouseY >= this.y && mouseY < this.y + this.h;
        }
    }

    private int rowY(int row) {
        return LIST_TOP + row * VanillaStyle.ROW_H;
    }

    private Rect functionRow(int row) {
        return new Rect(this.leftPos + COL_LEFT_X, this.topPos + this.rowY(row),
                COL_LEFT_W, VanillaStyle.ROW_H);
    }

    /** 「已选」的一行；箱子那行右边留给 - / +，点那里不算取消。 */
    private Rect basketRow(int row) {
        WorkLoadout.Entry entry = this.entryAt(row);
        int width = COL_MID_W;
        if (entry != null && entry.isChest()) {
            width = this.chestMinusX - 3;
        }
        return new Rect(this.leftPos + COL_MID_X, this.topPos + this.rowY(row),
                width, VanillaStyle.ROW_H);
    }

    private static Component chestSlots(int slots) {
        return Component.translatable("gui.furniture_workwand.chest_slots", slots);
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        VanillaStyle.panel(graphics, this.leftPos, this.topPos, W, H);
        this.drawTitle(graphics);
        this.drawHeaders(graphics);
        this.drawFunctions(graphics, mouseX, mouseY);
        this.drawBasket(graphics, mouseX, mouseY);
        this.drawSide(graphics, mouseX, mouseY);
        this.drawPagers(graphics);
        for (AbstractWidget widget : this.widgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawTitle(GuiGraphics graphics) {
        int x = this.leftPos + PAD;
        int y = this.topPos + TITLE_Y;
        VanillaStyle.text(graphics, this.font, this.title, x, y, VanillaStyle.TEXT);
        int slotX = this.leftPos + W - PAD - VanillaStyle.SLOT_SIZE;
        VanillaStyle.slot(graphics, slotX, this.topPos + 4);
        graphics.renderItem(this.wand, slotX + 1, this.topPos + 5);
    }

    private void drawHeaders(GuiGraphics graphics) {
        int x = this.leftPos;
        int y = this.topPos;
        VanillaStyle.text(graphics, this.font, Component.translatable("gui.furniture_workwand.column_function"),
                x + COL_LEFT_X, y + HEADER_Y, VanillaStyle.TEXT);
        VanillaStyle.text(graphics, this.font, Component.translatable("gui.furniture_workwand.column_selected"),
                x + COL_MID_X, y + HEADER_Y, VanillaStyle.TEXT);
        VanillaStyle.text(graphics, this.font, Component.translatable("gui.furniture_workwand.column_material"),
                x + COL_RIGHT_X, y + HEADER_Y, VanillaStyle.TEXT);
        // 表头下面刻一道槽，两栏之间也刻一道，和原版的分栏一个味道。
        VanillaStyle.grooveH(graphics, x + COL_LEFT_X, y + HEADER_Y + 10, COL_LEFT_W);
        VanillaStyle.grooveH(graphics, x + COL_MID_X, y + HEADER_Y + 10, COL_MID_W);
        VanillaStyle.grooveH(graphics, x + COL_RIGHT_X, y + HEADER_Y + 10, COL_RIGHT_W);
        int divider = PAGER_Y + Pager.HEIGHT - HEADER_Y;
        VanillaStyle.grooveV(graphics, x + COL_LEFT_X + COL_LEFT_W + 4, y + HEADER_Y, divider);
        VanillaStyle.grooveV(graphics, x + COL_MID_X + COL_MID_W + 4, y + HEADER_Y, divider);
    }

    /** 两条翻页条：只有多页的栏才刻槽、才画页码。 */
    private void drawPagers(GuiGraphics graphics) {
        if (this.functionPager.isShown()) {
            VanillaStyle.grooveH(graphics, this.leftPos + COL_LEFT_X, this.topPos + GROOVE_Y, COL_LEFT_W);
            this.functionPager.drawLabel(graphics, this.font);
        }
        if (this.basketPager.isShown()) {
            VanillaStyle.grooveH(graphics, this.leftPos + COL_MID_X, this.topPos + GROOVE_Y, COL_MID_W);
            this.basketPager.drawLabel(graphics, this.font);
        }
    }

    private void drawFunctions(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int row = 0; row < ROWS; row++) {
            int index = this.page * ROWS + row;
            if (index >= FUNCTIONS.size()) {
                break;
            }
            StationFunction function = FUNCTIONS.get(index);
            Rect rect = this.functionRow(row);
            this.drawRow(graphics, rect, function, mouseX, mouseY);
            int count = this.counts.getOrDefault(function, 0);
            if (count > 0) {
                Component badge = Component.literal("x" + count);
                VanillaStyle.text(graphics, this.font, badge,
                        rect.x() + rect.w() - 2 - this.font.width(badge), rect.y() + 5,
                        VanillaStyle.TEXT);
            }
        }
    }

    /** 列表的一行：原版格子 + 图标 + 名字；鼠标悬停时整行盖一层原版的白色高光。 */
    private void drawRow(GuiGraphics graphics, Rect rect, StationFunction function, int mouseX, int mouseY) {
        VanillaStyle.slot(graphics, rect.x(), rect.y());
        graphics.renderItem(function.iconStack(), rect.x() + 1, rect.y() + 1);
        if (rect.contains(mouseX, mouseY)) {
            VanillaStyle.hover(graphics, rect.x(), rect.y(), rect.w(), rect.h());
        }
        // 名字太长就截断，免得画出格子、压到旁边那栏。
        Component name = VanillaStyle.fit(this.font, function.display(), rect.w() - NAME_X - NAME_RIGHT);
        VanillaStyle.text(graphics, this.font, name,
                rect.x() + NAME_X, rect.y() + 5, VanillaStyle.TEXT);
    }

    private void drawBasket(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int row = 0; row < ROWS; row++) {
            WorkLoadout.Entry entry = this.entryAt(row);
            if (entry == null) {
                break;
            }
            this.drawRow(graphics, this.basketRow(row), entry.function(), mouseX, mouseY);
            if (entry.isChest()) {
                VanillaStyle.textCentered(graphics, this.font, chestSlots(entry.chestSlots()),
                        this.leftPos + COL_MID_X + this.chestTextX + this.chestLabelWidth / 2,
                        this.topPos + this.rowY(row) + 5, VanillaStyle.TEXT);
            }
        }
    }

    private void drawSide(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.leftPos + COL_RIGHT_X;
        int y = this.topPos;
        Player player = this.minecraft == null ? null : this.minecraft.player;
        boolean creative = player != null && player.isCreative();
        int need = this.cost.total();

        // 只放图标，不再写一遍「工作材料」：栏头已经写着「材料」了，再写一遍是废话。
        VanillaStyle.slot(graphics, x, y + RIGHT_MATERIAL_Y);
        graphics.renderItem(new ItemStack(FurnitureWorkWand.WORK_MATERIAL.get()),
                x + 1, y + RIGHT_MATERIAL_Y + 1);

        VanillaStyle.text(graphics, this.font, this.needText, x, y + RIGHT_NEED_Y, VanillaStyle.TEXT);

        // 费用明细一条条往下排，中间不留空行；槽位固定，所以行数变了下面的字也不会跟着乱跳。
        int lineY = y + RIGHT_DETAIL_Y;
        for (Component line : this.costLines) {
            VanillaStyle.text(graphics, this.font, line, x, lineY, VanillaStyle.TEXT);
            lineY += RIGHT_DETAIL_STEP;
        }

        // 「要花多少」和「手上有多少」分开：上面是明细，下面这条凹槽之后是持有量。
        VanillaStyle.grooveH(graphics, x, y + RIGHT_DIVIDER_Y, COL_RIGHT_W);

        int have = player == null ? 0 : WorkMaterials.count(player);
        boolean enough = creative || have >= need;
        Component haveText = creative
                ? Component.translatable("gui.furniture_workwand.creative")
                : Component.translatable("gui.furniture_workwand.material_have", have);
        VanillaStyle.text(graphics, this.font, haveText, x, y + RIGHT_HAVE_Y,
                enough ? VanillaStyle.TEXT_GREEN : VanillaStyle.TEXT_RED);
    }

    // ---------------------------------------------------------------- 点击

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // 原版按钮在最上层，先给它们。
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (int row = 0; row < ROWS; row++) {
            int index = this.page * ROWS + row;
            if (index >= FUNCTIONS.size()) {
                break;
            }
            if (this.functionRow(row).contains(mouseX, mouseY)) {
                this.add(FUNCTIONS.get(index));
                return true;
            }
        }
        for (int row = 0; row < ROWS; row++) {
            if (this.entryAt(row) == null) {
                break;
            }
            if (this.basketRow(row).contains(mouseX, mouseY)) {
                this.remove(this.midPage * ROWS + row);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
