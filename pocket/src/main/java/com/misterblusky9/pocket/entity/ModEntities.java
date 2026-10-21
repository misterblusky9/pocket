package com.misterblusky9.pocket.entity;

import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PocketSized.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TheMoonPackageEntity>> THE_MOON_PACKAGE =
            ENTITIES.register(
                    "the_moon_package",
                    () -> EntityType.Builder
                            .<TheMoonPackageEntity>of(TheMoonPackageEntity::new, MobCategory.MISC)
                            .sized(2.0F, 2.0F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(PocketSized.MOD_ID + ":the_moon_package")
            );

    public static final DeferredHolder<EntityType<?>, EntityType<HattersStand>> HATTERS_STAND =
            ENTITIES.register(
                    "hatters_stand",
                    () -> EntityType.Builder
                            .<HattersStand>of(HattersStand::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.25F)
                            .clientTrackingRange(10)
                            .build(PocketSized.MOD_ID + ":hatters_stand")
            );

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(THE_MOON_PACKAGE.get(), PackageEntity.createPackageAttributes().build());
        event.put(HATTERS_STAND.get(), HattersStand.createAttributes().build());
    }

    private ModEntities() {}
}
