package com.misterblusky9.pym.gametest;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleFormat;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@GameTestHolder(Pym.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PymCollisionBenchmarks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FLOOR = "empty48";
    private static final int LENGTH = 40;
    private static final int WIDTH = 20;
    private static final int LAYERS = 3;
    private static final int SETTLE_TICKS = 20;
    private static final int WARMUP_MOVES = 60;
    private static final int TIMED_MOVES = 100;
    private static final double WALK = 0.1D;
    private static final double GRAVITY = 0.08D;

    private static final double[][] CASES = {
            {1.0D, 1.0D}, {1.0D, 1.0D / 4.0D}, {1.0D, 1.0D / 8.0D}, {1.0D, 1.0D / 16.0D},
            {1.0D, 1.0D / 32.0D}, {16.0D, 1.0D}, {16.0D, 1.0D / 32.0D},
            {1.0D, 0.0D}, {16.0D, 0.0D},
    };

    @GameTestGenerator
    public static List<TestFunction> collisionBenchmarks() {
        final List<TestFunction> tests = new ArrayList<>();
        for (final double[] c : CASES) {
            final String name = "collision_bench_" + tag(c[0]) + "_on_" + (c[1] == 0.0D ? "terrain" : tag(c[1]));
            tests.add(new TestFunction("collisionBench", name, Pym.MOD_ID + ":" + FLOOR, 600, 0L, false,
                    helper -> run(helper, c[0], c[1])));
        }
        return tests;
    }

    private static void run(final GameTestHelper helper, final double walkerScale, final double craftScale) {
        if (craftScale == 0.0D) {
            runOnTerrain(helper, walkerScale);
            return;
        }
        final ServerSubLevel craft = assembleTerrain(helper);
        if (craftScale != 1.0D) {
            helper.assertTrue(Pym.resize().request(craft).scaleTo(craftScale).immediate().submit().accepted(), "resize refused");
        }
        final Player walker = helper.makeMockPlayer(GameType.SURVIVAL);
        if (walkerScale != 1.0D) {
            helper.assertTrue(EntityScaleTracker.set(walker, walkerScale, 0), "could not scale the walker");
        }
        walker.refreshDimensions();

        final long[] nanos = new long[TIMED_MOVES];
        final int[] moves = {0};
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    final BoundingBox3ic plot = craft.getPlot().getBoundingBox();
                    final Vector3d top = new Vector3d(
                            (plot.minX() + plot.maxX() + 1) * 0.5D, plot.maxY() + 1.05D, (plot.minZ() + plot.maxZ() + 1) * 0.5D);
                    final Vector3d world = craft.logicalPose().transformPosition(top);
                    walker.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(WARMUP_MOVES + TIMED_MOVES, () -> {
                    final Vector3d forward = craft.logicalPose().transformNormal(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
                    final double pace = (moves[0] % 2 == 0 ? WALK : -WALK) * walkerScale;
                    final Vec3 motion = new Vec3(forward.x * pace, -GRAVITY * walkerScale, forward.z * pace);
                    final long start = System.nanoTime();
                    walker.move(MoverType.SELF, motion);
                    final long spent = System.nanoTime() - start;
                    final int index = moves[0]++ - WARMUP_MOVES;
                    if (index >= 0) nanos[index] = spent;
                })
                .thenExecute(() -> report(walkerScale, craftScale, nanos, walker.onGround()))
                .thenSucceed();
    }

    private static void runOnTerrain(final GameTestHelper helper, final double walkerScale) {
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                final int height = LAYERS + Math.floorMod(x * 7 + z * 13, 3) - 1;
                for (int y = 0; y < height; y++) helper.setBlock(new BlockPos(2 + x, 2 + y, 2 + z), Blocks.STONE);
            }
        }
        final Player walker = helper.makeMockPlayer(GameType.SURVIVAL);
        if (walkerScale != 1.0D) helper.assertTrue(EntityScaleTracker.set(walker, walkerScale, 0), "could not scale the walker");
        walker.refreshDimensions();
        final Vec3 start = helper.absoluteVec(new Vec3(2 + LENGTH * 0.5D, 2 + LAYERS + 1.05D, 2 + WIDTH * 0.5D));
        final long[] nanos = new long[TIMED_MOVES];
        final int[] moves = {0};
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> walker.setPos(start.x, start.y, start.z))
                .thenExecuteFor(WARMUP_MOVES + TIMED_MOVES, () -> {
                    final double pace = (moves[0] % 2 == 0 ? WALK : -WALK) * walkerScale;
                    final long begin = System.nanoTime();
                    walker.move(MoverType.SELF, new Vec3(pace, -GRAVITY * walkerScale, 0.0D));
                    final long spent = System.nanoTime() - begin;
                    final int index = moves[0]++ - WARMUP_MOVES;
                    if (index >= 0) nanos[index] = spent;
                })
                .thenExecute(() -> report(walkerScale, 0.0D, nanos, walker.onGround()))
                .thenSucceed();
    }

    private static void report(final double walkerScale, final double craftScale, final long[] nanos, final boolean onGround) {
        final long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        LOGGER.info(String.format(Locale.ROOT,
                "collision-bench walker=%s craft=%s ratio=%s meanUs=%.1f medianUs=%.1f maxUs=%.1f onGround=%s",
                tag(walkerScale), craftScale == 0.0D ? "terrain" : tag(craftScale),
                craftScale == 0.0D ? "-" : String.format(Locale.ROOT, "%.0f:1", walkerScale / craftScale),
                Arrays.stream(nanos).average().orElse(0.0D) / 1000.0D,
                sorted[sorted.length / 2] / 1000.0D, sorted[sorted.length - 1] / 1000.0D, onGround));
    }

    private static ServerSubLevel assembleTerrain(final GameTestHelper helper) {
        final List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                final int height = LAYERS + Math.floorMod(x * 7 + z * 13, 3) - 1;
                for (int y = 0; y < height; y++) {
                    final BlockPos relative = new BlockPos(2 + x, 2 + y, 2 + z);
                    helper.setBlock(relative, Blocks.STONE);
                    blocks.add(helper.absolutePos(relative));
                }
            }
        }
        final BlockPos min = helper.absolutePos(new BlockPos(2, 2, 2));
        final BlockPos max = helper.absolutePos(new BlockPos(1 + LENGTH, 1 + LAYERS + 1, 1 + WIDTH));
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), min, blocks, new BoundingBox3i(min, max));
        helper.assertTrue(subLevel != null && !subLevel.isRemoved(), "Sable did not assemble the bench terrain");
        return subLevel;
    }

    private static void pin(final GameTestHelper helper, final ServerSubLevel craft) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        final BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        final Vector3d anchor = new Vector3d(
                (plot.minX() + plot.maxX() + 1) * 0.5D, (plot.minY() + plot.maxY() + 1) * 0.5D, (plot.minZ() + plot.maxZ() + 1) * 0.5D);
        final Pose3dc pose = craft.logicalPose();
        final Vector3d world = pose.transformPosition(new Vector3d(anchor));
        container.physicsSystem().getPipeline().addConstraint(
                craft, null, new FixedConstraintConfiguration(anchor, world, new Quaterniond(pose.orientation())));
    }

    private static String tag(final double scale) {
        return ScaleFormat.number(scale).replace('/', '_').replace('.', '_');
    }

    private PymCollisionBenchmarks() {}
}
