package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.client.PocketLaserBeamColour;
import com.misterblusky9.pocket.network.ShrinkRayBeamColourPayload;
import com.simibubi.create.content.equipment.zapper.ZapperRenderHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ZapperRenderHandler.LaserBeam.class, remap = false)
public abstract class ZapperLaserBeamColourMixin implements PocketLaserBeamColour {
    @Shadow Vec3 end;

    @Unique
    private int pocket$colour = ShrinkRayBeamColourPayload.INERT_COLOUR;

    @Override
    public int pocket$colour() {
        return this.pocket$colour;
    }

    @Override
    public void pocket$colour(final int colour) {
        this.pocket$colour = colour;
    }

    @Override
    public Vec3 pocket$end() {
        return this.end;
    }
}
