package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.PocketBeamColours;
import com.misterblusky9.pocket.client.PocketLaserBeamColour;
import com.misterblusky9.pym.api.client.LineEmitter;
import com.simibubi.create.content.equipment.zapper.ZapperRenderHandler;
import net.createmod.catnip.outliner.Outline;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ZapperRenderHandler.class, remap = false)
public abstract class ZapperBeamColourMixin {
    @Inject(method = "addBeam", at = @At("HEAD"), require = 1)
    private void pocket$tagBeamColour(
            ZapperRenderHandler.LaserBeam beam,
            CallbackInfo ci
    ) {
        if (beam instanceof PocketLaserBeamColour coloured) {
            PocketBeamColours.Pending tag = PocketBeamColours.take(coloured.pocket$end());
            coloured.pocket$colour(tag.colour());
            coloured.pocket$shooter(tag.shooter());
        }
    }

    @ModifyConstant(
            method = "addBeam",
            constant = @Constant(intValue = 10),
            require = 1
    )
    private int pocket$impactParticleCount(int original) {
        return 3;
    }

    @ModifyConstant(
            method = "lambda$tick$1(Lcom/simibubi/create/content/equipment/zapper/ZapperRenderHandler$LaserBeam;)V",
            constant = @Constant(intValue = 0xFFFFFF),
            require = 1
    )
    private static int pocket$beamColour(
            int original,
            @Local(argsOnly = true) ZapperRenderHandler.LaserBeam beam
    ) {
        return beam instanceof PocketLaserBeamColour coloured ? coloured.pocket$colour() : original;
    }

    @WrapOperation(
            method = "lambda$tick$1(Lcom/simibubi/create/content/equipment/zapper/ZapperRenderHandler$LaserBeam;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;lineWidth(F)Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            require = 1
    )
    private static Outline.OutlineParams pocket$beamFiredByShooter(
            Outline.OutlineParams params,
            float width,
            Operation<Outline.OutlineParams> original,
            @Local(argsOnly = true) ZapperRenderHandler.LaserBeam beam
    ) {
        Outline.OutlineParams result = original.call(params, width);
        if (!(beam instanceof PocketLaserBeamColour tagged) || tagged.pocket$shooter() < 0) return result;
        var level = Minecraft.getInstance().level;
        return LineEmitter.firedBy(result, level == null ? null : level.getEntity(tagged.pocket$shooter()));
    }
}
