package com.misterblusky9.pocket.compat.vista.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.client.BuildMirrors;
import net.minecraft.core.BlockPos;
import com.misterblusky9.pocket.client.HeadLayerClip;
import com.misterblusky9.pocket.client.WornHatFrame;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import com.misterblusky9.pocket.compat.vista.VistaHatFeed;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VistaLevelRenderer.class, remap = false)
public abstract class VistaFeedHatCameraMixin {
    @Unique
    private static @Nullable ClientSubLevel pocket$part(ViewFinderBlockEntity shown) {
        return shown.getLevel() != null && Sable.HELPER.getContaining(shown.getLevel(), shown.getBlockPos())
                instanceof ClientSubLevel part ? part : null;
    }

    @WrapOperation(
            method = "setupSceneCamera",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/vista/common/view_finder/ViewFinderBlockEntity;getGlobalPosition(F)Lnet/minecraft/world/phys/Vec3;"),
            remap = false)
    private static Vec3 pocket$feedFromShownHat(ViewFinderBlockEntity camera, float partialTick,
                                                Operation<Vec3> original) {
        ViewFinderBlockEntity shown = VistaHatFeed.shown(camera);
        Vec3 position = original.call(shown, partialTick);
        ClientSubLevel part = pocket$part(shown);
        Pose3d exact = part == null ? null : BuildMirrors.wornFrame(part, partialTick);
        if (exact == null) {
            BlockPos host = part == null ? null : BuildMirrors.standHost(part);
            if (host != null && shown.getLevel().getBlockEntity(host) instanceof HatStandBlockEntity stand) {
                Vec3 centre = stand.headCentre(partialTick).add(host.getX(), host.getY(), host.getZ());
                Vector3d head = new Vector3d(position.x - centre.x, position.y - centre.y, position.z - centre.z);
                stand.orientation(partialTick).conjugate(new Quaterniond()).transform(head).mul(16.0);
                HeadLayerClip.begin(stand, head);
            }
            return position;
        }
        Vec3 lens = exact.transformPosition(part.renderPose(partialTick).transformPositionInverse(position));
        Player wearer = BuildMirrors.wearerOf(part);
        ClientSubLevel root = BuildMirrors.wornRootOf(part);
        Vector3d head = wearer == null || root == null ? null
                : WornHatFrame.headPixels(root, wearer, new Vector3d(lens.x, lens.y, lens.z), partialTick);
        if (head != null) HeadLayerClip.begin(wearer, head);
        return lens;
    }

    @WrapOperation(
            method = "doRender",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/vista/client/renderer/VistaLevelRenderer;renderLevel(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V"),
            remap = false)
    private static void pocket$endHeadClip(Minecraft minecraft, RenderTarget target, Camera camera, float fov,
                                           Matrix4f projection, Operation<Void> original) {
        try {
            original.call(minecraft, target, camera, fov, projection);
        } finally {
            HeadLayerClip.end();
        }
    }

    @WrapOperation(
            method = "setupSceneCamera",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/vista/common/view_finder/ViewFinderBlockEntity;getWorldOrientation(F)Lorg/joml/Quaternionf;"),
            remap = false)
    private static Quaternionf pocket$feedTurnsWithShownHat(ViewFinderBlockEntity camera, float partialTick,
                                                            Operation<Quaternionf> original) {
        ViewFinderBlockEntity shown = VistaHatFeed.shown(camera);
        Quaternionf orientation = original.call(shown, partialTick);
        ClientSubLevel part = pocket$part(shown);
        Pose3d exact = part == null ? null : BuildMirrors.wornFrame(part, partialTick);
        if (exact == null) return orientation;
        Pose3dc drawn = part.renderPose(partialTick);
        Quaterniond correction = new Quaterniond(exact.orientation())
                .mul(new Quaterniond(drawn.orientation()).invert());
        return new Quaternionf(correction.mul(new Quaterniond(orientation)));
    }
}
