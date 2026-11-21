package cn.blockforge.generated.furnitureworkwand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「别的模组里哪些方块能加进工作棒」的清单。
 *
 * <p>目前只认一个模组：<b>庄园的馈赠 Manor's Bounty</b>。没装就一个都扫不到。</p>
 *
 * <p><b>命名空间不能写死</b>：庄园本体和它的机器是分开注册的，游戏里实际看到的名字是
 * {@code manors_bounty_machine:ice_cream_machine}——前面的 {@code manors_bounty_machine}
 * 跟 {@code manors_bounty} 差一截，写死就一台都对不上（这就是上一版“庄园的功能不见了”的原因）。
 * 所以现在只要命名空间里同时有 {@code manor} 和 {@code bounty}，就认作这个模组。</p>
 *
 * <p><b>只收机器</b>：那个模组有八百多个方块，绝大多数是农作物、树、墙纸、蛋糕、披萨，
 * 摆进功能列表根本没法用。所以这里不按名字里的单词猜，而是<b>直接把用户点名的机器写进清单</b>
 * （见 {@link #MACHINES}），其余一律不进列表：冰淇淋机、油炸锅、烤箱、茶壶、发酵桶、搅拌机、
 * 高汤锅、平底锅加打蛋器、摇壶。</p>
 *
 * <p>注册名可能是 {@code manors_bounty_machine:oven}，也可能带一层目录
 * {@code manors_bounty_machine:manor_bounty_machine/oven}，所以比对前先把 {@code /} 换成
 * {@code _}，再按「等于」或「以 _名字 结尾」认——前缀是什么都不用管。名字里的 {@code _and_}
 * 也算可有可无（{@code saucepan_whisk} 和 {@code saucepan_and_whisk} 当成同一台）。</p>
 *
 * <p>真开界面的时候还会再验一次：方块右键没打开界面的，就退回本模组自己的通用储物页
 * （见 {@code ModStationRunner}），所以列表里不会出现点了没反应的死按钮。</p>
 */
public final class ModGuiBlocks {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurnitureWorkWand.MODID);

    /**
     * 用户点名要的机器（注册名最后一段写成什么样都行）。
     *
     * <p>以后那个模组要加机器、或者要换一批，改这张表就行。</p>
     */
    private static final List<String> MACHINES = List.of(
            "ice_cream_machine",  // 冰淇淋机
            "fryer",              // 油炸锅
            "oven",               // 烤箱
            "teapot",             // 茶壶
            "fermenter",          // 发酵桶
            "blender",            // 搅拌机
            "stock_pot",          // 高汤锅
            "saucepan_and_whisk", // 平底锅加打蛋器
            "shaker");            // 摇壶（目前那个模组里它是物品不是方块，扫不到是正常的）

    /** 扫描结果为空时要往日志里列几个方块名给人看（最多这么多，免得刷屏）。 */
    private static final int LOG_SAMPLE = 60;

    /** 扫一遍的结果：开游戏后只算一次（方块注册表之后不会再加东西）。 */
    private static List<StationFunction> cached;

    private ModGuiBlocks() {
    }

    /**
     * 这个方块是不是我们认识的模组里的。
     *
     * <p>比「能加进列表」放宽一档：命名空间对得上就算，哪怕不是清单里的机器。
     * 上一版存下的、已经不在列表里的功能页（比如橱柜、抽屉）读存档时依然认，
     * 格子里的东西不会跟着消失。</p>
     */
    public static boolean isKnownModBlock(Block block) {
        ResourceLocation key = idOf(block);
        return key != null
                && (isManorNamespace(key.getNamespace()) || isWantedMachine(key));
    }

    /** 能加进功能列表的方块，按注册名排好序（每次开游戏只扫一遍）。 */
    public static List<StationFunction> functions() {
        if (cached == null) {
            cached = List.copyOf(scan());
        }
        return cached;
    }

    /** 这个方块算不算能加进列表的功能：我们认识的模组、而且是被点名的那些机器。 */
    public static boolean isGuiBlock(Block block) {
        if (block == null || block.asItem() == Items.AIR) {
            return false;
        }
        ResourceLocation key = idOf(block);
        return key != null
                && isManorNamespace(key.getNamespace())
                && isWantedMachine(key);
    }

    // ------------------------------------------------------------------ 扫描

    /**
     * 扫一遍方块注册表。
     *
     * <p>主路径只按「命名空间 + 机器名」找；万一那边把命名空间改得面目全非、
     * 一台都没找到，再退回只按机器名找一次兜底（见下），这样列表不会莫名其妙空着。</p>
     */
    private static List<StationFunction> scan() {
        List<String> ids = new ArrayList<>();
        List<String> namespaceSamples = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation key = idOf(block);
            if (key == null || !isManorNamespace(key.getNamespace())) {
                continue;
            }
            if (namespaceSamples.size() < LOG_SAMPLE) {
                namespaceSamples.add(key.toString());
            }
            if (isGuiBlock(block)) {
                ids.add(key.toString());
            }
        }
        if (ids.isEmpty() && !namespaceSamples.isEmpty()) {
            // 模组在、但清单里的机器一台都没对上：多半是那边改了注册名。只记日志，不把八百多个方块塞进列表。
            LOGGER.warn("没在庄园的命名空间里找到清单里的机器，扫到的方块示例：{}", namespaceSamples);
        }
        if (ids.isEmpty()) {
            // 连命名空间都认不出来（比如机器被拆成了另一个附属模组）：只按方块名兜一次底。
            for (Block block : ForgeRegistries.BLOCKS) {
                ResourceLocation key = idOf(block);
                if (key != null && block.asItem() != Items.AIR && isWantedMachine(key)) {
                    ids.add(key.toString());
                }
            }
            if (!ids.isEmpty()) {
                LOGGER.warn("按方块名兜底找到了 {} 个功能方块（命名空间不在已知清单里）：{}", ids.size(), ids);
            }
        }
        ids.sort(null);
        List<StationFunction> list = new ArrayList<>(ids.size());
        for (String id : ids) {
            StationFunction function = StationFunctions.byId(id);
            if (function != null) {
                list.add(function);
            }
        }
        if (!list.isEmpty()) {
            LOGGER.info("工作棒的功能列表里加进了 {} 个模组方块：{}", list.size(), ids);
        }
        return list;
    }

    /**
     * 这个命名空间是不是庄园的。
     *
     * <p>庄原本体和机器用过的写法有 {@code manors_bounty}、{@code manors_bounty_machine}、
     * {@code manor_bounty}、{@code manorsbounty} 这些，共同点是同时含 {@code manor} 和
     * {@code bounty}，所以按这两截认，不再逐个列。</p>
     */
    private static boolean isManorNamespace(String namespace) {
        String name = namespace.toLowerCase(Locale.ROOT);
        return name.contains("manor") && name.contains("bounty");
    }

    private static boolean isWantedMachine(ResourceLocation key) {
        String path = normalize(key.getPath());
        for (String machine : MACHINES) {
            String wanted = normalize(machine);
            if (path.equals(wanted) || path.endsWith("_" + wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 注册名统一成小写、目录拉平成下划线，顺手去掉 {@code and}
     * （{@code saucepan_whisk} 和 {@code saucepan_and_whisk} 当成同一个）。
     */
    private static String normalize(String path) {
        String flat = path.toLowerCase(Locale.ROOT).replace('/', '_');
        flat = flat.replace("_and_", "_");
        if (flat.startsWith("and_")) {
            flat = flat.substring(4);
        }
        if (flat.endsWith("_and")) {
            flat = flat.substring(0, flat.length() - 4);
        }
        return flat;
    }

    private static ResourceLocation idOf(Block block) {
        return block == null ? null : ForgeRegistries.BLOCKS.getKey(block);
    }
}
