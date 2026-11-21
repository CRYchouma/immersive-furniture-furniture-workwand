package cn.blockforge.generated.furnitureworkwand;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 工作棒：一根普通木棍（不发光、没有附魔效果）。
 *
 * <ul>
 *   <li>拿着它<b>右键</b>（对着空气或普通方块）—— 打开配置界面，把要装的功能选进“待安装清单”；</li>
 *   <li>清单里选好后，右键<b>沉浸式家具的家具</b> —— 服务端校验材料（每页一份工作材料），
 *       扣除后给这件家具装上全部功能。家具外观一个像素都不变；</li>
 *   <li>潜行右键随时都能打开配置界面（对着家具也行）。</li>
 * </ul>
 */
public class WorkWandItem extends Item {

    public WorkWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openScreen(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 用 {@code onItemUseFirst} 而不是 {@code useOn}：沉浸式家具的家具方块自己会吃掉右键
     * （坐下、睡觉、开关灯）。这个回调比方块自己的 use 更早，才能在它之前动手。
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockState state = level.getBlockState(context.getClickedPos());
        boolean onFurniture = FurnitureAccess.isFurnitureModel(state) || FurnitureAccess.isProxy(state);
        boolean wantConfig = player.isShiftKeyDown() || WorkLoadout.read(stack).isEmpty() || !onFurniture;

        if (level.isClientSide) {
            if (wantConfig) {
                openScreen(stack);
            }
            return InteractionResult.SUCCESS;
        }
        if (wantConfig) {
            // 配置界面是客户端打开的，服务端什么都不用做，只把方块自己的右键拦掉。
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return install(serverPlayer, level, context.getClickedPos(), stack);
    }

    private InteractionResult install(ServerPlayer player, Level level, BlockPos clicked, ItemStack stack) {
        // 只认沉浸式家具的家具模型；点到的可能是多格家具的隐形占位格，换算到主体那一格。
        BlockPos target = FurnitureAccess.resolveBase(level, clicked);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.furniture_workwand.wrong_block")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        FurnitureStations stations = FurnitureStations.of(level);
        if (stations == null || !(level instanceof ServerLevel server)) {
            return InteractionResult.FAIL;
        }

        StationData existing = stations.get(target);
        if (existing != null && existing.pageCount() > 0) {
            // 已经是功能家具了：只打开它自己的界面，不会被工作棒的清单覆盖，免得手滑换掉。
            stations.open(player, target, existing.lastPage());
            return InteractionResult.SUCCESS;
        }

        List<WorkLoadout.Entry> loadout = WorkLoadout.read(stack);
        // 每页功能 3 份；箱子按存储空间再加：每多 9 格多 2 份。
        int need = WorkMaterials.costOf(loadout);
        if (!WorkMaterials.consume(player, need)) {
            player.displayClientMessage(Component.translatable("message.furniture_workwand.missing",
                    need, WorkMaterials.count(player)).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        stations.install(server, target, loadout);
        player.displayClientMessage(Component.translatable("message.furniture_workwand.installed",
                loadout.size(), WorkLoadout.describe(loadout)).withStyle(ChatFormatting.GREEN), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        List<WorkLoadout.Entry> loadout = WorkLoadout.read(stack);
        if (loadout.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.furniture_workwand.loadout",
                WorkLoadout.describe(loadout)).withStyle(ChatFormatting.AQUA));
    }

    /** 配置界面是纯客户端的东西：只有跑在客户端时才会去加载那个类。 */
    private static void openScreen(ItemStack stack) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> cn.blockforge.generated.furnitureworkwand.client.ClientWand.openScreen(stack));
    }
}
