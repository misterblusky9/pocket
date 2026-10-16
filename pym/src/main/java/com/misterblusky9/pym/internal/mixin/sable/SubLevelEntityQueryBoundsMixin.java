package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.collision.PlotScanBounds;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

@Mixin(value = SubLevelInclusiveLevelEntityGetter.class, remap = false)
public abstract class SubLevelEntityQueryBoundsMixin {
    @WrapOperation(
            method = "get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/LevelEntityGetter;get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V",
                    ordinal = 2
            ),
            require = 1
    )
    private void pym$clipSubLevelQuery(
            LevelEntityGetter<?> delegate,
            AABB bounds,
            Consumer<?> consumer,
            Operation<Void> original,
            @Local(ordinal = 1) SubLevel subLevel
    ) {
        AABB clipped = PlotScanBounds.clipEntityQuery(subLevel, bounds);
        if (clipped != null) original.call(delegate, clipped, consumer);
    }

    @WrapOperation(
            method = "get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/LevelEntityGetter;get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V",
                    ordinal = 2
            ),
            require = 1
    )
    private void pym$clipTypedSubLevelQuery(
            LevelEntityGetter<?> delegate,
            EntityTypeTest<?, ?> type,
            AABB bounds,
            AbortableIterationConsumer<?> consumer,
            Operation<Void> original,
            @Local(ordinal = 1) SubLevel subLevel
    ) {
        AABB clipped = PlotScanBounds.clipEntityQuery(subLevel, bounds);
        if (clipped != null) original.call(delegate, type, clipped, consumer);
    }
}
