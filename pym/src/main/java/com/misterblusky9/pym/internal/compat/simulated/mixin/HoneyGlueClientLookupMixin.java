package com.misterblusky9.pym.internal.compat.simulated.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueClientHandler;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = HoneyGlueClientHandler.class, remap = false)
public abstract class HoneyGlueClientLookupMixin {
    @Unique
    private ClientLevel pym$glueLevel;

    @Unique
    private long pym$glueTick = Long.MIN_VALUE;

    @Unique
    private List<HoneyGlueEntity> pym$glues = List.of();

    @Redirect(
            method = {"updateHovered", "renderHoneyGlue"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/content/entities/honey_glue/HoneyGlueClientHandler;getHoneyGlue(Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;"
            ),
            remap = false,
            require = 2
    )
    private List<HoneyGlueEntity> pym$getHoneyGlue(Player player) {
        if (!(player.level() instanceof ClientLevel level)) return List.of();

        long tick = level.getGameTime();
        if (this.pym$glueLevel == level && this.pym$glueTick == tick) return this.pym$glues;

        double range = SimConfigService.INSTANCE.server().assembly.honeyGlueRange.get();
        BoundingBox3d search = new BoundingBox3d(player.getBoundingBox()).expand(range);
        SubLevel playerSubLevel = Sable.HELPER.getContainingClient(player);
        if (playerSubLevel != null) search.transform(playerSubLevel.logicalPose());

        ArrayList<HoneyGlueEntity> found = new ArrayList<>();
        BoundingBox3d bounds = new BoundingBox3d();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof HoneyGlueEntity glue)) continue;

            bounds.set(glue.getBoundingBox());
            SubLevel subLevel = Sable.HELPER.getContainingClient(glue);
            if (subLevel != null) bounds.transform(subLevel.logicalPose());

            if (bounds.intersects(search)) found.add(glue);
        }

        this.pym$glueLevel = level;
        this.pym$glueTick = tick;
        this.pym$glues = found;
        return found;
    }
}
