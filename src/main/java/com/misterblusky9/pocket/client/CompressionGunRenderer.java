package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.misterblusky9.pocket.item.CompressionGunTank;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CompressionGunRenderer extends CustomRenderedItemModelRenderer {
    private static final PartialModel COG = partial("item/compression_gun/cog");
    private static final PartialModel LEVITITE = partial("item/compression_gun/levitite");
    private static final PartialModel COG_PEARLESCENT = partial("item/compression_gun/cog_pearlescent");
    private static final PartialModel LEVITITE_PEARLESCENT = partial("item/compression_gun/levitite_pearlescent");

    private static PartialModel partial(final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, path));
    }

    private final PartialModel cogModel;
    private final PartialModel levititeModel;

    public CompressionGunRenderer(final boolean pearlescent) {
        this.cogModel = pearlescent ? COG_PEARLESCENT : COG;
        this.levititeModel = pearlescent ? LEVITITE_PEARLESCENT : LEVITITE;
    }

    private static final float MAX_SPIN_SPEED = 62.0F;

    private static final float COAST_RATE = 0.045F;

    private static BakedModel cogSource;
    private static float cogPivotX;
    private static float cogPivotY;

    private static final float GLOW_THRESHOLD = 0.55F;

    private static final float IDLE_SPIN_SPEED = 1.0F;

    private static final float STATE_EXPIRY_TICKS = 200.0F;

    private static final int STATE_SOFT_LIMIT = 64;

    private static final float FILL_EASE = 0.2F;

    private static final java.util.Map<Object, Spin> SPINS = new java.util.HashMap<>();

    private record LocalSlot(int slot) {}

    @Override
    protected void render(
            final ItemStack stack,
            final CustomRenderedItemModel model,
            final PartialItemModelRenderer renderer,
            final ItemDisplayContext transformType,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        final Holder holder = holderOf(stack, transformType);
        final boolean growing = com.misterblusky9.pocket.item.CompressionGunItem.isGrowing(holder.live());

        final BakedModel body = model.getOriginalModel();

        ms.pushPose();
        CompressionGunMuzzleTracker.capture(stack, transformType, ms, CompressionGunStateModel.muzzle(body));
        renderer.render(CompressionGunStateModel.get(body, panelState(holder), growing), light);

        final Spin state = advanceSpin(stack, holder, body);
        CompressionGunTankMesh.render(levititeModel.get(), state.fill, ms, buffer, light);

        syncCogPivot(COG.get());
        ms.pushPose();
        spinCog(ms, state.angle);

        renderer.render(cogModel.get(), glowingLight(light, state.speed));
        ms.popPose();

        CompressionGunSweep.render(
                body, state.source, state.sweep, growing, transformType,
                AnimationTickHolder.getRenderTime(), ms, buffer, pose -> spinCog(pose, state.angle));
        ms.popPose();
    }

    private static void spinCog(final PoseStack ms, final float angle) {
        ms.translate(cogPivotX, cogPivotY, 0.0F);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(-cogPivotX, -cogPivotY, 0.0F);
    }

    private static void syncCogPivot(final BakedModel cog) {
        if (cog == cogSource) return;
        cogSource = cog;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        final net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42L);
        final java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads =
                new java.util.ArrayList<>(cog.getQuads(null, null, random));
        for (final net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            quads.addAll(cog.getQuads(null, side, random));
        }
        for (final var quad : quads) {
            final int[] data = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                final int o = i * net.neoforged.neoforge.client.model.IQuadTransformer.STRIDE;
                final float x = Float.intBitsToFloat(data[o]);
                final float y = Float.intBitsToFloat(data[o + 1]);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        cogPivotX = quads.isEmpty() ? 0.0F : (minX + maxX) * 0.5F - 0.5F;
        cogPivotY = quads.isEmpty() ? 0.0F : (minY + maxY) * 0.5F - 0.5F;
    }

    private static int glowingLight(final int light, final float speed) {
        final float heat = heat(speed);
        if (heat <= 0.0F) return light;

        final int block = net.minecraft.client.renderer.LightTexture.block(light);
        final int sky = net.minecraft.client.renderer.LightTexture.sky(light);
        final int lit = Math.round(Mth.lerp(heat, block, 15.0F));
        return net.minecraft.client.renderer.LightTexture.pack(Math.max(block, lit), sky);
    }

    private static float heat(final float speed) {
        final float fraction = Math.abs(speed) / MAX_SPIN_SPEED;
        return Mth.clamp((fraction - GLOW_THRESHOLD) / (1.0F - GLOW_THRESHOLD), 0.0F, 1.0F);
    }

    private static Spin advanceSpin(final ItemStack rendered, final Holder holder, final BakedModel body) {
        final ItemStack stack = holder.live();
        final Spin state = stateFor(stack, holder);
        final float targetFill = CompressionGunTank.amount(stack) / (float) CompressionGunTank.CAPACITY;
        final float now = AnimationTickHolder.getRenderTime();
        final float delta = Float.isNaN(state.lastTime)
                ? 0.0F
                : Math.max(0.0F, Math.min(4.0F, now - state.lastTime));

        state.lastTime = now;
        state.fill = Float.isNaN(state.fill)
                ? targetFill
                : state.fill + (targetFill - state.fill) * Math.min(1.0F, FILL_EASE * delta);

        final Player holderPlayer = holder.player();
        final boolean spooling = holderPlayer != null && isSpooling(holder);
        final float ticksUsing = spooling
                ? holderPlayer.getTicksUsingItem() + AnimationTickHolder.getPartialTicks()
                : 0.0F;
        state.source = CompressionGunSweep.advanceSource(state.source, spooling, ticksUsing, delta);
        state.sweep = CompressionGunSweep.advance(body, state.sweep, spooling, ticksUsing, delta);

        if (delta <= 0.0F) return state;

        final float direction = com.misterblusky9.pocket.item.CompressionGunItem.isGrowing(stack)
                ? 1.0F : -1.0F;

        if (isDriving(holder)) {
            final float curve = MAX_SPIN_SPEED * com.misterblusky9.pocket.item.CompressionGunItem.spinFraction(ticksUsing);
            final float coasting = state.speed * direction > 0.0F ? Math.abs(state.speed) : 0.0F;
            state.speed = direction * Math.max(curve, coasting);
        } else {
            final float rest = CompressionGunTank.hasAirPressure(holder.player()) ? IDLE_SPIN_SPEED * direction : 0.0F;
            state.speed += (rest - state.speed) * COAST_RATE * delta;
            if (Math.abs(state.speed - rest) < 0.05F) state.speed = rest;
        }
        state.angle = (state.angle + state.speed * delta) % 360.0F;
        return state;
    }

    private static Spin stateFor(final ItemStack stack, final Holder holder) {
        final Object key = holder.slot() >= 0 ? new LocalSlot(holder.slot()) : stack;
        Spin state = SPINS.get(key);
        if (state == null) {
            if (SPINS.size() >= STATE_SOFT_LIMIT) prune();
            state = new Spin();
            SPINS.put(key, state);
        }
        return state;
    }

    private record Holder(Player player, int slot, ItemStack live) {}

    private static Holder holderOf(final ItemStack stack, final ItemDisplayContext context) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer local = minecraft.player;
        if (local != null) {
            if (context.firstPerson()) {
                final boolean rightHand = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
                final boolean mainHand = rightHand == (local.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
                final int slot = mainHand ? local.getInventory().selected : Inventory.SLOT_OFFHAND;
                final ItemStack live = mainHand ? local.getMainHandItem() : local.getOffhandItem();
                if (live.getItem() == stack.getItem()) return new Holder(local, slot, live);
            }

            final Inventory inventory = local.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                if (inventory.items.get(i) == stack) return new Holder(local, i, stack);
            }
            if (inventory.offhand.get(0) == stack) return new Holder(local, Inventory.SLOT_OFFHAND, stack);
        }
        if (minecraft.level != null) {
            for (final Player player : minecraft.level.players()) {
                if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
                    return new Holder(player, -1, stack);
                }
            }
        }
        return new Holder(null, -1, stack);
    }

    private static void prune() {
        final float now = AnimationTickHolder.getRenderTime();
        SPINS.values().removeIf(state ->
                !Float.isNaN(state.lastTime) && now - state.lastTime > STATE_EXPIRY_TICKS);
    }

    private static final class Spin {
        private float angle;
        private float speed;
        private float lastTime = Float.NaN;
        private float fill = Float.NaN;
        private float source;
        private float sweep;
    }

    private static CompressionGunStateModel.State panelState(final Holder holder) {
        if (CompressionGunTank.amount(holder.live()) <= 0) {
            return CompressionGunStateModel.State.EMPTY;
        }
        return isSpooling(holder) ? CompressionGunStateModel.State.ACTIVE : CompressionGunStateModel.State.IDLE;
    }

    // Local player: exact hand slot. Other players: using a compression gun at all.
    private static boolean isSpooling(final Holder holder) {
        if (holder.player() instanceof LocalPlayer) return isDriving(holder);
        final Player player = holder.player();
        return player != null
                && player.isUsingItem()
                && player.getUseItem().getItem() == holder.live().getItem()
                && CompressionGunTank.hasPower(player, holder.live());
    }

    private static boolean isDriving(final Holder holder) {
        if (!(holder.player() instanceof final LocalPlayer player) || !player.isUsingItem()) return false;
        final int used = player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? player.getInventory().selected
                : Inventory.SLOT_OFFHAND;
        return holder.slot() == used && CompressionGunTank.hasPower(player, holder.live());
    }
}
