package com.chino.persistentframes.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PersistentFramesMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"), cancellable = true)
    private void onEntitiesDestroy(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        if (this.level != null) {
            Minecraft client = Minecraft.getInstance();
            IntList ids = packet.getEntityIds();

            for (int i = 0; i < ids.size(); i++) {
                int id = ids.getInt(i);
                Entity entity = this.level.getEntity(id);
                
                if (entity instanceof ItemFrame) {
                    // Check if the player is currently hitting the block/entity
                    // If they are actively destroying it, we shouldn't cancel it.
                    if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY) {
                        return;
                    }

                    // Cancel the packet to prevent the server from despawning the item frame
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
