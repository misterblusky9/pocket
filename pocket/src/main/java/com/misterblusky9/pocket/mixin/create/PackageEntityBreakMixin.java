package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.item.PocketCaseItem;
import com.simibubi.create.content.logistics.box.PackageEntity;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PackageEntity.class, remap = false)
public abstract class PackageEntityBreakMixin extends LivingEntity {
    @Unique
    private static final String DESTROY =
            "Lcom/simibubi/create/content/logistics/box/PackageEntity;destroy(Lnet/minecraft/world/damagesource/DamageSource;)V";

    @Unique
    private long pocket$nextBreakAttempt;

    protected PackageEntityBreakMixin(final EntityType<? extends LivingEntity> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "hurt", at = @At(value = "INVOKE", target = DESTROY), cancellable = true, remap = false)
    private void pocket$takeThePunch(
            final DamageSource source,
            final float amount,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (!pocket$pocketed() || pocket$breakOpen(false)) return;
        cir.setReturnValue(!source.is(DamageTypeTags.IS_EXPLOSION) && pocket$knockedBack(source));
    }

    @Inject(method = {"takeDamage", "onInsideBlock"}, at = @At(value = "INVOKE", target = DESTROY),
            cancellable = true, remap = false)
    private void pocket$surviveUnbreakable(final CallbackInfo ci) {
        if (pocket$pocketed() && !pocket$breakOpen(true)) ci.cancel();
    }

    @Unique
    private boolean pocket$pocketed() {
        final ItemStack box = ((PackageEntity) (Object) this).getBox();
        return box != null && box.getItem() instanceof PocketCaseItem && PocketCaseItem.isFilled(box);
    }

    @Unique
    private boolean pocket$breakOpen(final boolean throttled) {
        if (!(this.level() instanceof final ServerLevel level)) return false;
        if (throttled && level.getGameTime() < this.pocket$nextBreakAttempt) return false;

        final boolean opened = PocketCaseItem.breakOpen(level, (PackageEntity) (Object) this);
        if (!opened && throttled) this.pocket$nextBreakAttempt = level.getGameTime() + 20L;
        return opened;
    }

    @Unique
    private boolean pocket$knockedBack(final DamageSource source) {
        if (this.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) return false;
        this.invulnerableTime = 20;
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        if (!source.is(DamageTypeTags.NO_IMPACT)) this.markHurt();
        if (source.is(DamageTypeTags.NO_KNOCKBACK)) return true;

        double towardX = 0.0D;
        double towardZ = 0.0D;
        if (source.getDirectEntity() instanceof final Projectile projectile) {
            final DoubleDoubleImmutablePair direction = projectile.calculateHorizontalHurtKnockbackDirection(this, source);
            towardX = -direction.leftDouble();
            towardZ = -direction.rightDouble();
        } else if (source.getSourcePosition() != null) {
            towardX = source.getSourcePosition().x() - this.getX();
            towardZ = source.getSourcePosition().z() - this.getZ();
        }
        this.knockback(0.4D, towardX, towardZ);
        return true;
    }
}
