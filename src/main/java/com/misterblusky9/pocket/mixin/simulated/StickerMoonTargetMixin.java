package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.moon.MoonStickerTarget;
import com.simibubi.create.content.contraptions.chassis.StickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Sable's own sticker logic does the work; this only lets it see the moon.
// Priority 1500 so it applies after Sable's mixin has merged sable$tryAttach and
// sable$tickConstraint into the block entity.
@Mixin(value = StickerBlockEntity.class, priority = 1500)
public abstract class StickerMoonTargetMixin {
    @WrapOperation(
            method = "sable$tryAttach",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"),
            remap = false,
            require = 0
    )
    private BlockHitResult pocket$clipMoon(
            final Level level,
            final ClipContext context,
            final Operation<BlockHitResult> original
    ) {
        final BlockHitResult blocks = original.call(level, context);
        if (!(level instanceof final ServerLevel serverLevel)) return blocks;

        final BlockHitResult moon = MoonStickerTarget.clip(serverLevel, context);
        if (moon == null) return blocks;

        // Blocks win ties: the sticker should grab what it is actually touching.
        if (blocks != null && blocks.getType() != HitResult.Type.MISS) return blocks;
        return moon;
    }

    // The moon has no block face to glue to, so Create's check has to answer for it.
    @WrapOperation(
            method = {"sable$tryAttach", "sable$tickConstraint"},
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/glue/SuperGlueEntity;isValidFace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"),
            remap = false,
            require = 0
    )
    private boolean pocket$moonIsGlueable(
            final Level level,
            final BlockPos pos,
            final Direction direction,
            final Operation<Boolean> original
    ) {
        if (level instanceof final ServerLevel serverLevel
                && MoonStickerTarget.isMoonPosition(serverLevel, pos)) {
            return true;
        }
        return original.call(level, pos, direction);
    }
}
