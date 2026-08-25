package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2500)
public abstract class BlockDamageDecalScaleMixin {
    @Shadow @Nullable private ClientLevel level;

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;last()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;",
                    shift = At.Shift.BEFORE
            )
    )
    private void pocket$scaleBlockDamageDecal(
            final DeltaTracker deltaTracker,
            final boolean renderBlockOutline,
            final Camera camera,
            final GameRenderer gameRenderer,
            final LightTexture lightTexture,
            final Matrix4f frustumMatrix,
            final Matrix4f projectionMatrix,
            final CallbackInfo ci,
            @Local(ordinal = 0) final PoseStack poseStack,
            @Local(ordinal = 0) final BlockPos pos
    ) {
        if (this.level == null) return;

        final Vec3 plotPos = new Vec3(pos.getX(), pos.getY(), pos.getZ());
        if (!(Sable.HELPER.getContaining(this.level, (Position) plotPos) instanceof final ClientSubLevel subLevel)) {
            return;
        }
        if (subLevel.isRemoved()) return;

        final Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.y() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.z() - 1.0D) <= PocketSized.EPSILON) {
            return;
        }

        poseStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }
}
