package cn.blockforge.generated.furnitureworkwand;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 家具上的一页「功能」。
 *
 * <p>它只有两种来源：</p>
 * <ul>
 *   <li><b>原版功能</b>：{@link WorkFunction} 里写死的那 11 样（熔炉、箱子……），界面用原版自己的；</li>
 *   <li><b>模组方块</b>：别的模组里能开界面的方块（目前是庄园的馈赠里那 8 台厨房机器），
 *       界面还是那个方块自己的界面（见 {@link ModStationRunner}）。</li>
 * </ul>
 *
 * <p>存档里只记一个字符串 id：原版功能就是 {@code "furnace"} 这种，模组方块是
 * {@code "模组的modid:方块名"}。这样以后装了 / 卸了模组，存档里的 id 依然认得出来。</p>
 */
public final class StationFunction {

    private final String id;
    private final WorkFunction vanilla;
    private final Block block;
    /** 方块名字现算一次就够：列表每帧都要用，别每帧都新建一遍。 */
    private Component name;

    StationFunction(String id, WorkFunction vanilla, Block block) {
        this.id = id;
        this.vanilla = vanilla;
        this.block = block;
    }

    /** 存档里写的那个 id。 */
    public String id() {
        return this.id;
    }

    /** 是不是「别的模组的方块」这种功能。 */
    public boolean isMod() {
        return this.vanilla == null;
    }

    /** 原版功能；模组方块返回 {@code null}。 */
    public WorkFunction vanilla() {
        return this.vanilla;
    }

    /** 是不是指定的那个原版功能（模组方块永远返回 false）。 */
    public boolean is(WorkFunction function) {
        return this.vanilla == function;
    }

    /** 模组方块；原版功能返回 {@code null}。 */
    public Block block() {
        return this.block;
    }

    public boolean isChest() {
        return this.vanilla == WorkFunction.CHEST;
    }

    public Component display() {
        if (this.vanilla != null) {
            return this.vanilla.display();
        }
        if (this.name == null) {
            this.name = this.block.getName();
        }
        return this.name;
    }

    public ItemStack iconStack() {
        if (this.vanilla != null) {
            return this.vanilla.iconStack();
        }
        return new ItemStack(this.block);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StationFunction function && this.id.equals(function.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return this.id;
    }
}
