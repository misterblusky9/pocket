package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.item.ModDataComponents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class HatStandInteractions {
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.getBlockState(event.getPos()).getBlock() instanceof HatStandBlock) {
            if (event.getItemStack().getItem() instanceof BlockItem) event.setUseItem(TriState.FALSE);
            return;
        }
        if (!HatStandBlock.isHeadwear(event.getItemStack())) return;
        SubLevel subLevel = Sable.HELPER.getContaining(level, event.getPos());
        if (subLevel == null || !level.getBlockState(subLevel.getPlot().getCenterBlock())
                .is(ModBlocks.POCKET_DUMMY.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!player.isCreative() || BuildHostBlockEntity.token(stack) == null) return;
        EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
        if (!slot.isArmor() || !player.canUseSlot(slot)) return;
        ItemStack worn = player.getItemBySlot(slot);
        player.setItemSlot(slot, stack.copyAndClear());
        player.setItemInHand(event.getHand(), worn);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }

    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack hat = event.getLeft();
        ItemStack target = event.getRight();
        if (!transfersHat(hat, target)) return;
        ItemStack result = BuildHostBlockEntity.withHatOf(hat, target);
        copyLayer(ModDataComponents.SKIN_LAYER.get(), hat, result);
        copyLayer(ModDataComponents.HEADWEAR_LAYER.get(), hat, result);
        if (HatStandBlockEntity.firstPersonRendered(hat)) result.set(ModDataComponents.FIRST_PERSON_RENDERED.get(), true);
        else result.remove(ModDataComponents.FIRST_PERSON_RENDERED.get());
        event.setOutput(result);
        event.setCost(1);
        event.setMaterialCost(1);
    }

    public static void onAnvilTake(AnvilRepairEvent event) {
        Player player = event.getEntity();
        ItemStack hat = event.getLeft();
        if (player.level().isClientSide || !transfersHat(hat, event.getRight())) return;
        ItemStack stripped = BuildHostBlockEntity.plain(hat);
        stripped.remove(ModDataComponents.SKIN_LAYER.get());
        stripped.remove(ModDataComponents.HEADWEAR_LAYER.get());
        stripped.remove(ModDataComponents.FIRST_PERSON_RENDERED.get());
        if (!player.getInventory().add(stripped)) player.drop(stripped, false);
    }

    private static boolean transfersHat(ItemStack hat, ItemStack target) {
        return hat.getCount() == 1 && HatStandBlock.isHeadwear(hat) && BuildHostBlockEntity.token(hat) != null
                && HatStandBlock.isHeadwear(target) && BuildHostBlockEntity.token(target) == null;
    }

    private static void copyLayer(DataComponentType<Integer> type, ItemStack from, ItemStack to) {
        int layer = from.getOrDefault(type, 0);
        if (layer == 0) to.remove(type);
        else to.set(type, layer);
    }

    private HatStandInteractions() {}
}
