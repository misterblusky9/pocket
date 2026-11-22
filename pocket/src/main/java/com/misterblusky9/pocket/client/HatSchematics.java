package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.build.BuildHostBlock;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.compat.create.HatSchematicPlacement;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.network.HatCasePlacePayload;
import com.misterblusky9.pym.api.client.Mirrors;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class HatSchematics {
    private static final double AIM_STEP = 0.125D;
    private static final double AIM_PAST = 0.75D;
    private static final int WAIT_TICKS = 60;
    private static @Nullable UUID token;
    private static @Nullable ItemStack stamped;
    private static @Nullable StructureTemplate template;
    private static BlockPos standPos = BlockPos.ZERO;
    private static BlockPos anchor = BlockPos.ZERO;
    private static Rotation rotation = Rotation.NONE;
    private static Mirror mirror = Mirror.NONE;
    private static @Nullable BlockPos waitingStand;
    private static @Nullable UUID waitingToken;
    private static long waitingUntil;
    private static @Nullable BlockPos schematicStand;

    public static boolean deploy(BuildHostBlockEntity stand) {
        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        ItemStack item = handler.getActiveSchematicItem();
        ClientSubLevel hat = BuildMirrors.get(stand.getHat());
        if (!handler.isActive() || item == null || handler.getBounds() == null || hat == null) return false;

        int size = ((BuildHostBlock) stand.getBlockState().getBlock()).scaffoldSize();
        Vec3 centre = handler.getBounds().getCenter();
        BlockPos target = BuildSites.scaffoldOrigin(hat, size, stand.getOffset())
                .offset(size / 2 - (int) centre.x, size - 1, size / 2 - (int) centre.z);
        item.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        item.set(AllDataComponents.SCHEMATIC_ANCHOR, target);
        handler.getTransformation().startAt(target);
        handler.getTransformation().moveTo(target);
        handler.markDirty();
        handler.deploy();
        schematicStand = stand.getBlockPos();
        return true;
    }

    public static boolean deployOnHat() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isShiftKeyDown()
                || !HatSchematicPlacement.holds(player.getMainHandItem())) return false;
        BuildHostBlockEntity stand = aimed(minecraft, player);
        return stand != null && stand.getHat() != null && deploy(stand);
    }

    public static boolean printOnHat() {
        Minecraft minecraft = Minecraft.getInstance();
        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        if (schematicStand == null || minecraft.level == null
                || !(minecraft.level.getBlockEntity(schematicStand) instanceof BuildHostBlockEntity stand)) return false;
        ClientSubLevel hat = BuildMirrors.get(stand.getHat());
        BlockPos at = handler.getTransformation().getAnchor();
        if (hat == null || Sable.HELPER.getContainingClient(at) != hat) return false;
        StructurePlaceSettings settings = handler.getTransformation().toSettings();
        PacketDistributor.sendToServer(new HatCasePlacePayload(schematicStand, at,
                settings.getRotation().ordinal(), settings.getMirror().ordinal()));
        return true;
    }

    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND
                || player.isShiftKeyDown()) return;
        ItemStack held = player.getMainHandItem();
        if (!PocketCaseItem.isFilled(held) || placing(held)) return;
        BuildHostBlockEntity stand = aimed(minecraft, player);
        if (stand == null) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        if (stand.getHat() == null) {
            player.displayClientMessage(Component.translatable("pocket.case.no_helmet"), true);
        } else if (PocketCaseItem.isSealed(held)) {
            player.displayClientMessage(Component.translatable("pocket.case.sealed", held.getHoverName()), true);
        } else {
            begin(stand, held);
        }
    }

    private static @Nullable BuildHostBlockEntity aimed(Minecraft minecraft, LocalPlayer player) {
        Level level = minecraft.level;
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BuildHostBlockEntity direct = standAt(level, hit.getBlockPos());
            if (direct != null) return direct;
            SubLevel subLevel = Sable.HELPER.getContaining(level, hit.getBlockPos());
            BlockPos host = subLevel == null ? null : BuildMirrors.standHost(subLevel);
            if (host != null && level.getBlockEntity(host) instanceof BuildHostBlockEntity stand) return stand;
            reach = Math.min(reach, eye.distanceTo(hit.getLocation()) + AIM_PAST);
        }
        Vec3 look = player.getLookAngle();
        for (double along = 0.0D; along <= reach; along += AIM_STEP) {
            BlockPos at = BlockPos.containing(eye.add(look.scale(along)));
            BuildHostBlockEntity stand = standAt(level, at);
            if (stand == null) stand = standAt(level, at.below());
            if (stand != null) return stand;
        }
        return null;
    }

    private static @Nullable BuildHostBlockEntity standAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof HatStandBlock block
                && level.getBlockEntity(block.standPos(state, pos)) instanceof BuildHostBlockEntity stand ? stand : null;
    }

    public static boolean begin(BuildHostBlockEntity stand, ItemStack stack) {
        UUID held = PocketCaseItem.token(stack);
        ClientSubLevel build = BuildMirrors.peek(held);
        ClientSubLevel hat = BuildMirrors.peek(stand.getHat());
        StructureTemplate built = build == null || hat == null ? null : Mirrors.template(build);
        if (built == null || built.getSize().equals(Vec3i.ZERO)) {
            Level level = stand.getLevel();
            waitingStand = stand.getBlockPos();
            waitingToken = held;
            waitingUntil = level == null ? 0L : level.getGameTime() + WAIT_TICKS;
            return false;
        }
        end();
        waitingToken = null;
        Vec3i size = built.getSize();
        int head = ((BuildHostBlock) stand.getBlockState().getBlock()).scaffoldSize();
        token = held;
        template = built;
        standPos = stand.getBlockPos();
        rotation = Rotation.NONE;
        mirror = Mirror.NONE;
        BlockPos centered = BuildSites.scaffoldOrigin(hat, head, stand.getOffset())
                .offset(head / 2 - size.getX() / 2, head - 1, head / 2 - size.getZ() / 2);
        anchor = PocketCaseItem.rememberedHatAnchor(hat, head, stand.getOffset(), stack, centered);
        stamp(stack);
        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        if (handler.isActive()) handler.loadSettings(stack);
        return true;
    }

    public static boolean placing(@Nullable ItemStack stack) {
        return token != null && stack != null && stack.getItem() instanceof PocketCaseItem
                && token.equals(PocketCaseItem.token(stack));
    }

    public static StructureTemplate template() {
        return template == null ? new StructureTemplate() : template;
    }

    public static boolean swallows(int button, boolean pressed) {
        LocalPlayer player = Minecraft.getInstance().player;
        return token != null && pressed && button == 1 && player != null && !player.isShiftKeyDown();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (waitingToken != null && minecraft.level != null) {
            boolean waiting = minecraft.level.getGameTime() < waitingUntil && waitingToken.equals(PocketCaseItem.token(held))
                    && minecraft.level.getBlockEntity(waitingStand) instanceof BuildHostBlockEntity stand
                    && stand.getHat() != null && !begin(stand, held);
            if (!waiting) waitingToken = null;
        }
        if (token == null) return;
        if (!placing(held) || !(minecraft.level.getBlockEntity(standPos) instanceof BuildHostBlockEntity stand)
                || stand.getHat() == null) {
            end();
            return;
        }
        if (held != stamped || !held.has(AllDataComponents.SCHEMATIC_FILE)) stamp(held);
        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        if (handler.isActive() && handler.isDeployed()) {
            StructurePlaceSettings settings = handler.getTransformation().toSettings();
            anchor = handler.getTransformation().getAnchor();
            rotation = settings.getRotation();
            mirror = settings.getMirror();
        }
    }

    public static boolean print() {
        if (token == null) return true;
        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        StructurePlaceSettings settings = handler.getTransformation().toSettings();
        BlockPos at = handler.getTransformation().getAnchor();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !player.isCreative() && overlaps(at, settings)) {
            player.displayClientMessage(Component.translatable("pocket.case.hat_overlaps"), true);
            return false;
        }
        PacketDistributor.sendToServer(new HatCasePlacePayload(standPos, at,
                settings.getRotation().ordinal(), settings.getMirror().ordinal()));
        end();
        return true;
    }

    private static boolean overlaps(BlockPos at, StructurePlaceSettings settings) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(standPos) instanceof BuildHostBlockEntity stand)) return false;
        ClientSubLevel hat = BuildMirrors.get(stand.getHat());
        if (hat == null) return false;
        PocketCaseItem.HatFit fit = PocketCaseItem.hatFit(level, hat, template().getSize(),
                PocketCaseItem.templateSolids(template()), at, settings.getRotation(), settings.getMirror());
        return fit.inside() && !fit.overlaps().isEmpty();
    }

    private static void stamp(ItemStack stack) {
        stack.set(AllDataComponents.SCHEMATIC_FILE, "pocket:" + token);
        stack.set(AllDataComponents.SCHEMATIC_OWNER, "pocket");
        stack.set(AllDataComponents.SCHEMATIC_BOUNDS, template().getSize());
        stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        stack.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor);
        stack.set(AllDataComponents.SCHEMATIC_ROTATION, rotation);
        stack.set(AllDataComponents.SCHEMATIC_MIRROR, mirror);
        stamped = stack;
    }

    private static void end() {
        if (token == null) return;
        if (stamped != null) {
            stamped.remove(AllDataComponents.SCHEMATIC_FILE);
            stamped.remove(AllDataComponents.SCHEMATIC_OWNER);
            stamped.remove(AllDataComponents.SCHEMATIC_BOUNDS);
            stamped.remove(AllDataComponents.SCHEMATIC_DEPLOYED);
            stamped.remove(AllDataComponents.SCHEMATIC_ANCHOR);
            stamped.remove(AllDataComponents.SCHEMATIC_ROTATION);
            stamped.remove(AllDataComponents.SCHEMATIC_MIRROR);
        }
        token = null;
        stamped = null;
        template = null;
    }

    private HatSchematics() {}
}
