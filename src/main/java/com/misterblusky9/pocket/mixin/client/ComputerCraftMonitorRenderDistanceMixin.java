package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pocket.client.PocketClientFrame;
import com.misterblusky9.pocket.debug.PocketTrace;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockEntityRenderDispatcher.class, priority = 900)
public abstract class ComputerCraftMonitorRenderDistanceMixin {
    private static final String COMPUTERCRAFT = "computercraft";
    private static final String MONITOR_NORMAL = "monitor_normal";
    private static final String MONITOR_ADVANCED = "monitor_advanced";

    @ModifyExpressionValue(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"
            )
    )
    private boolean pocket$useWorldSpaceMonitorDistance(
            final boolean original,
            final BlockEntity blockEntity,
            final float partialTick,
            final com.mojang.blaze3d.vertex.PoseStack poseStack,
            final MultiBufferSource bufferSource
    ) {
        if (!PocketClientFrame.isInSubLevelBlockEntityPass()) return original;
        if (!pocket$isComputerCraftMonitor(blockEntity)) return original;

        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null || subLevel.isRemoved()) return original;

        final var scaleVector = subLevel.renderPose().scale();
        final double scale = scaleVector.x();
        if (!Double.isFinite(scale) || scale <= 0.0D || Math.abs(scale - 1.0D) <= 1.0E-7D) {
            return original;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final BlockEntityRenderer<?> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        if (renderer == null) return original;

        final int viewDistance = renderer.getViewDistance();
        if (viewDistance <= 0) return false;

        final BlockPos pos = blockEntity.getBlockPos();
        final Vector3d worldCenter = new Vector3d(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        );
        subLevel.renderPose().transformPosition(worldCenter);

        final var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        final double maxDistanceSquared = (double) viewDistance * (double) viewDistance;
        final boolean visible = worldCenter.distanceSquared(camera.x, camera.y, camera.z) <= maxDistanceSquared;

        PocketTrace.render(
                "cc-monitor-distance:" + subLevel.getUniqueId(),
                "CC:T monitor distance uuid={} scale={} configuredDistance={} worldDistance={} localResult={} worldResult={}",
                subLevel.getUniqueId(),
                scale,
                viewDistance,
                Math.sqrt(worldCenter.distanceSquared(camera.x, camera.y, camera.z)),
                original,
                visible
        );

        return visible;
    }

    private static boolean pocket$isComputerCraftMonitor(final BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.getType() == null) return false;

        final ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        if (id == null || !COMPUTERCRAFT.equals(id.getNamespace())) return false;

        final String path = id.getPath();
        return MONITOR_NORMAL.equals(path) || MONITOR_ADVANCED.equals(path);
    }
}
