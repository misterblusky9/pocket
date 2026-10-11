package com.misterblusky9.pym.gametest;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ResizeResult;
import com.misterblusky9.pym.api.ScaleFormat;
import com.misterblusky9.pym.api.ScaleDriver;
import com.misterblusky9.pym.api.event.SubLevelScaleEvent;
import com.misterblusky9.pym.api.spi.ResizePolicy;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import com.misterblusky9.pym.internal.physics.ScalePhysicsTransitions;
import com.misterblusky9.pym.internal.scale.PlotScan;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@GameTestHolder(Pym.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PymGameTests {
    private static final String EMPTY = "empty8";
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

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void edgeProbeScanKeepsTheCraftsOuterCells(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    final Vector3d face = plotPoint(subLevel, 1.0D, 0.5D, 0.5D);
                    final AABB outerColumn = new AABB(face.x - 0.2D, face.y - 0.5D, face.z - 0.5D, face.x + 0.6D, face.y + 0.5D, face.z + 0.5D);
                    helper.assertTrue(CanFallAtleastHelper.canFallAtleastWithSubLevels(helper.getLevel(), outerColumn) != null,
                            "the edge probe missed the craft's outer column");
                    helper.assertTrue(Pym.resize().request(subLevel).scaleTo(1.0D / 16.0D).immediate().submit().accepted(), "resize refused");
                })
                .thenExecuteAfter(2, () -> {
                    near(helper, subLevel.lastPose().scale().x(), 1.0D / 16.0D, "last pose scale");
                    final Vector3d centre = plotPoint(subLevel, 0.5D, 0.5D, 0.5D);
                    final AABB player = AABB.ofSize(new Vec3(centre.x, centre.y, centre.z), 0.6D, 1.8D, 0.6D);
                    helper.assertTrue(CanFallAtleastHelper.canFallAtleastWithSubLevels(helper.getLevel(), player) != null,
                            "a player-sized probe missed the 1/16 craft");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void fluidScanKeepsTheCraftsOuterCells(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleWaterColumn(helper);
        final BoundingBox3ic plot = subLevel.getPlot().getBoundingBox();
        final Vector3d waterCell = new Vector3d(plot.minX() + 0.5D, plot.maxY() + 0.5D, plot.minZ() + 0.5D);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(touchesWater(helper, subLevel, waterCell), "an entity on the craft's water cell stayed dry");
                    helper.assertTrue(Pym.resize().request(subLevel).scaleTo(1.0D / 16.0D).immediate().submit().accepted(), "resize refused");
                })
                .thenExecuteAfter(2, () -> {
                    near(helper, subLevel.lastPose().scale().x(), 1.0D / 16.0D, "last pose scale");
                    helper.assertTrue(touchesWater(helper, subLevel, waterCell), "an entity on the 1/16 craft's water cell stayed dry");
                })
                .thenSucceed();
    }

    @GameTestGenerator
    public static Collection<TestFunction> stepUpMatrix() {
        final List<TestFunction> tests = new ArrayList<>();
        for (final double craft : STEP_CRAFT_SCALES) {
            addStepUp(tests, false, 1.0D, craft);
            for (final double personal : STEP_PLAYER_SCALES) addStepUp(tests, true, personal, craft);
        }
        return tests;
    }

    @GameTest(template = STEP_FLOOR, timeoutTicks = 200)
    public static void crouchOnSmallCraft(final GameTestHelper helper) {
        crouchWalk(helper, 1.0D, 1.0D / 16.0D, 10, 34);
    }

    private record StepCase(boolean player, double personal, double craft, double stepHeight, int footprint) {
        private String label() {
            return (this.player ? "player " + ScaleFormat.number(this.personal) : "zombie")
                    + " on " + ScaleFormat.number(this.craft);
        }
    }

    private static void addStepUp(final List<TestFunction> tests, final boolean player, final double personal, final double craft) {
        final double reach = MAX_UP_STEP * personal * STEP_REACH_MARGIN;
        final double stepHeight = craft <= reach ? 1.0D : craft * 0.5D <= reach ? 0.5D : 0.0D;
        final int footprint = (int) Math.ceil(WALKER_WIDTH * personal / craft);
        if (stepHeight == 0.0D || 2 * (footprint + 3) + 2 > STEP_FLOOR_LENGTH || footprint + 4 > STEP_FLOOR_WIDTH) return;

        final StepCase step = new StepCase(player, personal, craft, stepHeight, footprint);
        final String name = "stepup_" + (player ? "player_" + scaleTag(personal) : "zombie") + "_on_" + scaleTag(craft);
        tests.add(new TestFunction("defaultBatch", name, Pym.MOD_ID + ":" + STEP_FLOOR, STEP_TIMEOUT, 0L, false,
                helper -> stepUp(helper, step)));
    }

    private static String scaleTag(final double scale) {
        return ScaleFormat.number(scale).replace('/', '_').replace('.', '_');
    }

    private static void stepUp(final GameTestHelper helper, final StepCase step) {
        final int runUp = step.footprint() + 3;
        final int width = step.footprint() + 2;
        final ServerSubLevel craft = assembleStep(helper, runUp + step.footprint() + 3, runUp, width, step.stepHeight() < 1.0D);
        if (step.craft() != 1.0D) {
            helper.assertTrue(Pym.resize().request(craft).scaleTo(step.craft()).immediate().submit().accepted(), "resize refused");
        }

        final Entity walker = step.player() ? stepPlayer(helper) : EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(walker != null, "could not create the walker");
        if (step.personal() != 1.0D) {
            helper.assertTrue(EntityScaleTracker.set(walker, step.personal(), 0),
                    "Pehkui did not accept a " + ScaleFormat.number(step.personal()) + " player");
            walker.refreshDimensions();
        }
        near(helper, walker.getBbWidth(), WALKER_WIDTH * step.personal(), step.label() + " hitbox width");
        near(helper, ((LivingEntity) walker).maxUpStep(), MAX_UP_STEP * step.personal(), step.label() + " step height");

        final BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        final double floorTop = plot.minY() + 1.0D;
        final double stepFace = plot.minX() + runUp;
        final double halfWidth = walker.getBbWidth() * 0.5D / step.craft();
        final Vector3d start = new Vector3d(plot.minX() + 1.0D + halfWidth, floorTop + 0.05D, plot.minZ() + width * 0.5D);
        final StepWalk walk = new StepWalk(stepFace, floorTop, step.stepHeight(), halfWidth);
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft))
                .thenIdle(STEP_SETTLE_TICKS)
                .thenExecute(() -> {
                    final Vector3d world = craft.logicalPose().transformPosition(new Vector3d(start));
                    walker.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(STEP_WALK_TICKS, () -> {
                    final Vector3d forward = craft.logicalPose().transformNormal(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
                    final double pace = walk.ticks++ >= STEP_LAND_TICKS && !walk.stepped ? WALK_SPEED : 0.0D;
                    walker.move(MoverType.SELF, new Vec3(forward.x * pace, -GRAVITY, forward.z * pace));
                    walk.record(craft.logicalPose().transformPositionInverse(new Vector3d(walker.getX(), walker.getY(), walker.getZ())),
                            walker.onGround(), walker.horizontalCollision);
                })
                .thenExecute(() -> helper.assertTrue(walk.stepped, step.label() + " did not step up: " + walk.describe()))
                .thenSucceed();
    }

    private static Player stepPlayer(final GameTestHelper helper) {
        final Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.refreshDimensions();
        return player;
    }

    private static void pin(final GameTestHelper helper, final ServerSubLevel craft) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        helper.assertTrue(container != null, "no sublevel container");

        final BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        final Vector3d anchor = new Vector3d(
                (plot.minX() + plot.maxX() + 1) * 0.5D,
                (plot.minY() + plot.maxY() + 1) * 0.5D,
                (plot.minZ() + plot.maxZ() + 1) * 0.5D
        );
        final Pose3dc pose = craft.logicalPose();
        final Vector3d world = pose.transformPosition(new Vector3d(anchor));
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

        private StepWalk(final double stepFace, final double floorTop, final double stepHeight, final double halfWidth) {
            this.stepFace = stepFace;
            this.floorTop = floorTop;
            this.stepHeight = stepHeight;
            this.halfWidth = halfWidth;
        }

        private void record(final Vector3d local, final boolean onGround, final boolean blocked) {
            this.rise = local.y - this.floorTop;
            this.onGround = onGround;
            this.blocked = blocked;
            final double front = local.x + this.halfWidth - this.stepFace;
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
            final GameTestHelper helper,
            final int length,
            final int runUp,
            final int width,
            final boolean slab
    ) {
        final List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < width; z++) {
                final BlockPos floor = new BlockPos(1 + x, 2, 1 + z);
                helper.setBlock(floor, Blocks.IRON_BLOCK);
                blocks.add(helper.absolutePos(floor));
                if (x < runUp) continue;

                final BlockPos step = floor.above();
                helper.setBlock(step, slab ? Blocks.SMOOTH_STONE_SLAB : Blocks.IRON_BLOCK);
                blocks.add(helper.absolutePos(step));
            }
        }

        final BlockPos min = helper.absolutePos(new BlockPos(1, 2, 1));
        final BlockPos max = helper.absolutePos(new BlockPos(length, 3, width));
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), min, blocks, new BoundingBox3i(min, max));
        helper.assertTrue(subLevel != null && !subLevel.isRemoved(), "Sable did not assemble the step craft");
        return subLevel;
    }

    private static void crouchWalk(final GameTestHelper helper, final double personal, final double craftScale,
                                   final int footprint, final int length) {
        final int width = footprint + 2;
        final ServerSubLevel craft = assembleStep(helper, length, length, width, false);
        if (craftScale != 1.0D) {
            helper.assertTrue(Pym.resize().request(craft).scaleTo(craftScale).immediate().submit().accepted(), "resize refused");
        }
        final ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pym-crouch"), ClientInformation.createDefault());
        if (personal != 1.0D) helper.assertTrue(EntityScaleTracker.set(player, personal, 0), "could not scale the player");
        player.refreshDimensions();
        player.setShiftKeyDown(true);

        final BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        final double halfWidth = player.getBbWidth() * 0.5D / craftScale;
        final double stopAt = plot.maxX() + 0.5D - halfWidth;
        final Vector3d start = new Vector3d(plot.minX() + 0.5D + halfWidth, plot.minY() + 1.05D, plot.minZ() + width * 0.5D);
        final CrouchWalk walk = new CrouchWalk();
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft))
                .thenIdle(STEP_SETTLE_TICKS)
                .thenExecute(() -> {
                    final Vector3d world = craft.logicalPose().transformPosition(new Vector3d(start));
                    player.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(CROUCH_WALK_TICKS, () -> {
                    final Vector3d before = craft.logicalPose().transformPositionInverse(
                            new Vector3d(player.getX(), player.getY(), player.getZ()));
                    final boolean walking = walk.ticks++ >= STEP_LAND_TICKS && before.x < stopAt;
                    final Vector3d forward = craft.logicalPose().transformNormal(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
                    final double pace = walking ? 0.1D * craftScale : 0.0D;
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

        private void record(final Player player) {
            if (this.ticks > STEP_LAND_TICKS && player.getPose() != Pose.CROUCHING) this.dropped++;
        }
    }

    private static void updatePose(final Player player) {
        try {
            final Method update = Player.class.getDeclaredMethod("updatePlayerPose");
            update.setAccessible(true);
            update.invoke(player);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean touchesWater(final GameTestHelper helper, final ServerSubLevel subLevel, final Vector3d plotCell) {
        final Vector3d world = subLevel.lastPose().transformPosition(new Vector3d(plotCell));
        final Pig pig = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(pig != null, "could not create a pig");
        pig.setPos(world.x, world.y - pig.getBbHeight() * 0.5D, world.z);
        pig.updateFluidHeightAndDoFluidPushing();
        return pig.getFluidTypeHeight(NeoForgeMod.WATER_TYPE.value()) > 0.0D;
    }

    private static Vector3d plotPoint(final ServerSubLevel subLevel, final double fx, final double fy, final double fz) {
        final BoundingBox3ic plot = subLevel.getPlot().getBoundingBox();
        return subLevel.lastPose().transformPosition(new Vector3d(
                plot.minX() + (plot.maxX() + 1 - plot.minX()) * fx,
                plot.minY() + (plot.maxY() + 1 - plot.minY()) * fy,
                plot.minZ() + (plot.maxZ() + 1 - plot.minZ()) * fz
        ));
    }

    private static ServerSubLevel assembleWaterColumn(final GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        final BlockPos glass = new BlockPos(3, 2, 3);
        final BlockPos water = glass.above();
        helper.setBlock(glass, Blocks.GLASS);
        helper.setBlock(water, Blocks.WATER);

        final BlockPos min = helper.absolutePos(glass);
        final BlockPos max = helper.absolutePos(water);
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), min, List.of(min, max), new BoundingBox3i(min, max));
        helper.assertTrue(subLevel != null && !subLevel.isRemoved(), "Sable did not assemble the water craft");
        return subLevel;
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
