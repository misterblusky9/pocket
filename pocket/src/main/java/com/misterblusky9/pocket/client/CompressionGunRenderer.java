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

    private static PartialModel partial(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, path));
    }

    private final PartialModel cogModel;
    private final PartialModel levititeModel;
    private final ClipMesh tankMesh = new ClipMesh(net.minecraft.core.Direction.Axis.Y);

    public CompressionGunRenderer(boolean pearlescent) {
        this.cogModel = pearlescent ? COG_PEARLESCENT : COG;
        this.levititeModel = pearlescent ? LEVITITE_PEARLESCENT : LEVITITE;
    }

    private static BakedModel cogSource;
    private static float cogPivotX;
    private static float cogPivotY;

    private static final float STATE_EXPIRY_TICKS = 200.0F;

    private static final int STATE_SOFT_LIMIT = 64;

    private static final java.util.Map<Object, CompressionCogSpin> SPINS = new java.util.HashMap<>();

    private record LocalSlot(int slot) {}

    @Override
    protected void render(
            ItemStack stack,
            CustomRenderedItemModel model,
            PartialItemModelRenderer renderer,
            ItemDisplayContext transformType,
            PoseStack ms,
            MultiBufferSource buffer,
            int light,
            int overlay
    ) {
        Holder holder = holderOf(stack, transformType);
        boolean growing = com.misterblusky9.pocket.item.CompressionGunItem.isGrowing(holder.live());

        BakedModel body = model.getOriginalModel();

        ms.pushPose();
        CompressionGunMuzzleTracker.capture(stack, transformType, ms, CompressionGunStateModel.muzzle(body));
        renderer.render(CompressionGunStateModel.get(body, panelState(holder), growing), light);

        CompressionCogSpin state = advanceSpin(stack, holder, body);
        if (state.fill > 0.001F) {
            BakedModel tank = levititeModel.get();
            float top = Mth.lerp(Math.min(1.0F, state.fill), tankMesh.min(tank), tankMesh.max(tank));
            tankMesh.render(tank, top, ms, buffer.getBuffer(net.minecraft.client.renderer.Sheets.translucentItemSheet()), light);
        }

        syncCogPivot(COG.get());
        ms.pushPose();
        spinCog(ms, state.angle);

        renderer.render(cogModel.get(), state.glowingLight(light));
        ms.popPose();

        CompressionGunSweep.render(
                body, state.source, state.sweep, growing, transformType,
                AnimationTickHolder.getRenderTime(), ms, buffer, pose -> spinCog(pose, state.angle));
        ms.popPose();
    }

    private static void spinCog(PoseStack ms, float angle) {
        ms.translate(cogPivotX, cogPivotY, 0.0F);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(-cogPivotX, -cogPivotY, 0.0F);
    }

    private static void syncCogPivot(BakedModel cog) {
        if (cog == cogSource) return;
        cogSource = cog;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42L);
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads =
                new java.util.ArrayList<>(cog.getQuads(null, null, random));
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            quads.addAll(cog.getQuads(null, side, random));
        }
        for (var quad : quads) {
            int[] data = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                int o = i * net.neoforged.neoforge.client.model.IQuadTransformer.STRIDE;
                float x = Float.intBitsToFloat(data[o]);
                float y = Float.intBitsToFloat(data[o + 1]);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        cogPivotX = quads.isEmpty() ? 0.0F : (minX + maxX) * 0.5F - 0.5F;
        cogPivotY = quads.isEmpty() ? 0.0F : (minY + maxY) * 0.5F - 0.5F;
    }

    private static CompressionCogSpin advanceSpin(ItemStack rendered, Holder holder, BakedModel body) {
        ItemStack stack = holder.live();
        CompressionCogSpin state = stateFor(stack, holder);
        float targetFill = CompressionGunTank.amount(stack) / (float) CompressionGunTank.capacity(stack);
        float delta = state.frame(AnimationTickHolder.getRenderTime());
        state.easeFill(targetFill, delta);

        Player holderPlayer = holder.player();
        boolean powered = CompressionGunTank.hasPower(holderPlayer, stack);
        boolean triggered = holderPlayer != null && isSpooling(holder);
        boolean spooling = triggered && powered;
        float ticksUsing = triggered
                ? holderPlayer.getTicksUsingItem() + AnimationTickHolder.getPartialTicks()
                : 0.0F;
        state.source = CompressionGunSweep.advanceSource(state.source, spooling, ticksUsing, delta);
        state.sweep = CompressionGunSweep.advance(body, state.sweep, spooling, ticksUsing, delta);

        state.turn(delta, isDriving(holder), powered,
                com.misterblusky9.pocket.item.CompressionGunItem.isGrowing(stack), ticksUsing);
        return state;
    }

    private static CompressionCogSpin stateFor(ItemStack stack, Holder holder) {
        Object key = holder.slot() >= 0 ? new LocalSlot(holder.slot()) : stack;
        CompressionCogSpin state = SPINS.get(key);
        if (state == null) {
            if (SPINS.size() >= STATE_SOFT_LIMIT) prune();
            state = new CompressionCogSpin();
            SPINS.put(key, state);
        }
        return state;
    }

    private record Holder(Player player, int slot, ItemStack live) {}

    private static Holder holderOf(ItemStack stack, ItemDisplayContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer local = minecraft.player;
        if (local != null) {
            if (context.firstPerson()) {
                boolean rightHand = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
                boolean mainHand = rightHand == (local.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
                int slot = mainHand ? local.getInventory().selected : Inventory.SLOT_OFFHAND;
                ItemStack live = mainHand ? local.getMainHandItem() : local.getOffhandItem();
                if (live.getItem() == stack.getItem()) return new Holder(local, slot, live);
            }

            Inventory inventory = local.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                if (inventory.items.get(i) == stack) return new Holder(local, i, stack);
            }
            if (inventory.offhand.get(0) == stack) return new Holder(local, Inventory.SLOT_OFFHAND, stack);
        }
        if (minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
                if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
                    return new Holder(player, -1, stack);
                }
            }
        }
        return new Holder(null, -1, stack);
    }

    private static void prune() {
        float now = AnimationTickHolder.getRenderTime();
        SPINS.values().removeIf(state ->
                !Float.isNaN(state.lastTime) && now - state.lastTime > STATE_EXPIRY_TICKS);
    }

    private static CompressionGunStateModel.State panelState(Holder holder) {
        if (isSpooling(holder)) return CompressionGunStateModel.State.ACTIVE;
        return CompressionGunTank.amount(holder.live()) <= 0
                ? CompressionGunStateModel.State.EMPTY
                : CompressionGunStateModel.State.IDLE;
    }

    private static boolean isSpooling(Holder holder) {
        if (holder.player() instanceof LocalPlayer) return isDriving(holder);
        Player player = holder.player();
        return player != null
                && player.isUsingItem()
                && player.getUseItem().getItem() == holder.live().getItem()
                && CompressionGunTank.hasPower(player, holder.live());
    }

    private static boolean isDriving(Holder holder) {
        if (!(holder.player() instanceof LocalPlayer player) || !player.isUsingItem()) return false;
        int used = player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? player.getInventory().selected
                : Inventory.SLOT_OFFHAND;
        return holder.slot() == used && CompressionGunTank.hasPower(player, holder.live());
    }
}
