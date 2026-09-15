package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

final class CompressionGunMuzzleTracker {
    private static final Vec3 FIRST_PERSON_OFFSET = new Vec3(0.45D, -0.06D, 1.10D);
    private static final long MAX_AGE_TICKS = 2L;
    private static final double RECOIL_PULL = 1.25D;

    private static Captured captured;

    private CompressionGunMuzzleTracker() {}

    static void capture(
            final ItemStack stack,
            final ItemDisplayContext transformType,
            final PoseStack poseStack,
            final Vec3 anchor
    ) {
        final boolean thirdPerson = transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!thirdPerson) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        if (!player.isUsingItem() || player.getItemInHand(player.getUsedItemHand()) != stack) return;

        final HumanoidArm usedArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        final boolean rightHand = transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if ((usedArm == HumanoidArm.RIGHT) != rightHand) return;

        final Vector3f point = new Vector3f(
                (float) anchor.x - 0.5F,
                (float) anchor.y - 0.5F,
                (float) anchor.z - 0.5F);
        poseStack.last().pose().transformPosition(point);

        captured = new Captured(
                player.getUUID(),
                minecraft.gameRenderer.getMainCamera().getPosition().add(point.x(), point.y(), point.z()),
                minecraft.level.getGameTime());
    }

    static Vec3 muzzle(final UUID playerId) {
        return muzzle(playerId, 1.0F);
    }

    static Vec3 muzzle(final UUID playerId, final float partialTick) {
        if (playerId == null) return null;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;

        final LocalPlayer player = minecraft.player;
        if (minecraft.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON
                && player != null
                && playerId.equals(player.getUUID())) {
            return firstPersonMuzzle(minecraft, player, partialTick);
        }

        final Captured value = captured;
        if (value == null || !playerId.equals(value.playerId())) return null;
        if (minecraft.level.getGameTime() - value.gameTime() > MAX_AGE_TICKS) return null;
        return value.position();
    }

    private static Vec3 firstPersonMuzzle(
            final Minecraft minecraft,
            final LocalPlayer player,
            final float partialTick
    ) {
        final HumanoidArm usedArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        // Recoil
        final double pull = 1.0D - CompressionGunRenderHandler.INSTANCE
                .getAnimation(usedArm == HumanoidArm.RIGHT, partialTick) * RECOIL_PULL;
        final Vec3 local = FIRST_PERSON_OFFSET.scale(pull);
        final double lateral = usedArm == HumanoidArm.RIGHT ? -local.x : local.x;

        final var camera = minecraft.gameRenderer.getMainCamera();
        final Vector3f left = camera.getLeftVector();
        final Vector3f up = camera.getUpVector();
        final Vector3f look = camera.getLookVector();

        return camera.getPosition().add(
                left.x() * lateral + up.x() * local.y + look.x() * local.z,
                left.y() * lateral + up.y() * local.y + look.y() * local.z,
                left.z() * lateral + up.z() * local.y + look.z() * local.z);
    }

    static void clear(final UUID playerId) {
        final Captured value = captured;
        if (value != null && playerId != null && playerId.equals(value.playerId())) captured = null;
    }

    static void clear() {
        captured = null;
    }

    private record Captured(UUID playerId, Vec3 position, long gameTime) {
    }
}
