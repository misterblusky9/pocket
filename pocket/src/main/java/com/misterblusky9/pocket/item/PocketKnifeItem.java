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
import net.minecraft.tags.BlockTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.SimpleTier;

public class PocketKnifeItem extends SwordItem implements PriorityInteractionItem {
    public static final Tier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 100,
            Tiers.WOOD.getSpeed(), Tiers.WOOD.getAttackDamageBonus(), Tiers.WOOD.getEnchantmentValue(),
            () -> Ingredient.EMPTY);

    public PocketKnifeItem(Properties properties) {
        super(TIER, properties);
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
        if (player != null && (!ToolPermissions.mayAlter(player, level, record.smallPos())
                || (record.bigPos() != null && !ToolPermissions.mayAlter(player, level, record.bigPos())))) {
            return true;
        }

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
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return ability != ItemAbilities.SWORD_SWEEP && super.canPerformAction(stack, ability);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
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
