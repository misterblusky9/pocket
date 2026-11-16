package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class WornHatFrame {
    private static final double MODEL_SCALE = 0.9375;
    private static final double MODEL_LIFT = 1.501;
    private static final double CROUCH_HEAD_PIXELS = 4.2;
    private static final double CROUCH_DROP = 2.0 / 16.0;
    private static final double NECK_TO_CENTRE = 0.25;

    private WornHatFrame() {}

    public static @Nullable Vector3d headPixels(ClientSubLevel hat, Player wearer, Vector3d world, float partialTick) {
        Pose3d head = at(hat, wearer, partialTick);
        if (head == null) return null;
        double model = head.scale().x() / BuildSites.scale(HatRendering.headwear(wearer));
        Vector3d local = new Vector3d(world).sub(head.position());
        head.orientation().conjugate(new Quaterniond()).transform(local);
        return local.mul(16.0 / model);
    }

    public static @Nullable Pose3d at(ClientSubLevel hat, Player wearer, float partialTick) {
        if (wearer.isFallFlying() || wearer.isVisuallySwimming() || wearer.isSleeping() || wearer.isAutoSpinAttack())
            return null;
        ItemStack headwear = HatRendering.headwear(wearer);
        double entity = Pym.entities().renderScale(wearer, partialTick);
        double model = MODEL_SCALE * entity;
        boolean crouching = wearer.isCrouching();
        Vec3 feet = wearer.getPosition(partialTick);
        double neckY = feet.y + (MODEL_LIFT - (crouching ? CROUCH_HEAD_PIXELS / 16.0 : 0.0)) * model
                - (crouching ? CROUCH_DROP * entity : 0.0);
        Quaterniond orientation = new Quaterniond()
                .rotateY(Math.toRadians(180.0 - Mth.rotLerp(partialTick, wearer.yHeadRotO, wearer.yHeadRot)))
                .rotateX(Math.toRadians(-wearer.getViewXRot(partialTick)));
        Vector3d centre = orientation.transform(new Vector3d(0.0, NECK_TO_CENTRE * model, 0.0))
                .add(feet.x, neckY, feet.z);
        Pose3d pose = new Pose3d();
        pose.rotationPoint().set(BuildSites.head(hat, BuildSites.offsetVector(headwear), headwear));
        pose.position().set(centre);
        pose.orientation().set(orientation);
        pose.scale().set(BuildSites.scale(headwear) * model);
        return pose;
    }
}
