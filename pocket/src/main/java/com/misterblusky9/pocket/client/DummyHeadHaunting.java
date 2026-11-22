package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.mixin.BatAccessor;
import com.mojang.blaze3d.platform.Window;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class DummyHeadHaunting {
    private static final int RARITY = 100000;
    private static final double ARRIVE_RANGE = 50.0;
    private static final double LEAVE_RANGE = 100.0;
    private static final double MIN_ARRIVE_RANGE = 10.0;
    private static final double CLOSE_RANGE = 5.0;
    private static final int NOTICE_TICKS = 20;
    private static final double HEAD_RADIUS = 0.35;
    private static final List<HatStandBlockEntity> CANDIDATES = new ArrayList<>();
    private static final int APPROACH_GRACE = 40;
    private static final double APPROACH_SPEED = 0.15;
    private static final int APPROACH_TICKS = 20;
    private static final double NEAR = 5.0;
    private static final double VIEW_MARGIN = 1.15;
    private static final int PATIENCE_TICKS = 600;
    private static @Nullable HatStandBlockEntity haunted;
    private static @Nullable HatStandBlockEntity lingering;
    private static boolean armed;
    private static boolean noticed;
    private static boolean wasSeen;
    private static int contact;
    private static int patience;
    private static int seenTicks;
    private static int advancing;
    private static double lastDistance;

    public static void arm() {
        armed = true;
    }

    public static boolean haunts(HatStandBlockEntity stand) {
        return haunted == stand;
    }

    public static boolean tick(HatStandBlockEntity stand) {
        if (haunted != null && haunted.isRemoved()) haunted = null;
        if (lingering != null && lingering.isRemoved()) lingering = null;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return false;
        if (lingering == stand) {
            if (eligible(stand) && !HatStandWrenchInput.isTurning() && seen(minecraft, player, centre(stand))) return true;
            lingering = null;
            stand.settleShown();
            return false;
        }
        if (haunted == null && lingering == null && eligible(stand)) CANDIDATES.add(stand);
        if (haunted != stand) return false;

        Vec3 centre = centre(stand);
        boolean seen = seen(minecraft, player, centre);
        boolean covered = wasSeen && !seen && inView(minecraft, centre);
        wasSeen = seen;
        double distance = centre.distanceTo(player.getEyePosition());
        if (seen) seenTicks++;
        advancing = seen && seenTicks > APPROACH_GRACE && lastDistance - distance > APPROACH_SPEED ? advancing + 1 : 0;
        lastDistance = distance;
        contact = seen && eyeContact(minecraft.gameRenderer.getMainCamera(), centre) ? contact + 1 : 0;
        if (contact >= NOTICE_TICKS) noticed = true;
        if (covered || noticed && !seen) return leave(stand, false);
        if (!eligible(stand) || HatStandWrenchInput.isTurning()
                || seen && (distance < CLOSE_RANGE || advancing >= APPROACH_TICKS)) return leave(stand, seen);
        if (!seen && (distance > LEAVE_RANGE || ++patience >= PATIENCE_TICKS)) return leave(stand, false);
        stand.stareAt(player, !seen);
        return true;
    }

    public static boolean stalk(HatStandBlockEntity stand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || HatStandWrenchInput.isTurning()) return false;
        stand.stareAt(player, false);
        return true;
    }

    private static boolean screenHides(Minecraft minecraft, Vec3 centre) {
        Screen screen = minecraft.screen;
        if (screen == null || screen instanceof ChatScreen) return false;
        if (!(screen instanceof AbstractContainerScreen<?> panel)) return true;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 to = centre.subtract(camera.getPosition());
        double depth = to.dot(new Vec3(camera.getLookVector()));
        if (depth <= 0.0) return false;
        Window window = minecraft.getWindow();
        double scale = window.getGuiScaledHeight() / 2.0
                / (depth * Math.tan(Math.toRadians(minecraft.options.fov().get() / 2.0)));
        double x = window.getGuiScaledWidth() / 2.0 - to.dot(new Vec3(camera.getLeftVector())) * scale;
        double y = window.getGuiScaledHeight() / 2.0 - to.dot(new Vec3(camera.getUpVector())) * scale;
        double radius = HEAD_RADIUS * scale;
        return x - radius >= panel.getGuiLeft() && x + radius <= panel.getGuiLeft() + panel.getXSize()
                && y - radius >= panel.getGuiTop() && y + radius <= panel.getGuiTop() + panel.getYSize();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (haunted != null && !haunted.isRemoved() && wasSeen && screenHides(minecraft, centre(haunted)))
            leave(haunted, false);
        if (lingering != null && !lingering.isRemoved() && screenHides(minecraft, centre(lingering))) {
            lingering.settleShown();
            lingering = null;
        }
        if (player != null && minecraft.level != null && haunted == null && lingering == null && !CANDIDATES.isEmpty()
                && (armed || minecraft.level.random.nextInt(RARITY) == 0 && BatAccessor.pocket$isHalloween()))
            arrive(minecraft, player);
        CANDIDATES.clear();
    }

    private static void arrive(Minecraft minecraft, LocalPlayer player) {
        List<HatStandBlockEntity> valid = new ArrayList<>();
        for (HatStandBlockEntity stand : CANDIDATES) {
            Vec3 centre = centre(stand);
            double distance = centre.distanceTo(player.getEyePosition());
            if (distance >= MIN_ARRIVE_RANGE && distance <= ARRIVE_RANGE && !seen(minecraft, player, centre)) valid.add(stand);
        }
        if (valid.isEmpty()) return;
        HatStandBlockEntity stand = valid.get(minecraft.level.random.nextInt(valid.size()));
        haunted = stand;
        armed = false;
        noticed = false;
        wasSeen = false;
        contact = 0;
        patience = 0;
        seenTicks = 0;
        advancing = 0;
        lastDistance = centre(stand).distanceTo(player.getEyePosition());
        stand.stareAt(player, true);
        minecraft.getSoundManager().play(SimpleSoundInstance.forAmbientAddition(SoundEvents.AMBIENT_CAVE.value()));
    }

    private static boolean leave(HatStandBlockEntity stand, boolean seen) {
        haunted = null;
        if (seen) lingering = stand;
        else stand.settleShown();
        return seen;
    }

    private static boolean eligible(HatStandBlockEntity stand) {
        return stand.getSkinProfile() == null && !stand.isActive();
    }

    private static Vec3 centre(HatStandBlockEntity stand) {
        Vec3 local = Vec3.atLowerCornerOf(stand.getBlockPos()).add(stand.headCentre());
        SubLevel parent = Sable.HELPER.getContaining(stand.getLevel(), stand.getBlockPos());
        if (parent == null) return local;
        Vector3d world = parent.logicalPose().transformPosition(new Vector3d(local.x, local.y, local.z));
        return new Vec3(world.x, world.y, world.z);
    }

    private static boolean seen(Minecraft minecraft, LocalPlayer player, Vec3 centre) {
        if (!inView(minecraft, centre)) return false;
        HitResult hit = player.level().clip(new ClipContext(minecraft.gameRenderer.getMainCamera().getPosition(), centre,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(centre) < 1.0;
    }

    private static boolean inView(Minecraft minecraft, Vec3 centre) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        return camera.getPosition().distanceTo(centre) <= NEAR || dot(camera, centre) > viewCosine(minecraft);
    }

    private static boolean eyeContact(Camera camera, Vec3 centre) {
        return dot(camera, centre) > 1.0 - 0.025 / camera.getPosition().distanceTo(centre);
    }

    private static double dot(Camera camera, Vec3 centre) {
        return new Vec3(camera.getLookVector()).dot(centre.subtract(camera.getPosition()).normalize());
    }

    private static double viewCosine(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        double aspect = (double) window.getWidth() / Math.max(1, window.getHeight());
        double vertical = Math.tan(Math.toRadians(minecraft.options.fov().get() / 2.0));
        double corner = Math.sqrt(1.0 + aspect * aspect) * vertical * VIEW_MARGIN;
        return 1.0 / Math.sqrt(1.0 + corner * corner);
    }

    private DummyHeadHaunting() {}
}
