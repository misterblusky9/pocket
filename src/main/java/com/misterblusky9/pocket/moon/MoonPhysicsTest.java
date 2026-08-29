package com.misterblusky9.pocket.moon;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Locale;

@EventBusSubscriber(modid = "pocket")
public final class MoonPhysicsTest {
    private static final double DEFAULT_DISTANCE = 24.0D;
    private static final double MIN_DISTANCE = 8.0D;
    private static final double MAX_DISTANCE = 96.0D;
    private static final double BASE_HALF_EXTENT = 16.0D;
    private static final double MASS = 8.0D;
    private static final double PUNCH_IMPULSE = 24.0D;
    private static final double MIN_HALF_EXTENT = 0.02D;
    private static final double SCALE_EPSILON = 1.0E-6D;
    private static TestBody active;

    /*
    @SubscribeEvent
    public static void registerCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("pocket")
                        .then(Commands.literal("moonPhysics")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("spawn")
                                        .executes(context -> spawn(
                                                context.getSource().getPlayerOrException(),
                                                DEFAULT_DISTANCE
                                        ))
                                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(MIN_DISTANCE, MAX_DISTANCE))
                                                .executes(context -> spawn(
                                                        context.getSource().getPlayerOrException(),
                                                        DoubleArgumentType.getDouble(context, "distance")
                                                ))))
                                .then(Commands.literal("remove")
                                        .executes(context -> remove(context.getSource())))
                                .then(Commands.literal("status")
                                        .executes(context -> status(context.getSource())))
                                .then(Commands.literal("kick")
                                        .executes(context -> kick(context.getSource().getPlayerOrException(), 8.0D))
                                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(-64.0D, 64.0D))
                                                .executes(context -> kick(
                                                        context.getSource().getPlayerOrException(),
                                                        DoubleArgumentType.getDouble(context, "speed")
                                                ))))
                                .then(Commands.literal("teleport")
                                        .executes(context -> teleportToMoonDirection(
                                                context.getSource().getPlayerOrException(),
                                                active != null ? active.distance : DEFAULT_DISTANCE
                                        )))
                                .then(Commands.literal("debug")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(context -> debug(
                                                        context.getSource(),
                                                        BoolArgumentType.getBool(context, "enabled")
                                                )))))
        );
    }
     */

    @SubscribeEvent
    public static void tick(final ServerTickEvent.Post event) {
        final TestBody body = active;
        if (body == null) return;
        if (body.server != event.getServer()) {
            active = null;
            return;
        }
        if (!body.box.isActive()) {
            MoonPhysicsState.clearServer(body.level);
            MoonScaleNetwork.broadcastPhysicsRemoved(body.level);
            active = null;
            return;
        }

        body.box.updatePose();
        final float moonScale = MoonScale.get(event.getServer());
        if (Math.abs(moonScale - body.scale) > SCALE_EPSILON) {
            if (!body.resize(moonScale)) {
                MoonPhysicsState.clearServer(body.level);
                MoonScaleNetwork.broadcastPhysicsRemoved(body.level);
                active = null;
                return;
            }
        }

        final MoonSubLevel tickShim = MoonSubLevels.getOrCreate(body.level);

        final Pose3dc pose = body.box.getPose();
        MoonPhysicsState.updateServer(
                body.level,
                pose.position().x(),
                pose.position().y(),
                pose.position().z(),
                body.halfExtent(),
                pose.orientation().x(),
                pose.orientation().y(),
                pose.orientation().z(),
                pose.orientation().w()
        );
        MoonScaleNetwork.broadcastPhysics(
                body.level,
                pose.position().x(),
                pose.position().y(),
                pose.position().z(),
                body.halfExtent(),
                pose.orientation().x(),
                pose.orientation().y(),
                pose.orientation().z(),
                pose.orientation().w(),
                tickShim == null ? 0 : tickShim.getPlot().getChunkMin().x,
                tickShim == null ? 0 : tickShim.getPlot().getChunkMin().z
        );

        if (body.debugParticles && (body.level.getGameTime() & 3L) == 0L) {
            render(body);
        }
    }

    @SubscribeEvent
    public static void stopping(final ServerStoppingEvent event) {
        remove(event.getServer(), false);
    }

    private static int spawn(final ServerPlayer player, final double distance) {
        final ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            player.sendSystemMessage(Component.literal("Moon physics test only runs in the Overworld."));
            return 0;
        }

        final Vector3d direction = moonDirection(level);
        if (direction.y <= 0.05D) {
            player.sendSystemMessage(Component.literal("The moon is below the horizon. Set the time near midnight and try again."));
            return 0;
        }

        remove(player.serverLevel().getServer(), false);

        final Vec3 eye = player.getEyePosition();
        final Vector3d position = new Vector3d(
                eye.x + direction.x * distance,
                eye.y + direction.y * distance,
                eye.z + direction.z * distance
        );
        final float scale = MoonScale.get(player.serverLevel().getServer());
        final TestBody body = new TestBody(
                player.serverLevel().getServer(),
                level,
                distance,
                scale,
                position
        );

        if (!body.add()) {
            player.sendSystemMessage(Component.literal("Moon physics proxy would intersect unloaded chunks. Try a smaller distance."));
            return 0;
        }

        active = body;
        body.box.updatePose();
        final MoonSubLevel spawnShim = MoonSubLevels.getOrCreate(level);
        final Pose3dc spawnedPose = body.box.getPose();
        MoonPhysicsState.updateServer(
                body.level,
                spawnedPose.position().x(),
                spawnedPose.position().y(),
                spawnedPose.position().z(),
                body.halfExtent(),
                spawnedPose.orientation().x(),
                spawnedPose.orientation().y(),
                spawnedPose.orientation().z(),
                spawnedPose.orientation().w()
        );
        MoonScaleNetwork.broadcastPhysics(
                body.level,
                spawnedPose.position().x(),
                spawnedPose.position().y(),
                spawnedPose.position().z(),
                body.halfExtent(),
                spawnedPose.orientation().x(),
                spawnedPose.orientation().y(),
                spawnedPose.orientation().z(),
                spawnedPose.orientation().w(),
                spawnShim == null ? 0 : spawnShim.getPlot().getChunkMin().x,
                spawnShim == null ? 0 : spawnShim.getPlot().getChunkMin().z
        );
        player.sendSystemMessage(Component.literal(
                "Spawned Sable moon proxy at " + format(position)
                        + " halfExtent=" + format(body.halfExtent())
                        + " diameter=" + format(body.halfExtent() * 2.0D)
                        + " scale=" + format(scale)
                        + " mass=" + MASS
                        + " runtimeId=" + body.box.getRuntimeId()
        ));
        return 1;
    }

    private static int remove(final CommandSourceStack source) {
        final TestBody body = active;
        if (body == null || body.server != source.getServer()) {
            source.sendFailure(Component.literal("No moon physics proxy is active."));
            return 0;
        }

        remove(source.getServer(), true);
        source.sendSuccess(() -> Component.literal("Removed Sable moon physics proxy."), false);
        return 1;
    }

    private static int remove(final MinecraftServer server, final boolean notifyClients) {
        final TestBody body = active;
        if (body == null || body.server != server) return 0;
        MoonSubLevels.release(body.level);
        MoonPhysicsState.clearServer(body.level);
        if (notifyClients) MoonScaleNetwork.broadcastPhysicsRemoved(body.level);
        if (body.box.isActive()) {
            body.physics.removeObject(body.box);
        }
        active = null;
        return 1;
    }

    private static int kick(final ServerPlayer player, final double speed) {
        final TestBody body = active;
        if (body == null || body.server != player.serverLevel().getServer() || !body.box.isActive()) {
            player.sendSystemMessage(Component.literal("No moon physics proxy is active."));
            return 0;
        }

        final Vec3 look = player.getLookAngle();
        body.pipeline.addLinearAndAngularVelocity(
                body.box,
                new Vector3d(look.x * speed, look.y * speed, look.z * speed),
                new Vector3d()
        );
        return 1;
    }

    public static void punch(final ServerPlayer player) {
        final TestBody body = active;
        if (body == null || body.server != player.serverLevel().getServer() || body.level != player.serverLevel() || !body.box.isActive()) return;

        body.box.updatePose();
        final Pose3dc pose = body.box.getPose();
        MoonPhysicsState.updateServer(
                body.level,
                pose.position().x(),
                pose.position().y(),
                pose.position().z(),
                body.halfExtent(),
                pose.orientation().x(),
                pose.orientation().y(),
                pose.orientation().z(),
                pose.orientation().w()
        );

        final double reach = 5.0D;
        final Vec3 from = player.getEyePosition();
        final Vec3 look = player.getLookAngle();
        final Vec3 to = from.add(look.scale(reach));
        final MoonPhysicsState.RayHit hit = MoonPhysicsState.raycast(body.level, from, to);
        if (hit == null) return;

        final BlockHitResult blockHit = body.level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (blockHit.getType() == HitResult.Type.BLOCK
                && from.distanceToSqr(blockHit.getLocation()) + 1.0E-4D < from.distanceToSqr(hit.worldPoint())) {
            return;
        }

        final Vector3d localImpulse = new Quaterniond(pose.orientation()).conjugate()
                .transform(new Vector3d(look.x, look.y, look.z).mul(PUNCH_IMPULSE));
        body.pipeline.applyImpulse(body.box, hit.localPoint(), localImpulse);
    }

    private static int teleportToMoonDirection(final ServerPlayer player, final double distance) {
        final TestBody body = active;
        if (body == null || body.server != player.serverLevel().getServer() || !body.box.isActive()) {
            player.sendSystemMessage(Component.literal("No moon physics proxy is active."));
            return 0;
        }

        final Vector3d direction = moonDirection(body.level);
        final Vec3 eye = player.getEyePosition();
        final Vector3d position = new Vector3d(
                eye.x + direction.x * distance,
                eye.y + direction.y * distance,
                eye.z + direction.z * distance
        );
        body.pipeline.teleport(body.box, position, body.box.getPose().orientation());
        body.pipeline.resetVelocity(body.box);
        body.box.updatePose();
        return 1;
    }

    private static int status(final CommandSourceStack source) {
        final TestBody body = active;
        if (body == null || body.server != source.getServer() || !body.box.isActive()) {
            source.sendSuccess(() -> Component.literal("Moon physics proxy: inactive"), false);
            return 0;
        }

        body.box.updatePose();
        final Vector3d linear = body.pipeline.getLinearVelocity(body.box, new Vector3d());
        final Vector3d angular = body.pipeline.getAngularVelocity(body.box, new Vector3d());

        final MoonSubLevel shim = MoonSubLevels.get(body.level);
        final String shimInfo;
        if (shim == null) {
            shimInfo = " shim=absent";
        } else {
            final Vector3d center = shim.plotCenter();
            final Vector3d edge = shim.plotAnchor(body.halfExtent(), 0.0D, body.halfExtent());
            shimInfo = " shim plot=" + shim.getPlot().getChunkMin()
                    + " center=" + format(center)
                    + " edgeAnchor=" + format(edge)
                    + " accepted=" + shim.acceptsPlotAnchor(edge)
                    + " centerAccepted=" + shim.acceptsPlotAnchor(center);
        }
        source.sendSuccess(() -> Component.literal(
                "Moon physics proxy: runtimeId=" + body.box.getRuntimeId()
                        + " pos=" + format(body.box.getPose().position())
                        + " scale=" + format(body.scale)
                        + " halfExtent=" + format(body.halfExtent())
                        + " diameter=" + format(body.halfExtent() * 2.0D)
                        + " mass=" + format(MASS)
                        + " velocity=" + format(linear)
                        + " angular=" + format(angular)
                        + shimInfo
                        + " debug=" + body.debugParticles
        ), false);
        return 1;
    }

    private static int debug(final CommandSourceStack source, final boolean enabled) {
        final TestBody body = active;
        if (body == null || body.server != source.getServer() || !body.box.isActive()) {
            source.sendFailure(Component.literal("No moon physics proxy is active."));
            return 0;
        }
        body.debugParticles = enabled;
        source.sendSuccess(
                () -> Component.literal("Moon physics wireframe: " + (enabled ? "on" : "off")),
                false
        );
        return 1;
    }


    public static BoxPhysicsObject body(final ServerLevel level) {
        final TestBody body = active;
        if (body == null || body.level != level || !body.box.isActive()) return null;
        body.box.updatePose();
        return body.box;
    }

    public static boolean isBody(final dev.ryanhcode.sable.api.physics.PhysicsPipelineBody candidate) {
        final TestBody body = active;
        return body != null && body.box == candidate && body.box.isActive();
    }

    private static Vector3d moonDirection(final ServerLevel level) {
        return new Vector3d(0.0D, -1.0D, 0.0D)
                .rotateX(level.getSunAngle(1.0F))
                .rotateY(-Math.PI * 0.5D)
                .normalize();
    }

    private static void render(final TestBody body) {
        final double half = body.halfExtent();
        final int segments = Math.max(2, Math.min(32, (int) Math.ceil((half * 2.0D) / 0.5D)));

        for (int sy = -1; sy <= 1; sy += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                edge(body, -half, sy * half, sz * half, half, sy * half, sz * half, segments);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                edge(body, sx * half, -half, sz * half, sx * half, half, sz * half, segments);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                edge(body, sx * half, sy * half, -half, sx * half, sy * half, half, segments);
            }
        }
    }

    private static void edge(
            final TestBody body,
            final double x0, final double y0, final double z0,
            final double x1, final double y1, final double z1,
            final int segments
    ) {
        for (int i = 0; i <= segments; i++) {
            final double t = i / (double) segments;
            particleAtLocal(
                    body,
                    x0 + (x1 - x0) * t,
                    y0 + (y1 - y0) * t,
                    z0 + (z1 - z0) * t
            );
        }
    }

    private static void particleAtLocal(final TestBody body, final double x, final double y, final double z) {
        final Pose3dc pose = body.box.getPose();
        final Vector3d point = new Vector3d(x, y, z);
        pose.orientation().transform(point);
        point.add(pose.position());
        body.level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static String format(final Vector3d value) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", value.x, value.y, value.z);
    }

    private static String format(final org.joml.Vector3dc value) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", value.x(), value.y(), value.z());
    }

    private static String format(final double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static final class TestBody {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final SubLevelPhysicsSystem physics;
        private final PhysicsPipeline pipeline;
        private final double distance;
        private BoxPhysicsObject box;
        private float scale;
        private boolean debugParticles;

        private TestBody(
                final MinecraftServer server,
                final ServerLevel level,
                final double distance,
                final float scale,
                final Vector3d position
        ) {
            this.server = server;
            this.level = level;
            this.physics = SubLevelPhysicsSystem.require(level);
            this.pipeline = this.physics.getPipeline();
            this.distance = distance;
            this.scale = scale;
            this.box = create(position, new Quaterniond(), scale);
        }

        private BoxPhysicsObject create(final Vector3d position, final Quaterniond orientation, final float scale) {
            final Pose3d pose = new Pose3d();
            pose.position().set(position);
            pose.orientation().set(orientation);
            final double half = Math.max(MIN_HALF_EXTENT, BASE_HALF_EXTENT * Math.max(0.0F, scale));
            return new BoxPhysicsObject(pose, new Vector3d(half, half, half), MASS);
        }

        private boolean add() {
            if (!this.physics.getTicketManager().wouldBeLoaded(this.level, this.box)) return false;
            this.physics.addObject(this.box);
            return true;
        }

        private double halfExtent() {
            return this.box.getHalfExtents().x();
        }

        private boolean resize(final float newScale) {
            if (!this.box.isActive()) return false;
            this.box.updatePose();
            final Pose3d pose = new Pose3d(this.box.getPose());
            final Vector3d linear = this.pipeline.getLinearVelocity(this.box, new Vector3d());
            final Vector3d angular = this.pipeline.getAngularVelocity(this.box, new Vector3d());
            final double half = Math.max(MIN_HALF_EXTENT, BASE_HALF_EXTENT * Math.max(0.0F, newScale));
            final BoxPhysicsObject replacement = new BoxPhysicsObject(
                    pose,
                    new Vector3d(half, half, half),
                    MASS
            );
            if (!this.physics.getTicketManager().wouldBeLoaded(this.level, replacement)) return false;
            this.physics.removeObject(this.box);
            this.box = replacement;
            this.scale = newScale;
            this.physics.addObject(this.box);
            this.pipeline.addLinearAndAngularVelocity(this.box, linear, angular);
            this.box.updatePose();
            return true;
        }
    }

    private MoonPhysicsTest() {}
}
