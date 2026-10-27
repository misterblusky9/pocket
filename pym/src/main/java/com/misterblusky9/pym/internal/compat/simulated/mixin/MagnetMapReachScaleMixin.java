package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.misterblusky9.pym.internal.compat.simulated.ScaledMagnetField;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetMap;
import dev.simulated_team.simulated.util.SimMovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Mixin(value = MagnetMap.class, remap = false)
public abstract class MagnetMapReachScaleMixin {
    @Shadow @Final public Map<LevelAccessor, Map<SectionPos, HashSet<BlockPos>>> magnetMap;

    @Inject(method = "findNearby", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$widenForLargeMagnets(
            SimMovementContext context,
            CallbackInfoReturnable<List<SimMovementContext>> cir
    ) {
        double scale = ScaledMagnetField.scaleOf(context.subLevel());
        if (!(scale > 1.0D)) return;

        Map<SectionPos, HashSet<BlockPos>> sections = this.magnetMap.get(context.level());
        if (sections == null) {
            cir.setReturnValue(new ArrayList<>());
            return;
        }

        double reach = ScaledMagnetField.REACH * scale;
        Vec3 centre = context.globalPosition();
        int minX = SectionPos.blockToSectionCoord(centre.x - reach);
        int minY = SectionPos.blockToSectionCoord(centre.y - reach);
        int minZ = SectionPos.blockToSectionCoord(centre.z - reach);
        int maxX = SectionPos.blockToSectionCoord(centre.x + reach);
        int maxY = SectionPos.blockToSectionCoord(centre.y + reach);
        int maxZ = SectionPos.blockToSectionCoord(centre.z + reach);

        List<SimMovementContext> contexts = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    HashSet<BlockPos> positions = sections.get(SectionPos.of(x, y, z));
                    if (positions == null) continue;
                    for (BlockPos pos : positions) {
                        if (pos.equals(context.localBlockPos())) continue;
                        contexts.add(SimMovementContext.getMovementContext(context.level(), Vec3.atCenterOf(pos)));
                    }
                }
            }
        }
        cir.setReturnValue(contexts);
    }
}
