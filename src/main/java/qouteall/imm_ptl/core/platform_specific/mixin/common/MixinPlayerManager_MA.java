package qouteall.imm_ptl.core.platform_specific.mixin.common;

import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;

@Mixin({PlayerManager.class})
public class MixinPlayerManager_MA {
   @Inject(
      method = {"respawnPlayer"},
      at = @At("HEAD")
   )
   private void onPlayerRespawn(ServerPlayerEntity oldPlayer, boolean bl, RemovalReason removalReason, CallbackInfoReturnable<ServerPlayerEntity> cir) {
      ImmPtlChunkTracking.removePlayerFromChunkTrackersAndEntityTrackers(oldPlayer);
   }

   @Inject(
      method = {"remove"},
      at = @At("HEAD")
   )
   private void onPlayerDisconnect(ServerPlayerEntity player, CallbackInfo ci) {
      ImmPtlChunkTracking.removePlayerFromChunkTrackersAndEntityTrackers(player);
   }
}
