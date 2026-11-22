package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.create.SchematicGrid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SchematicTransformation.class, remap = false)
public abstract class SchematicTransformationGridMixin implements SchematicGrid.Placement {
    @Shadow private Vec3 chasingPos;
    @Shadow private Vec3 prevChasingPos;
    @Shadow private BlockPos target;
    @Shadow public abstract BlockPos fromAnchor(BlockPos pos);
    @Unique private Vec3 pym$fine = Vec3.ZERO;
    @Unique private Vec3 pym$shown = Vec3.ZERO;
    @Unique private Vec3 pym$prevShown = Vec3.ZERO;
    @Unique private double pym$placementRatio = Double.NaN;

    @Unique
    private double pym$ratio() {
        return Double.isNaN(this.pym$placementRatio) ? SchematicGrid.ratio(this.target) : this.pym$placementRatio;
    }

    @Unique
    private Vec3 pym$corner() {
        return Vec3.atLowerCornerOf(this.target).add(this.pym$fine);
    }

    @Unique
    private void pym$settle(Vec3 corner) {
        this.target = BlockPos.containing(corner);
        this.pym$fine = corner.subtract(Vec3.atLowerCornerOf(this.target));
    }

    @Unique
    private Vec3 pym$drift() {
        float pt = AnimationTickHolder.getPartialTicks();
        return VecHelper.lerp(pt, this.pym$prevShown, this.pym$shown).subtract(VecHelper.lerp(pt, this.prevChasingPos, this.chasingPos));
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false, require = 1)
    private void pym$startInOwnGrid(BlockPos anchor, StructurePlaceSettings settings, AABB bounds, CallbackInfo ci) {
        this.pym$fine = Vec3.ZERO;
        double ratio = SchematicGrid.ratio(anchor);
        this.pym$placementRatio = ratio;
        if (ratio != ScaleBounds.FULL) {
            Vec3 cells = Vec3.atLowerCornerOf(anchor.subtract(this.target));
            pym$settle(Vec3.atLowerCornerOf(anchor).add(SchematicGrid.fine()).subtract(cells.scale(ratio)));
            this.chasingPos = Vec3.atLowerCornerOf(this.target);
            this.prevChasingPos = this.chasingPos;
        }
        this.pym$shown = pym$corner();
        this.pym$prevShown = this.pym$shown;
    }

    @Override
    public void pym$placeAt(Vec3 corner, double ratio) {
        this.pym$placementRatio = ratio;
        pym$settle(corner);
        this.chasingPos = Vec3.atLowerCornerOf(this.target);
        this.prevChasingPos = this.chasingPos;
        this.pym$shown = pym$corner();
        this.pym$prevShown = this.pym$shown;
    }

    @Override
    public void pym$placeFull() {
        this.pym$placementRatio = ScaleBounds.FULL;
        this.pym$fine = Vec3.ZERO;
        this.pym$shown = pym$corner();
        this.pym$prevShown = this.pym$shown;
    }

    @Inject(method = "startAt", at = @At("HEAD"), remap = false, require = 1)
    private void pym$restartFine(BlockPos pos, CallbackInfo ci) {
        this.pym$placementRatio = SchematicGrid.ratio(pos);
        this.pym$fine = Vec3.ZERO;
        this.pym$shown = Vec3.atLowerCornerOf(pos);
        this.pym$prevShown = this.pym$shown;
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void pym$stepInOwnGrid(int x, int y, int z, CallbackInfo ci) {
        double ratio = pym$ratio();
        if (ratio == ScaleBounds.FULL) return;
        pym$settle(pym$corner().add(x * ratio, y * ratio, z * ratio));
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false, require = 1)
    private void pym$chaseFine(CallbackInfo ci) {
        this.pym$prevShown = this.pym$shown;
        this.pym$shown = VecHelper.lerp(0.45F, this.pym$shown, pym$corner());
    }

    @Inject(method = "getAnchor", at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private void pym$anchorInOwnGrid(CallbackInfoReturnable<BlockPos> cir) {
        double ratio = pym$ratio();
        if (ratio != ScaleBounds.FULL) cir.setReturnValue(BlockPos.containing(pym$anchor(ratio)));
    }

    @Unique
    private Vec3 pym$anchor(double ratio) {
        Vec3 cells = Vec3.atLowerCornerOf(BlockPos.ZERO.subtract(fromAnchor(BlockPos.ZERO)));
        return pym$corner().add(cells.scale(ratio));
    }

    @Override
    public Vec3 pym$anchorFine() {
        double ratio = pym$ratio();
        if (ratio == ScaleBounds.FULL) return Vec3.ZERO;
        Vec3 anchor = pym$anchor(ratio);
        return anchor.subtract(Vec3.atLowerCornerOf(BlockPos.containing(anchor)));
    }

    @Inject(
            method = "applyTransformations",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0),
            remap = false,
            require = 1
    )
    private void pym$drawInOwnGrid(PoseStack ms, Vec3 camera, CallbackInfo ci) {
        float ratio = (float) pym$ratio();
        if (ratio == 1.0F) return;
        Vec3 drift = pym$drift();
        ms.translate(drift.x, drift.y, drift.z);
        ms.scale(ratio, ratio, ratio);
    }

    @WrapOperation(
            method = "toLocalSpace",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0
            ),
            remap = false,
            require = 1
    )
    private Vec3 pym$aimInOwnGrid(Vec3 point, Vec3 origin, Operation<Vec3> original) {
        Vec3 local = original.call(point, origin);
        double ratio = pym$ratio();
        return ratio == ScaleBounds.FULL ? local : local.subtract(pym$drift()).scale(1.0D / ratio);
    }
}
