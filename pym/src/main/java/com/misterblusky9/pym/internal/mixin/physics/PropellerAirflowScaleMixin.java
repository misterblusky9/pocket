package com.misterblusky9.pym.internal.mixin.physics;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = BlockEntityPropeller.class, remap = false)
public interface PropellerAirflowScaleMixin {
    @Overwrite
    default double getAirflowScaling() {
        BlockEntityPropeller propeller = (BlockEntityPropeller) this;
        double airflow = propeller.getAirflow();
        if (Math.abs(airflow) <= 0.001D) return 1.0D;

        Level level = propeller.getLevel();
        SubLevel subLevel = Sable.HELPER.getContaining(level, propeller.getBlockPos());
        if (subLevel == null) return 1.0D;

        Vector3d pos = JOMLConversion.toJOML(propeller.getBlockPos().getCenter());
        Vector3d velocity = Sable.HELPER.getVelocity(level, subLevel, pos, new Vector3d());
        Vector3d thrustDirection = subLevel.logicalPose().orientation().transform(
                JOMLConversion.atLowerCornerOf(propeller.getBlockDirection().getNormal()));
        return Math.clamp((airflow + velocity.dot(thrustDirection)) / airflow, 0.0D, 1.0D);
    }
}
