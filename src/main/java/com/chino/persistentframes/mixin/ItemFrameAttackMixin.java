package com.chino.persistentframes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class ItemFrameAttackMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttackEntity(Player player, Entity entity, CallbackInfo ci) {
        if (entity instanceof ItemFrame itemFrame) {
            // If the player punches an empty item frame (or a phantom/ghost frame),
            // remove it immediately on the client for zero-latency response and phantom cleanup.
            if (itemFrame.getItem().isEmpty() && this.minecraft.level != null) {
                this.minecraft.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
    }
}
