package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.FlexibleCouplingBlock;
import com.misterblusky9.pocket.block.FlexibleCouplingBlockEntity;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.client.TaperedLinkProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.index.SimRenderTypes;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.SimMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix3d;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class FlexibleCouplingRenderer extends SmartBlockEntityRenderer<FlexibleCouplingBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/block/flexible_shaft/flexible_shaft.png");
    private static final float SHAFT_WIDTH = 4.0F;
    private static final float SHAFT_U0 = 0.0F;
    private static final float SHAFT_U_SPAN = 4.0F / 16.0F;
    private static final float COIL_WIDTH = 5.0F;
    private static final float COIL_U0 = 10.0F / 16.0F;
    private static final float COIL_U_SPAN = 6.0F / 16.0F;

    public static final PartialModel SHAFT_END = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "block/flexible_coupling/shaft_end"));
    public static final PartialModel SHAFT_END_OPEN = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "block/flexible_coupling/shaft_end_open"));

    private final Vector3d controlPointA = new Vector3d();
    private final Vector3d controlPointB = new Vector3d();
    private final Vector3d segmentALerp = new Vector3d();
    private final Vector3d segmentBLerp = new Vector3d();
    private final Vector3d segmentCLerp = new Vector3d();
    private final Vector3d startUp = new Vector3d();
    private final Vector3d endUp = new Vector3d();
    private final Vector3d startLeft = new Vector3d();
    private final Vector3d endLeft = new Vector3d();
    private final Vector3d normalizedNormal = new Vector3d();
    private final Vector3d vertex = new Vector3d();
    private TaperedLinkProfile taper = TaperedLinkProfile.UNIFORM;

    public FlexibleCouplingRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void init() {}

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.FLEXIBLE_COUPLING.get(), FlexibleCouplingRenderer::new);
    }

    private static int getStressColor(FlexibleCouplingBlockEntity be, float partialTicks,
                                      Vector3d otherCenter, Vector3dc center, Minecraft minecraft,
                                      double localScale) {
        double distance = otherCenter.distance(center);
        double snapDistance = be.getSnappingDistance() * localScale;
        double flashingStartExtension = Mth.lerp(0.7, (be.getRenderLength(partialTicks) - 0.75) * localScale, snapDistance);
        float stressAlpha = 0.0F;
        if (distance > flashingStartExtension) {
            double renderTime = minecraft.player.tickCount + partialTicks;
            stressAlpha = Mth.clamp((float) ((distance - flashingStartExtension)
                    / (snapDistance - flashingStartExtension)), 0.0F, 1.0F) * 0.3F;
            stressAlpha *= Mth.lerp(0.25F, (float) Math.sin(renderTime / 3.0) * 0.5F + 0.5F, 1.0F);
        }
        return SimColors.STRESSED_RED & 0xFFFFFF | (int) (stressAlpha * 255.0F) << 24;
    }

    @Override
    protected void renderSafe(FlexibleCouplingBlockEntity be, float partialTicks, PoseStack ps,
                              MultiBufferSource bufferSource, int light, int overlay) {
        super.renderSafe(be, partialTicks, ps, bufferSource, light, overlay);
        Direction couplingFacing = be.getBlockState().getValue(FlexibleCouplingBlock.FACING);
        Direction.Axis axis = couplingFacing.getAxis();
        float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis);
        VertexConsumer cutout = bufferSource.getBuffer(RenderType.cutoutMipped());
        renderShaftEnd(SHAFT_END, be, couplingFacing, axis, angle, light, ps, cutout);
        if (be.getLevel().getBlockState(be.getBlockPos().relative(couplingFacing.getOpposite())).isAir()) {
            renderShaftEnd(SHAFT_END_OPEN, be, couplingFacing, axis, angle, light, ps, cutout);
        }

        if (!be.isController()) {
            return;
        }
        FlexibleCouplingBlockEntity other = be.getPartner();
        if (other == null) {
            return;
        }
        BlockState state = be.getBlockState();
        ps.pushPose();
        Minecraft minecraft = Minecraft.getInstance();
        ClientSubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);

        UUID otherSubLevelID = be.getPartnerSubLevelID();
        ClientSubLevel otherSubLevel = otherSubLevelID != null
                ? (ClientSubLevel) container.getSubLevel(otherSubLevelID) : null;
        ClientSubLevel subLevel = Sable.HELPER.getContainingClient(be);
        BlockPos blockPos = be.getBlockPos();
        Vector3dc center = be.getCenter();
        Vector3d otherCenter = other.getCenter();
        Direction facing = state.getValue(FlexibleCouplingBlock.FACING);
        Direction otherFacing = other.getBlockState().getValue(FlexibleCouplingBlock.FACING);
        Vector3dc normalA = JOMLConversion.atLowerCornerOf(facing.getNormal());
        Vector3d normalB = JOMLConversion.atLowerCornerOf(otherFacing.getNormal());
        ps.translate(center.x() - blockPos.getX(), center.y() - blockPos.getY(), center.z() - blockPos.getZ());
        Pose3dc renderPose = subLevel != null ? subLevel.renderPose() : null;
        Pose3dc otherRenderPose = otherSubLevel != null ? otherSubLevel.renderPose() : null;
        if (otherRenderPose != null) {
            otherRenderPose.transformNormal(normalB);
            otherRenderPose.transformPosition(otherCenter);
        }
        if (renderPose != null) {
            renderPose.transformNormalInverse(normalB);
            renderPose.transformPositionInverse(otherCenter);
        }
        normalB.normalize();
        double localScale = Pym.scale().linkFactorLocal(subLevel, otherSubLevel);

        taper = otherSubLevelID == null || otherSubLevel != null
                ? TaperedLinkProfile.between(subLevel, otherSubLevel,
                        Math.max(1.0E-6D, (be.getRenderLength(partialTicks) - 0.75D) * localScale))
                : TaperedLinkProfile.UNIFORM;

        int color = getStressColor(be, partialTicks, otherCenter, center, minecraft, localScale);
        List<SplinePoint> splinePoints = generateSpline(JOMLConversion.ZERO,
                otherCenter.sub(center, new Vector3d()), normalA, normalB, center.distance(otherCenter) / 5.0 + 0.25);
        int totalPoints = splinePoints.size();
        Vector3d pointNormal = new Vector3d();
        Vector3d startUpDir = JOMLConversion.toJOML(getUpDirection(be, otherCenter.sub(center, new Vector3d())));
        pointNormal.set(splinePoints.getFirst().normal);
        Matrix3d matrix = new Matrix3d(startUpDir, pointNormal, startUpDir.cross(pointNormal, new Vector3d()));
        double totalSpringLength = 0.0;

        for (int i = 0; i < totalPoints - 1; i++) {
            SplinePoint point = splinePoints.get(i);
            SplinePoint nextPoint = splinePoints.get(i + 1);
            totalSpringLength += point.point.distance(nextPoint.point);
            matrix.rotateLocal(SimMathUtils.getQuaternionfFromVectorRotation(point.normal, nextPoint.normal));
        }

        Quaterniond orientation = new Quaterniond();
        Quaterniondc orientation1 = renderPose != null ? renderPose.orientation() : JOMLConversion.QUAT_IDENTITY;
        Quaterniondc orientation2 = otherRenderPose != null ? otherRenderPose.orientation() : JOMLConversion.QUAT_IDENTITY;
        Quaterniond blockOrientation1 = new Quaterniond(facing.getRotation());
        Quaterniond blockOrientation2 = new Quaterniond(otherFacing.getRotation());
        blockOrientation2.premul(orientation2).premul(orientation1.conjugate(new Quaterniond()));
        Quaterniond relativeBlockOrientation = new Quaterniond(blockOrientation1).div(blockOrientation2);
        orientation.mul(new Quaterniond(relativeBlockOrientation));
        orientation.mul(matrix.getNormalizedRotation(new Quaterniond()));
        if (Math.abs(OrientedBoundingBox3d.UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()))) < 1.0E-5) {
            orientation.rotateLocalX(Math.PI);
        }

        double d = OrientedBoundingBox3d.UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()));
        double deg = 2.0 * Math.atan2(-d, orientation.w());
        double twist = Math.floor((deg + Math.PI / 4) / (Math.PI / 2)) * (Math.PI / 2) - deg;
        float uvScale = (float) ((be.getRenderLength(partialTicks) - 0.75) * localScale / totalSpringLength);
        matrix.set(startUpDir, pointNormal, startUpDir.cross(pointNormal, new Vector3d()));

        double spin = angle * facing.getAxisDirection().getStep();

        drawTube(ps, splinePoints, new Matrix3d(matrix), twist, uvScale, spin, light, color,
                bufferSource.getBuffer(SimRenderTypes.spring(TEXTURE)), SHAFT_WIDTH, SHAFT_U_SPAN, SHAFT_U0, SHAFT_U0);
        drawTube(ps, splinePoints, new Matrix3d(matrix), twist, uvScale, spin, light, color,
                bufferSource.getBuffer(SimRenderTypes.spring(TEXTURE)), COIL_WIDTH, COIL_U_SPAN, COIL_U0, COIL_U0);

        ps.popPose();
    }

    private static void renderShaftEnd(PartialModel model, FlexibleCouplingBlockEntity be,
                                       Direction facing, Direction.Axis axis, float angle,
                                       int light, PoseStack ps, VertexConsumer buffer) {
        SuperByteBuffer part = CachedBuffers.partialFacingVertical(model, be.getBlockState(), facing);
        KineticBlockEntityRenderer.kineticRotationTransform(part, be, axis, angle, light).renderInto(ps, buffer);
    }

    private void drawTube(PoseStack ps, List<SplinePoint> splinePoints, Matrix3d matrix,
                          double twist, float uvScale, double spin, int light,
                          int color, VertexConsumer buffer, float width,
                          float uSpan, float outerU, float innerU) {
        int totalPoints = splinePoints.size();
        double runningLength = 0.0;
        for (int i = 0; i < totalPoints - 1; i++) {
            SplinePoint point = splinePoints.get(i);
            SplinePoint nextPoint = splinePoints.get(i + 1);
            Vector3dc upDir = spun(matrix.getColumn(0, new Vector3d()), point.normal, spin);
            matrix.rotateLocal(SimMathUtils.getQuaternionfFromVectorRotation(point.normal, nextPoint.normal));
            matrix.rotateY(-twist / (totalPoints - 1));
            Vector3dc nextUpDir = spun(matrix.getColumn(0, new Vector3d()), nextPoint.normal, spin);
            double length = point.point.distance(nextPoint.point);
            renderSegment(ps, point.normal, nextPoint.normal, upDir, nextUpDir, point.point, nextPoint.point, outerU,
                    (float) runningLength * uvScale, (float) (runningLength + length) * uvScale,
                    light, color, buffer, width, uSpan);
            renderSegment(ps, point.normal.negate(new Vector3d()), nextPoint.normal.negate(new Vector3d()),
                    upDir.negate(new Vector3d()), nextUpDir.negate(new Vector3d()), point.point, nextPoint.point, innerU,
                    -(float) runningLength * uvScale, -(float) (runningLength + length) * uvScale,
                    light, color, buffer, width, uSpan);
            runningLength += length;
        }
    }

    private Vec3 getUpDirection(FlexibleCouplingBlockEntity be, Vector3dc directionToSpring) {
        Direction facing = be.getBlockState().getValue(FlexibleCouplingBlock.FACING);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        double dot = directionToSpring.dot(normal.x, normal.y, normal.z);
        Vector3d dir = directionToSpring.sub(normal.x * dot, normal.y * dot, normal.z * dot, new Vector3d());
        if (dir.lengthSquared() < 1.0E-6) {
            return facing.getAxis().isHorizontal() ? new Vec3(0.0, 1.0, 0.0) : new Vec3(0.0, 0.0, -1.0);
        }
        return Vec3.atLowerCornerOf(Direction.getNearest(dir.x, dir.y, dir.z).getOpposite().getNormal());
    }

    private List<SplinePoint> generateSpline(Vector3dc pointA, Vector3dc pointB, Vector3dc normalA,
                                             Vector3dc normalB, double controlPointLength) {
        List<SplinePoint> list = new ObjectArrayList<>();
        pointA.fma(controlPointLength, normalA, controlPointA);
        pointB.fma(controlPointLength, normalB, controlPointB);
        double len = pointA.distance(pointB);
        int initialPointCount = Mth.clamp(Mth.ceil(len), 5, 8);

        for (int i = 0; i <= initialPointCount; i++) {
            double t = (double) i / initialPointCount;
            pointA.lerp(controlPointA, t, segmentALerp);
            controlPointA.lerp(controlPointB, t, segmentBLerp);
            controlPointB.lerp(pointB, t, segmentCLerp);
            Vector3d point = new Vector3d(segmentALerp.lerp(segmentBLerp, t).lerp(segmentBLerp.lerp(segmentCLerp, t), t));
            Vector3d normal = new Vector3d();
            if (list.isEmpty()) {
                normal.set(normalA);
            } else if (list.size() == initialPointCount) {
                normal.set(normalB).negate();
            } else {
                point.sub(list.get(list.size() - 1).point, normal).normalize();
            }
            list.add(new SplinePoint(point, normal));
        }
        return list;
    }

    private void renderSegment(PoseStack ms, Vector3dc startDirection, Vector3dc endDirection,
                               Vector3dc inputStartUp, Vector3dc inputEndUp, Vector3dc startPos,
                               Vector3dc endPos, float uvXOffset, float uvStart, float uvEnd,
                               int light, int color, VertexConsumer a, float width,
                               float uSpan) {
        double startWidth = taper.radiusAtV(uvStart);
        double endWidth = taper.radiusAtV(uvEnd);
        inputStartUp.cross(startDirection, startLeft).normalize();
        inputEndUp.cross(endDirection, endLeft).normalize();
        float texW = uSpan;
        double scale = width / 16.0 / 2.0;
        startLeft.mul(scale * startWidth);
        inputStartUp.mul(scale * startWidth, startUp);
        endLeft.mul(scale * endWidth);
        inputEndUp.mul(scale * endWidth, endUp);
        Vector3d startDown = startUp.negate(new Vector3d());
        Vector3d endDown = endUp.negate(new Vector3d());
        Vector3d startRight = startLeft.negate(new Vector3d());
        Vector3d endRight = endLeft.negate(new Vector3d());
        float uvScale = 1.0F;
        vert(ms, a, startPos.add(startLeft, vertex).sub(startUp), color, uvXOffset, uvStart * uvScale, startDown, light);
        vert(ms, a, endPos.add(endLeft, vertex).sub(endUp), color, uvXOffset, uvEnd * uvScale, endDown, light);
        vert(ms, a, endPos.sub(endLeft, vertex).sub(endUp), color, texW + uvXOffset, uvEnd * uvScale, endDown, light);
        vert(ms, a, startPos.sub(startLeft, vertex).sub(startUp), color, texW + uvXOffset, uvStart * uvScale, startDown, light);
        vert(ms, a, startPos.sub(startLeft, vertex).add(startUp), color, uvXOffset, uvStart * uvScale, startUp, light);
        vert(ms, a, endPos.sub(endLeft, vertex).add(endUp), color, uvXOffset, uvEnd * uvScale, endUp, light);
        vert(ms, a, endPos.add(endLeft, vertex).add(endUp), color, texW + uvXOffset, uvEnd * uvScale, endUp, light);
        vert(ms, a, startPos.add(startLeft, vertex).add(startUp), color, texW + uvXOffset, uvStart * uvScale, startUp, light);
        vert(ms, a, startPos.sub(startLeft, vertex).sub(startUp), color, uvXOffset, uvStart * uvScale, startRight, light);
        vert(ms, a, endPos.sub(endLeft, vertex).sub(endUp), color, uvXOffset, uvEnd * uvScale, endRight, light);
        vert(ms, a, endPos.sub(endLeft, vertex).add(endUp), color, texW + uvXOffset, uvEnd * uvScale, endRight, light);
        vert(ms, a, startPos.sub(startLeft, vertex).add(startUp), color, texW + uvXOffset, uvStart * uvScale, startRight, light);
        vert(ms, a, startPos.add(startLeft, vertex).add(startUp), color, uvXOffset, uvStart * uvScale, startLeft, light);
        vert(ms, a, endPos.add(endLeft, vertex).add(endUp), color, uvXOffset, uvEnd * uvScale, endLeft, light);
        vert(ms, a, endPos.add(endLeft, vertex).sub(endUp), color, texW + uvXOffset, uvEnd * uvScale, endLeft, light);
        vert(ms, a, startPos.add(startLeft, vertex).sub(startUp), color, texW + uvXOffset, uvStart * uvScale, startLeft, light);
    }

    private void vert(PoseStack ms, VertexConsumer a, Vector3dc pos, int color, float u,
                      float v, Vector3dc normal, int light) {
        normal.normalize(normalizedNormal);
        a.addVertex(ms.last().pose(), (float) pos.x(), (float) pos.y(), (float) pos.z())
                .setColor(color)
                .setUv(u, v)
                .setLight(light)
                .setNormal(ms.last(), (float) normalizedNormal.x(), (float) normalizedNormal.y(), (float) normalizedNormal.z());
    }

    private static Vector3d spun(Vector3d up, Vector3dc axis, double angle) {
        if (angle == 0.0 || axis.lengthSquared() < 1.0e-12) {
            return up;
        }
        return new Quaterniond().fromAxisAngleRad(axis.x(), axis.y(), axis.z(), angle).transform(up);
    }

    @Override
    public boolean shouldRenderOffScreen(FlexibleCouplingBlockEntity blockEntity) {
        return true;
    }

    record SplinePoint(Vector3dc point, Vector3dc normal) {
    }
}
