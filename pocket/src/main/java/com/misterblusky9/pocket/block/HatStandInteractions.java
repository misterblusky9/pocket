package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.TriState;
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

    private HatStandInteractions() {}
}
