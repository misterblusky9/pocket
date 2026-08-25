package com.misterblusky9.pocket.create;

import com.misterblusky9.pocket.item.PocketCaseItem;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileBlockHitAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.BlockHitResult;

public record PocketedSubLevelBlockHitAction() implements PotatoProjectileBlockHitAction {
    public static final PocketedSubLevelBlockHitAction INSTANCE = new PocketedSubLevelBlockHitAction();
    public static final MapCodec<PocketedSubLevelBlockHitAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean execute(final LevelAccessor level, final ItemStack projectile, final BlockHitResult ray) {
        if (level.isClientSide()) return true;
        if (!(level instanceof final ServerLevel serverLevel)) return false;

        if (PocketCaseItem.deployPocketedProjectile(serverLevel, projectile, ray)) return true;

        final ItemEntity recovered = new ItemEntity(
                serverLevel,
                ray.getLocation().x, ray.getLocation().y + 0.15D, ray.getLocation().z,
                projectile.copyWithCount(1)
        );
        serverLevel.addFreshEntity(recovered);
        return true;
    }

    @Override
    public MapCodec<? extends PotatoProjectileBlockHitAction> codec() { return CODEC; }
}
