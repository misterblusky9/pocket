package com.misterblusky9.pocket.mixin.entity;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.PehkuiScaleBridge;
import com.misterblusky9.pocket.entity.PrimedTntScaleAccess;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
public abstract class PrimedTntScaleMixin implements PrimedTntScaleAccess {
    @Unique
    private static final EntityDataAccessor<Float> pocket$LIT_SCALE =
            SynchedEntityData.defineId(PrimedTnt.class, EntityDataSerializers.FLOAT);

    @Unique
    private double pocket$lastDimensionScale = Double.NaN;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void pocket$defineScale(final SynchedEntityData.Builder builder, final CallbackInfo ci) {
        builder.define(pocket$LIT_SCALE, 1.0F);
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("TAIL")
    )
    private void pocket$captureScale(
            final Level level,
            final double x,
            final double y,
            final double z,
            final LivingEntity owner,
            final CallbackInfo ci
    ) {
        final PrimedTnt self = (PrimedTnt) (Object) this;
        self.getEntityData().set(pocket$LIT_SCALE, (float) pocket$currentScale(0.0F, false));
        pocket$lastDimensionScale = Double.NaN;
        self.refreshDimensions();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void pocket$refreshDimensions(final CallbackInfo ci) {
        final PrimedTnt self = (PrimedTnt) (Object) this;
        if (self.isRemoved()) return;

        final double scale = pocket$dimensionScale();
        if (Double.isFinite(pocket$lastDimensionScale)
                && Math.abs(scale - pocket$lastDimensionScale) <= PocketSized.EPSILON) return;

        pocket$lastDimensionScale = scale;
        self.refreshDimensions();
    }

    @ModifyArg(
            method = "explode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"
            ),
            index = 6
    )
    private float pocket$scaleExplosion(final float power) {
        return power * (float) pocket$litScale();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void pocket$saveScale(final CompoundTag tag, final CallbackInfo ci) {
        tag.putFloat("PocketLitScale", (float) pocket$litScale());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void pocket$loadScale(final CompoundTag tag, final CallbackInfo ci) {
        if (!tag.contains("PocketLitScale", Tag.TAG_ANY_NUMERIC)) return;

        final PrimedTnt self = (PrimedTnt) (Object) this;
        self.getEntityData().set(pocket$LIT_SCALE, (float) pocket$sanitize(tag.getFloat("PocketLitScale")));
        pocket$lastDimensionScale = Double.NaN;
        self.refreshDimensions();
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void pocket$restoreScale(final Entity entity, final CallbackInfo ci) {
        if (!(entity instanceof PrimedTntScaleAccess source)) return;

        final PrimedTnt self = (PrimedTnt) (Object) this;
        self.getEntityData().set(pocket$LIT_SCALE, (float) source.pocket$litScale());
        pocket$lastDimensionScale = Double.NaN;
        self.refreshDimensions();
    }

    @Override
    public double pocket$litScale() {
        return pocket$sanitize(((PrimedTnt) (Object) this).getEntityData().get(pocket$LIT_SCALE));
    }

    @Override
    public double pocket$dimensionScale() {
        return pocket$litScale() / pocket$currentScale(0.0F, false);
    }

    @Override
    public double pocket$renderScale(final float partialTick) {
        if (!PehkuiScaleBridge.ownsScaling()) return pocket$litScale();
        return pocket$litScale() / pocket$currentScale(partialTick, true);
    }

    @Unique
    private double pocket$currentScale(final float partialTick, final boolean render) {
        final Entity self = (Entity) (Object) this;
        final SubLevel subLevel = Sable.HELPER.getContaining(self);
        if (subLevel == null || subLevel.isRemoved()) return 1.0D;

        if (render && subLevel instanceof final ClientSubLevel clientSubLevel) {
            return pocket$sanitize(clientSubLevel.renderPose(partialTick).scale().x());
        }

        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            return pocket$sanitize(ScaleState.getServerScale(serverSubLevel));
        }

        return pocket$sanitize(subLevel.logicalPose().scale().x());
    }

    @Unique
    private static double pocket$sanitize(final double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) return 1.0D;
        return Math.max(PocketSized.MIN_SCALE, Math.min(1.0D, scale));
    }
}
