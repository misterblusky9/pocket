package com.misterblusky9.pocket.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

public final class ContraptionSightline {
    private static final double SURFACE_BACKOFF = 1.0E-3D;

    public static boolean blocks(final AbstractContraptionEntity entity, final Player player, final Vec3 plotHit) {
        if (entity == null || !entity.isAlive() || entity.getContraption() == null || plotHit == null) return false;

        final Vec3 eye = player.getEyePosition();
        final Vector3d plotEye = new Vector3d(eye.x, eye.y, eye.z);
        final SubLevel containing = Sable.HELPER.getContaining(entity);
        if (containing != null) {
            if (containing.isRemoved()) return false;
            containing.logicalPose().transformPositionInverse(plotEye);
        }

        final Vec3 from = entity.toLocalVector(new Vec3(plotEye.x, plotEye.y, plotEye.z), 1.0F);
        final Vec3 hit = entity.toLocalVector(plotHit, 1.0F);
        final Vec3 reach = hit.subtract(from);
        if (reach.lengthSqr() < SURFACE_BACKOFF * SURFACE_BACKOFF) return false;
        return between(entity.getContraption(), from, hit.subtract(reach.normalize().scale(SURFACE_BACKOFF)));
    }

    public static boolean between(final Contraption contraption, final Vec3 from, final Vec3 to) {
        final RaycastHelper.PredicateTraceResult result = RaycastHelper.rayTraceUntil(from, to, pos -> {
            final StructureTemplate.StructureBlockInfo info = contraption.getBlocks().get(pos);
            if (info == null) return false;
            final VoxelShape shape = info.state().getShape(contraption.getContraptionWorld(), pos);
            return !shape.isEmpty() && shape.clip(from, to, pos) != null;
        });
        return result != null && !result.missed();
    }

    private ContraptionSightline() {}
}
