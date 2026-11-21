package cn.blockforge.generated.furnitureworkwand.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 一栏列表底下的翻页条：{@code <  1/2  >}，左右箭头和页码居中成一组。
 *
 * <p>以前箭头是钉在栏的两头、页码飘在正中间的，栏一宽（比如中间的「已选」栏）三个东西就散开了；
 * 现在整组一起居中，看起来是一套控件。</p>
 *
 * <p>页码槽的宽度按「最大的那个页码」算死，所以翻到两位数页码时两个箭头也不会左右跳。</p>
 */
final class Pager {

    /** 翻页条的高度，和原版按钮一样高。 */
    static final int HEIGHT = 16;

    private static final int ARROW = 16;
    /** 箭头与页码之间的空隙。 */
    private static final int GAP = 4;

    private final Button prev;
    private final Button next;

    private int left;
    private int top;
    private int labelWidth;
    private Component label = Component.empty();
    private boolean shown;

    /**
     * @param widgets 界面自己管的那批按钮，箭头会加进去（由界面统一画、统一收点击）
     */
    Pager(List<AbstractWidget> widgets, Runnable onPrev, Runnable onNext) {
        this.prev = arrow(widgets, "<", onPrev);
        this.next = arrow(widgets, ">", onNext);
    }

    private static Button arrow(List<AbstractWidget> widgets, String text, Runnable action) {
        Button button = Button.builder(Component.literal(text), b -> action.run()).size(ARROW, ARROW).build();
        widgets.add(button);
        return button;
    }

    /**
     * 把翻页条摆到这一栏的正中，并决定两个箭头能不能点。
     *
     * @param page  当前是第几页（从 0 数）
     * @param pages 一共几页
     */
    void layout(Font font, int columnX, int columnWidth, int y, int page, int pages) {
        int total = Math.max(1, pages);
        int current = Math.max(0, Math.min(total - 1, page));
        this.top = y;
        this.shown = total > 1;
        // 「最大页码 / 总数」一定是最宽的一行，其余页共用这个槽宽。
        this.labelWidth = font.width(label(total, total));
        this.label = label(current + 1, total);

        int group = ARROW * 2 + GAP * 2 + this.labelWidth;
        this.left = columnX + (columnWidth - group) / 2;
        this.prev.visible = this.shown;
        this.next.visible = this.shown;
        this.prev.active = this.shown && current > 0;
        this.next.active = this.shown && current + 1 < total;
        this.prev.setX(this.left);
        this.next.setX(this.left + ARROW + GAP + this.labelWidth + GAP);
        this.prev.setY(y);
        this.next.setY(y);
    }

    /** 只有一页的栏不画翻页条，也不占位置。 */
    boolean isShown() {
        return this.shown;
    }

    /** 页码画在两个箭头中间（箭头本身由原版按钮自己画）。 */
    void drawLabel(GuiGraphics graphics, Font font) {
        if (!this.shown) {
            return;
        }
        int centerX = this.left + ARROW + GAP + this.labelWidth / 2;
        VanillaStyle.textCentered(graphics, font, this.label,
                centerX, VanillaStyle.textTop(this.top, HEIGHT), VanillaStyle.TEXT);
    }

    /** 所有翻页条共用一个文案：{@code 1 / 2}，和界面顶部的翻页条写成一样。 */
    private static Component label(int page, int total) {
        return Component.translatable("gui.furniture_workwand.page", page, total);
    }
}
