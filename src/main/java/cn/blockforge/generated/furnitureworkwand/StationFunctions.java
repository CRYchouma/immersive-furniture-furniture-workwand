package cn.blockforge.generated.furnitureworkwand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 功能清单的总表：原版那 11 样 + 装上的模组里「带界面的方块」（{@link ModGuiBlocks}）。
 *
 * <p>界面按 {@link #all()} 的顺序排，存档按 {@link StationFunction#id()} 记，
 * 两边都对得上；扫模组方块只做一次，之后都走缓存。</p>
 */
public final class StationFunctions {

    private static final Map<String, StationFunction> BY_ID = new HashMap<>();

    private static List<StationFunction> all;

    static {
        for (WorkFunction function : WorkFunction.values()) {
            BY_ID.put(function.id(), new StationFunction(function.id(), function, null));
        }
    }

    private StationFunctions() {
    }

    /** 界面上能选的全部功能：先是原版那 11 样，再接模组方块。 */
    public static List<StationFunction> all() {
        if (all == null) {
            List<StationFunction> list = new ArrayList<>(WorkFunction.values().length + 32);
            for (WorkFunction function : WorkFunction.values()) {
                list.add(byId(function.id()));
            }
            list.addAll(ModGuiBlocks.functions());
            all = List.copyOf(list);
        }
        return all;
    }

    /**
     * 按存档里的 id 找回功能；认不出来（模组被卸了、id 写错）返回 {@code null}。
     *
     * <p>这里比「能加进列表」放宽一档：只要方块还在、还是我们认识的模组，就认。
     * 这样上一版存下的、已经不在列表里的功能页还能照常打开，格子里的东西不会跟着消失。</p>
     */
    public static StationFunction byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        StationFunction known = BY_ID.get(id);
        if (known != null) {
            return known;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(key);
        if (block == null || !ModGuiBlocks.isKnownModBlock(block)) {
            return null;
        }
        return BY_ID.computeIfAbsent(id, found -> new StationFunction(found, null, block));
    }
}
