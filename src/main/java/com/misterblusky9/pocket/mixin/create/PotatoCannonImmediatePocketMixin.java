package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.pocket.CannonDeploymentQueue;
import com.misterblusky9.pocket.pocket.CannonExpansionMode;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PotatoCannonItem.class, remap = false)
public abstract class PotatoCannonImmediatePocketMixin {
    @org.spongepowered.asm.mixin.Unique
    private static CannonExpansionMode pocket$firingMode(final Entity owner) {
        if (!(owner instanceof final Player player)) return CannonExpansionMode.IMMEDIATE;

        for (final net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            final ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof PotatoCannonItem) return CannonExpansionMode.of(held);
        }
        return CannonExpansionMode.IMMEDIATE;
    }

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            ),
            remap = false
    )
    private boolean pocket$replacePocketProjectileBeforeSpawn(final Level rawLevel, final Entity entity) {
        if (!(rawLevel instanceof final ServerLevel level)
                || !(entity instanceof final PotatoProjectileEntity projectile)) {
            return rawLevel.addFreshEntity(entity);
        }

        final ItemStack payload = projectile.getItem();
        if (!PocketCaseItem.isCannonPayload(payload)) {
            return rawLevel.addFreshEntity(entity);
        }

        final Vec3 motion = projectile.getDeltaMovement();
        final Vec3 forward = motion.lengthSqr() > 1.0E-8D ? motion.normalize() : Vec3.ZERO;

        final Vec3 spawnPos = projectile.position().add(forward.scale(0.45D));
        final boolean creativeShot = projectile.getOwner() instanceof final Player player && player.isCreative();
        if (creativeShot && projectile.getOwner() instanceof final Player player) {
            final var token = PocketCaseItem.token(payload);
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                final ItemStack candidate = player.getInventory().getItem(slot);
                if (!PocketCaseItem.isCannonPayload(candidate)) continue;
                if (token != null && token.equals(PocketCaseItem.token(candidate))) {
                    candidate.shrink(1);
                    if (candidate.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
                    player.getInventory().setChanged();
                    break;
                }
            }
        }

        final UUID ownerId = projectile.getOwner() == null ? null : projectile.getOwner().getUUID();
        CannonDeploymentQueue.enqueue(level, payload, spawnPos, motion, ownerId, pocket$firingMode(projectile.getOwner()));

        return true;
    }
}
