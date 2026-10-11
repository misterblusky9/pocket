package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulated.AssemblyGlueIndex;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SimAssemblyContraption.class, remap = false)
public abstract class SimAssemblyGlueLookupMixin implements AssemblyGlueIndex.Owner {
    @Unique
    private AssemblyGlueIndex pym$glueIndex;

    @Override
    public @Nullable AssemblyGlueIndex pym$glueIndex(final ServerLevel level) {
        if (this.pym$glueIndex == null) {
            this.pym$glueIndex = new AssemblyGlueIndex(level);
        }
        return this.pym$glueIndex.level() == level ? this.pym$glueIndex : null;
    }

    @WrapOperation(
            method = "checkAndCacheGlue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass("
                            + "Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            ),
            remap = false,
            require = 2
    )
    private <T extends Entity> List<T> pym$indexedGlueLookup(
            final LevelAccessor level,
            final Class<T> type,
            final AABB box,
            final Operation<List<T>> original
    ) {
        return pym$lookup(this, level, type, box, original);
    }

    @WrapOperation(
            method = "addInitialHoneyGlue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass("
                            + "Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            ),
            remap = false,
            require = 1
    )
    private static <T extends Entity> List<T> pym$indexedInitialHoneyGlueLookup(
            final Level level,
            final Class<T> type,
            final AABB box,
            final Operation<List<T>> original,
            @Local(argsOnly = true) final SimAssemblyContraption contraption
    ) {
        return pym$lookup(contraption, level, type, box, original);
    }

    @Unique
    private static <T extends Entity> List<T> pym$lookup(
            final Object contraption,
            final LevelAccessor level,
            final Class<T> type,
            final AABB box,
            final Operation<List<T>> original
    ) {
        if (level instanceof final ServerLevel serverLevel) {
            final AssemblyGlueIndex index = ((AssemblyGlueIndex.Owner) contraption).pym$glueIndex(serverLevel);
            if (index != null) return index.query(type, box);
        }
        return original.call(level, type, box);
    }
}
