package com.misterblusky9.pym.internal.compat.create;

import com.misterblusky9.pym.internal.client.ColliderOutlines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.collision.CollisionList;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class ContraptionColliderOutlines {
    public static void register() {
        ColliderOutlines.extend(ContraptionColliderOutlines::draw);
    }

    private static void draw(
            final Minecraft minecraft,
            final Vec3 camera,
            final float partialTick,
            final PoseStack poseStack,
            final VertexConsumer lines
    ) {
        final Vector3d[] corners = ColliderOutlines.corners();
        final Vector3d center = new Vector3d();
        for (final Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof final AbstractContraptionEntity contraptionEntity) || contraptionEntity.isRemoved()) continue;

            final SubLevel raw = Sable.HELPER.getContaining(contraptionEntity);
            final Pose3dc pose = raw instanceof final ClientSubLevel subLevel && !subLevel.isRemoved()
                    ? subLevel.renderPose(partialTick)
                    : null;
            final Vec3 position = contraptionEntity.position();
            center.set(position.x, position.y, position.z);
            if (pose != null) pose.transformPosition(center);
            if (camera.distanceToSqr(center.x, center.y, center.z) > ColliderOutlines.RANGE * ColliderOutlines.RANGE) continue;

            final Contraption contraption = contraptionEntity.getContraption();
            final CollisionList colliders = contraption == null ? null : contraption.getSimplifiedEntityColliders();
            if (colliders == null) continue;

            for (int index = 0; index < colliders.size; index++) {
                for (int i = 0; i < corners.length; i++) {
                    final Vec3 global = contraptionEntity.toGlobalVector(new Vec3(
                            colliders.centerX[index] + ((i & 1) == 0 ? -colliders.extentsX[index] : colliders.extentsX[index]),
                            colliders.centerY[index] + ((i & 2) == 0 ? -colliders.extentsY[index] : colliders.extentsY[index]),
                            colliders.centerZ[index] + ((i & 4) == 0 ? -colliders.extentsZ[index] : colliders.extentsZ[index])),
                            partialTick);
                    corners[i].set(global.x, global.y, global.z);
                    if (pose != null) pose.transformPosition(corners[i]);
                    corners[i].sub(camera.x, camera.y, camera.z);
                }
                ColliderOutlines.box(poseStack, lines, corners, 0.0F, 1.0F, 1.0F);
            }
        }
    }

    private ContraptionColliderOutlines() {}
}
