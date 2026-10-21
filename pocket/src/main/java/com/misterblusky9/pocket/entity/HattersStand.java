package com.misterblusky9.pocket.entity;

import com.misterblusky9.pocket.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class HattersStand extends LivingEntity {
    private static final String HEAD_KEY = "HeadItem";
    private static final byte HIT_EVENT = 32;
    private static final long DOUBLE_HIT_TICKS = 5L;
    private static final int HEAD = EquipmentSlot.HEAD.getIndex();

    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    private long lastHit;

    public HattersStand(EntityType<? extends HattersStand> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    @Override
    public void tick() {
        super.tick();
        ItemStack worn = this.armor.get(HEAD);
        if (!worn.isEmpty()) worn.inventoryTick(level(), this, HEAD, false);
    }

    // Equipment

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD ? this.armor.get(HEAD) : ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.HEAD) return;
        verifyEquippedItem(stack);
        onEquipItem(slot, this.armor.set(HEAD, stack), stack);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isSpectator() || held.is(Items.NAME_TAG)) return InteractionResult.PASS;
        ItemStack worn = this.armor.get(HEAD);
        if (held.isEmpty() ? worn.isEmpty() : !held.canEquip(EquipmentSlot.HEAD, this)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;

        if (held.getCount() > 1) {
            if (!worn.isEmpty()) return InteractionResult.FAIL;
            setItemSlot(EquipmentSlot.HEAD, held.split(1));
        } else {
            setItemSlot(EquipmentSlot.HEAD, held);
            player.setItemInHand(hand, worn);
        }
        return InteractionResult.CONSUME;
    }

    // Breaking

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            kill();
            return false;
        }
        if (isInvulnerableTo(source)) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            breakApart(server, true);
            return false;
        }
        boolean always = source.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);
        if (!always && !source.is(DamageTypeTags.CAN_BREAK_ARMOR_STAND)) return false;
        if (source.getEntity() instanceof Player player && !player.getAbilities().mayBuild) return false;
        if (source.isCreativePlayer()) {
            breakApart(server, false);
            return true;
        }

        long now = level().getGameTime();
        if (now - this.lastHit > DOUBLE_HIT_TICKS && !always) {
            level().broadcastEntityEvent(this, HIT_EVENT);
            gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
            this.lastHit = now;
        } else {
            breakApart(server, true);
        }
        return true;
    }

    private void breakApart(ServerLevel level, boolean dropStand) {
        if (dropStand) {
            ItemStack stand = new ItemStack(ModItems.HATTERS_STAND.get());
            if (hasCustomName()) stand.set(DataComponents.CUSTOM_NAME, getCustomName());
            Block.popResource(level, blockPosition(), stand);
        }
        ItemStack worn = this.armor.set(HEAD, ItemStack.EMPTY);
        if (!worn.isEmpty()) Block.popResource(level, blockPosition().above(), worn);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_BREAK, getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                getX(), getY(0.5D), getZ(), 10, getBbWidth() / 4.0F, getBbHeight() / 4.0F, getBbWidth() / 4.0F, 0.05D);
        kill();
    }

    @Override
    public void kill() {
        remove(RemovalReason.KILLED);
        gameEvent(GameEvent.ENTITY_DIE);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id != HIT_EVENT) {
            super.handleEntityEvent(id);
            return;
        }
        if (level().isClientSide) {
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_HIT, getSoundSource(), 0.3F, 1.0F, false);
            this.lastHit = level().getGameTime();
        }
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return attacker instanceof Player player && !level().mayInteract(player, blockPosition());
    }

    // Armor stand

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        this.yBodyRotO = this.yRotO;
        this.yBodyRot = getYRot();
        return 0.0F;
    }

    @Override
    public void setYBodyRot(float rotation) {
        this.yBodyRotO = this.yRotO = rotation;
        this.yHeadRotO = this.yHeadRot = rotation;
    }

    @Override
    public void setYHeadRot(float rotation) {
        this.yBodyRotO = this.yRotO = rotation;
        this.yHeadRotO = this.yHeadRot = rotation;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.HATTERS_STAND.get());
    }

    // Save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ItemStack worn = this.armor.get(HEAD);
        if (!worn.isEmpty()) tag.put(HEAD_KEY, worn.save(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.armor.set(HEAD, ItemStack.parseOptional(registryAccess(), tag.getCompound(HEAD_KEY)));
    }
}
