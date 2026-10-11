package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.block.PortableSubspaceCompressorBlockEntity;
import com.misterblusky9.pocket.block.StaticSubspaceCompressorBlockEntity;
import com.misterblusky9.pocket.compression.PersonalLock;
import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pocket.item.ScaleToolModifier;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleDriver;
import com.misterblusky9.pym.api.spi.ResizeFollower;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

import java.util.List;

final class TrackingResizeFollower implements ResizeFollower {
    private static final double SEARCH_MARGIN = 3.0D;

    @Override
    public List<Entity> followers(final ServerSubLevel subLevel) {
        return followers(subLevel, null);
    }

    @Override
    public List<Entity> followers(final ServerSubLevel subLevel, final ScaleDriver driver) {
        if (!DeviceRanges.resizeEntitiesOnCrafts()) return List.of();
        if (subLevel == null || subLevel.isRemoved() || !(subLevel.getLevel() instanceof final ServerLevel level)) {
            return List.of();
        }

        final var bounds = subLevel.boundingBox();
        final AABB search = new AABB(
                bounds.minX() - SEARCH_MARGIN, bounds.minY() - SEARCH_MARGIN, bounds.minZ() - SEARCH_MARGIN,
                bounds.maxX() + SEARCH_MARGIN, bounds.maxY() + SEARCH_MARGIN, bounds.maxZ() + SEARCH_MARGIN);

        return level.getEntities((Entity) null, search, entity -> follows(entity, subLevel, driver));
    }

    @Override
    public ScaleBounds bounds(final Entity entity) {
        return entity instanceof Player
                ? DeviceRanges.of(DeviceRanges.Device.PERSONAL_COMPRESSOR)
                : ScaleBounds.ANY;
    }

    private static boolean follows(final Entity entity, final ServerSubLevel subLevel, final ScaleDriver driver) {
        if (entity == null || !entity.isAlive() || entity.isPassenger() || entity.isSpectator()) return false;
        if (entity instanceof Projectile) return false;
        if (entity instanceof final Player player) {
            final boolean compressor = driver instanceof StaticSubspaceCompressorBlockEntity
                    || driver instanceof PortableSubspaceCompressorBlockEntity;
            if ((!compressor && !ScaleToolModifier.follows(player))
                    || PersonalLock.blocksResize(driver, player)) return false;
        }
        if (Sable.HELPER.getTrackingSubLevel(entity) != subLevel) return false;
        return Pym.entities().supports(entity);
    }
}
