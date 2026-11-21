package cn.blockforge.generated.furnitureworkwand.client;

import java.nio.DoubleBuffer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import cn.blockforge.generated.furnitureworkwand.FurnitureWorkWand;
import cn.blockforge.generated.furnitureworkwand.network.ModNetwork;
import cn.blockforge.generated.furnitureworkwand.network.PageInfoPacket;
import cn.blockforge.generated.furnitureworkwand.network.SwitchPagePacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 多页家具的界面顶部：一条「1 / 3」的小标签，两头是原版的翻页按钮。
 *
 * <p>标签贴在原版容器界面的<b>上沿外侧</b>，用和界面同一套灰色面板样式，
 * 所以在任何容器界面（熔炉、箱子、工作台……）上都不突兀，也不需要替换原版界面——
 * 每个功能页用的还是原版自己的界面。字和原版容器界面的标题一样不带阴影。</p>
 *
 * <p>翻页有两种办法：点标签两头的箭头，或者直接按键盘的 ← →。后者对常用键盘党更顺手。
 * 唯一要让一步的地方是铁砧那种带改名输入框的界面：光标还能往那个方向挪时，这一下归输入框；
 * 光标已经到头（或者框里还没字）时，再按也不会发生任何事，那就照常翻页——
 * 所以每一页都能用方向键翻，不用先点一下别处把焦点从输入框里挪开。</p>
 *
 * <p>翻页 = 服务端重新打开一个界面，而原版换界面时会把鼠标指针甩到窗口正中间。
 * 所以翻页前先记住指针在哪，新界面一建好就把它放回去，翻页时手感不会断。</p>
 */
@Mod.EventBusSubscriber(modid = FurnitureWorkWand.MODID, value = Dist.CLIENT)
public final class StationPageOverlay {

    /** 标签宽度：两个箭头 + 一句「10 / 12」都装得下，翻页时不会忽宽忽窄。 */
    private static final int WIDTH = 78;
    private static final int HEIGHT = 18;
    private static final int ARROW = 14;
    /** 按钮离标签边缘的距离，正好落在面板内圈里。 */
    private static final int INSET = 2;

    /** 翻页后最多等这么久还没建好新界面，就不再挪鼠标了（毫秒）。 */
    private static final long CURSOR_RESTORE_TIMEOUT = 2000L;

    private static PageInfoPacket info;
    private static Button prevButton;
    private static Button nextButton;
    /** 翻页前记下的鼠标指针位置；没翻过页时是 {@link Double#NaN}，表示“不用管”。 */
    private static double cursorX = Double.NaN;
    private static double cursorY = Double.NaN;
    private static long cursorSince;

    private StationPageOverlay() {
    }

    /** 服务端刚打开/翻过一页时调用。 */
    public static void accept(PageInfoPacket packet) {
        info = packet;
    }

    private static boolean applies(Screen screen) {
        if (info == null || info.total() <= 1) {
            return false;
        }
        return screen instanceof AbstractContainerScreen<?> container
                && container.getMenu().containerId == info.containerId();
    }

    private static void ensureButtons() {
        if (prevButton != null) {
            return;
        }
        prevButton = Button.builder(Component.literal("<"), b -> turnAndRemember(-1)).size(ARROW, ARROW).build();
        nextButton = Button.builder(Component.literal(">"), b -> turnAndRemember(1)).size(ARROW, ARROW).build();
    }

    /**
     * 往前 / 往后翻一页。
     *
     * @return 真的发出翻页请求了；已经在头 / 尾上没得翻时返回 {@code false}
     */
    private static boolean turn(int delta) {
        if (info == null) {
            return false;
        }
        int target = info.page() + delta;
        if (target < 0 || target >= info.total()) {
            return false;
        }
        ModNetwork.CHANNEL.sendToServer(new SwitchPagePacket(info.pos(), target));
        return true;
    }

    /**
     * 翻一页，并记住鼠标现在在哪。
     *
     * <p>翻页 = 服务端重新打开一个界面，而原版换界面时会把鼠标指针甩到窗口正中间，
     * 所以翻页前先记一下，新界面一建好就放回去，翻页时手感不会断。</p>
     *
     * @return 真的翻页了；已经在头 / 尾上时返回 {@code false}
     */
    private static boolean turnAndRemember(int delta) {
        if (!turn(delta)) {
            return false;
        }
        rememberCursor();
        return true;
    }

    /** 标签左上角在屏幕上的位置。 */
    private record Bar(int x, int y) {
    }

    /**
     * 标签放哪儿：优先贴在容器界面上沿外侧；上下都没空地（小窗口 + 大盘子）时，
     * 塞进界面右上角那条标题行里——原版的标题都靠左写，右边是空的。
     *
     * <p>横向是<b>对着容器界面的中线</b>摆的，不是对着屏幕中线：容器一般就在屏幕正中，
     * 但有些模组界面会偏一点，对着容器摆才不会看着歪。</p>
     */
    private static Bar bar(Screen screen) {
        int centered = (screen.width - WIDTH) / 2;
        if (screen instanceof AbstractContainerScreen<?> container) {
            int x = container.getGuiLeft() + (container.getXSize() - WIDTH) / 2;
            x = Mth.clamp(x, 2, Math.max(2, screen.width - WIDTH - 2));
            int above = container.getGuiTop() - HEIGHT - 2;
            if (above >= 2) {
                return new Bar(x, above);
            }
            int below = container.getGuiTop() + container.getYSize() + 2;
            if (below + HEIGHT <= screen.height - 24) {
                return new Bar(x, below);
            }
            return new Bar(container.getGuiLeft() + container.getXSize() - WIDTH - 1,
                    container.getGuiTop() + 1);
        }
        return new Bar(centered, 2);
    }

    /** 两个箭头摆进标签，并按当前页决定能不能点（画和点之前都调一次）。 */
    private static void layout(Bar bar) {
        int page = info.page();
        int total = info.total();
        prevButton.active = page > 0;
        nextButton.active = page + 1 < total;
        prevButton.setX(bar.x() + INSET);
        nextButton.setX(bar.x() + WIDTH - INSET - ARROW);
        prevButton.setY(bar.y() + INSET);
        nextButton.setY(bar.y() + INSET);
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!applies(event.getScreen())) {
            return;
        }
        ensureButtons();
        Screen screen = event.getScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        Bar bar = bar(screen);
        Font font = Minecraft.getInstance().font;

        VanillaStyle.panel(graphics, bar.x(), bar.y(), WIDTH, HEIGHT);
        VanillaStyle.textCentered(graphics, font,
                Component.translatable("gui.furniture_workwand.page", info.page() + 1, info.total()),
                bar.x() + WIDTH / 2, VanillaStyle.textTop(bar.y(), HEIGHT), VanillaStyle.TEXT);

        layout(bar);
        prevButton.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
        nextButton.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !applies(event.getScreen())) {
            return;
        }
        ensureButtons();
        Bar bar = bar(event.getScreen());
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        if (mouseX < bar.x() || mouseX >= bar.x() + WIDTH
                || mouseY < bar.y() || mouseY >= bar.y() + HEIGHT) {
            return;
        }
        layout(bar);
        // 两个箭头自己会翻页、自己会记住鼠标位置，这里只把这一下吃掉，
        // 别让标签底下的容器界面也跟着响应。
        prevButton.mouseClicked(mouseX, mouseY, 0);
        nextButton.mouseClicked(mouseX, mouseY, 0);
        event.setCanceled(true);
    }

    /**
     * 键盘 ← → 也能翻页，每一页都一样，铁砧那页也不例外。
     *
     * <p>铁砧（以及别的带输入框的界面）一打开，输入框就拿着焦点。以前只要焦点在输入框里
     * 我们就把左右键整个让出去，于是翻到那一页时方向键突然不翻页了。现在改成看光标：
     * 光标还能往这个方向挪，键就归输入框；已经挪到头了，再按也什么都不会发生，
     * 那就拿它翻页。这样改名照样能用键盘，翻页也不会被某一页卡住。</p>
     */
    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!applies(event.getScreen())) {
            return;
        }
        int delta = switch (event.getKeyCode()) {
            case InputConstants.KEY_LEFT -> -1;
            case InputConstants.KEY_RIGHT -> 1;
            default -> 0;
        };
        if (delta == 0 || inputBoxKeepsKey(event.getScreen(), delta)) {
            return;
        }
        if (turnAndRemember(delta)) {
            event.setCanceled(true);
        }
    }

    /**
     * 焦点在输入框里时，这一个左右键是不是该留给输入框。
     *
     * <p>输入框这会儿根本收不了键盘（铁砧格子空着时那个改名框就是不可编辑的），
     * 或者光标已经到头、没选着字——按下去什么也不会变，那就让给翻页。
     * 只有真能挪光标 / 收选区的时候才留给它。</p>
     */
    private static boolean inputBoxKeepsKey(Screen screen, int delta) {
        if (!(screen.getFocused() instanceof EditBox box) || !box.canConsumeInput()) {
            return false;
        }
        if (!box.getHighlighted().isEmpty()) {
            return true;
        }
        int caret = box.getCursorPosition();
        return delta < 0 ? caret > 0 : caret < box.getValue().length();
    }

    /**
     * 新界面建好之后：先把“过期”的页码信息扔掉，再把鼠标指针放回翻页前的位置。
     *
     * <p>容器界面的 id 会被原版循环复用（每 100 个界面绕一圈），所以旧的那条页码信息必须
     * 在新界面一来就清掉，否则打开一个毫不相干的箱子时顶部可能冒出「2 / 3」。</p>
     *
     * <p>原版每次换容器界面都会把指针挪到窗口正中（{@code MouseHandler#releaseMouse}），
     * 这里在界面初始化完成后把它挪回来，翻页就不会“鼠标突然跳走”。</p>
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (info != null) {
            boolean sameMenu = event.getScreen() instanceof AbstractContainerScreen<?> container
                    && container.getMenu().containerId == info.containerId();
            if (!sameMenu) {
                info = null;
            }
        }
        if (Double.isNaN(cursorX) || Double.isNaN(cursorY)) {
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            forgetCursor();
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        if (Util.getMillis() - cursorSince <= CURSOR_RESTORE_TIMEOUT) {
            GLFW.glfwSetCursorPos(window.getWindow(), cursorX, cursorY);
        }
        forgetCursor();
    }

    private static void rememberCursor() {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.getWindow(), x, y);
            cursorX = x.get(0);
            cursorY = y.get(0);
            cursorSince = Util.getMillis();
        }
    }

    private static void forgetCursor() {
        cursorX = Double.NaN;
        cursorY = Double.NaN;
    }
}
