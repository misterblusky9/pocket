package com.misterblusky9.pocket.mixin.simulated;

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
    private ClientLevel pocket$glueLevel;

    @Unique
    private long pocket$glueTick = Long.MIN_VALUE;

    @Unique
    private List<HoneyGlueEntity> pocket$glues = List.of();

    @Redirect(
            method = {"updateHovered", "renderHoneyGlue"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/content/entities/honey_glue/HoneyGlueClientHandler;getHoneyGlue(Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;"
            ),
            remap = false,
            require = 2
    )
    private List<HoneyGlueEntity> pocket$getHoneyGlue(final Player player) {
        if (!(player.level() instanceof final ClientLevel level)) return List.of();

        final long tick = level.getGameTime();
        if (this.pocket$glueLevel == level && this.pocket$glueTick == tick) return this.pocket$glues;

        final double range = SimConfigService.INSTANCE.server().assembly.honeyGlueRange.get();
        final BoundingBox3d search = new BoundingBox3d(player.getBoundingBox()).expand(range);
        final SubLevel playerSubLevel = Sable.HELPER.getContainingClient(player);
        if (playerSubLevel != null) search.transform(playerSubLevel.logicalPose());

        final ArrayList<HoneyGlueEntity> found = new ArrayList<>();
        final BoundingBox3d bounds = new BoundingBox3d();

        for (final Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof final HoneyGlueEntity glue)) continue;

            bounds.set(glue.getBoundingBox());
            final SubLevel subLevel = Sable.HELPER.getContainingClient(glue);
            if (subLevel != null) bounds.transform(subLevel.logicalPose());

            if (bounds.intersects(search)) found.add(glue);
        }

        this.pocket$glueLevel = level;
        this.pocket$glueTick = tick;
        this.pocket$glues = found;
        return found;
    }
}
