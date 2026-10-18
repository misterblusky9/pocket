package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.block.FlexibleCouplingBlock;
import com.misterblusky9.pocket.block.FlexibleCouplingBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class FlexibleShaftItem extends Item {
    public static final double MAX_LENGTH = FlexibleCouplingBlockEntity.MAX_LENGTH;

    public FlexibleShaftItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("item.pocket.flexible_shaft.tooltip.hint")
                .withStyle(ChatFormatting.WHITE));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockPos first = pending(stack, level);

        if (first == null && level.getBlockEntity(pos) instanceof FlexibleCouplingBlockEntity coupling
                && coupling.isLinked()) {
            if (!level.isClientSide && player != null) {
                FlexibleCouplingBlock.tryAdjust(level, pos, player);
            }
            return InteractionResult.SUCCESS;
        }

        if (player != null && player.isShiftKeyDown()) {
            stack.remove(ModDataComponents.FLEXIBLE_SHAFT_FIRST.get());
            return InteractionResult.SUCCESS;
        }
        if (!isValidAttachment(level, pos)) {
            return super.useOn(context);
        }
        if (first == null) {
            stack.set(ModDataComponents.FLEXIBLE_SHAFT_FIRST.get(), GlobalPos.of(level.dimension(), pos));
            return InteractionResult.SUCCESS;
        }
        stack.remove(ModDataComponents.FLEXIBLE_SHAFT_FIRST.get());
        if (!level.isClientSide && attach(level, first, pos)) {
            if (player != null) {
                player.awardStat(Stats.ITEM_USED.get(this));
                if (!player.hasInfiniteMaterials()) {
                    stack.shrink(1);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    public static BlockPos pending(ItemStack stack, Level level) {
        GlobalPos first = stack.get(ModDataComponents.FLEXIBLE_SHAFT_FIRST.get());
        return first != null && first.dimension().equals(level.dimension()) ? first.pos() : null;
    }

    private static boolean attach(Level level, BlockPos first, BlockPos second) {
        if (first.equals(second) || !isValidAttachment(level, first) || !inRange(level, first, second.getCenter())) {
            return false;
        }
        FlexibleCouplingBlockEntity controller = (FlexibleCouplingBlockEntity) level.getBlockEntity(first);
        FlexibleCouplingBlockEntity partner = (FlexibleCouplingBlockEntity) level.getBlockEntity(second);
        double distance = Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(
                level, first.getCenter(), second.getCenter()));
        double length = Math.clamp(distance + 1.0, FlexibleCouplingBlockEntity.MIN_LENGTH, MAX_LENGTH);
        controller.link(second, subLevelId(level, second), true, length);
        partner.link(first, subLevelId(level, first), false, length);
        level.playSound(null, first, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
        level.playSound(null, second, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
        return true;
    }

    public static boolean isValidAttachment(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FlexibleCouplingBlockEntity coupling && !coupling.isLinked();
    }

    public static boolean inRange(Level level, BlockPos first, Vec3 target) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, first.getCenter(), target) < MAX_LENGTH * MAX_LENGTH;
    }

    @Nullable
    private static UUID subLevelId(Level level, BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel != null ? subLevel.getUniqueId() : null;
    }
}
