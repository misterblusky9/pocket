package com.misterblusky9.pym.internal.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class PlayerPoseFitScaleMixin {
    @Unique private static final double PYM$SKIN_CRAFT_BLOCKS = 1.0E-4D;
    @Unique private static final double PYM$SKIN_MIN_WORLD = 1.0E-5D;

    @WrapMethod(method = "canPlayerFitWithinBlocksAndEntitiesWhen")
    private boolean pym$ignoreRestingContact(final Pose pose, final Operation<Boolean> original) {
        if (original.call(pose)) return true;
        final Player self = (Player) (Object) this;
        final double skin = pym$restingSkin(self);
        if (skin <= 0.0D) return false;
        final AABB box = self.getDimensions(pose).makeBoundingBox(self.position()).deflate(1.0E-7D);
        return self.level().noCollision(self, box.setMinY(box.minY + skin));
    }

    @Unique
    private static double pym$restingSkin(final Player player) {
        final AABB box = player.getBoundingBox();
        final AABB under = new AABB(box.minX, box.minY - 0.05D, box.minZ, box.maxX, box.minY + 0.05D, box.maxZ);
        final boolean fullPlayer = ScaleBounds.same(Pym.entities().scaleOf(player), ScaleBounds.FULL);
        double skin = 0.0D;
        for (final SubLevel craft : Sable.HELPER.getAllIntersecting(player.level(), new BoundingBox3d(under))) {
            final double scale = Pym.scale().of(craft);
            if (!ScaleBounds.isValid(scale) || fullPlayer && ScaleBounds.same(scale, ScaleBounds.FULL)) continue;
            skin = Math.max(skin, Math.max(PYM$SKIN_MIN_WORLD, PYM$SKIN_CRAFT_BLOCKS * scale));
        }
        return skin;
    }
}
