package com.chino.persistentframes.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ItemFrameAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Player player, Entity entity, CallbackInfo ci) {
        if (entity instanceof ItemFrame itemFrame) {
            PersistentFramesMixin.registerPendingInteraction(itemFrame.getId());
        }
    }

    @Inject(method = "interact", at = @At("HEAD"))
    private void onInteract(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (entity instanceof ItemFrame itemFrame) {
            PersistentFramesMixin.registerPendingInteraction(itemFrame.getId());
        }
    }
}
