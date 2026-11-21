package cn.blockforge.generated.furnitureworkwand;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 工作棒能装到家具上的全部“家具功能”。
 *
 * <p>每个功能只带一个 id（写进存档）和一个图标。装功能要花的是<b>工作材料</b>
 * （见 {@link WorkMaterials}），每装一页消耗一份。</p>
 */
public enum WorkFunction {

    FURNACE("furnace", Items.FURNACE),

    CARTOGRAPHY_TABLE("cartography_table", Items.CARTOGRAPHY_TABLE),

    LOOM("loom", Items.LOOM),

    STONECUTTER("stonecutter", Items.STONECUTTER),

    CRAFTING_TABLE("crafting_table", Items.CRAFTING_TABLE),

    BREWING_STAND("brewing_stand", Items.BREWING_STAND),

    ENCHANTING_TABLE("enchanting_table", Items.ENCHANTING_TABLE),

    ANVIL("anvil", Items.ANVIL),

    GRINDSTONE("grindstone", Items.GRINDSTONE),

    SMITHING_TABLE("smithing_table", Items.SMITHING_TABLE),

    CHEST("chest", Items.CHEST);

    private static final Map<String, WorkFunction> BY_ID = new HashMap<>();

    static {
        for (WorkFunction function : values()) {
            BY_ID.put(function.id, function);
        }
    }

    private final String id;
    private final Item icon;

    WorkFunction(String id, Item icon) {
        this.id = id;
        this.icon = icon;
    }

    public String id() {
        return this.id;
    }

    public Component display() {
        return Component.translatable("gui.furniture_workwand.fn." + this.id);
    }

    public ItemStack iconStack() {
        return new ItemStack(this.icon);
    }

    /** id -> 功能；未知或空字符串返回 null。 */
    public static WorkFunction byId(String id) {
        return id == null || id.isEmpty() ? null : BY_ID.get(id);
    }
}
