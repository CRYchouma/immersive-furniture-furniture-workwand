package cn.blockforge.generated.furnitureworkwand;

import net.conczin.immersive_furniture.block.BaseFurnitureBlock;
import net.conczin.immersive_furniture.block.FurnitureProxyBlock;
import net.conczin.immersive_furniture.block.entity.FurnitureBlockEntity;
import net.conczin.immersive_furniture.data.FurnitureData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 和「沉浸式家具」打交道的唯一一扇门。
 *
 * <p>沉浸式家具已经是本模组的<b>硬前置</b>（{@code mods.toml} 里写死了），所以这里直接用它自己的
 * 类判断，不再靠注册名猜、也不再靠反射：</p>
 * <ul>
 *   <li>{@link BaseFurnitureBlock} 的所有子类都是家具（家具、带实体的家具、灯具都算）；</li>
 *   <li>工匠台（{@code ArtisansWorkstationBlock}）直接继承原版 {@code Block}，所以天然不算家具；</li>
 *   <li>{@link FurnitureProxyBlock} 是多格家具的隐形占位格，它自己知道主体在哪。</li>
 * </ul>
 *
 * <p>家具自己的名字存在 {@link FurnitureData} 里，直接读字段，不用再猜。</p>
 */
public final class FurnitureAccess {

    private FurnitureAccess() {
    }

    /** 是不是沉浸式家具的家具模型（工匠台、隐形占位格都不算）。 */
    public static boolean isFurnitureModel(BlockState state) {
        return state != null && state.getBlock() instanceof BaseFurnitureBlock;
    }

    /** 是不是多格家具的隐形占位格。 */
    public static boolean isProxy(BlockState state) {
        return state != null && state.getBlock() instanceof FurnitureProxyBlock;
    }

    /**
     * 把「玩家点到的那一格」换算成真正要变成工作方块的那一格。
     *
     * @return 家具主体坐标；点到的不是家具（含工匠台、普通方块）时返回 {@code null}
     */
    public static BlockPos resolveBase(Level level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        if (isFurnitureModel(state)) {
            return clicked;
        }
        if (state.getBlock() instanceof FurnitureProxyBlock proxy) {
            BlockPos base = proxy.getBasePos(state, clicked);
            if (base != null && isFurnitureModel(level.getBlockState(base))) {
                return base;
            }
        }
        return null;
    }

    /**
     * 家具自己的名字（玩家在工作台上给它起的名字），给箱子界面的标题用。
     *
     * <p>方块本身叫「家具」，直接拿来当标题分不清是哪一件；名字存在家具的方块实体里，
     * 没加载到方块实体（或玩家没起名）时才退回方块名。</p>
     */
    public static Component nameOf(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture) {
            FurnitureData data = furniture.getData();
            if (data != null && data.name != null && !data.name.isBlank()) {
                return Component.literal(data.name);
            }
        }
        return level.getBlockState(pos).getBlock().getName();
    }
}
