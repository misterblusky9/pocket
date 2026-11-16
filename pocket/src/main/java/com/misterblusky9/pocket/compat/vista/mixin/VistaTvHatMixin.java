package com.misterblusky9.pocket.compat.vista.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.compat.vista.VistaHatFeed;
import com.misterblusky9.pym.api.client.Portrait;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.moonlight.api.client.util.LOD;
import net.mehvahdjukaar.vista.common.tv.TVBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.common.tv.PowerState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vista's world-space LOD and non-persisted tick state do not suit hat portraits. */
@Mixin(targets = "net.mehvahdjukaar.vista.client.renderer.TvBlockEntityRenderer", remap = false)
public abstract class VistaTvHatMixin {
    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/vista/common/tv/TVBlockEntity;isScreenOn(F)Z"),
            remap = false)
    private boolean pocket$screenOnInPortrait(TVBlockEntity tv, float partialTick,
                                              Operation<Boolean> original) {
        if (original.call(tv, partialTick)) return true;
        // wasScreenOn is rebuilt in TVBlockEntity.onTick, but detached hat TVs
        // may never get a BE tick. Use the real powered state, not a forced ON.
        return Portrait.drawing()
                && tv.getBlockState().getValue(TVBlock.POWER_STATE).isOn();
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/moonlight/api/client/util/LOD;at(Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/mehvahdjukaar/moonlight/api/client/util/LOD;"),
            remap = false)
    private LOD pocket$portraitLod(BlockEntity tv, Operation<LOD> original) {
        // ZERO-distance LOD suppresses plot-space distance / view culling.
        // No change to TVs rendered in a regular world or Sable level.
        return Portrait.drawing() ? LOD.MAX : original.call(tv);
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/vista/common/tv/TVBlockEntity;getVideoSource()Lnet/mehvahdjukaar/vista/client/video_source/IVideoSource;"),
            remap = false)
    private IVideoSource pocket$idleHatShowsBars(TVBlockEntity tv, Operation<IVideoSource> original) {
        return VistaHatFeed.unavailable(tv) ? IVideoSource.EMPTY : original.call(tv);
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/moonlight/api/client/util/LOD;isPlaneCulled(Lnet/minecraft/core/Direction;FFF)Z"),
            remap = false)
    private boolean pocket$portraitNeverCulled(LOD lod, Direction facing, float width, float height,
                                               float depth, Operation<Boolean> original) {
        return !Portrait.drawing() && original.call(lod, facing, width, height, depth);
    }
}
