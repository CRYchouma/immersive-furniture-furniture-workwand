package cn.blockforge.generated.furnitureworkwand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * 原版风格的一小套绘制工具。
 *
 * <p>颜色、尺寸、立体边都照原版容器界面抄：灰色面板 + 黑边 + 亮边/暗边、
 * 内凹的 18×18 格子、字用原版的深灰 {@code #404040}、分栏凹槽。
 * 按钮不用自己画——直接用原版的 {@link net.minecraft.client.gui.components.Button}，
 * 所以悬停变色、点击音效跟原版一模一样。</p>
 */
final class VanillaStyle {

    /** 面板灰底。 */
    static final int PANEL = 0xFFC6C6C6;
    /** 面板最外圈的黑边。 */
    static final int OUTLINE = 0xFF000000;
    /** 面板里的亮边 / 暗边，做出“凸起来”的立体感。 */
    static final int LIGHT = 0xFFFFFFFF;
    static final int SHADOW = 0xFF555555;

    /** 原版格子：灰底 + 左上暗边 + 右下亮边。 */
    static final int SLOT = 0xFF8B8B8B;
    static final int SLOT_DARK = 0xFF373737;
    static final int SLOT_LIGHT = 0xFFFFFFFF;

    /** 原版界面上的字色。 */
    static final int TEXT = 0xFF404040;
    static final int TEXT_RED = 0xFFAA0000;
    static final int TEXT_GREEN = 0xFF00AA00;

    /** 鼠标悬停时盖在整行上的白雾（原版格子高光同一个颜色）。 */
    static final int HOVER = 0x80FFFFFF;

    /** 原版一个格子的大小，列表行也按这个高度排。 */
    static final int SLOT_SIZE = 18;
    static final int ROW_H = 18;
    /** 一行字的高度，用来在按钮、标签这种固定高度的框里居中。 */
    static final int FONT_H = 8;

    private VanillaStyle() {
    }

    /** 原版容器面板：黑边 + 灰底 + 上左亮边、下右暗边。 */
    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, OUTLINE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, LIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, LIGHT);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, SHADOW);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, SHADOW);
    }

    /** 原版内凹格子（18×18），放物品图标用。 */
    static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT);
        graphics.fill(x, y, x + SLOT_SIZE, y + 1, SLOT_DARK);
        graphics.fill(x, y, x + 1, y + SLOT_SIZE, SLOT_DARK);
        graphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_LIGHT);
        graphics.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_LIGHT);
    }

    /** 横向凹槽：上暗下亮，像原版界面上刻出来的一道线。 */
    static void grooveH(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, SHADOW);
        graphics.fill(x, y + 1, x + width, y + 2, LIGHT);
    }

    /** 竖向凹槽：左暗右亮。 */
    static void grooveV(GuiGraphics graphics, int x, int y, int height) {
        graphics.fill(x, y, x + 1, y + height, SHADOW);
        graphics.fill(x + 1, y, x + 2, y + height, LIGHT);
    }

    /** 整行的悬停高光。 */
    static void hover(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, HOVER);
    }

    /**
     * 界面上的字：原版容器界面（箱子、熔炉那些）用的是<b>不带阴影</b>的画法，
     * 所以这里也一律不开阴影。开了阴影，深灰的字旁边会多出一道近黑的重影，
     * 看着就像加粗了一样。
     */
    static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    /** 以 {@code centerX} 为中线居中画一行字（同样不带阴影）。 */
    static void textCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /** 一行字放在 height 高的框里时，垂直居中该用的 y。 */
    static int textTop(int top, int height) {
        return top + (height - FONT_H) / 2;
    }

    /**
     * 名字太长就截成「前面几个字...」，保证不会画出格子、压到旁边那栏。
     *
     * <p>中文名字都很短，主要是英文（比如 Cartography Table）会顶到栏外。</p>
     */
    static Component fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String tail = "...";
        int room = maxWidth - font.width(tail);
        return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, room)) + tail);
    }

    /** 原版按钮的点击音效（列表行、勾选框这种不是 Button 的地方用）。 */
    static void playClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
