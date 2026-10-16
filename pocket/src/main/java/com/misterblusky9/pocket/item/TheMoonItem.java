package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.entity.TheMoonPackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;

public final class TheMoonItem extends PackageItem {
    public static final PackageStyles.PackageStyle STYLE =
            new PackageStyles.PackageStyle("the_moon", 32, 32, 16.0F, false);

    public TheMoonItem(Properties properties) {
        super(properties, STYLE);
        PackageStyles.ALL_BOXES.remove(this);
        PackageStyles.STANDARD_BOXES.remove(this);
    }

    @Override
    public String getDescriptionId() {
        return "item.pocket.the_moon";
    }

    @Override
    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        TheMoonPackageEntity moon =
                TheMoonPackageEntity.create(level, location.position(), stack.copyWithCount(1));
        if (moon == null) return null;
        moon.setDeltaMovement(location.getDeltaMovement());
        return moon;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int timeLeft
    ) {
        if (!(entity instanceof Player player)) return;

        int used = getUseDuration(stack, entity) - timeLeft;
        if (used < 4 || level.isClientSide) return;

        float charge = Math.min(1.0F, used / 20.0F);
        float speed = 0.55F + charge * 0.95F;

        Vec3 look = player.getLookAngle();
        Vec3 spawn = player.getEyePosition()
                .add(look.scale(1.15D))
                .add(0.0D, -0.65D, 0.0D);

        TheMoonPackageEntity moon =
                TheMoonPackageEntity.create(level, spawn, stack.copyWithCount(1));
        if (moon == null) return;

        moon.setDeltaMovement(
                look.scale(speed)
                        .add(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z)
        );
        moon.tossedBy = new WeakReference<>(player);

        level.addFreshEntity(moon);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS,
                0.7F,
                0.75F
        );

        if (!player.getAbilities().instabuild)
            stack.shrink(1);
    }
}
