package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.misterblusky9.pym.api.Detached;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerLevelPlot.class, remap = false)
public abstract class DetachedPlotLoadMixin {
    @WrapWithCondition(method = "load", remap = false, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;updateMergedMassData(F)V"))
    private boolean pym$skipMergedMass(ServerSubLevel subLevel, float partialTick) {
        return !Detached.isDetached(subLevel);
    }

    @WrapWithCondition(method = "load", remap = false, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicketManager;addTicketForSection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/SectionPos;)V"))
    private boolean pym$skipTicket(PhysicsChunkTicketManager tickets, ServerLevel level, SectionPos section) {
        return !Detached.isDetached(((ServerLevelPlot) (Object) this).getSubLevel());
    }

    @WrapWithCondition(method = "load", remap = false, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;handleChunkSectionAddition(Lnet/minecraft/world/level/chunk/LevelChunkSection;IIIZ)V"))
    private boolean pym$skipSection(PhysicsPipeline pipeline, LevelChunkSection section, int x, int y, int z,
                                    boolean uploadData) {
        return !Detached.isDetached(((ServerLevelPlot) (Object) this).getSubLevel());
    }
}
