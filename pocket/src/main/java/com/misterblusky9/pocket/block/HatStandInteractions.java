package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.item.ModDataComponents;
import dev.ryanhcode.sable.Sable;
import com.misterblusky9.pocket.build.BuildSites;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.inventory.ClickAction;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class HatStandInteractions {
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        ItemStack carried = event.getItemStack();
        if (event.getHand() == InteractionHand.MAIN_HAND && PocketCaseItem.isFilled(carried)) {
            BuildHostBlockEntity stand = standFor(level, event.getPos());
            boolean sneaking = event.getEntity().isShiftKeyDown();
            boolean sealed = PocketCaseItem.isSealed(carried);
            if (stand != null && (!sneaking || sealed)) {
                if (!sneaking && stand.getHat() == null) {
                    if (!level.isClientSide)
                        event.getEntity().displayClientMessage(Component.translatable("pocket.case.no_helmet"), true);
                } else if (sealed) {
                    if (!level.isClientSide) event.getEntity().displayClientMessage(
                            Component.translatable("pocket.case.sealed", carried.getHoverName()), true);
                } else if (!sneaking && level.isClientSide) {
                    com.misterblusky9.pocket.client.HatSchematics.begin(stand, carried);
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }
        if (state.getBlock() instanceof HatStandBlock) {
            if (event.getItemStack().getItem() instanceof BlockItem) event.setUseItem(TriState.FALSE);
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!HatStandBlock.isHeadwear(stack)
                || stack.getItem() instanceof BlockItem && BuildHostBlockEntity.token(stack) == null) return;
        SubLevel subLevel = Sable.HELPER.getContaining(level, event.getPos());
        if (subLevel == null || !level.getBlockState(subLevel.getPlot().getCenterBlock())
                .is(ModBlocks.POCKET_DUMMY.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static @Nullable BuildHostBlockEntity standFor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos host = null;
        if (state.getBlock() instanceof HatStandBlock block) {
            host = block.standPos(state, pos);
        } else {
            SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
            if (subLevel instanceof ServerSubLevel server) return BuildSites.hostOf(server);
            if (subLevel != null && level.isClientSide) host = com.misterblusky9.pocket.client.BuildMirrors.standHost(subLevel);
        }
        return host != null && level.getBlockEntity(host) instanceof BuildHostBlockEntity stand ? stand : null;
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).is(ModBlocks.POCKET_DUMMY.get())) event.setCanceled(true);
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

    public static void onStackedOnOther(ItemStackedOnOtherEvent event) {
        ItemStack hat = event.getStackedOnItem();
        if (event.getClickAction() != ClickAction.SECONDARY || !event.getCarriedItem().isEmpty()
                || HatStandBlockEntity.NameEgg.of(hat) != HatStandBlockEntity.NameEgg.SPIN) return;
        HatStandBlockEntity.cycleSpin(hat);
        event.getSlot().setChanged();
        event.setCanceled(true);
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
        if (hat.getOrDefault(ModDataComponents.CUSTOM_SPRITE.get(), true)) result.remove(ModDataComponents.CUSTOM_SPRITE.get());
        else result.set(ModDataComponents.CUSTOM_SPRITE.get(), false);
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
        stripped.remove(ModDataComponents.HAT_SPIN.get());
        stripped.remove(ModDataComponents.CUSTOM_SPRITE.get());
        if (!player.getInventory().add(stripped)) player.drop(stripped, false);
    }

    private static boolean transfersHat(ItemStack hat, ItemStack target) {
        return hat.getCount() == 1 && HatStandBlock.isHeadwear(hat) && BuildHostBlockEntity.token(hat) != null
                && !PocketCaseItem.isSealed(hat)
                && HatStandBlock.isHeadwear(target) && BuildHostBlockEntity.token(target) == null;
    }

    private static void copyLayer(DataComponentType<Integer> type, ItemStack from, ItemStack to) {
        int layer = from.getOrDefault(type, 0);
        if (layer == 0) to.remove(type);
        else to.set(type, layer);
    }

    private HatStandInteractions() {}
}
