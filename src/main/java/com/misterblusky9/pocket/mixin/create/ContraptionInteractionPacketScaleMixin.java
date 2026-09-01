package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ContraptionInteractionPacket.class, remap = false)
public abstract class ContraptionInteractionPacketScaleMixin {

    @Redirect(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D",
                    remap = true
            ),
            require = 1
    )
    private double pocket$projectedDistanceToContraption(
            final ServerPlayer player,
            final Entity target
    ) {
        if (!(target instanceof AbstractContraptionEntity contraptionEntity)) {
            return player.distanceToSqr(target);
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(contraptionEntity);
        if (subLevel == null || subLevel.isRemoved()) {
            return player.distanceToSqr(target);
        }

        final Vector3dc scale = subLevel.logicalPose().scale();
        if (!PocketSized.isValidScale(scale.x())) {
            return player.distanceToSqr(target);
        }

        return player.position().distanceToSqr(
                subLevel.logicalPose().transformPosition(contraptionEntity.position())
        );
    }
}