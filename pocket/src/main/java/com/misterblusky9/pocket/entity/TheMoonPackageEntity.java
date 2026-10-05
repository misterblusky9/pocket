package com.misterblusky9.pocket.entity;

import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TheMoonPackageEntity extends PackageEntity {
    public TheMoonPackageEntity(
            final EntityType<? extends TheMoonPackageEntity> type,
            final Level level
    ) {
        super(type, level);
    }

    public static TheMoonPackageEntity create(
            final Level level,
            final Vec3 position,
            final ItemStack stack
    ) {
        final TheMoonPackageEntity entity = ModEntities.THE_MOON_PACKAGE.get().create(level);
        if (entity == null) return null;
        entity.setPos(position.x, position.y, position.z);
        entity.setBox(stack.copyWithCount(1));
        return entity;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }
}
