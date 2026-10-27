package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageRenderScale;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.client.ConnectionPreviewRenderer", remap = false)
public abstract class ConnectionPreviewScaleMixin {
    @Inject(method = "render", at = {@At("HEAD"), @At("RETURN")}, remap = false)
    private static void pym$resetPreviewScale(CallbackInfo ci) {
        LinkageRenderScale.resetPreview();
    }

    @ModifyExpressionValue(method = {"renderUniversalPreview", "renderHydraulicPreview"}, at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/client/ConnectionPreviewRenderer;resolve("
                    + "Lnet/minecraft/world/level/Level;Lcom/enxv/aerouniversaljoint/content/JointBindingData$Selection;)"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;"), remap = false)
    private static BlockEntity pym$previewStartScale(BlockEntity start) {
        if (start != null) LinkageRenderScale.previewStart(LinkageRenderScale.of(start));
        return start;
    }

    @ModifyExpressionValue(method = {"renderUniversalPreview", "renderHydraulicPreview"}, at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/util/SubLevelReferenceHelper;resolveBlockEntity("
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/UUID;)"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;"), remap = false)
    private static BlockEntity pym$previewTargetScale(
            BlockEntity target, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockHitResult hit
    ) {
        LinkageRenderScale.previewTarget(target != null
                ? LinkageRenderScale.of(target)
                : LinkageRenderScale.of(Sable.HELPER.getContaining(level, hit.getBlockPos())));
        return target;
    }

    @WrapOperation(method = {"renderUniversalPreview", "renderHydraulicPreview"}, at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/client/ConnectionPreviewRenderer;isBeyondWarningLimit("
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;D)Z"), remap = false)
    private static boolean pym$scaledWarningLimit(Vec3 start, Vec3 end, double limit, Operation<Boolean> original) {
        double scale = LinkageRenderScale.previewPair();
        return original.call(start, end, limit * scale);
    }
}
