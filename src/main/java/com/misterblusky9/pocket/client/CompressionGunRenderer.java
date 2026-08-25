package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CompressionGunRenderer extends CustomRenderedItemModelRenderer {
    private static final PartialModel BODY = partial("item/compression_gun/body");
    private static final PartialModel BODY_GROW = partial("item/compression_gun/body_grow");
    private static final PartialModel COG = partial("item/compression_gun/cog");
    private static final PartialModel COG_GROW = partial("item/compression_gun/cog_grow");

    private static PartialModel partial(final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, path));
    }

    private static final float MAX_SPIN_SPEED = 62.0F;

    private static final float DRAG = 0.145F;

    private static final float DRIVE_TORQUE = MAX_SPIN_SPEED * DRAG;

    private static final float COAST_RATE = 0.045F;

    private static final float COG_PIVOT_Y = 0.03125F;

    private static final float GLOW_THRESHOLD = 0.55F;

    private static final net.minecraft.world.phys.Vec3 VENT_OFFSET =
            new net.minecraft.world.phys.Vec3(0.55D, -0.1D, 0.9D);
    private static final int VENT_PARTICLES = 2;
    private static final double VENT_SPREAD = 0.035D;

    private static float spin;
    private static float speed;
    private static float lastRenderTime = Float.NaN;
    private static long lastVentTick = -1L;
    private static final java.util.Random RANDOM = new java.util.Random();

    @Override
    protected void render(
            final ItemStack stack,
            final CustomRenderedItemModel model,
            final PartialItemModelRenderer renderer,
            final ItemDisplayContext transformType,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        final boolean growing = com.misterblusky9.pocket.item.CompressionGunItem.isGrowing(stack);
        renderer.render((growing ? BODY_GROW : BODY).get(), light);

        advanceSpin(stack);

        ms.pushPose();
        ms.translate(0.0F, COG_PIVOT_Y, 0.0F);
        ms.mulPose(Axis.ZP.rotationDegrees(spin));
        ms.translate(0.0F, -COG_PIVOT_Y, 0.0F);

        renderer.render((growing ? COG_GROW : COG).get(), glowingLight(light));
        ms.popPose();

        emitVentParticles(stack, growing);
    }

    private static int glowingLight(final int light) {
        final float heat = heat();
        if (heat <= 0.0F) return light;

        final int block = net.minecraft.client.renderer.LightTexture.block(light);
        final int sky = net.minecraft.client.renderer.LightTexture.sky(light);
        final int lit = Math.round(Mth.lerp(heat, block, 15.0F));
        return net.minecraft.client.renderer.LightTexture.pack(Math.max(block, lit), sky);
    }

    private static float heat() {
        final float fraction = Math.abs(speed) / MAX_SPIN_SPEED;
        return Mth.clamp((fraction - GLOW_THRESHOLD) / (1.0F - GLOW_THRESHOLD), 0.0F, 1.0F);
    }

    private static void emitVentParticles(final ItemStack stack, final boolean growing) {
        if (heat() < 1.0F || !isDriving(stack)) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        if (minecraft.options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON) return;

        final long tick = minecraft.level.getGameTime();
        if (tick == lastVentTick) return;
        lastVentTick = tick;

        final var muzzle = com.simibubi.create.content.equipment.zapper.ShootableGadgetItemMethods
                .getGunBarrelVec(player, player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND,
                        VENT_OFFSET);

        for (int i = 0; i < VENT_PARTICLES; i++) {
            minecraft.level.addParticle(
                    growing ? net.minecraft.core.particles.ParticleTypes.END_ROD
                            : net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    muzzle.x, muzzle.y, muzzle.z,
                    (RANDOM.nextDouble() - 0.5D) * VENT_SPREAD,
                    RANDOM.nextDouble() * VENT_SPREAD,
                    (RANDOM.nextDouble() - 0.5D) * VENT_SPREAD);
        }
    }

    private static void advanceSpin(final ItemStack stack) {
        final float now = AnimationTickHolder.getRenderTime();
        final float delta = Float.isNaN(lastRenderTime)
                ? 0.0F
                : Math.max(0.0F, Math.min(4.0F, now - lastRenderTime));

        lastRenderTime = now;
        if (delta <= 0.0F) return;

        final float direction = com.misterblusky9.pocket.item.CompressionGunItem.isGrowing(stack)
                ? -1.0F : 1.0F;

        if (isDriving(stack)) {
            speed += (DRIVE_TORQUE * direction - speed * DRAG) * delta;
        } else {
            speed -= speed * COAST_RATE * delta;
            if (Math.abs(speed) < 0.05F) speed = 0.0F;
        }
        spin = (spin + speed * delta) % 360.0F;
    }

    private static boolean isDriving(final ItemStack stack) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) return false;
        return player.getUseItem().getItem() == stack.getItem();
    }
}
