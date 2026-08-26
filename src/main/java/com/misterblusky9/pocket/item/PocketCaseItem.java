package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.pocket.DeploymentClearance;
import com.misterblusky9.pocket.network.ScaleNetwork;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import com.misterblusky9.pocket.pocket.PocketRenderSnapshot;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.misterblusky9.pocket.pocket.CannonReleaseSource;
import com.misterblusky9.pocket.physics.ScaledBoundsCollider;
import com.misterblusky9.pocket.physics.ScaledFluidForces;
import com.misterblusky9.pocket.scale.ScaleController;
import com.misterblusky9.pocket.scale.ScaleState;
import com.misterblusky9.pocket.scale.SubLevelParentage;
import com.misterblusky9.pocket.client.PocketedSubLevelItemRenderer;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PocketCaseItem extends PackageItem {
    public static final PackageStyles.PackageStyle STYLE =
            new PackageStyles.PackageStyle("cardboard", 10, 8, 18f, false);

    private static final String TOKEN_KEY = "pocket_token";
    private static final String DIMENSION_KEY = "source_dimension";

    private static final String ORPHANED_KEY = "pocket_orphaned";
    private static final String ORPHANED_REASON_KEY = "pocket_orphaned_reason";
    private static final String NAME_KEY = "sublevel_name";
    private static final String BLOCKS_KEY = "pocket_blocks";
    private static final String BLOCK_ENTITIES_KEY = "pocket_block_entities";
    private static final String MASS_KEY = "pocket_mass";
    private static final String PACKED_BY_KEY = "packed_by";

    private static final String CONTAINER_KEY = "pocket_container";
    private static final String INTEGRITY_KEY = "pocket_integrity";
    private static final String INTEGRITY_VERSION_KEY = "version";
    private static final String INTEGRITY_BLOCKS_KEY = "blocks";
    private static final String INTEGRITY_BLOCK_ENTITIES_KEY = "block_entities";
    private static final String INTEGRITY_CHUNKS_KEY = "chunks";
    private static final String INTEGRITY_STRUCTURE_HASH_KEY = "structure_hash";
    private static final int INTEGRITY_VERSION = 2;
    private static final long STRUCTURE_HASH_OFFSET = 0xcbf29ce484222325L;
    private static final long STRUCTURE_HASH_PRIME = 0x100000001b3L;
    private static final double SAFE_CLEARANCE = 0.025D;

    private static final double REFERENCE_SHOT_MASS = 5_000.0D;
    private static final double MIN_SHOT_SPEED_FACTOR = 1.00D;
    private static final double MAX_SHOT_SPEED_FACTOR = 2.20D;

    private static final double MUZZLE_EFFICIENCY = 0.80D;

    private static final int IMPACT_TIMEOUT_TICKS = 20 * 60;
    private static final double CANNON_BLOOM_DISTANCE = 16.0D;

    private static final int CANNON_BLOOM_TIMEOUT_TICKS = 80;

    public PocketCaseItem(final Properties properties) {
        super(properties, STYLE);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(final Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new PocketedSubLevelItemRenderer()));
    }

    public static boolean isFilled(final ItemStack stack) { return token(stack) != null; }

    public static boolean isContainer(final ItemStack stack) {
        return PocketContainer.isContainer(stack);
    }

    public static ResourceLocation containerId(final ItemStack stack) {
        final CompoundTag tag = customTag(stack);
        if (tag == null || !tag.contains(CONTAINER_KEY)) return null;
        return ResourceLocation.tryParse(tag.getString(CONTAINER_KEY));
    }

    public static Item containerItem(final ItemStack stack) {
        return PocketContainer.of(stack).item();
    }

    public static void setContainer(final ItemStack stack, final ItemStack container) {
        final PocketContainer packedInto = PocketContainer.byItem(container.getItem());
        if (packedInto == null) return;
        final CompoundTag tag = customTag(stack);
        final CompoundTag updated = tag == null ? new CompoundTag() : tag;
        updated.putString(CONTAINER_KEY, packedInto.id().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
    }

    public static boolean isCannonPayload(final ItemStack stack) {
        return stack.getItem() instanceof PocketCaseItem && isFilled(stack);
    }

    @Override
    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    @Override
    public int getUseDuration(final ItemStack stack, final LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(final ItemStack stack) {
        return UseAnim.BOW;
    }

    public static ItemStack createFilled(
            final ItemStack carrier,
            final UUID token,
            final ServerLevel sourceLevel,
            final String displayName,
            final PocketRenderSnapshot snapshot,
            final int blocks,
            final int blockEntities,
            final double mass
    ) {
        final ItemStack stack = carrier.copyWithCount(1);
        final CompoundTag tag = new CompoundTag();
        tag.putUUID(TOKEN_KEY, token);
        tag.putString(DIMENSION_KEY, sourceLevel.dimension().location().toString());
        tag.putInt(BLOCKS_KEY, blocks);
        tag.putInt(BLOCK_ENTITIES_KEY, blockEntities);
        if (Double.isFinite(mass) && mass > 0.0D) tag.putDouble(MASS_KEY, mass);
        if (displayName != null && !displayName.isBlank()) tag.putString(NAME_KEY, displayName);
        if (snapshot != null) snapshot.writeTo(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static PocketMetrics prepareCapturedPayload(
            final ServerSubLevel subLevel,
            final CompoundTag fullTag,
            final UUID token,
            final Player feedbackPlayer
    ) {
        if (subLevel == null || fullTag == null) return null;

        final ServerSubLevelContainer container =
                ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (SubLevelParentage.isJoinedToAnother(container, subLevel)) {
            PocketTrace.logger().info(
                    "[PocketTransfer] capture rejected uuid={} reason=joined_to_another_sublevel",
                    subLevel.getUniqueId());
            if (feedbackPlayer != null) {
                feedbackPlayer.displayClientMessage(Component.literal(
                        "Can't pocket this sublevel! Disconnect any joints and try again."), true);
            }
            return null;
        }

        final PayloadStructure structure = measureStructure(subLevel);
        if (structure.blocks() > PocketSized.MAX_COMPRESSED_BLOCKS) {
            PocketTrace.logger().error(
                    "[PocketTransfer] capture payload rejected token={} uuid={} blocks={}/{} "
                            + "reason=hard limit backendValid=false",
                    token, subLevel.getUniqueId(), structure.blocks(), PocketSized.MAX_COMPRESSED_BLOCKS);
            if (feedbackPlayer != null) {
                feedbackPlayer.displayClientMessage(Component.literal(
                        "Pocket Sized hard limit: " + structure.blocks() + "/"
                                + PocketSized.MAX_COMPRESSED_BLOCKS + " blocks"), true);
            }
            return null;
        }
        final BlockEntityAudit audit = auditBlockEntities(subLevel, fullTag, token, "capture");
        final int canonicalBlockEntities = audit.serializedEntries() - audit.orphans().size();
        final boolean valid = !audit.fatal()
                && fullTag.getCompound("plot").getCompound("chunks").size() == structure.chunks()
                && canonicalBlockEntities == audit.validEntries()
                && canonicalBlockEntities == structure.blockEntities();

        if (!valid) {
            PocketTrace.logger().error(
                    "[PocketTransfer] capture payload rejected token={} uuid={} chunks={}/{} "
                            + "serializedBlockEntities={} canonicalBlockEntities={} liveBlockEntities={} "
                            + "fatal={} backendValid=false",
                    token, subLevel.getUniqueId(),
                    fullTag.getCompound("plot").getCompound("chunks").size(), structure.chunks(),
                    audit.serializedEntries(), canonicalBlockEntities, structure.blockEntities(), audit.fatal());
            if (feedbackPlayer != null) {
                feedbackPlayer.displayClientMessage(Component.literal(
                        "Could not pocket the sublevel: its serialized blocks and block entities disagree."), true);
            }
            return null;
        }

        pruneOrphans(audit.orphans(), token, "capture", "live_source");
        writeIntegrityManifest(fullTag, structure);
        PocketTrace.logger().info(
                "[PocketTransfer] capture payload canonical token={} uuid={} blocks={} chunks={} "
                        + "blockEntities={} repairedOrphans={} structureHash=0x{} backendValid=true",
                token, subLevel.getUniqueId(), structure.blocks(), structure.chunks(),
                structure.blockEntities(), audit.orphans().size(), Long.toHexString(structure.structureHash()));
        return new PocketMetrics(structure.blocks(), structure.blockEntities());
    }

    public static void setPackedBy(final ItemStack stack, final String playerName) {
        final CompoundTag tag = customTag(stack);
        if (tag == null || playerName == null || playerName.isBlank()) return;
        tag.putString(PACKED_BY_KEY, playerName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static double storedMass(final ItemStack stack) {
        final CompoundTag tag = customTag(stack);
        return tag == null ? 0.0D : tag.getDouble(MASS_KEY);
    }

    public static UUID token(final ItemStack stack) {
        final CompoundTag tag = customTag(stack);
        return tag != null && tag.hasUUID(TOKEN_KEY) ? tag.getUUID(TOKEN_KEY) : null;
    }

    public static String sourceDimension(final ItemStack stack) {
        final CompoundTag tag = customTag(stack);
        return tag == null ? "" : tag.getString(DIMENSION_KEY);
    }

    public static PocketRenderSnapshot renderSnapshot(final ItemStack stack) {
        return PocketRenderSnapshot.readFrom(customTag(stack));
    }

    public static CompoundTag customTag(final ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    @Override
    public void inventoryTick(
            final ItemStack stack,
            final Level level,
            final Entity entity,
            final int slotId,
            final boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide) return;

        if (!isFilled(stack)) {
            markOrphaned(stack, entity, slotId, "no payload token on the carrier");
            return;
        }

        if (!(level instanceof final ServerLevel currentLevel)) return;

        final UUID payloadToken = token(stack);
        final ResourceLocation sourceId = ResourceLocation.tryParse(sourceDimension(stack));
        final ServerLevel sourceLevel = sourceId == null ? null : currentLevel.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, sourceId));

        if (sourceLevel == null) return;

        if (PocketedSubLevelSavedData.getOrLoad(sourceLevel).contains(payloadToken)) {
            clearOrphaned(stack, entity, payloadToken);
            return;
        }

        markOrphaned(stack, entity, slotId,
                "payload " + payloadToken + " is not in " + sourceId + "'s pocketed-sublevel storage");
    }

    private static void markOrphaned(
            final ItemStack stack,
            final Entity entity,
            final int slotId,
            final String reason
    ) {
        final CompoundTag tag = customTag(stack);
        if (tag != null && tag.getBoolean(ORPHANED_KEY)) return;

        final CompoundTag updated = tag == null ? new CompoundTag() : tag;
        updated.putBoolean(ORPHANED_KEY, true);
        updated.putString(ORPHANED_REASON_KEY, reason);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));

        PocketTrace.logger().error(
                "[PocketTransfer] carrier PRESERVED with unreachable payload - {} holder={} slot={}. "
                        + "The item has NOT been removed; its token is intact and it will recover if "
                        + "the entry returns.",
                reason, entity == null ? "?" : entity.getScoreboardName(), slotId);
    }

    private static void clearOrphaned(final ItemStack stack, final Entity entity, final UUID payloadToken) {
        final CompoundTag tag = customTag(stack);
        if (tag == null || !tag.getBoolean(ORPHANED_KEY)) return;

        tag.remove(ORPHANED_KEY);
        tag.remove(ORPHANED_REASON_KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        PocketTrace.logger().warn(
                "[PocketTransfer] carrier recovered token={} holder={} - payload is reachable again",
                payloadToken, entity == null ? "?" : entity.getScoreboardName());
    }

    public static boolean isOrphaned(final ItemStack stack) {
        final CompoundTag tag = customTag(stack);
        return tag != null && tag.getBoolean(ORPHANED_KEY);
    }

    private static int storedCount(final ItemStack stack, final String key) {
        final CompoundTag tag = customTag(stack);
        return tag == null || !tag.contains(key, Tag.TAG_INT) ? 0 : Math.max(0, tag.getInt(key));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);

        if (!isFilled(stack)) return InteractionResultHolder.pass(stack);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) rotatePreferredYaw(stack, Math.PI * 0.5D);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void releaseUsing(
            final ItemStack stack,
            final Level level,
            final LivingEntity entity,
            final int remainingUseTicks
    ) {
        if (!(entity instanceof final Player player)) return;

        final int elapsed = getUseDuration(stack, entity) - remainingUseTicks;
        final float velocity = PackageItem.getPackageVelocity(elapsed);
        if (velocity < 0.1F || level.isClientSide) return;

        final UUID payloadToken = token(stack);
        final ItemStack thrownStack = stack.copyWithCount(1);

        stack.shrink(1);
        player.getInventory().setChanged();

        level.playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.5F
        );

        final Vec3 motion = entity.getLookAngle().scale(velocity * 2.0D);
        final Vec3 origin = new Vec3(
                entity.getX(),
                entity.getY() + entity.getBoundingBox().getYsize() * 0.5D,
                entity.getZ()
        ).add(motion);

        final ItemEntity thrown = new ItemEntity(level, origin.x, origin.y, origin.z, thrownStack);
        thrown.setDeltaMovement(motion);
        thrown.setPickUpDelay(20);
        level.addFreshEntity(thrown);

        PocketTrace.logger().info(
                "[PocketTransfer] route=item_throw token={} dimension={} player={} velocity={} "
                        + "entity={} packageEntity=false",
                payloadToken, level.dimension().location(), player.getScoreboardName(), velocity,
                thrown.getUUID()
        );
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Player player = context.getPlayer();
        final ItemStack stack = context.getItemInHand();
        if (player == null || !isFilled(stack)) return InteractionResult.PASS;

        if (!player.isShiftKeyDown()) return super.useOn(context);

        if (!(context.getLevel() instanceof final ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        return deployFromHand(
                serverLevel, player, context.getHand(), stack,
                context.getClickLocation(), context.getClickedFace()
        ) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private static void rotatePreferredYaw(final ItemStack stack, final double delta) {
        final CompoundTag tag = customTag(stack);
        if (tag == null) return;
        final PocketRenderSnapshot snapshot = PocketRenderSnapshot.readFrom(tag);
        if (snapshot == null) return;
        snapshot.withUprightYaw(snapshot.uprightYaw() + delta).writeTo(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static boolean deployFromHand(
            final ServerLevel level,
            final Player player,
            final InteractionHand hand,
            final ItemStack stack,
            final Vec3 click,
            final Direction face
    ) {
        final UUID payloadToken = token(stack);
        final ServerLevel sourceLevel = sourceLevel(level, stack);

        if (!payloadAvailable(level, stack)) {
            fail(player, "This package's contents are missing. It cannot be placed.");
            PocketTrace.logger().warn(
                    "[PocketTransfer] route=hand refused token={} target={} player={} reason=payload absent",
                    payloadToken, level.dimension().location(), player.getScoreboardName());
            return false;
        }

        PocketTrace.logger().info(
                "[PocketTransfer] route=hand begin token={} target={} player={} hand={}",
                payloadToken, level.dimension().location(), player.getScoreboardName(), hand);
        final ServerSubLevel restored = restoreAt(
                level, stack, click, face, player, true, false
        );
        if (restored == null) {
            PocketTrace.logger().warn(
                    "[PocketTransfer] route=hand end token={} target={} restored=false carrierRetained={} "
                            + "backendValid=false",
                    payloadToken, level.dimension().location(), token(stack) != null);
            return false;
        }

        replaceHeldAfterDeployment(player, hand, stack);

        if (player instanceof final net.minecraft.server.level.ServerPlayer serverPlayer) {
            final ServerLevel commitSource = sourceLevel == null ? level : sourceLevel;
            PocketedSubLevelSavedData.getOrLoad(commitSource).commitDeploy(
                    commitSource, level, payloadToken, serverPlayer);
        }

        PocketTrace.logger().info(
                "[PocketTransfer] route=hand end token={} target={} restored=true backendValid=true",
                payloadToken, level.dimension().location());
        return true;
    }

    public static boolean deployFromBrokenPackage(
            final ServerLevel level,
            final ItemStack stack,
            final Vec3 position
    ) {
        if (!isFilled(stack)) return false;
        final UUID payloadToken = token(stack);
        PocketTrace.logger().info(
                "[PocketTransfer] route=broken_package begin token={} target={} position={}",
                payloadToken, level.dimension().location(), position);
        final boolean restored = restoreAt(
                level, stack, position, Direction.UP, null, false, false, true) != null;
        PocketTrace.logger().info(
                "[PocketTransfer] route=broken_package end token={} target={} restored={} backendValid={}",
                payloadToken, level.dimension().location(), restored, restored);
        return restored;
    }

    public static boolean deployPocketedProjectile(
            final ServerLevel level,
            final ItemStack projectileStack,
            final BlockHitResult hit
    ) {
        final ServerSubLevel restored = deployFromCannon(
                level, projectileStack, hit.getLocation(), Vec3.ZERO
        );
        return restored != null;
    }

    public static ServerSubLevel deployFromCannon(
            final ServerLevel level,
            final ItemStack projectileStack,
            final Vec3 projectilePosition,
            final Vec3 projectileMotion
    ) {
        return deployFromCannon(level, projectileStack, projectilePosition, projectileMotion,
                com.misterblusky9.pocket.pocket.CannonExpansionMode.IMMEDIATE);
    }

    public static ServerSubLevel deployFromCannon(
            final ServerLevel level,
            final ItemStack projectileStack,
            final Vec3 projectilePosition,
            final Vec3 projectileMotion,
            final com.misterblusky9.pocket.pocket.CannonExpansionMode mode
    ) {
        if (!isCannonPayload(projectileStack)) return null;

        final double speedFactor = shotSpeedFactor(storedMass(projectileStack));

        final ServerSubLevel restored = restoreAt(
                level, projectileStack, projectilePosition, Direction.UP, null, false, true, false,
                projectileMotion.lengthSqr() > 1.0E-8D ? projectileMotion.normalize() : null
        );
        if (restored == null) return null;

        final dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle =
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(restored);
        if (handle != null) {
            final double perSecond = 20.0D * speedFactor * MUZZLE_EFFICIENCY;
            handle.addLinearAndAngularVelocity(
                    new Vector3d(
                            projectileMotion.x * perSecond,
                            projectileMotion.y * perSecond,
                            projectileMotion.z * perSecond
                    ),
                    new Vector3d()
            );
        }

        final boolean inFlight = projectileMotion.lengthSqr() > 1.0E-8D;

        final boolean onImpact = mode == com.misterblusky9.pocket.pocket.CannonExpansionMode.IMPACT;
        CannonReleaseSource.arm(
                level,
                restored,
                projectilePosition,
                inFlight ? CANNON_BLOOM_DISTANCE : 0.0D,
                inFlight ? (onImpact ? IMPACT_TIMEOUT_TICKS : CANNON_BLOOM_TIMEOUT_TICKS) : 1,
                mode
        );
        return restored;
    }

    private static double shotSpeedFactor(final double mass) {
        if (!Double.isFinite(mass) || mass <= 0.0D) return 1.0D;
        final double factor = Math.sqrt(REFERENCE_SHOT_MASS / mass);
        return Math.max(MIN_SHOT_SPEED_FACTOR, Math.min(MAX_SHOT_SPEED_FACTOR, factor));
    }

    private static Quaterniond noseAlignment(final double storedFront, final Vec3 aim) {
        final Vector3d nose = new Vector3d(0.0D, 0.0D, 1.0D).rotateY(storedFront);

        final Vector3d target = new Vector3d(aim.x, aim.y, aim.z);
        if (target.lengthSquared() < 1.0E-10D) return new Quaterniond().rotationY(-storedFront);
        target.normalize();

        return new Quaterniond().rotationTo(nose, target);
    }

    private static double facingYawFor(final Player player, final double storedFront) {
        final Vec3 look = player.getLookAngle();
        if (look.x * look.x + look.z * look.z < 1.0E-8D) return -storedFront;

        final double lookHeading = Math.atan2(look.x, look.z);
        return lookHeading - storedFront;
    }

    private static ServerSubLevel restoreAt(
            final ServerLevel serverLevel,
            final ItemStack stack,
            final Vec3 click,
            final Direction face,
            final Player feedbackPlayer,
            final boolean checkPlacement,
            final boolean freshIdentity
    ) {
        return restoreAt(serverLevel, stack, click, face, feedbackPlayer, checkPlacement, freshIdentity, false);
    }

    private static ServerSubLevel restoreAt(
            final ServerLevel serverLevel,
            final ItemStack stack,
            final Vec3 click,
            final Direction face,
            final Player feedbackPlayer,
            final boolean checkPlacement,
            final boolean freshIdentity,
            final boolean nudgeClear
    ) {
        return restoreAt(serverLevel, stack, click, face, feedbackPlayer,
                checkPlacement, freshIdentity, nudgeClear, null);
    }

    private static ServerSubLevel restoreAt(
            final ServerLevel serverLevel,
            final ItemStack stack,
            final Vec3 click,
            final Direction face,
            final Player feedbackPlayer,
            final boolean checkPlacement,
            final boolean freshIdentity,
            final boolean nudgeClear,
            final Vec3 aimDirection
    ) {
        final UUID token = token(stack);
        if (token == null) return fail(feedbackPlayer, "This pocketed item is empty.");

        final PayloadBackend backend = resolvePayloadBackend(
                serverLevel, token, sourceDimension(stack), feedbackPlayer);
        if (backend == null) return null;

        final CompoundTag fullTag = backend.fullTag();
        final PlotTransfer transfer = preparePlotTransfer(serverLevel, backend, fullTag, token, feedbackPlayer);
        if (transfer == null) return null;

        if (transfer.deltaBlocksX() != 0
                || transfer.deltaBlocksY() != 0
                || transfer.deltaBlocksZ() != 0) {
            final RebaseStats stats = rebasePlotPayload(
                    fullTag,
                    transfer.destinationPlotX(), transfer.destinationPlotZ(),
                    transfer.deltaBlocksX(), transfer.deltaBlocksY(), transfer.deltaBlocksZ());
            PocketTrace.logger().info(
                    "[PocketTransfer] rebase token={} source={} target={} plot=({},{})->({},{}) "
                            + "delta=({}, {}, {}) blockEntities={} ticks={} entities={}",
                    token,
                    backend.sourceLevel().dimension().location(), serverLevel.dimension().location(),
                    backend.sourcePlotX(), backend.sourcePlotZ(),
                    transfer.destinationPlotX(), transfer.destinationPlotZ(),
                    transfer.deltaBlocksX(), transfer.deltaBlocksY(), transfer.deltaBlocksZ(),
                    stats.blockEntities(), stats.ticks(), stats.entities());
        }

        final ServerSubLevelContainer destinationContainer = SubLevelContainer.getContainer(serverLevel);
        if (destinationContainer == null) {
            PocketTrace.logger().error(
                    "[PocketTransfer] invalid target backend token={} dimension={} reason=no Sable container",
                    token, serverLevel.dimension().location());
            return fail(feedbackPlayer, "The destination sublevel backend is unavailable.");
        }

        final UUID storedIdentity = fullTag.getUUID("uuid");
        final boolean identityCollision = destinationContainer.getSubLevel(storedIdentity) != null;
        final boolean replaceIdentity = freshIdentity || backend.crossDimension() || identityCollision;
        if (replaceIdentity) {
            final UUID replacement = UUID.randomUUID();
            PocketTrace.logger().warn(
                    "[PocketTransfer] replacing destination identity token={} old={} new={} "
                            + "freshRequested={} crossDimension={} collision={}",
                    token, storedIdentity, replacement, freshIdentity, backend.crossDimension(), identityCollision);
            fullTag.putUUID("uuid", replacement);
        }

        final SubLevelData original = SubLevelSerializer.fromData(fullTag);
        if (original == null) return fail(feedbackPlayer, "The stored sublevel payload is invalid.");

        fullTag.remove("linear_velocity");
        fullTag.remove("angular_velocity");

        final PocketRenderSnapshot snapshot = renderSnapshot(stack);
        final Pose3d pose = new Pose3d(original.pose());

        final double storedFront = snapshot != null && snapshot.hasPlacementGeometry()
                ? snapshot.uprightYaw() : 0.0D;
        if (aimDirection != null) {
            pose.orientation().set(noseAlignment(storedFront, aimDirection));
        } else {
            final double placementYaw = feedbackPlayer != null
                    ? facingYawFor(feedbackPlayer, storedFront)
                    : storedFront;
            pose.orientation().set(new Quaterniond().rotationY(placementYaw));
        }
        pose.scale().set(PocketSized.MIN_SCALE, PocketSized.MIN_SCALE, PocketSized.MIN_SCALE);

        final PlacementExtents extents = placementExtents(snapshot, original, pose);
        Vector3d newPosition = checkPlacement
                ? safePosition(click, face, extents)
                : new Vector3d(click.x - extents.centerX(), click.y - extents.centerY(), click.z - extents.centerZ());

        if (nudgeClear) {
            newPosition = DeploymentClearance.resolve(
                    serverLevel,
                    newPosition,
                    new Vector3d(extents.minX(), extents.minY(), extents.minZ()),
                    new Vector3d(extents.maxX(), extents.maxY(), extents.maxZ()));
        }

        final AABB candidate = new AABB(
                newPosition.x + extents.minX(), newPosition.y + extents.minY(), newPosition.z + extents.minZ(),
                newPosition.x + extents.maxX(), newPosition.y + extents.maxY(), newPosition.z + extents.maxZ()
        );

        if (checkPlacement && !placementClear(serverLevel, candidate)) {
            return fail(feedbackPlayer, "Deployment blocked: the compressed contraption does not fit there.");
        }

        pose.position().set(newPosition);
        fullTag.put("pose", SableNBTUtils.writePose3d(pose));
        fullTag.put("world_bounds", SableNBTUtils.writeBoundingBox(new BoundingBox3d(
                candidate.minX, candidate.minY, candidate.minZ,
                candidate.maxX, candidate.maxY, candidate.maxZ
        )));

        final SubLevelData positioned = SubLevelSerializer.fromData(fullTag);
        if (positioned == null) return fail(feedbackPlayer, "Could not prepare the stored sublevel.");

        final ServerSubLevel restored;
        PocketTrace.logger().info(
                "[PocketTransfer] load begin token={} source={} target={} destinationPlot=({}, {}) uuid={}",
                token, backend.sourceLevel().dimension().location(), serverLevel.dimension().location(),
                transfer.destinationPlotX(), transfer.destinationPlotZ(), positioned.uuid());
        try {
            restored = SubLevelSerializer.fullyLoad(serverLevel, positioned);
        } catch (final RuntimeException exception) {
            rollbackDestination(serverLevel, destinationContainer, transfer, positioned.uuid(), token,
                    "fullyLoad threw", exception);
            return fail(feedbackPlayer, "Could not restore the sublevel in this dimension.");
        }
        if (restored == null) {
            rollbackDestination(serverLevel, destinationContainer, transfer, positioned.uuid(), token,
                    "loader returned null", null);
            return fail(feedbackPlayer, "Could not restore the sublevel; its Sable plot may be unavailable.");
        }

        final int destinationIndex = destinationContainer.getIndex(
                transfer.destinationPlotX(), transfer.destinationPlotZ());
        final boolean identityRegistered = destinationContainer.getSubLevel(restored.getUniqueId()) == restored;
        final boolean plotRegistered = destinationContainer.getSubLevel(
                transfer.destinationPlotX(), transfer.destinationPlotZ()) == restored;
        final boolean destinationReserved = destinationContainer.getOccupancy().get(destinationIndex);
        final boolean chunksLoaded = !restored.getPlot().getLoadedChunks().isEmpty();
        final boolean destinationValid = identityRegistered && plotRegistered
                && destinationReserved && chunksLoaded;
        PocketTrace.logger().info(
                "[PocketTransfer] load validation token={} target={} destinationPlot=({}, {}) uuid={} "
                        + "identityRegistered={} plotRegistered={} reserved={} chunksLoaded={} backendValid={}",
                token, serverLevel.dimension().location(),
                transfer.destinationPlotX(), transfer.destinationPlotZ(), restored.getUniqueId(),
                identityRegistered, plotRegistered, destinationReserved, chunksLoaded, destinationValid);
        if (!destinationValid) {
            rollbackDestination(serverLevel, destinationContainer, transfer, positioned.uuid(), token,
                    "backend registration validation failed", null);
            return fail(feedbackPlayer, "The destination sublevel backend rejected the restored payload.");
        }

        final PayloadIntegrity integrity = validateRestoredPayload(
                restored,
                fullTag,
                token,
                storedCount(stack, BLOCKS_KEY),
                storedCount(stack, BLOCK_ENTITIES_KEY));
        PocketTrace.logger().info(
                "[PocketTransfer] payload validation token={} target={} expectedChunks={} loadedChunks={} "
                        + "serializedBlockEntities={} canonicalBlockEntities={} validBlockEntities={} "
                        + "expectedBlocks={} restoredBlocks={} repairedOrphans={} proof={} backendValid={}",
                token, serverLevel.dimension().location(), integrity.expectedChunks(), integrity.loadedChunks(),
                integrity.serializedBlockEntities(), integrity.canonicalBlockEntities(),
                integrity.validBlockEntities(), integrity.expectedBlocks(), integrity.restoredBlocks(),
                integrity.repairedOrphans(), integrity.proof(), integrity.valid());
        if (!integrity.valid()) {
            rollbackDestination(serverLevel, destinationContainer, transfer, positioned.uuid(), token,
                    "payload integrity validation failed", null);
            return fail(feedbackPlayer, "The destination backend could not restore the complete sublevel payload.");
        }

        try {
            ScaleController.adoptRestoredScale(restored, PocketSized.MIN_SCALE);

            if (checkPlacement) {
                for (final var actor : restored.getPlot().getBlockEntityActors()) {
                }
            }

            ScaleNetwork.sendScale(restored, PocketSized.MIN_SCALE, PocketSized.MIN_SCALE, true);
        } catch (final RuntimeException exception) {
            rollbackDestination(serverLevel, destinationContainer, transfer, positioned.uuid(), token,
                    "scale initialization failed", exception);
            return fail(feedbackPlayer, "The restored sublevel could not be initialized safely.");
        }

        com.misterblusky9.pocket.pocket.PocketedEntities.restore(serverLevel, fullTag);

        backend.storage().remove(token);
        boolean sourceReservationReleased = false;
        if (backend.crossDimension()) {
            final int sourceIndex = backend.sourceContainer().getIndex(
                    backend.sourcePlotX(), backend.sourcePlotZ());
            if (backend.sourceContainer().getSubLevel(
                    backend.sourcePlotX(), backend.sourcePlotZ()) == null) {
                backend.sourceContainer().getOccupancy().clear(sourceIndex);
                SubLevelOccupancySavedData.getOrLoad(backend.sourceLevel()).setDirty();
                sourceReservationReleased = !backend.sourceContainer().getOccupancy().get(sourceIndex);
            } else {
                PocketTrace.logger().error(
                        "[PocketTransfer] source cleanup refused token={} source={} plot=({}, {}) "
                                + "reason=live sublevel appeared",
                        token, backend.sourceLevel().dimension().location(),
                        backend.sourcePlotX(), backend.sourcePlotZ());
            }
        }
        PocketTrace.logger().info(
                "[PocketTransfer] commit token={} source={} target={} payloadRemoved=true "
                        + "sourceReservationReleased={} backendValid=true",
                token, backend.sourceLevel().dimension().location(), serverLevel.dimension().location(),
                sourceReservationReleased);
        return restored;
    }

    private static ServerLevel sourceLevel(final ServerLevel targetLevel, final ItemStack stack) {
        final ResourceLocation sourceId = ResourceLocation.tryParse(sourceDimension(stack));
        if (sourceId == null) return null;
        final ResourceKey<Level> sourceKey = ResourceKey.create(Registries.DIMENSION, sourceId);
        return targetLevel.getServer().getLevel(sourceKey);
    }

    private static PayloadBackend resolvePayloadBackend(
            final ServerLevel targetLevel,
            final UUID token,
            final String sourceDimension,
            final Player feedbackPlayer
    ) {
        final ResourceLocation sourceId = ResourceLocation.tryParse(sourceDimension);
        if (sourceId == null) {
            PocketTrace.logger().error(
                    "[PocketTransfer] invalid source backend token={} sourceDimension={} reason=invalid id",
                    token, sourceDimension);
            fail(feedbackPlayer, "The package has an invalid source dimension.");
            return null;
        }

        final ResourceKey<Level> sourceKey = ResourceKey.create(Registries.DIMENSION, sourceId);
        final ServerLevel sourceLevel = targetLevel.getServer().getLevel(sourceKey);
        if (sourceLevel == null) {
            PocketTrace.logger().error(
                    "[PocketTransfer] invalid source backend token={} sourceDimension={} reason=dimension unavailable",
                    token, sourceId);
            fail(feedbackPlayer, "The package's source dimension is unavailable.");
            return null;
        }

        final PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(sourceLevel);
        final CompoundTag fullTag = storage.getCopy(token);
        if (fullTag == null) {
            PocketTrace.logger().error(
                    "[PocketTransfer] invalid source backend token={} sourceDimension={} reason=payload missing",
                    token, sourceId);
            fail(feedbackPlayer, "The stored sublevel payload no longer exists.");
            return null;
        }

        final CompoundTag plotTag = fullTag.getCompound("plot");
        if (!plotTag.contains("plot_x", Tag.TAG_INT) || !plotTag.contains("plot_z", Tag.TAG_INT)) {
            PocketTrace.logger().error(
                    "[PocketTransfer] invalid source backend token={} sourceDimension={} reason=plot coordinates missing",
                    token, sourceId);
            fail(feedbackPlayer, "The stored sublevel plot metadata is invalid.");
            return null;
        }

        final int sourcePlotX = plotTag.getInt("plot_x");
        final int sourcePlotZ = plotTag.getInt("plot_z");
        final ServerSubLevelContainer sourceContainer = SubLevelContainer.getContainer(sourceLevel);
        if (sourceContainer == null) {
            PocketTrace.logger().error(
                    "[PocketTransfer] invalid source backend token={} sourceDimension={} reason=no Sable container",
                    token, sourceId);
            fail(feedbackPlayer, "The package's source sublevel backend is unavailable.");
            return null;
        }

        final boolean inBounds = plotInBounds(sourceContainer, sourcePlotX, sourcePlotZ);
        final boolean reserved = inBounds
                && sourceContainer.getOccupancy().get(sourceContainer.getIndex(sourcePlotX, sourcePlotZ));
        final boolean live = inBounds && sourceContainer.getSubLevel(sourcePlotX, sourcePlotZ) != null;
        PocketTrace.logger().info(
                "[PocketTransfer] source validation token={} source={} target={} payload=true plot=({}, {}) "
                        + "inBounds={} reserved={} live={} backendValid={}",
                token, sourceId, targetLevel.dimension().location(), sourcePlotX, sourcePlotZ,
                inBounds, reserved, live, inBounds && !live);

        if (!inBounds) {
            fail(feedbackPlayer, "The stored sublevel plot is outside the source backend.");
            return null;
        }

        if (live) {
            PocketTrace.logger().warn(
                    "[PocketTransfer] source plot occupied token={} source={} plot=({}, {}) - restoring "
                            + "to a free plot instead. A craft is present where this payload was "
                            + "captured, which means a capture's removal was never saved; the payload "
                            + "is authoritative and is being recovered.",
                    token, sourceId, sourcePlotX, sourcePlotZ);
        }

        if (!reserved) {
            PocketTrace.logger().warn(
                    "[PocketTransfer] source reservation missing token={} source={} plot=({}, {}); "
                            + "continuing from authoritative payload",
                    token, sourceId, sourcePlotX, sourcePlotZ);
        }

        return new PayloadBackend(
                sourceLevel, sourceContainer, storage, fullTag, sourcePlotX, sourcePlotZ,
                !sourceLevel.dimension().equals(targetLevel.dimension()));
    }

    private static PlotTransfer preparePlotTransfer(
            final ServerLevel targetLevel,
            final PayloadBackend backend,
            final CompoundTag fullTag,
            final UUID token,
            final Player feedbackPlayer
    ) {
        final ServerSubLevelContainer sourceContainer = backend.sourceContainer();
        final ServerSubLevelContainer targetContainer = SubLevelContainer.getContainer(targetLevel);
        if (sourceContainer == null || targetContainer == null) {
            PocketTrace.logger().error(
                    "[PocketTransfer] invalid backend token={} sourceContainer={} targetContainer={}",
                    token, sourceContainer != null, targetContainer != null);
            fail(feedbackPlayer, "A sublevel backend is unavailable.");
            return null;
        }

        final int serializedLogSize = fullTag.getCompound("plot").getInt("log_size");
        final boolean compatible = serializedLogSize == sourceContainer.getLogPlotSize()
                && serializedLogSize == targetContainer.getLogPlotSize();
        if (!compatible) {
            PocketTrace.logger().error(
                    "[PocketTransfer] incompatible backends token={} serializedLogSize={} sourceLogSize={} "
                            + "targetLogSize={}",
                    token, serializedLogSize, sourceContainer.getLogPlotSize(), targetContainer.getLogPlotSize());
            fail(feedbackPlayer, "The source and destination sublevel backends are incompatible.");
            return null;
        }

        int destinationX = backend.sourcePlotX();
        int destinationZ = backend.sourcePlotZ();
        final boolean sameDimension = !backend.crossDimension();
        final boolean originalAvailable = plotInBounds(targetContainer, destinationX, destinationZ)
                && targetContainer.getSubLevel(destinationX, destinationZ) == null
                && (sameDimension || !targetContainer.getOccupancy().get(
                        targetContainer.getIndex(destinationX, destinationZ)));

        if (!originalAvailable) {
            final int side = 1 << targetContainer.getLogSideLength();
            boolean found = false;
            for (int x = 0; x < side && !found; x++) {
                for (int z = 0; z < side; z++) {
                    final int index = targetContainer.getIndex(x, z);
                    if (targetContainer.getOccupancy().get(index)
                            || targetContainer.getSubLevel(x, z) != null) continue;
                    destinationX = x;
                    destinationZ = z;
                    found = true;
                    break;
                }
            }
            if (!found) {
                PocketTrace.logger().error(
                        "[PocketTransfer] invalid target backend token={} target={} reason=no free plots",
                        token, targetLevel.dimension().location());
                fail(feedbackPlayer, "The destination dimension has no free sublevel plots.");
                return null;
            }
        }

        final int plotBlockSize = 1 << (targetContainer.getLogPlotSize() + 4);
        final int sourceGlobalX = sourceContainer.getOrigin().x + backend.sourcePlotX();
        final int sourceGlobalZ = sourceContainer.getOrigin().y + backend.sourcePlotZ();
        final int targetGlobalX = targetContainer.getOrigin().x + destinationX;
        final int targetGlobalZ = targetContainer.getOrigin().y + destinationZ;
        final int deltaX = (targetGlobalX - sourceGlobalX) * plotBlockSize;

        final int deltaY = targetLevel.getMinBuildHeight() - backend.sourceLevel().getMinBuildHeight();
        final int deltaZ = (targetGlobalZ - sourceGlobalZ) * plotBlockSize;
        final boolean destinationInitiallyReserved = targetContainer.getOccupancy().get(
                targetContainer.getIndex(destinationX, destinationZ));

        PocketTrace.logger().info(
                "[PocketTransfer] target validation token={} target={} preferredPlot=({}, {}) "
                        + "preferredAvailable={} destinationPlot=({}, {}) deltaY={} "
                        + "destinationInitiallyReserved={} rebaseRequired={} backendValid=true",
                token, targetLevel.dimension().location(), backend.sourcePlotX(), backend.sourcePlotZ(),
                originalAvailable, destinationX, destinationZ, deltaY, destinationInitiallyReserved,
                deltaX != 0 || deltaY != 0 || deltaZ != 0);
        return new PlotTransfer(
                destinationX, destinationZ, deltaX, deltaY, deltaZ, destinationInitiallyReserved);
    }

    private static RebaseStats rebasePlotPayload(
            final CompoundTag fullTag,
            final int destinationPlotX,
            final int destinationPlotZ,
            final int deltaX,
            final int deltaY,
            final int deltaZ
    ) {
        final CompoundTag plotTag = fullTag.getCompound("plot");
        plotTag.putInt("plot_x", destinationPlotX);
        plotTag.putInt("plot_z", destinationPlotZ);

        final Pose3d storedPose = SableNBTUtils.readPose3d(fullTag.getCompound("pose"));
        storedPose.rotationPoint().add(deltaX, deltaY, deltaZ);
        fullTag.put("pose", SableNBTUtils.writePose3d(storedPose));

        int blockEntities = 0;
        int ticks = 0;
        final CompoundTag chunks = plotTag.getCompound("chunks");
        for (final String chunkKey : chunks.getAllKeys()) {
            final CompoundTag chunk = chunks.getCompound(chunkKey);
            blockEntities += shiftPositionList(
                    chunk.getList("block_entities", Tag.TAG_COMPOUND), deltaX, deltaY, deltaZ);
            ticks += shiftPositionList(
                    chunk.getList("block_ticks", Tag.TAG_COMPOUND), deltaX, deltaY, deltaZ);
            ticks += shiftPositionList(
                    chunk.getList("fluid_ticks", Tag.TAG_COMPOUND), deltaX, deltaY, deltaZ);
        }

        final int entities = com.misterblusky9.pocket.pocket.PocketedEntities
                .rebase(fullTag, deltaX, deltaY, deltaZ);
        return new RebaseStats(blockEntities, ticks, entities);
    }

    private static int shiftPositionList(
            final ListTag entries,
            final int deltaX,
            final int deltaY,
            final int deltaZ
    ) {
        int shifted = 0;
        for (int i = 0; i < entries.size(); i++) {
            final CompoundTag entry = entries.getCompound(i);
            if (!entry.contains("x", Tag.TAG_ANY_NUMERIC)
                    || !entry.contains("y", Tag.TAG_ANY_NUMERIC)
                    || !entry.contains("z", Tag.TAG_ANY_NUMERIC)) continue;
            entry.putInt("x", entry.getInt("x") + deltaX);
            entry.putInt("y", entry.getInt("y") + deltaY);
            entry.putInt("z", entry.getInt("z") + deltaZ);
            shifted++;
        }
        return shifted;
    }

    private static PayloadIntegrity validateRestoredPayload(
            final ServerSubLevel restored,
            final CompoundTag fullTag,
            final UUID token,
            final int carrierBlocks,
            final int carrierBlockEntities
    ) {
        final CompoundTag chunks = fullTag.getCompound("plot").getCompound("chunks");
        final int expectedChunks = chunks.size();
        final int loadedChunks = restored.getPlot().getLoadedChunks().size();
        final PayloadStructure structure = measureStructure(restored);
        final BlockEntityAudit audit = auditBlockEntities(restored, fullTag, token, "restore");
        final int canonicalBlockEntities = audit.serializedEntries() - audit.orphans().size();

        final boolean manifestPresent = fullTag.contains(INTEGRITY_KEY, Tag.TAG_COMPOUND);
        final CompoundTag manifest = fullTag.getCompound(INTEGRITY_KEY);
        final boolean manifestSupported = manifestPresent
                && manifest.getInt(INTEGRITY_VERSION_KEY) == INTEGRITY_VERSION
                && manifest.contains(INTEGRITY_BLOCKS_KEY, Tag.TAG_INT)
                && manifest.contains(INTEGRITY_BLOCK_ENTITIES_KEY, Tag.TAG_INT)
                && manifest.contains(INTEGRITY_CHUNKS_KEY, Tag.TAG_INT)
                && manifest.contains(INTEGRITY_STRUCTURE_HASH_KEY, Tag.TAG_LONG);

        final int expectedBlocks = manifestSupported
                ? manifest.getInt(INTEGRITY_BLOCKS_KEY) : carrierBlocks;
        final int expectedHostBlockEntities = manifestSupported
                ? manifest.getInt(INTEGRITY_BLOCK_ENTITIES_KEY) : carrierBlockEntities;

        final boolean commonStructureMatches = expectedChunks == loadedChunks
                && expectedChunks == structure.chunks()
                && (expectedBlocks <= 0 || expectedBlocks == structure.blocks())
                && (expectedHostBlockEntities <= 0
                        || expectedHostBlockEntities == structure.blockEntities());
        final boolean manifestMatches = manifestSupported
                && commonStructureMatches
                && manifest.getInt(INTEGRITY_CHUNKS_KEY) == structure.chunks()
                && manifest.getLong(INTEGRITY_STRUCTURE_HASH_KEY) == structure.structureHash();
        final boolean legacyCountsMatch = !manifestPresent
                && carrierBlocks > 0
                && carrierBlockEntities > 0
                && commonStructureMatches;
        final boolean repairProved = audit.orphans().isEmpty() || manifestMatches || legacyCountsMatch;
        final boolean canonicalCountsMatch = canonicalBlockEntities == audit.validEntries()
                && (expectedHostBlockEntities <= 0
                        || canonicalBlockEntities == expectedHostBlockEntities);

        final boolean valid = !audit.fatal()
                && (!manifestPresent || manifestSupported)
                && (!manifestPresent || manifestMatches)
                && commonStructureMatches
                && canonicalCountsMatch
                && repairProved;
        final String proof = manifestPresent
                ? manifestMatches ? "manifest_hash" : "manifest_mismatch"
                : legacyCountsMatch ? "legacy_counts"
                : audit.orphans().isEmpty() ? "not_needed"
                : "none";

        if (valid && !audit.orphans().isEmpty()) {
            pruneOrphans(audit.orphans(), token, "restore", proof);
        } else if (!valid && !audit.orphans().isEmpty()) {
            PocketTrace.logger().error(
                    "[PocketTransfer] orphan repair refused token={} candidates={} proof={} "
                            + "expectedBlocks={} restoredBlocks={} expectedBlockEntities={} "
                            + "restoredHostBlockEntities={} structureHash=0x{} backendValid=false",
                    token, audit.orphans().size(), proof,
                    expectedBlocks, structure.blocks(), expectedHostBlockEntities,
                    structure.blockEntities(), Long.toHexString(structure.structureHash()));
        }

        return new PayloadIntegrity(
                expectedChunks,
                loadedChunks,
                audit.serializedEntries(),
                canonicalBlockEntities,
                audit.validEntries(),
                valid ? audit.orphans().size() : 0,
                expectedBlocks,
                structure.blocks(),
                proof,
                valid);
    }

    private static BlockEntityAudit auditBlockEntities(
            final ServerSubLevel subLevel,
            final CompoundTag fullTag,
            final UUID token,
            final String route
    ) {
        final CompoundTag chunks = fullTag.getCompound("plot").getCompound("chunks");
        final List<OrphanBlockEntityTag> orphans = new ArrayList<>();
        int serializedEntries = 0;
        int validEntries = 0;
        boolean fatal = false;

        for (final String chunkKey : chunks.getAllKeys()) {
            final ListTag entries = chunks.getCompound(chunkKey)
                    .getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                serializedEntries++;
                final CompoundTag entry = entries.getCompound(i);
                if (!entry.contains("x", Tag.TAG_ANY_NUMERIC)
                        || !entry.contains("y", Tag.TAG_ANY_NUMERIC)
                        || !entry.contains("z", Tag.TAG_ANY_NUMERIC)) {
                    fatal = true;
                    PocketTrace.logger().error(
                            "[PocketTransfer] payload block entity fatal token={} route={} chunk={} "
                                    + "index={} reason=position missing",
                            token, route, chunkKey, i);
                    continue;
                }

                final BlockPos position = new BlockPos(
                        entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                try {
                    final ChunkPos localChunk = subLevel.getPlot().toLocal(new ChunkPos(position));
                    final long serializedChunk = Long.parseLong(chunkKey);
                    final var loadedChunk = subLevel.getPlot().getChunk(localChunk);
                    if (serializedChunk != localChunk.toLong() || loadedChunk == null) {
                        fatal = true;
                        PocketTrace.logger().error(
                                "[PocketTransfer] payload block entity fatal token={} route={} chunk={} pos={} "
                                        + "localChunk={} chunkLoaded={} reason=chunk mapping mismatch",
                                token, route, chunkKey, position, localChunk, loadedChunk != null);
                        continue;
                    }

                    final var state = loadedChunk.getBlockState(position);
                    final var actual = loadedChunk.getBlockEntity(position);
                    final String expectedId = entry.getString("id");
                    final ResourceLocation actualId = actual == null ? null
                            : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(actual.getType());
                    final boolean idMatches = expectedId.isEmpty()
                            || actualId != null && expectedId.equals(actualId.toString());

                    if (state.isAir() && actual == null) {
                        orphans.add(new OrphanBlockEntityTag(
                                entries, i, chunkKey, position, expectedId));
                        PocketTrace.logger().warn(
                                "[PocketTransfer] payload block entity repair candidate token={} route={} "
                                        + "chunk={} pos={} expectedId={} hostBlock=minecraft:air "
                                        + "reason=impossible orphan",
                                token, route, chunkKey, position, expectedId);
                        continue;
                    }

                    if (state.isAir() || actual == null || !idMatches) {
                        fatal = true;
                        PocketTrace.logger().error(
                                "[PocketTransfer] payload block entity fatal token={} route={} chunk={} pos={} "
                                        + "expectedId={} actualId={} hostBlock={} reason={}",
                                token, route, chunkKey, position, expectedId, actualId,
                                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                                state.isAir() ? "air host still has an instance"
                                        : actual == null ? "instance missing" : "type mismatch");
                        continue;
                    }
                    validEntries++;
                } catch (final RuntimeException exception) {
                    fatal = true;
                    PocketTrace.logger().error(
                            "[PocketTransfer] payload block entity audit threw token={} route={} chunk={} pos={}",
                            token, route, chunkKey, position, exception);
                }
            }
        }

        return new BlockEntityAudit(serializedEntries, validEntries, fatal, orphans);
    }

    private static void pruneOrphans(
            final List<OrphanBlockEntityTag> orphans,
            final UUID token,
            final String route,
            final String proof
    ) {
        for (int i = orphans.size() - 1; i >= 0; i--) {
            final OrphanBlockEntityTag orphan = orphans.get(i);
            orphan.owner().remove(orphan.index());
            PocketTrace.logger().warn(
                    "[PocketTransfer] payload block entity canonicalized token={} route={} chunk={} "
                            + "pos={} expectedId={} proof={} action=removed_impossible_tag",
                    token, route, orphan.chunkKey(), orphan.position(), orphan.expectedId(), proof);
        }
    }

    private static void writeIntegrityManifest(
            final CompoundTag fullTag,
            final PayloadStructure structure
    ) {
        final CompoundTag manifest = new CompoundTag();
        manifest.putInt(INTEGRITY_VERSION_KEY, INTEGRITY_VERSION);
        manifest.putInt(INTEGRITY_BLOCKS_KEY, structure.blocks());
        manifest.putInt(INTEGRITY_BLOCK_ENTITIES_KEY, structure.blockEntities());
        manifest.putInt(INTEGRITY_CHUNKS_KEY, structure.chunks());
        manifest.putLong(INTEGRITY_STRUCTURE_HASH_KEY, structure.structureHash());
        fullTag.put(INTEGRITY_KEY, manifest);
    }

    private static PayloadStructure measureStructure(final ServerSubLevel subLevel) {
        final var bounds = subLevel.getPlot().getBoundingBox();
        final List<LevelChunk> chunks = new ArrayList<>();
        for (final var holder : subLevel.getPlot().getLoadedChunks()) chunks.add(holder.getChunk());
        chunks.sort(Comparator.comparingLong(chunk -> chunk.getPos().toLong()));

        int blocks = 0;
        int blockEntities = 0;
        long hash = STRUCTURE_HASH_OFFSET;
        hash = hashStructure(hash, bounds.maxX() - bounds.minX() + 1);
        hash = hashStructure(hash, bounds.maxY() - bounds.minY() + 1);
        hash = hashStructure(hash, bounds.maxZ() - bounds.minZ() + 1);

        for (final LevelChunk chunk : chunks) {
            final ChunkPos chunkPos = chunk.getPos();
            final int minX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
            final int maxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
            final int minZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
            final int maxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
            if (minX > maxX || minZ > maxZ) continue;

            final LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                final LevelChunkSection section = sections[index];
                if (section.hasOnlyAir()) continue;

                final int sectionMinY = chunk.getSectionYFromSectionIndex(index) << 4;
                final int minY = Math.max(bounds.minY(), sectionMinY);
                final int maxY = Math.min(bounds.maxY(), sectionMinY + 15);
                if (minY > maxY) continue;

                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            final var state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (state.isAir()) continue;
                            blocks++;
                            if (state.hasBlockEntity()) blockEntities++;
                            hash = hashStructure(hash, x - bounds.minX());
                            hash = hashStructure(hash, y - bounds.minY());
                            hash = hashStructure(hash, z - bounds.minZ());
                            hash = hashBlockState(hash, state);
                        }
                    }
                }
            }
        }
        return new PayloadStructure(blocks, blockEntities, chunks.size(), hash);
    }

    private static long hashBlockState(long hash, final BlockState state) {
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        hash = hashStructure(hash, blockId == null ? "" : blockId.toString());

        final List<Property<?>> properties = new ArrayList<>(state.getProperties());
        properties.sort(Comparator.comparing(Property::getName));
        hash = hashStructure(hash, properties.size());
        for (final Property<?> property : properties) {
            hash = hashStructure(hash, property.getName());
            hash = hashStructure(hash, propertyValueName(state, property));
        }
        return hash;
    }

    private static <T extends Comparable<T>> String propertyValueName(
            final BlockState state,
            final Property<T> property
    ) {
        return property.getName(state.getValue(property));
    }

    private static long hashStructure(long hash, final String value) {
        hash = hashStructure(hash, value.length());
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= STRUCTURE_HASH_PRIME;
        }
        return hash;
    }

    private static long hashStructure(long hash, final int value) {
        hash ^= value & 0xffffffffL;
        hash *= STRUCTURE_HASH_PRIME;
        return hash;
    }

    private static boolean plotInBounds(
            final ServerSubLevelContainer container,
            final int plotX,
            final int plotZ
    ) {
        final int side = 1 << container.getLogSideLength();
        return plotX >= 0 && plotZ >= 0 && plotX < side && plotZ < side;
    }

    private static void rollbackDestination(
            final ServerLevel level,
            final ServerSubLevelContainer container,
            final PlotTransfer transfer,
            final UUID identity,
            final UUID token,
            final String reason,
            final RuntimeException exception
    ) {
        final var partial = container.getSubLevel(identity);
        boolean removalSucceeded = partial == null;
        if (partial != null) {
            try {
                container.removeSubLevel(partial, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                removalSucceeded = container.getSubLevel(identity) == null;
            } catch (final RuntimeException cleanupFailure) {
                PocketTrace.logger().error(
                        "[PocketTransfer] rollback cleanup failed token={} uuid={}",
                        token, identity, cleanupFailure);
            }
        }

        if (removalSucceeded) {
            ScaleState.clearServerState(identity);
            ScaleState.clearServerBounds(identity);
            ScaledBoundsCollider.forgetSubLevel(identity);
            ScaledFluidForces.forget(identity);
            PocketMetrics.invalidate(identity);
        }

        boolean reservationRestored = false;
        if (removalSucceeded && container.getSubLevel(
                transfer.destinationPlotX(), transfer.destinationPlotZ()) == null) {
            final int index = container.getIndex(
                    transfer.destinationPlotX(), transfer.destinationPlotZ());
            if (transfer.destinationInitiallyReserved()) container.getOccupancy().set(index);
            else container.getOccupancy().clear(index);
            SubLevelOccupancySavedData.getOrLoad(level).setDirty();
            reservationRestored = container.getOccupancy().get(index)
                    == transfer.destinationInitiallyReserved();
        }
        final boolean rollbackComplete = removalSucceeded && reservationRestored;
        if (exception == null) {
            PocketTrace.logger().error(
                    "[PocketTransfer] load invalid token={} uuid={} rollbackComplete={} "
                            + "reservationRestored={} reason={}",
                    token, identity, rollbackComplete, reservationRestored, reason);
        } else {
            PocketTrace.logger().error(
                    "[PocketTransfer] load invalid token={} uuid={} rollbackComplete={} "
                            + "reservationRestored={} reason={} exception={}",
                    token, identity, rollbackComplete, reservationRestored, reason,
                    exception.toString(), exception);
        }
    }

    private record PayloadBackend(
            ServerLevel sourceLevel,
            ServerSubLevelContainer sourceContainer,
            PocketedSubLevelSavedData storage,
            CompoundTag fullTag,
            int sourcePlotX,
            int sourcePlotZ,
            boolean crossDimension
    ) {}

    private record PlotTransfer(
            int destinationPlotX,
            int destinationPlotZ,
            int deltaBlocksX,
            int deltaBlocksY,
            int deltaBlocksZ,
            boolean destinationInitiallyReserved
    ) {}

    private record RebaseStats(int blockEntities, int ticks, int entities) {}

    private record PayloadStructure(
            int blocks,
            int blockEntities,
            int chunks,
            long structureHash
    ) {}

    private record OrphanBlockEntityTag(
            ListTag owner,
            int index,
            String chunkKey,
            BlockPos position,
            String expectedId
    ) {}

    private record BlockEntityAudit(
            int serializedEntries,
            int validEntries,
            boolean fatal,
            List<OrphanBlockEntityTag> orphans
    ) {}

    private record PayloadIntegrity(
            int expectedChunks,
            int loadedChunks,
            int serializedBlockEntities,
            int canonicalBlockEntities,
            int validBlockEntities,
            int repairedOrphans,
            int expectedBlocks,
            int restoredBlocks,
            String proof,
            boolean valid
    ) {}

    private static boolean placementClear(final ServerLevel level, final AABB candidate) {
        if (level.getBlockCollisions(null, candidate).iterator().hasNext()) return false;
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return true;
        for (final ServerSubLevel other : container.getAllSubLevels()) {
            if (other.isRemoved()) continue;
            final var b = other.boundingBox();
            if (candidate.maxX > b.minX() && candidate.minX < b.maxX()
                    && candidate.maxY > b.minY() && candidate.minY < b.maxY()
                    && candidate.maxZ > b.minZ() && candidate.minZ < b.maxZ()) return false;
        }
        return true;
    }

    public static boolean payloadAvailable(final ServerLevel level, final ItemStack stack) {
        final UUID token = token(stack);
        if (token == null) return false;

        final ResourceLocation sourceId = ResourceLocation.tryParse(sourceDimension(stack));
        final ServerLevel sourceLevel = sourceId == null ? null : level.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, sourceId));
        if (sourceLevel == null) return true;

        return PocketedSubLevelSavedData.getOrLoad(sourceLevel).contains(token);
    }

    private static ServerSubLevel fail(final Player player, final String message) {
        if (player != null) player.displayClientMessage(Component.literal(message), true);
        return null;
    }

    private static PlacementExtents placementExtents(
            final PocketRenderSnapshot snapshot,
            final SubLevelData original,
            final Pose3d pose
    ) {
        if (snapshot == null || !snapshot.hasPlacementGeometry()) {
            final BoundingBox3d oldBounds = original.bounds();
            final Vector3d oldPosition = new Vector3d(original.pose().position());
            return new PlacementExtents(
                    oldBounds.minX() - oldPosition.x, oldBounds.minY() - oldPosition.y, oldBounds.minZ() - oldPosition.z,
                    oldBounds.maxX() - oldPosition.x, oldBounds.maxY() - oldPosition.y, oldBounds.maxZ() - oldPosition.z
            );
        }

        final int[] b = snapshot.localBounds();
        final Vector3d rp = snapshot.rotationPoint();
        final Quaterniond q = new Quaterniond(pose.orientation());
        final double s = PocketSized.MIN_SCALE;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        final double[] xs = {b[0], b[3] + 1.0D};
        final double[] ys = {b[1], b[4] + 1.0D};
        final double[] zs = {b[2], b[5] + 1.0D};

        for (final double x : xs) for (final double y : ys) for (final double z : zs) {
            final Vector3d corner = new Vector3d(x, y, z).sub(rp).mul(s);
            q.transform(corner);
            minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
        }
        return new PlacementExtents(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vector3d safePosition(final Vec3 click, final Direction face, final PlacementExtents e) {
        double x = click.x - e.centerX();
        double y = click.y - e.centerY();
        double z = click.z - e.centerZ();
        if (face.getStepX() > 0) x = click.x - e.minX() + SAFE_CLEARANCE;
        else if (face.getStepX() < 0) x = click.x - e.maxX() - SAFE_CLEARANCE;
        if (face.getStepY() > 0) y = click.y - e.minY() + SAFE_CLEARANCE;
        else if (face.getStepY() < 0) y = click.y - e.maxY() - SAFE_CLEARANCE;
        if (face.getStepZ() > 0) z = click.z - e.minZ() + SAFE_CLEARANCE;
        else if (face.getStepZ() < 0) z = click.z - e.maxZ() - SAFE_CLEARANCE;
        return new Vector3d(x, y, z);
    }

    private static double horizontalHeading(final Pose3d pose) {
        final Vector3d forward = new Vector3d(0.0D, 0.0D, 1.0D);
        pose.orientation().transform(forward);
        if (forward.x * forward.x + forward.z * forward.z < 1.0E-10D) return 0.0D;
        return Math.atan2(forward.x, forward.z);
    }

    private static void replaceHeldAfterDeployment(
            final Player player,
            final InteractionHand hand,
            final ItemStack deployedStack
    ) {
        final UUID deployedToken = token(deployedStack);
        final ItemStack emptied = new ItemStack(containerItem(deployedStack));

        deployedStack.setCount(0);
        player.setItemInHand(hand, emptied);
        player.getInventory().setChanged();
        if (player instanceof final ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
            if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) serverPlayer.containerMenu.broadcastChanges();
        }
        PocketTrace.logger().info(
                "[PocketTransfer] carrier replaced token={} player={} hand={} "
                        + "payloadStackEmpty={} returnedContainer={} backendValid=true",
                deployedToken, player.getScoreboardName(), hand, deployedStack.isEmpty(),
                emptied.getItem().builtInRegistryHolder().key().location());
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final List<Component> tooltip, final TooltipFlag flag) {
        final CompoundTag tag = customTag(stack);

        if (tag != null && tag.getBoolean(ORPHANED_KEY)) {
            tooltip.add(Component.literal("⚠ Payload unreachable").withStyle(ChatFormatting.RED));
            tooltip.add(Component.literal("Kept safe — works again if the payload returns")
                    .withStyle(ChatFormatting.GRAY));
            if (flag.isAdvanced()) {
                final String reason = tag.getString(ORPHANED_REASON_KEY);
                if (!reason.isEmpty()) {
                    tooltip.add(Component.literal(reason).withStyle(ChatFormatting.DARK_RED));
                }
            }
        }

        if (tag == null || !tag.hasUUID(TOKEN_KEY)) {
            tooltip.add(Component.literal(
                    "Sneak-use a 1/16× contraption while holding an Empty Cardboard Box.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        if (tag.contains(PACKED_BY_KEY)) {
            tooltip.add(Component.literal("Captured by: " + tag.getString(PACKED_BY_KEY))
                    .withStyle(ChatFormatting.AQUA));
        }

        final int[] localBounds = tag.getIntArray(PocketRenderSnapshot.LOCAL_BOUNDS_KEY);
        final int[] size = tag.getIntArray(PocketRenderSnapshot.SIZE_KEY);
        String summary = null;
        if (localBounds.length == 6) {
            summary = (localBounds[3] - localBounds[0] + 1) + "x"
                    + (localBounds[4] - localBounds[1] + 1) + "x"
                    + (localBounds[5] - localBounds[2] + 1);
        } else if (size.length == 3) {
            summary = size[0] + "x" + size[1] + "x" + size[2];
        }
        final double mass = storedMass(stack);
        if (mass > 0.0D) {
            summary = summary == null ? describeMass(mass) : summary + " • " + describeMass(mass);
        }
        if (summary != null) {
            tooltip.add(Component.literal(summary).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (!flag.isAdvanced()) return;

        tooltip.add(Component.literal("Compressed: 1/16×").withStyle(ChatFormatting.DARK_GRAY));
        if (tag.contains(BLOCKS_KEY)) {
            tooltip.add(Component.literal(tag.getInt(BLOCKS_KEY) + " blocks • "
                    + tag.getInt(BLOCK_ENTITIES_KEY) + " block entities").withStyle(ChatFormatting.DARK_GRAY));
        }
        final int passengers = com.misterblusky9.pocket.pocket.PocketedEntities.count(tag);
        if (passengers > 0) {
            tooltip.add(Component.literal(passengers + (passengers == 1 ? " passenger" : " passengers"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("Potato Cannon ammo").withStyle(ChatFormatting.GOLD));
    }

    private static String describeMass(final double mass) {
        if (mass <= com.misterblusky9.pocket.physics.ScaledMassData.SUPERLIGHT_MASS) {
            return "Superlight";
        }
        if (mass >= 1_000.0D) {
            return String.format(java.util.Locale.ROOT, "%.1ft", mass / 1_000.0D);
        }
        return String.format(java.util.Locale.ROOT, "%.0fkg", mass);
    }

    private record PlacementExtents(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double centerX() { return (minX + maxX) * 0.5D; }
        double centerY() { return (minY + maxY) * 0.5D; }
        double centerZ() { return (minZ + maxZ) * 0.5D; }
    }
}
