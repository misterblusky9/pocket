package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.track.CoasterWrenchCurveAnchorOutlineClient", remap = false)
public abstract class CoasterAnchorSelectionScaleMixin {
    private static final int[][] POCKET$EDGES = {
            {0, 1}, {0, 2}, {0, 4},
            {1, 3}, {1, 5},
            {2, 3}, {2, 6},
            {3, 7},
            {4, 5}, {4, 6},
            {5, 7}, {6, 7}
    };

    @Inject(method = "showAnchorBlockOutline", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void pocket$renderScaledAnchorOutline(
            final Level level,
            final BlockPos pos,
            final String key,
            final int color,
            final CallbackInfo ci
    ) {
        final Outliner outliner = Outliner.getInstance();
        if (!(Sable.HELPER.getContaining(level, pos) instanceof final ClientSubLevel subLevel)) {
            pocket$clear(outliner, key);
            return;
        }

        final Pose3dc pose = subLevel.renderPose();
        final double scale = pose.scale().x();
        if (!PocketSized.isValidScale(scale) || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            pocket$clear(outliner, key);
            return;
        }

        final VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        outliner.remove(key);
        pocket$clear(outliner, key);
        if (shape.isEmpty()) {
            ci.cancel();
            return;
        }

        final AABB box = shape.bounds().move(pos);
        final Vec3[] corners = pocket$corners(box, pose);
        for (int i = 0; i < POCKET$EDGES.length; i++) {
            final int[] edge = POCKET$EDGES[i];
            outliner.showLine(Pair.of("pocket_coaster_anchor_" + key, i), corners[edge[0]], corners[edge[1]])
                    .colored(color)
                    .lineWidth(0.0625F);
        }
        ci.cancel();
    }

    private static Vec3[] pocket$corners(final AABB box, final Pose3dc pose) {
        final Vec3[] result = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            final double x = (i & 4) == 0 ? box.minX : box.maxX;
            final double y = (i & 2) == 0 ? box.minY : box.maxY;
            final double z = (i & 1) == 0 ? box.minZ : box.maxZ;
            result[i] = pose.transformPosition(new Vec3(x, y, z));
        }
        return result;
    }

    private static void pocket$clear(final Outliner outliner, final String key) {
        for (int i = 0; i < POCKET$EDGES.length; i++) {
            outliner.remove(Pair.of("pocket_coaster_anchor_" + key, i));
        }
    }
}
