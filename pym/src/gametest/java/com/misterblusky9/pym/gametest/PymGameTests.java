package com.misterblusky9.pym.gametest;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ResizeResult;
import com.misterblusky9.pym.api.ScaleDriver;
import com.misterblusky9.pym.api.event.SubLevelScaleEvent;
import com.misterblusky9.pym.api.spi.ResizePolicy;
import com.misterblusky9.pym.internal.physics.ScalePhysicsTransitions;
import com.misterblusky9.pym.internal.scale.PlotScan;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@GameTestHolder(Pym.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PymGameTests {
    private static final String EMPTY = "empty8";
    private static final double TOLERANCE = 1.0E-6D;
    private static final Set<UUID> SETTLED = ConcurrentHashMap.newKeySet();

    private static final Set<UUID> BLOCKED = ConcurrentHashMap.newKeySet();
    private static final String BLOCK_MESSAGE = "Test policy blocks this sublevel";

    static {
        NeoForge.EVENT_BUS.addListener((SubLevelScaleEvent.Settled event) -> SETTLED.add(event.subLevel().getUniqueId()));
        Pym.extensions().register((ResizePolicy) (subLevel, from, to) ->
                BLOCKED.contains(subLevel.getUniqueId()) ? BLOCK_MESSAGE : null);
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void shrinkSettlesAndPersists(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        final ResizeResult result = Pym.resize().request(subLevel).scaleTo(0.5D).submit();
        helper.assertTrue(result.accepted(), "resize refused: " + result);

        helper.succeedWhen(() -> {
            helper.assertTrue(Pym.scale().isAt(subLevel, 0.5D), "not settled at 1/2, at " + Pym.scale().of(subLevel));
            near(helper, subLevel.logicalPose().scale().x(), 0.5D, "pose scale");
            final CompoundTag stored = subLevel.getUserDataTag();
            helper.assertTrue(stored != null && stored.contains("pym_scale"), "scale was not persisted under pym_scale");
            helper.assertFalse(stored.contains("pocket_scale"), "Pym must not write the legacy key");
            near(helper, stored.getCompound("pym_scale").getDouble("current"), 0.5D, "persisted current scale");
            near(helper, stored.getCompound("pym_scale").getDouble("target"), 0.5D, "persisted target scale");
            helper.assertTrue(SETTLED.contains(subLevel.getUniqueId()), "no settle event was posted");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void growthSettles(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        helper.assertTrue(Pym.resize().request(subLevel).scaleTo(2.0D).submit().accepted(), "growth refused");
        helper.succeedWhen(() -> {
            helper.assertTrue(Pym.scale().isAt(subLevel, 2.0D), "not settled at 2x, at " + Pym.scale().of(subLevel));
            near(helper, subLevel.logicalPose().scale().x(), 2.0D, "pose scale");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void immediateResizeIsSettledAtOnce(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        final ResizeResult result = Pym.resize().request(subLevel).scaleTo(0.25D).immediate().submit();
        helper.assertTrue(result.accepted(), "immediate resize refused");
        helper.assertTrue(Pym.scale().isAt(subLevel, 0.25D), "immediate resize did not settle");
        near(helper, subLevel.logicalPose().scale().x(), 0.25D, "pose scale");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void invalidScalesAreRefused(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        for (final double scale : new double[] {Double.NaN, 0.0D, -1.0D, Double.POSITIVE_INFINITY}) {
            final ResizeResult result = Pym.resize().request(subLevel).scaleTo(scale).submit();
            helper.assertTrue(result.status() == ResizeResult.Status.INVALID_SCALE, scale + " was not refused: " + result);
        }
        helper.assertFalse(Pym.scale().isScaled(subLevel), "a refused resize changed the scale");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void arbitraryValidScalesNeedNoGlobalConsent(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        final ResizeResult large = Pym.resize().request(subLevel).scaleTo(1000.0D).check();
        final ResizeResult small = Pym.resize().request(subLevel).scaleTo(1.0E-9D).check();
        helper.assertTrue(large.accepted(), "1000x was globally gated: " + large);
        helper.assertTrue(small.accepted(), "1e-9x was globally gated: " + small);
        helper.assertFalse(Pym.scale().isScaled(subLevel), "a dry run changed the scale");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void blockedResizeIsRefusedAtSubmit(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        BLOCKED.add(subLevel.getUniqueId());

        final ResizeResult dryRun = Pym.resize().check(subLevel, 0.5D, null, true);
        final ResizeResult result = Pym.resize().request(subLevel).scaleTo(0.5D).submit();
        helper.assertTrue(result.status() == ResizeResult.Status.BLOCKED, "a blocked resize was not refused: " + result);
        helper.assertTrue(BLOCK_MESSAGE.equals(result.message()), "the policy reason was lost: " + result);
        helper.assertTrue(dryRun.equals(result), "the dry run disagrees with submit: " + dryRun + " vs " + result);

        helper.runAfterDelay(20, () -> {
            BLOCKED.remove(subLevel.getUniqueId());
            helper.assertTrue(Pym.scale().isAt(subLevel, 1.0D), "a refused resize still moved, at " + Pym.scale().of(subLevel));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void retargetingMidTransitionLandsOnTheNewTarget(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        helper.assertTrue(Pym.resize().request(subLevel).scaleTo(0.5D).ticks(20).submit().accepted(), "first resize refused");

        helper.runAfterDelay(5, () -> {
            helper.assertFalse(Pym.scale().isSettled(subLevel), "the first resize should still be running");
            final ResizeResult second = Pym.resize().request(subLevel).scaleTo(2.0D).ticks(10).submit();
            helper.assertTrue(second.accepted(), "a resize mid-transition was refused: " + second);
            helper.assertTrue(Pym.scale().target(subLevel) == 2.0D, "the new target did not replace the old one");
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(Pym.scale().isAt(subLevel, 2.0D), "did not land on the new target, at " + Pym.scale().of(subLevel));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void driverResizesAndHearsWhenDone(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        final double[] completed = {Double.NaN};
        final ScaleDriver driver = new ScaleDriver() {
            @Override public double commandedScale() { return 0.5D; }
            @Override public boolean isRemoved() { return false; }
            @Override public void onTransitionCompleted(final ServerSubLevel done, final double scale) { completed[0] = scale; }
        };
        Pym.resize().drive(subLevel, driver, helper.getLevel().getGameTime() + 400L);

        helper.succeedWhen(() -> {
            helper.assertTrue(Pym.scale().isAt(subLevel, 0.5D), "the driver's scale was not reached, at " + Pym.scale().of(subLevel));
            helper.assertTrue(completed[0] == 0.5D, "the driver was not told the transition finished");
            Pym.resize().release(subLevel.getUniqueId());
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void framesRoundTrip(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        Pym.resize().request(subLevel).scaleTo(0.5D).immediate().submit();

        final Vector3d local = new Vector3d(subLevel.getPlot().getCenterBlock().getX() + 0.5D, 64.25D, 3.0D);
        final Vector3d back = Pym.frames().toLocal(subLevel, Pym.frames().toWorld(subLevel, local));
        helper.assertTrue(back.distance(local) < 1.0E-6D, "local -> world -> local drifted: " + back + " vs " + local);

        final Vector3d unit = new Vector3d(0.0D, 1.0D, 0.0D);
        near(helper, Pym.frames().vectorToWorld(subLevel, unit).length(), 0.5D, "a local block edge is half a world block");
        near(helper, Pym.frames().directionToWorld(subLevel, unit).length(), 1.0D, "directions stay unit length");
        near(helper, Pym.frames().lengthToLocal(subLevel, 1.0D), 2.0D, "one world block spans two local blocks");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void removalClearsTransitionRuntimeState(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        final UUID id = subLevel.getUniqueId();
        final ResizeResult result = Pym.resize().request(subLevel).scaleTo(0.5D).submit();
        helper.assertTrue(result.accepted(), "resize refused before cleanup check: " + result);
        helper.assertTrue(hasTransitionState(id), "resize did not create transition runtime state");

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        helper.assertTrue(container != null, "no sublevel container");
        container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        helper.assertFalse(hasTransitionState(id), "removed sublevel leaked ScalePhysicsTransitions state");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void scaleSurvivesSaveAndReload(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        Pym.resize().request(subLevel).scaleTo(0.5D).submit();

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(Pym.scale().isAt(subLevel, 0.5D), "not settled before save");
            final ServerSubLevel restored = reload(helper, subLevel, fullTag -> {});
            helper.assertTrue(Pym.scale().isAt(restored, 0.5D), "reload lost the scale, at " + Pym.scale().of(restored));
            near(helper, restored.logicalPose().scale().x(), 0.5D, "restored pose scale");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pocketSizedWorldsAreMigrated(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);

        helper.runAfterDelay(5, () -> {
            final ServerSubLevel restored = reload(helper, subLevel, fullTag -> {
                final CompoundTag legacy = new CompoundTag();
                legacy.putDouble("current", 0.25D);
                legacy.putInt("stable_stage", 2);
                legacy.putInt("requested_stage", 2);
                final CompoundTag user = fullTag.getCompound("user_data");
                user.remove("pym_scale");
                user.put("pocket_scale", legacy);
                fullTag.put("user_data", user);
            });

            helper.assertTrue(Pym.scale().isAt(restored, 0.25D), "0.19 pocket_scale was not read, at " + Pym.scale().of(restored));
            near(helper, restored.logicalPose().scale().x(), 0.25D, "restored pose scale");

            SubLevelSerializer.toData(restored, List.of());
            final CompoundTag user = restored.getUserDataTag();
            helper.assertTrue(user.contains("pym_scale"), "the next save did not write pym_scale");
            helper.assertFalse(user.contains("pocket_scale"), "the legacy key was not retired");
            helper.succeed();
        });
    }

    @SuppressWarnings("unchecked")
    private static boolean hasTransitionState(final UUID id) {
        try {
            final Field field = ScalePhysicsTransitions.class.getDeclaredField("STATES");
            field.setAccessible(true);
            return ((Map<UUID, ?>) field.get(null)).containsKey(id);
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError("ScalePhysicsTransitions test seam changed", exception);
        }
    }

    private interface TagEdit {
        void apply(CompoundTag fullTag);
    }

    private static ServerSubLevel reload(final GameTestHelper helper, final ServerSubLevel subLevel, final TagEdit edit) {
        final ServerLevel level = helper.getLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        helper.assertTrue(container != null, "no sublevel container");

        final SubLevelData data = SubLevelSerializer.toData(subLevel, List.of());
        final CompoundTag fullTag = data.fullTag().copy();
        edit.apply(fullTag);

        container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        final ServerSubLevel restored = SubLevelSerializer.fullyLoad(level, SubLevelSerializer.fromData(fullTag));
        helper.assertTrue(restored != null && !restored.isRemoved(), "Sable did not reload the sublevel");
        return restored;
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void noShrinkBlocksStopShrinkingOnly(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        final int limit = PlotScan.shrunkBlockLimit();
        try {
            PlotScan.configure(Set.of(Blocks.IRON_BLOCK), limit);
            helper.assertTrue(Pym.resize().contents(subLevel).hasNoShrinkBlock(), "the iron block was not found");
            final ResizeResult shrink = Pym.resize().request(subLevel).scaleTo(0.5D).check();
            helper.assertTrue(shrink.status() == ResizeResult.Status.BLOCKED && shrink.message() != null,
                    "a no-shrink block did not refuse shrinking with a reason: " + shrink);
            helper.assertTrue(Pym.resize().request(subLevel).scaleTo(2.0D).check().accepted(),
                    "a no-shrink block must not stop growing");

            PlotScan.configure(Set.of(), 4);
            final ResizeResult crowded = Pym.resize().request(subLevel).scaleTo(0.5D).check();
            helper.assertTrue(crowded.status() == ResizeResult.Status.BLOCKED,
                    "8 blocks shrank past a limit of 4: " + crowded);
        } finally {
            PlotScan.configure(Set.of(), limit);
        }
        helper.assertTrue(Pym.resize().request(subLevel).scaleTo(0.5D).check().accepted(), "restoring the config did not clear the refusal");
        helper.succeed();
    }

    private static ServerSubLevel assembleCube(final GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        final List<BlockPos> blocks = new ArrayList<>();
        for (int x = 3; x <= 4; x++) {
            for (int y = 2; y <= 3; y++) {
                for (int z = 3; z <= 4; z++) {
                    final BlockPos relative = new BlockPos(x, y, z);
                    helper.setBlock(relative, Blocks.IRON_BLOCK);
                    blocks.add(helper.absolutePos(relative));
                }
            }
        }

        final BlockPos min = helper.absolutePos(new BlockPos(3, 2, 3));
        final BlockPos max = helper.absolutePos(new BlockPos(4, 3, 4));
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), min, blocks, new BoundingBox3i(min, max));
        helper.assertTrue(subLevel != null && !subLevel.isRemoved(), "Sable did not assemble the test craft");
        return subLevel;
    }

    private static void near(final GameTestHelper helper, final double actual, final double expected, final String what) {
        helper.assertTrue(Math.abs(actual - expected) <= TOLERANCE, what + ": expected " + expected + " but was " + actual);
    }

    private PymGameTests() {}
}
