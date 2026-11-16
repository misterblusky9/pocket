package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.advancement.HatAdvancements;
import com.misterblusky9.pocket.build.CarriedBuilds;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(ServerLevel.class)
public abstract class HatLightningRodMixin {
    @Unique
    private static final double POCKET$ROD_RANGE = 128.0D * 128.0D;

    @ModifyReturnValue(method = "findLightningRod", at = @At("RETURN"))
    private Optional<BlockPos> pocket$wornRods(Optional<BlockPos> rod, @Local(argsOnly = true) BlockPos near) {
        ServerLevel level = (ServerLevel) (Object) this;
        BlockPos best = rod.orElse(null);
        double closest = best == null ? Double.MAX_VALUE : best.distSqr(near);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            BlockPos head = BlockPos.containing(player.getX(), player.getBoundingBox().maxY, player.getZ());
            double distance = head.distSqr(near);
            if (distance > POCKET$ROD_RANGE || distance >= closest || !level.canSeeSky(head)) continue;
            ServerSubLevel hat = CarriedBuilds.worn(player);
            if (hat == null || !HatAdvancements.contains(hat, Blocks.LIGHTNING_ROD)) continue;
            best = head;
            closest = distance;
        }
        return Optional.ofNullable(best);
    }
}
