package com.misterblusky9.pym.gametest;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleFormat;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@GameTestHolder(Pym.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PymGameTests {
    private static final String STEP_FLOOR = "empty48";
    private static final int STEP_FLOOR_LENGTH = 48;
    private static final int STEP_FLOOR_WIDTH = 24;
    private static final double[] STEP_CRAFT_SCALES = {1.0D, 1.0D / 2.0D, 1.0D / 4.0D, 1.0D / 8.0D, 1.0D / 16.0D, 1.0D / 32.0D};
    private static final double[] STEP_PLAYER_SCALES = {1.0D / 4.0D, 1.0D / 2.0D, 1.0D, 2.0D, 4.0D};
    private static final double STEP_REACH_MARGIN = 0.9D;
    private static final int STEP_TIMEOUT = 120;
    private static final int STEP_SETTLE_TICKS = 30;
    private static final int STEP_LAND_TICKS = 3;
    private static final int STEP_WALK_TICKS = 60;
    private static final int CROUCH_WALK_TICKS = 80;
    private static final double STEP_RISE_TOLERANCE = 0.25D;
    private static final double WALKER_WIDTH = 0.6D;
    private static final double MAX_UP_STEP = 0.6D;
    private static final double WALK_SPEED = 0.1D;
    private static final double GRAVITY = 0.08D;
    private static final double TOLERANCE = 1.0E-6D;

    @GameTestGenerator
    public static Collection<TestFunction> stepUpMatrix() {
        List<TestFunction> tests = new ArrayList<>();
        for (double craft : STEP_CRAFT_SCALES) {
            addStepUp(tests, false, 1.0D, craft);
            for (double personal : STEP_PLAYER_SCALES) addStepUp(tests, true, personal, craft);
        }
        return tests;
    }

    @GameTest(template = STEP_FLOOR, timeoutTicks = 200)
    public static void crouchOnSmallCraft(GameTestHelper helper) {
        crouchWalk(helper, 1.0D, 1.0D / 16.0D, 10, 34);
    }

    private record StepCase(boolean player, double personal, double craft, double stepHeight, int footprint) {
        private String label() {
            return (this.player ? "player " + ScaleFormat.number(this.personal) : "zombie")
                    + " on " + ScaleFormat.number(this.craft);
        }
    }

    private static void addStepUp(List<TestFunction> tests, boolean player, double personal, double craft) {
        double reach = MAX_UP_STEP * personal * STEP_REACH_MARGIN;
        double stepHeight = craft <= reach ? 1.0D : craft * 0.5D <= reach ? 0.5D : 0.0D;
        int footprint = (int) Math.ceil(WALKER_WIDTH * personal / craft);
        if (stepHeight == 0.0D || 2 * (footprint + 3) + 2 > STEP_FLOOR_LENGTH || footprint + 4 > STEP_FLOOR_WIDTH) return;

        StepCase step = new StepCase(player, personal, craft, stepHeight, footprint);
        String name = "stepup_" + (player ? "player_" + scaleTag(personal) : "zombie") + "_on_" + scaleTag(craft);
        tests.add(new TestFunction("defaultBatch", name, Pym.MOD_ID + ":" + STEP_FLOOR, STEP_TIMEOUT, 0L, false,
                helper -> stepUp(helper, step)));
    }

    private static String scaleTag(double scale) {
        return ScaleFormat.number(scale).replace('/', '_').replace('.', '_');
    }

    private static void stepUp(GameTestHelper helper, StepCase step) {
        int runUp = step.footprint() + 3;
        int width = step.footprint() + 2;
        ServerSubLevel craft = assembleStep(helper, runUp + step.footprint() + 3, runUp, width, step.stepHeight() < 1.0D);
        if (step.craft() != 1.0D) {
            helper.assertTrue(Pym.resize().request(craft).scaleTo(step.craft()).immediate().submit().accepted(), "resize refused");
        }

        Entity walker = step.player() ? stepPlayer(helper) : EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(walker != null, "could not create the walker");
        if (step.personal() != 1.0D) {
            helper.assertTrue(EntityScaleTracker.set(walker, step.personal(), 0),
                    "Pehkui did not accept a " + ScaleFormat.number(step.personal()) + " player");
            walker.refreshDimensions();
        }
        near(helper, walker.getBbWidth(), WALKER_WIDTH * step.personal(), step.label() + " hitbox width");
        near(helper, ((LivingEntity) walker).maxUpStep(), MAX_UP_STEP * step.personal(), step.label() + " step height");

        BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        double floorTop = plot.minY() + 1.0D;
        double stepFace = plot.minX() + runUp;
        double halfWidth = walker.getBbWidth() * 0.5D / step.craft();
        Vector3d start = new Vector3d(plot.minX() + 1.0D + halfWidth, floorTop + 0.05D, plot.minZ() + width * 0.5D);
        StepWalk walk = new StepWalk(stepFace, floorTop, step.stepHeight(), halfWidth);
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft))
                .thenIdle(STEP_SETTLE_TICKS)
                .thenExecute(() -> {
                    Vector3d world = craft.logicalPose().transformPosition(new Vector3d(start));
                    walker.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(STEP_WALK_TICKS, () -> {
                    Vector3d forward = craft.logicalPose().transformNormal(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
                    double pace = walk.ticks++ >= STEP_LAND_TICKS && !walk.stepped ? WALK_SPEED : 0.0D;
                    walker.move(MoverType.SELF, new Vec3(forward.x * pace, -GRAVITY, forward.z * pace));
                    walk.record(craft.logicalPose().transformPositionInverse(new Vector3d(walker.getX(), walker.getY(), walker.getZ())),
                            walker.onGround(), walker.horizontalCollision);
                })
                .thenExecute(() -> helper.assertTrue(walk.stepped, step.label() + " did not step up: " + walk.describe()))
                .thenSucceed();
    }

    private static Player stepPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.refreshDimensions();
        return player;
    }

    private static void pin(GameTestHelper helper, ServerSubLevel craft) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        helper.assertTrue(container != null, "no sublevel container");

        BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        Vector3d anchor = new Vector3d(
                (plot.minX() + plot.maxX() + 1) * 0.5D,
                (plot.minY() + plot.maxY() + 1) * 0.5D,
                (plot.minZ() + plot.maxZ() + 1) * 0.5D
        );
        Pose3dc pose = craft.logicalPose();
        Vector3d world = pose.transformPosition(new Vector3d(anchor));
        helper.assertTrue(container.physicsSystem().getPipeline().addConstraint(
                        craft, null, new FixedConstraintConfiguration(anchor, world, new Quaterniond(pose.orientation()))) != null,
                "could not pin the craft");
    }

    private static final class StepWalk {
        private final double stepFace;
        private final double floorTop;
        private final double stepHeight;
        private final double halfWidth;
        private int ticks;
        private boolean stepped;
        private double closestFront = Double.NEGATIVE_INFINITY;
        private double riseAtClosest;
        private double rise;
        private boolean onGround;
        private boolean blocked;

        private StepWalk(double stepFace, double floorTop, double stepHeight, double halfWidth) {
            this.stepFace = stepFace;
            this.floorTop = floorTop;
            this.stepHeight = stepHeight;
            this.halfWidth = halfWidth;
        }

        private void record(Vector3d local, boolean onGround, boolean blocked) {
            this.rise = local.y - this.floorTop;
            this.onGround = onGround;
            this.blocked = blocked;
            double front = local.x + this.halfWidth - this.stepFace;
            if (front > this.closestFront) {
                this.closestFront = front;
                this.riseAtClosest = this.rise;
            }
            if (local.x > this.stepFace && this.rise >= this.stepHeight - STEP_RISE_TOLERANCE) this.stepped = true;
        }

        private String describe() {
            return String.format(Locale.ROOT,
                    "front edge peaked %+.3f blocks past the step face at rise %.3f; ended at rise %.3f of %.2f, onGround=%s, horizontalCollision=%s",
                    this.closestFront, this.riseAtClosest, this.rise, this.stepHeight, this.onGround, this.blocked);
        }
    }

    private static ServerSubLevel assembleStep(
            GameTestHelper helper,
            int length,
            int runUp,
            int width,
            boolean slab
    ) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos floor = new BlockPos(1 + x, 2, 1 + z);
                helper.setBlock(floor, Blocks.IRON_BLOCK);
                blocks.add(helper.absolutePos(floor));
                if (x < runUp) continue;

                BlockPos step = floor.above();
                helper.setBlock(step, slab ? Blocks.SMOOTH_STONE_SLAB : Blocks.IRON_BLOCK);
                blocks.add(helper.absolutePos(step));
            }
        }

        BlockPos min = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos max = helper.absolutePos(new BlockPos(length, 3, width));
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), min, blocks, new BoundingBox3i(min, max));
        helper.assertTrue(subLevel != null && !subLevel.isRemoved(), "Sable did not assemble the step craft");
        return subLevel;
    }

    private static void crouchWalk(GameTestHelper helper, double personal, double craftScale,
                                   int footprint, int length) {
        int width = footprint + 2;
        ServerSubLevel craft = assembleStep(helper, length, length, width, false);
        if (craftScale != 1.0D) {
            helper.assertTrue(Pym.resize().request(craft).scaleTo(craftScale).immediate().submit().accepted(), "resize refused");
        }
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pym-crouch"), ClientInformation.createDefault());
        if (personal != 1.0D) helper.assertTrue(EntityScaleTracker.set(player, personal, 0), "could not scale the player");
        player.refreshDimensions();
        player.setShiftKeyDown(true);

        BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        double halfWidth = player.getBbWidth() * 0.5D / craftScale;
        double stopAt = plot.maxX() + 0.5D - halfWidth;
        Vector3d start = new Vector3d(plot.minX() + 0.5D + halfWidth, plot.minY() + 1.05D, plot.minZ() + width * 0.5D);
        CrouchWalk walk = new CrouchWalk();
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft))
                .thenIdle(STEP_SETTLE_TICKS)
                .thenExecute(() -> {
                    Vector3d world = craft.logicalPose().transformPosition(new Vector3d(start));
                    player.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(CROUCH_WALK_TICKS, () -> {
                    Vector3d before = craft.logicalPose().transformPositionInverse(
                            new Vector3d(player.getX(), player.getY(), player.getZ()));
                    boolean walking = walk.ticks++ >= STEP_LAND_TICKS && before.x < stopAt;
                    Vector3d forward = craft.logicalPose().transformNormal(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
                    double pace = walking ? 0.1D * craftScale : 0.0D;
                    player.move(MoverType.SELF, new Vec3(forward.x * pace, -GRAVITY, forward.z * pace));
                    updatePose(player);
                    walk.record(player);
                })
                .thenExecute(() -> {
                    helper.assertTrue(walk.dropped == 0, "crouching was interrupted during traversal");
                })
                .thenSucceed();
    }

    private static final class CrouchWalk {
        private int ticks;
        private int dropped;

        private void record(Player player) {
            if (this.ticks > STEP_LAND_TICKS && player.getPose() != Pose.CROUCHING) this.dropped++;
        }
    }

    private static void updatePose(Player player) {
        try {
            Method update = Player.class.getDeclaredMethod("updatePlayerPose");
            update.setAccessible(true);
            update.invoke(player);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }


    private static void near(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) <= TOLERANCE, what + ": expected " + expected + " but was " + actual);
    }

    private PymGameTests() {}
}
