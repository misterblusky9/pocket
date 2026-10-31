package com.misterblusky9.pym.internal.mixin.collision;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.internal.collision.SubLevelIndexedCollision;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerCollisionProbeMixin {
    @WrapMethod(method = "maybeBackOffFromEdge")
    private Vec3 pym$probeEdge(Vec3 movement, MoverType type, Operation<Vec3> original) {
        return SubLevelIndexedCollision.probing((Player) (Object) this, () -> original.call(movement, type));
    }

    @WrapMethod(method = "canFallAtLeast")
    private boolean pym$probeFall(double x, double z, float distance, Operation<Boolean> original) {
        return SubLevelIndexedCollision.probing((Player) (Object) this, () -> original.call(x, z, distance));
    }

    @WrapMethod(method = "canPlayerFitWithinBlocksAndEntitiesWhen")
    private boolean pym$probeFit(Pose pose, Operation<Boolean> original) {
        return SubLevelIndexedCollision.probing((Player) (Object) this, () -> original.call(pose));
    }
}
