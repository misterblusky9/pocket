package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.client.TaperedLinkProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.spring.SpringRenderer", remap = false)
public abstract class SpringTaperMixin {
    @Unique private TaperedLinkProfile pym$profile = TaperedLinkProfile.UNIFORM;
    @Unique private double pym$startWidth = 1.0;
    @Unique private double pym$endWidth = 1.0;
    @Unique private static double pym$localScale = 1.0;

    @Inject(method = "renderSafe", at = @At("HEAD"), remap = false, require = 1)
    private void pym$prepareTaper(
            SpringBlockEntity spring,
            float partialTicks,
            PoseStack ps,
            MultiBufferSource buffers,
            int light,
            int overlay,
            CallbackInfo ci
    ) {
        this.pym$profile = TaperedLinkProfile.UNIFORM;
        pym$localScale = 1.0;

        if (!spring.isController() || spring.getPairedSpring() == null) return;

        ClientSubLevel owner = Sable.HELPER.getContainingClient(spring);
        UUID otherId = spring.getPartnerSubLevelID();
        ClientSubLevel other = null;

        if (otherId != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            ClientSubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
            if (container == null) return;
            other = (ClientSubLevel) container.getSubLevel(otherId);
            if (other == null) return;
        }

        pym$localScale = Pym.scale().linkFactorLocal(owner, other);
        this.pym$profile = TaperedLinkProfile.between(
                owner, other, Math.max(1.0E-6D, (spring.getRenderLength(partialTicks) - 0.75D) * pym$localScale));
    }

    @WrapOperation(method = "renderSafe", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3dc;transformNormal(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"), remap = false)
    private Vector3d pym$unitNormal(Pose3dc pose, Vector3d normal, Operation<Vector3d> original) {
        return original.call(pose, normal).normalize();
    }

    @WrapOperation(method = "renderSafe", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3dc;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"), remap = false)
    private Vector3d pym$unitNormalInverse(Pose3dc pose, Vector3d normal, Operation<Vector3d> original) {
        return original.call(pose, normal).normalize();
    }

    @WrapOperation(method = "renderSafe", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/content/blocks/spring/SpringBlockEntity;getRenderLength(F)D"), remap = false)
    private double pym$localRenderLength(SpringBlockEntity spring, float pt, Operation<Double> original) {
        return pym$local(original.call(spring, pt));
    }

    @WrapOperation(method = "getStressColor", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/content/blocks/spring/SpringBlockEntity;getRenderLength(F)D"), remap = false)
    private static double pym$localStressLength(SpringBlockEntity spring, float pt, Operation<Double> original) {
        return pym$local(original.call(spring, pt));
    }

    @Unique
    private static double pym$local(double renderLength) {
        return (renderLength - 0.75) * pym$localScale + 0.75;
    }

    @WrapOperation(method = "getStressColor", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/content/blocks/spring/SpringBlockEntity;getSnappingDistance()D"), remap = false)
    private static double pym$localSnappingDistance(SpringBlockEntity spring, Operation<Double> original) {
        return original.call(spring) * pym$localScale;
    }

    @Inject(method = "renderSegment", at = @At("HEAD"), remap = false, require = 1)
    private void pym$prepareSegment(
            PoseStack ps,
            Vector3dc startDirection,
            Vector3dc endDirection,
            Vector3dc startUp,
            Vector3dc endUp,
            Vector3dc startPos,
            Vector3dc endPos,
            boolean inside,
            float uvStart,
            float uvEnd,
            int light,
            int color,
            VertexConsumer buffer,
            float width,
            float textureWidth,
            CallbackInfo ci
    ) {
        this.pym$startWidth = this.pym$profile.radiusAtV(uvStart);
        this.pym$endWidth = this.pym$profile.radiusAtV(uvEnd);
    }

    @ModifyArg(method = "renderSegment", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;mul(D)Lorg/joml/Vector3d;", ordinal = 0), index = 0, remap = false, require = 1)
    private double pym$scaleStartLeft(double scale) {
        return scale * this.pym$startWidth;
    }

    @ModifyArg(method = "renderSegment", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3dc;mul(DLorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 0), index = 0, remap = false, require = 1)
    private double pym$scaleStartUp(double scale) {
        return scale * this.pym$startWidth;
    }

    @ModifyArg(method = "renderSegment", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;mul(D)Lorg/joml/Vector3d;", ordinal = 1), index = 0, remap = false, require = 1)
    private double pym$scaleEndLeft(double scale) {
        return scale * this.pym$endWidth;
    }

    @ModifyArg(method = "renderSegment", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3dc;mul(DLorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 1), index = 0, remap = false, require = 1)
    private double pym$scaleEndUp(double scale) {
        return scale * this.pym$endWidth;
    }
}
