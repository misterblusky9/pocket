package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import dev.ryanhcode.sable.Sable;
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
    public PocketKnifeItem(final Properties properties) {
        super(properties);
    }

    @Override
    public boolean claimsBlock(
            final Player player,
            final ItemStack stack,
            final Level level,
            final BlockPos pos
    ) {
        return Sable.HELPER.getContaining(level, pos) != null
                || CrossScaleWelds.worldWeldAt(level, pos);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final Vec3 point = context.getClickLocation();

        if (!(level instanceof final ServerLevel serverLevel)) {
            return CrossScaleWelds.weldNear(level, point) != null
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }

        return cutWeld(serverLevel, point, pos, context.getPlayer(), context.getItemInHand(), context.getHand())
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    public static boolean cutWeld(
            final ServerLevel level,
            final Vec3 point,
            final BlockPos pos,
            final Player player,
            final ItemStack tool,
            final InteractionHand hand
    ) {
        final WeldRecord record = CrossScaleWelds.weldNear(level, point);
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
    public boolean hasCraftingRemainingItem(final ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(final ItemStack stack) {
        if (stack.getDamageValue() + 1 >= stack.getMaxDamage()) return ItemStack.EMPTY;
        final ItemStack knife = stack.copyWithCount(1);
        knife.setDamageValue(stack.getDamageValue() + 1);
        return knife;
    }
}
