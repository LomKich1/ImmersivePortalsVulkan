package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

@Mixin(
   targets = {"net/minecraft/ChunkLevelManager.NearbyChunkTicketUpdater"}
)
public class MixinPlayerTicketTracker {
   @Inject(
      method = {"Lnet/minecraft/ChunkLevelManager.NearbyChunkTicketUpdater;onDistanceChange(JII)V", "setWatchDistance", "Lnet/minecraft/ChunkLevelManager.NearbyChunkTicketUpdater;updateTicket(JIZZ)V", "updateLevels"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInject(CallbackInfo ci) {
      if (IPConfig.getConfig().enableImmPtlChunkLoading) {
         ci.cancel();
      }
   }
}
