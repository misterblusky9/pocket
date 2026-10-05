package com.misterblusky9.pocket.create;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileEntityHitAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

public record PocketedSubLevelEntityHitAction() implements PotatoProjectileEntityHitAction {
    public static final PocketedSubLevelEntityHitAction INSTANCE = new PocketedSubLevelEntityHitAction();
    public static final MapCodec<PocketedSubLevelEntityHitAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean execute(final ItemStack projectile, final EntityHitResult ray, final Type type) {
        if (type != Type.ON_HIT) return false;
        if (!ray.getEntity().level().isClientSide) {
            ray.getEntity().spawnAtLocation(projectile.copyWithCount(1));
        }
        return true;
    }

    @Override
    public MapCodec<? extends PotatoProjectileEntityHitAction> codec() { return CODEC; }
}
