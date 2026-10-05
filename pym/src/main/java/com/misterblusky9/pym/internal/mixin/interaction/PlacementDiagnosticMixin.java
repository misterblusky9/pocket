package com.misterblusky9.pym.internal.mixin.interaction;

import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class PlacementDiagnosticMixin {
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void pym$reportPlacementGates(
            final BlockPlaceContext context,
            final CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!PymTrace.enabled()) return;

        final Level level = context.getLevel();
        final BlockPos clicked = context.getClickedPos();

        final SubLevel subLevel = Sable.HELPER.getContaining(level, clicked);
        if (subLevel == null || !ScaleState.isScaled(subLevel)) return;

        final BlockState existing = level.getBlockState(clicked);

        PymTrace.scale(
                "placement into {} at plot {} | side={} | clickedFace={} | hit={} | "
                        + "existing={} replaceable={} | ctx.canPlace={} | inWorldBounds={}",
                subLevel.getUniqueId(),
                clicked,
                level.isClientSide() ? "client" : "server",
                context.getClickedFace(),
                context.getClickLocation(),
                existing.getBlock().getName().getString(),
                existing.canBeReplaced(context),
                context.canPlace(),
                level.isInWorldBounds(clicked));
    }

    @Inject(method = "canPlace", at = @At("RETURN"))
    private void pym$reportCanPlace(
            final BlockPlaceContext context,
            final BlockState state,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (!PymTrace.enabled()) return;

        final Level level = context.getLevel();
        final BlockPos clicked = context.getClickedPos();

        final SubLevel subLevel = Sable.HELPER.getContaining(level, clicked);
        if (subLevel == null || !ScaleState.isScaled(subLevel)) return;

        PymTrace.scale(
                "canPlace={} at plot {} | side={} | canSurvive={} | isUnobstructed={}",
                cir.getReturnValue(),
                clicked,
                level.isClientSide() ? "client" : "server",
                state.canSurvive(level, clicked),
                level.isUnobstructed(state, clicked, net.minecraft.world.phys.shapes.CollisionContext.empty()));
    }
}
