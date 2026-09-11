package com.misterblusky9.pocket.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.persistence.ScalePersistence;
import com.misterblusky9.pocket.physics.SubLevelLoadGuard;
import com.misterblusky9.pocket.scale.ScaleState;
import com.misterblusky9.pocket.scale.SubLevelParentage;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SubLevelSerializer.class, remap = false)
public abstract class SubLevelSerializerScaleMixin {
    @Inject(method = "toData", at = @At("HEAD"), remap = false)
    private static void pocket$persistScaleBeforeSerialize(
            final ServerSubLevel subLevel,
            final java.util.List<java.util.UUID> children,
            final CallbackInfoReturnable<SubLevelData> cir
    ) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        if (!ScaleState.hasServerState(subLevel.getUniqueId())) return;

        final ScaleState.ServerState state = ScaleState.serverState(subLevel);
        ScalePersistence.persist(subLevel, state);
        PocketTrace.scale(
                "serialize snapshot {} scale={} stable={} requested={} transition={}",
                PocketTrace.context(subLevel), state.currentScale(), state.stableStage(),
                state.requestedStage(), state.transitionStage());
    }

    @WrapMethod(method = "fullyLoad")
    private static ServerSubLevel pocket$restoreScaleAfterLoad(
            final ServerLevel level,
            final SubLevelData data,
            final Operation<ServerSubLevel> original
    ) {
        SubLevelLoadGuard.beginLoad();
        final ServerSubLevel subLevel;
        try {
            subLevel = original.call(level, data);
        } finally {
            SubLevelLoadGuard.endLoad();
        }

        if (subLevel != null) {
            SubLevelParentage.restore(subLevel);
            ScalePersistence.restore(subLevel, data.bounds());
        }
        return subLevel;
    }
}
