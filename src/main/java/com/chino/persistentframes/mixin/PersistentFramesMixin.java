package com.chino.persistentframes.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ClientPacketListener.class)
public abstract class PersistentFramesMixin {

    @Shadow
    private ClientLevel level;

    @Unique
    private static final IntSet RECENT_BREAK_ENTITY_IDS = IntSets.synchronize(new IntOpenHashSet());

    @Unique
    private static final Map<Vec3, Long> RECENT_BREAK_POSITIONS = new ConcurrentHashMap<>();

    @Unique
    private static final long BREAK_EVENT_TIMEOUT_MS = 1500L;

    @Unique
    private static boolean isFrameBreakSound(Holder<SoundEvent> soundHolder) {
        if (soundHolder == null) {
            return false;
        }
        SoundEvent sound = soundHolder.value();
        return sound == SoundEvents.ITEM_FRAME_BREAK
                || sound == SoundEvents.GLOW_ITEM_FRAME_BREAK
                || sound == SoundEvents.ITEM_FRAME_REMOVE_ITEM
                || sound == SoundEvents.GLOW_ITEM_FRAME_REMOVE_ITEM;
    }

    @Unique
    private static void recordBreakPosition(double x, double y, double z) {
        long now = System.currentTimeMillis();
        RECENT_BREAK_POSITIONS.entrySet().removeIf(entry -> now - entry.getValue() > BREAK_EVENT_TIMEOUT_MS);
        RECENT_BREAK_POSITIONS.put(new Vec3(x, y, z), now);
    }

    @Unique
    private static boolean wasRecentlyBroken(int entityId, Vec3 framePos) {
        if (RECENT_BREAK_ENTITY_IDS.remove(entityId)) {
            return true;
        }

        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Vec3, Long>> it = RECENT_BREAK_POSITIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Vec3, Long> entry = it.next();
            if (now - entry.getValue() > BREAK_EVENT_TIMEOUT_MS) {
                it.remove();
                continue;
            }
            if (entry.getKey().distanceToSqr(framePos) <= 4.0) { // within 2.0 blocks radius
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean isPlayerTargeting(Minecraft client, ItemFrame itemFrame) {
        HitResult hitResult = client.hitResult;
        if (hitResult instanceof EntityHitResult ehr && ehr.getEntity() == itemFrame) {
            return true;
        }
        if (hitResult instanceof BlockHitResult bhr) {
            BlockPos supportPos = itemFrame.getPos().relative(itemFrame.getDirection().getOpposite());
            return bhr.getBlockPos().equals(supportPos);
        }
        return false;
    }

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void onSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (isFrameBreakSound(packet.getSound())) {
            recordBreakPosition(packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"))
    private void onSoundEntityEvent(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (isFrameBreakSound(packet.getSound())) {
            RECENT_BREAK_ENTITY_IDS.add(packet.getId());
        }
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    private void onAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (packet.getType() == EntityType.ITEM) {
            recordBreakPosition(packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"), cancellable = true)
    private void onEntitiesDestroy(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        if (this.level == null) {
            return;
        }

        IntList ids = packet.getEntityIds();
        boolean hasFrame = false;

        // Fast-path: check if any entity in the packet is an ItemFrame
        for (int i = 0; i < ids.size(); i++) {
            Entity entity = this.level.getEntity(ids.getInt(i));
            if (entity instanceof ItemFrame) {
                hasFrame = true;
                break;
            }
        }

        // If no frames in packet, let vanilla execute with zero overhead
        if (!hasFrame) {
            return;
        }

        // Intercept packet and handle entity removals individually
        ci.cancel();
        Minecraft client = Minecraft.getInstance();

        for (int i = 0; i < ids.size(); i++) {
            int id = ids.getInt(i);
            Entity entity = this.level.getEntity(id);

            if (entity == null) {
                continue;
            }

            if (entity instanceof ItemFrame itemFrame) {
                // If the frame was legitimately broken (break sound, item drop, or player crosshair target)
                if (wasRecentlyBroken(id, itemFrame.position()) || isPlayerTargeting(client, itemFrame)) {
                    this.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
                    continue;
                }

                // Server distance culling -> preserve the item frame in client memory!
                continue;
            }

            // Normal entities (zombies, dropped items, arrows, etc.) -> remove normally
            this.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
    }
}
