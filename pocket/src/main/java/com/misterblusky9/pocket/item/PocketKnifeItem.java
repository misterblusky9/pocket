package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PocketKnifeItem extends Item implements PriorityInteractionItem {
    public PocketKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean claimsBlock(
            Player player,
            ItemStack stack,
            Level level,
            BlockPos pos
    ) {
        return Sable.HELPER.getContaining(level, pos) != null
                || CrossScaleWelds.worldWeldAt(level, pos);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Vec3 point = context.getClickLocation();

        var subLevel = Sable.HELPER.getContaining(level, pos);
        boolean casing = subLevel != null && BlueprintCasingSplit.isCasing(level, pos);

        if (!(level instanceof ServerLevel serverLevel)) {
            return CrossScaleWelds.weldNear(level, point) != null || casing
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }

        if (cutWeld(serverLevel, point, pos, context.getPlayer(), context.getItemInHand(), context.getHand())) {
            return InteractionResult.CONSUME;
        }
        if (!casing || !(subLevel instanceof ServerSubLevel serverSubLevel)
                || serverSubLevel.isRemoved()) return InteractionResult.PASS;

        BlueprintCasingSplit.cut(serverLevel, serverSubLevel, pos, context.getClickedFace(),
                context.getPlayer(), context.getItemInHand(), context.getHand());
        return InteractionResult.CONSUME;
    }

    public static boolean cutWeld(
            ServerLevel level,
            Vec3 point,
            BlockPos pos,
            Player player,
            ItemStack tool,
            InteractionHand hand
    ) {
        WeldRecord record = CrossScaleWelds.weldNear(level, point);
        if (record == null) return false;

        WeldRuntime.drop(record.weldId());
        WeldStore.get(level).remove(record.weldId());
        CrossScaleWeldSync.broadcast(level);

        if (player != null) {
            tool.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }

        level.playSound(
                null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.4F);
        return true;
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        if (stack.getDamageValue() + 1 >= stack.getMaxDamage()) return ItemStack.EMPTY;
        ItemStack knife = stack.copyWithCount(1);
        knife.setDamageValue(stack.getDamageValue() + 1);
        return knife;
    }
}
