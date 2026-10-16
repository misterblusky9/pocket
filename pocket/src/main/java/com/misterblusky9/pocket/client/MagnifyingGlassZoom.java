package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.item.MagnifyingGlassItem;
import com.misterblusky9.pocket.network.MagnifyingGlassPayload;
import com.misterblusky9.pym.api.Pym;
import net.minecraft.client.Minecraft;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.joml.Vector3f;


public final class MagnifyingGlassZoom {
    private static final double MIN_DIAL = 1.0D;
    private static final double EYE_DIAL = 2.0D;
    private static final double MAX_OPTICAL_ZOOM = 8192.0D;
    private static final double MAX_DIAL = EYE_DIAL * MAX_OPTICAL_ZOOM;
    private static final double SAME_ZOOM = 1.0E-3D;
    private static final double CAMERA_RESPONSE = 12.0D;
    private static final double POSE_RESPONSE = 14.0D;
    private static final double ENTITY_RANGE = 96.0D;

    private static boolean toggled;
    private static double target = MIN_DIAL;
    private static double dial = MIN_DIAL;
    private static double raise; // 0 lowered, 1 centered at the face
    private static double approach; // 0 centered, 1 lens at the eye
    private static long lastNanos;
    private static LocalPlayer sessionPlayer;
    private static net.minecraft.client.multiplayer.ClientLevel sessionLevel;

    public static double opticalZoom() {
        double candidate = Math.max(1.0D, dial / EYE_DIAL);
        // Optical FOV begins only once the moving lens is almost against the eye.
        double start = smooth((float) Mth.clamp((approach - 0.90D) / 0.10D, 0.0D, 1.0D));
        return Math.exp(Math.log(candidate) * start);
    }

    private static float nearClipProgress() {
        // Push the glass naturally beyond the camera near-plane as magnification takes over.
        float settled = smooth((float) Mth.clamp((approach - 0.975D) / 0.025D, 0.0D, 1.0D));
        float zooming = smooth((float) Mth.clamp((opticalZoom() - 1.0D) / 0.35D, 0.0D, 1.0D));
        return settled * zooming;
    }

    public static double raiseProgress() {
        return raise;
    }

    public static double approachProgress() {
        return approach;
    }

    /** A stable source of first-person hand movement, shared by all optical devices later. */
    public static boolean applyHandTransform(PoseStack matrices, LocalPlayer player,
                                              HumanoidArm arm, ItemStack stack,
                                              float partialTick, float equip, float swing) {
        if (player != sessionPlayer) return false;
        animate();
        if (raise < 0.001D && !toggled) return false;
        float r = smooth((float) raise);
        float a = smooth((float) approach);

        // The model's own first-person display places it; raising swaps models behind an equip-style dip.
        boolean raisedModel = raisedLocally(player, stack) || MagnifyingGlassItem.isRaised(stack);
        float dip = raisedModel ? 1.0F - r : r;
        PoseStack hand = new PoseStack();
        vanillaHandTransform(hand, arm, equip, swing);
        hand.translate(0.0F, -0.6F * dip, 0.0F);

        if (a > 1.0E-4F) {
            // Scale about the lens while carrying it from the authored pose to the eye.
            Vector3f lens = lensInHand(hand, player, arm, stack);
            float scale = 1.0F + 1.8F * a;
            float z = Mth.lerp(a, lens.z, EYE_LENS_Z) + 0.65F * nearClipProgress();
            matrices.translate(Mth.lerp(a, lens.x, 0.0F), Mth.lerp(a, lens.y, 0.0F), z);
            matrices.scale(scale, scale, scale);
            matrices.translate(-lens.x, -lens.y, -lens.z);
        }
        matrices.mulPose(hand.last().pose());
        return true;
    }

    private static final Vector3f LENS_CENTER = new Vector3f(8.04F / 16.0F, 10.02F / 16.0F, 8.0F / 16.0F);
    private static final float EYE_LENS_Z = -0.42F;

    private static void vanillaHandTransform(PoseStack matrices, HumanoidArm arm,
                                             float equip, float swing) {
        // ItemInHandRenderer's default branch, which returning true replaces.
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float root = Mth.sqrt(swing);
        matrices.translate(side * -0.4F * Mth.sin(root * (float) Math.PI),
                0.2F * Mth.sin(root * (float) Math.PI * 2.0F),
                -0.2F * Mth.sin(swing * (float) Math.PI));
        matrices.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        float f = Mth.sin(swing * swing * (float) Math.PI);
        matrices.mulPose(Axis.YP.rotationDegrees(side * (45.0F + f * -20.0F)));
        float f1 = Mth.sin(root * (float) Math.PI);
        matrices.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        matrices.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
    }

    private static Vector3f lensInHand(PoseStack hand, LocalPlayer player,
                                       HumanoidArm arm, ItemStack stack) {
        boolean left = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = left
                ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(stack, player.level(), player, player.getId() + context.ordinal());
        PoseStack probe = new PoseStack();
        probe.mulPose(hand.last().pose());
        ClientHooks.handleCameraTransforms(probe, model, context, left);
        probe.translate(-0.5F, -0.5F, -0.5F);
        return probe.last().pose().transformPosition(new Vector3f(LENS_CENTER));
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static double eyeAmount(double zoomDial) {
        return Mth.clamp(Math.log(Math.max(zoomDial, MIN_DIAL)) / Math.log(EYE_DIAL), 0.0D, 1.0D);
    }

    public static void use(Player player) {
        if (toggled) {
            setToggled(player, false);
            return;
        }

        target = MIN_DIAL;
        double viewerScale = viewerScale(player);
        EntityCompressionTargeting.Target entity = EntityCompressionTargeting.find(
                player, Math.min(ENTITY_RANGE, player.blockInteractionRange() * viewerScale));
        if (entity != null) {
            double relativeScale = EntityCompressionTargeting.scale(entity.entity()) / viewerScale;
            if (relativeScale > 0.0D && relativeScale < 0.5D) {
                double optical = Math.min(MAX_OPTICAL_ZOOM, 0.5D / relativeScale);
                target = Math.min(MAX_DIAL, EYE_DIAL * Math.pow(2.0D,
                        Math.ceil(Math.log(optical) / Math.log(2.0D))));
            }
        }
        setToggled(player, true);
    }

    static double viewerScale(Player player) {
        double scale = Pym.entities().scaleOf(player);
        return Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
    }

    public static boolean isViewing() {
        return active() && raise > 0.80D;
    }

    public static String readout() {
        return Math.round(Math.max(1.0D, target / EYE_DIAL)) + "×";
    }

    public static boolean raisedLocally(LivingEntity entity, ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!toggled || entity != player || player == null) return false;
        ItemStack main = player.getMainHandItem();
        ItemStack held = main.getItem() instanceof MagnifyingGlassItem ? main : player.getOffhandItem();
        return ItemStack.isSameItem(held, stack);
    }

    private static void setToggled(Player player, boolean on) {
        toggled = on;
        if (on) {
            sessionPlayer = Minecraft.getInstance().player;
            sessionLevel = Minecraft.getInstance().level;
        }
        PacketDistributor.sendToServer(new MagnifyingGlassPayload(on));
        player.playSound(on ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }

    private static boolean held() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && MagnifyingGlassItem.holds(player);
    }

    private static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !held()) {
            resetSession();
            return false;
        }
        if (sessionPlayer != null && (sessionPlayer != player || sessionLevel != minecraft.level)) {
            resetSession();
            return false;
        }
        return toggled && minecraft.options.getCameraType().isFirstPerson();
    }

    private static void resetSession() {
        toggled = false;
        target = MIN_DIAL;
        sessionPlayer = null;
        sessionLevel = null;
    }

    private static void animate() {
        long now = System.nanoTime();
        double seconds = lastNanos == 0L ? 0.0D : Mth.clamp((now - lastNanos) / 1.0e9D, 0.0D, 0.10D);
        lastNanos = now;
        boolean inUse = active();
        double goal = inUse ? target : MIN_DIAL;
        double raiseGoal = inUse ? 1.0D : 0.0D;
        double approachGoal = inUse ? eyeAmount(goal) : 0.0D;
        dial = Math.exp(Mth.lerp(1.0D - Math.exp(-CAMERA_RESPONSE * seconds), Math.log(dial), Math.log(goal)));
        double poseAlpha = 1.0D - Math.exp(-POSE_RESPONSE * seconds);
        raise = Mth.lerp(poseAlpha, raise, raiseGoal);
        approach = Mth.lerp(poseAlpha, approach, approachGoal);
        if (Math.abs(dial - goal) < SAME_ZOOM) dial = goal;
        if (Math.abs(raise - raiseGoal) < 1.0E-4D) raise = raiseGoal;
        if (Math.abs(approach - approachGoal) < 1.0E-4D) approach = approachGoal;
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        animate();
        if (!event.usedConfiguredFov()) return;
        // Complete physical approach before optical zoom begins. Retains vanilla FOV until then.
        double m = opticalZoom();
        if (m <= 1.00001D) return;
        double halfRadians = Math.toRadians(event.getFOV()) * 0.5D;
        event.setFOV(Math.toDegrees(2.0D * Math.atan(Math.tan(halfRadians) / m)));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetSession();
            return;
        }
        if (minecraft.screen == null && held()) {
            while (PocketKeys.MAGNIFIER_TOGGLE.consumeClick()) use(minecraft.player);
        } else {
            while (PocketKeys.MAGNIFIER_TOGGLE.consumeClick()) { }
        }
    }

    public static void onTurn(CalculatePlayerTurnEvent event) {
        if (opticalZoom() <= 1.00001D) return;
        double base = event.getMouseSensitivity() * 0.6D + 0.2D;
        double scaled = base / Math.cbrt(opticalZoom());
        event.setMouseSensitivity((scaled - 0.2D) / 0.6D);
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active()) return;
        Input input = event.getInput();
        input.shiftKeyDown = false;
        if (opticalZoom() <= 1.00001D) return;
        input.forwardImpulse /= (float) opticalZoom();
        input.leftImpulse /= (float) opticalZoom();
    }

    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isCanceled() || minecraft.screen != null || !active()) return;
        boolean mainHand = minecraft.player.getMainHandItem().getItem() instanceof MagnifyingGlassItem;
        if (!mainHand && !minecraft.options.keyShift.isDown()) return;

        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) return;

        event.setCanceled(true);
        int steps = Math.max(1, (int) Math.round(Math.abs(delta)));
        int direction = delta > 0.0D ? 1 : -1;
        int currentStep = (int) Math.round(Math.log(target) / Math.log(2.0D));
        int maxStep = (int) Math.round(Math.log(MAX_DIAL) / Math.log(2.0D));
        target = Math.pow(2.0D, Mth.clamp(currentStep + direction * steps, 0, maxStep));
    }

    public static void onRenderHand(RenderHandEvent event) {
        // Stop drawing only the glass hand once the lens has crossed the camera near-plane.
        // The other hand must remain rendered so that offhand inspection supports building.
        if (raise < 0.998D || nearClipProgress() < 0.999F) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getItemInHand(event.getHand()).getItem() instanceof MagnifyingGlassItem) {
            event.setCanceled(true);
        }
    }

    private MagnifyingGlassZoom() {}
}
