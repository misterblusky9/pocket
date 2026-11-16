package com.misterblusky9.pym.gametest;

import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.internal.physics.RapierBridge;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Pym.MOD_ID)
@PrefixGameTestTemplate(false)
public class HostedJointGameTests {
    private static final String FLOOR = "empty48";
    private static final String BATCH = "pym_hosted_joint";
    private static final int SETTLE = 10;

    private static List<BlockPos> bar(ServerLevel level, BlockPos start) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            level.setBlock(start.offset(i, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            blocks.add(start.offset(i, 0, 0));
        }
        return blocks;
    }

    private static ServerSubLevel assemble(GameTestHelper helper, BlockPos relative, double scale) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(relative);
        List<BlockPos> blocks = bar(level, start);
        ServerSubLevel body = SubLevelAssemblyHelper.assembleBlocks(level, start, blocks,
                BoundingBox3i.from(blocks).expand(1, 1, 1));
        helper.assertTrue(body != null, "assembly failed");
        if (Math.abs(scale - 1.0D) > 1.0E-9D) {
            helper.assertTrue(Pym.resize().request(body).scaleTo(scale).immediate().submit().accepted(),
                    "resize refused");
        }
        return body;
    }

    private record Rig(ServerSubLevel base, ServerSubLevel arm, boolean hosted, Long pin) {}

    private static void measure(GameTestHelper helper, double scale, boolean hosted, boolean horizontal) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        String label = String.format("scale=%.4f %s axis=%s", scale, hosted ? "HOSTED" : "WORLD-PINNED",
                horizontal ? "horizontal(gravity load on motor)" : "vertical(motor turn)");

        ServerSubLevel base;
        Long pin = null;
        if (hosted) {
            ServerSubLevel hat = Detached.create(level, UUID.randomUUID());
            helper.assertTrue(hat != null, "could not create a detached sub-level");
            BlockPos centre = hat.getPlot().getCenterBlock();
            for (int i = 0; i < 3; i++) level.setBlock(centre.offset(i, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            Vec3 world = helper.absoluteVec(new Vec3(8.0D, 8.0D, 8.0D));
            hat.logicalPose().position().set(world.x, world.y, world.z);
            hat.logicalPose().scale().set(scale);
            hat.updateLastPose();
            hat.updateBoundingBox();
            base = Hosted.host(hat);
            helper.assertTrue(base != null, "host failed");
        } else {
            base = assemble(helper, new BlockPos(30, 30, 30), scale);
        }
        ServerSubLevel arm = assemble(helper, new BlockPos(4, 20, 12), scale);
        ServerSubLevel root = base;

        GameTestSequence sequence = helper.startSequence();
        Rig[] rig = new Rig[1];
        RotaryConstraintHandle[] handle = new RotaryConstraintHandle[1];
        Vector3d[] points = new Vector3d[2];
        Vector3d axis = horizontal ? new Vector3d(0.0D, 0.0D, 1.0D) : new Vector3d(0.0D, 1.0D, 0.0D);
        double target = horizontal ? 0.0D : 1.2D;

        sequence.thenExecuteAfter(SETTLE, () -> {
            Long held = null;
            if (!hosted) {
                Vector3dc mass = root.getMassTracker().getCenterOfMass();
                helper.assertTrue(mass != null, "base has no centre of mass");
                level.setChunkForced(0, 0, true);
                Vector3d site = new Vector3d(8.5D, 250.0D, 8.5D);
                LogUtils.getLogger().info("JOINTMEASURE {} pivotVsMass rp={} com={}", label,
                        root.logicalPose().rotationPoint(), mass);
                pipeline.teleport(root, site, new Quaterniond());
                pipeline.resetVelocity(root);
                root.updateLastPose();
                root.updateBoundingBox();
                held = RapierBridge.pinToWorld(root, site, mass);
            }
            Vector3d rootPivot = new Vector3d(root.logicalPose().rotationPoint());
            Vector3d onRoot = new Vector3d(rootPivot).add(2.0D, 0.0D, 0.0D);
            Vector3d armPivot = new Vector3d(arm.logicalPose().rotationPoint());
            Vector3d onArm = new Vector3d(armPivot).sub(2.0D, 0.0D, 0.0D);
            Vector3d hinge = root.logicalPose().transformPosition(new Vector3d(onRoot));
            Quaterniond rootTurn = new Quaterniond(root.logicalPose().orientation());
            Vector3d armAt = new Vector3d(armPivot).sub(onArm).mul(scale);
            rootTurn.transform(armAt).add(hinge);
            pipeline.teleport(arm, armAt, rootTurn);
            pipeline.resetVelocity(arm);
            arm.updateLastPose();
            arm.updateBoundingBox();
            RotaryConstraintHandle joint = pipeline.addConstraint(root, arm,
                    new RotaryConstraintConfiguration(onRoot, onArm, axis, axis));
            helper.assertTrue(joint != null && joint.isValid(), "hinge was refused");
            joint.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, target, 400.0D, 80.0D, false, 0.0D);
            handle[0] = joint;
            points[0] = onRoot;
            points[1] = onArm;
            rig[0] = new Rig(root, arm, hosted, held);
            var rootMass = root.getMassTracker();
            var armMass = arm.getMassTracker();
            LogUtils.getLogger().info("JOINTMEASURE {} setup rawMass root={} arm={} settledScale root={} arm={} poseScale root={} arm={}",
                    label, rootMass == null ? -1 : rootMass.getMass(), armMass == null ? -1 : armMass.getMass(),
                    Pym.scale().settled(root), Pym.scale().settled(arm),
                    root.logicalPose().scale().x(), arm.logicalPose().scale().x());
        });
        for (int at : new int[] {5, 20, 60, 120}) {
            int wait = at == 5 ? 5 : at == 20 ? 15 : at == 60 ? 40 : 60;
            sequence.thenExecuteAfter(wait, () -> sample(label, at, rig[0], handle[0], points, axis, target, scale));
        }
        sequence.thenExecute(() -> {
            Rig done = rig[0];
            if (done.hosted()) {
                ServerSubLevel back = Hosted.unhost(done.base());
                if (back != null) {
                    for (ServerSubLevel child : Detached.children(back)) {
                        Detached.detach(child);
                        Detached.discard(child);
                    }
                    Detached.discard(back);
                }
            } else {
                if (done.pin() != null) RapierBridge.removeConstraint(RapierBridge.sceneHandle(level), done.pin());
                if (!done.arm().isRemoved()) container.removeSubLevel(done.arm(), SubLevelRemovalReason.REMOVED);
                if (!done.base().isRemoved()) container.removeSubLevel(done.base(), SubLevelRemovalReason.REMOVED);
            }
        }).thenSucceed();
    }

    private static void sample(String label, int tick, Rig rig, RotaryConstraintHandle handle, Vector3d[] points,
                               Vector3d axis, double target, double scale) {
        ServerSubLevel root = rig.base();
        ServerSubLevel arm = rig.arm();
        Vector3d a = root.logicalPose().transformPosition(new Vector3d(points[0]));
        Vector3d b = arm.logicalPose().transformPosition(new Vector3d(points[1]));
        double slipWorld = a.distance(b);
        Quaterniond relative = new Quaterniond(root.logicalPose().orientation()).conjugate()
                .mul(arm.logicalPose().orientation());
        Vector3d bent = relative.transform(new Vector3d(axis));
        double offAxis = Math.acos(Math.max(-1.0D, Math.min(1.0D, bent.dot(axis))));
        Vector3d probe = Math.abs(axis.y) > 0.5D ? new Vector3d(1.0D, 0.0D, 0.0D) : new Vector3d(1.0D, 0.0D, 0.0D);
        Vector3d turned = relative.transform(new Vector3d(probe));
        Vector3d cross = new Vector3d(probe).cross(turned);
        double about = Math.atan2(cross.dot(axis), probe.dot(turned));
        double drift = root.logicalPose().position().distance(rig.hosted() ? Hosted.site(root) : root.logicalPose().position());
        LogUtils.getLogger().info(
                "JOINTMEASURE {} t={} armPos={} valid={} hingeAngle={} target={} angleError={} offAxisRad={} slipWorld={} slipPlotBlocks={} rootDrift={} armY={}",
                label, tick, arm.logicalPose().position(), handle.isValid(), String.format("%.4f", about), target,
                String.format("%.4f", about - target), String.format("%.5f", offAxis),
                String.format("%.6f", slipWorld), String.format("%.4f", slipWorld / scale),
                String.format("%.5f", drift), String.format("%.3f", arm.logicalPose().position().y));
    }

    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointFullHostedTurn(GameTestHelper h) { measure(h, 1.0D, true, false); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointFullHostedHold(GameTestHelper h) { measure(h, 1.0D, true, true); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointFullPinnedTurn(GameTestHelper h) { measure(h, 1.0D, false, false); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointFullPinnedHold(GameTestHelper h) { measure(h, 1.0D, false, true); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointQuarterHostedTurn(GameTestHelper h) { measure(h, 0.25D, true, false); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointQuarterHostedHold(GameTestHelper h) { measure(h, 0.25D, true, true); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointQuarterPinnedTurn(GameTestHelper h) { measure(h, 0.25D, false, false); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointQuarterPinnedHold(GameTestHelper h) { measure(h, 0.25D, false, true); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointEighthHostedTurn(GameTestHelper h) { measure(h, 0.125D, true, false); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointEighthHostedHold(GameTestHelper h) { measure(h, 0.125D, true, true); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointEighthPinnedTurn(GameTestHelper h) { measure(h, 0.125D, false, false); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointEighthPinnedHold(GameTestHelper h) { measure(h, 0.125D, false, true); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointSixteenthHostedTurn(GameTestHelper h) { measure(h, 0.0625D, true, false); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointSixteenthHostedHold(GameTestHelper h) { measure(h, 0.0625D, true, true); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointSixteenthPinnedTurn(GameTestHelper h) { measure(h, 0.0625D, false, false); }
    @GameTest(template = FLOOR, timeoutTicks = 400, batch = BATCH) public static void jointSixteenthPinnedHold(GameTestHelper h) { measure(h, 0.0625D, false, true); }
}
